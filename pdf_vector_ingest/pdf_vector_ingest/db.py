import hashlib
from dataclasses import dataclass
from pathlib import Path

import psycopg

from pdf_vector_ingest.pdf import PdfChunk
from pdf_vector_ingest.settings import settings


@dataclass(frozen=True)
class FileRecord:
    path: Path
    sha256: str


@dataclass(frozen=True)
class StoredChunk:
    id: int
    document_id: int
    chunk_index: int
    content: str


class PgVectorRepository:
    def __init__(self) -> None:
        self._ready = False

    def ensure_schema(self) -> None:
        if self._ready:
            return
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute("CREATE EXTENSION IF NOT EXISTS vector")
                cur.execute(
                    f"""
                    CREATE TABLE IF NOT EXISTS legal_documents (
                        id bigserial PRIMARY KEY,
                        filename text NOT NULL,
                        content_type text NOT NULL,
                        size_bytes bigint NOT NULL,
                        stored_path text NOT NULL,
                        extracted_text text NOT NULL,
                        created_at timestamptz NOT NULL DEFAULT now()
                    )
                    """
                )
                cur.execute(
                    f"""
                    CREATE TABLE IF NOT EXISTS legal_document_chunks (
                        id bigserial PRIMARY KEY,
                        document_id bigint NOT NULL REFERENCES legal_documents(id) ON DELETE CASCADE,
                        chunk_index integer NOT NULL,
                        content text NOT NULL,
                        embedding vector({settings.embedding_dimensions}) NOT NULL,
                        created_at timestamptz NOT NULL DEFAULT now(),
                        UNIQUE (document_id, chunk_index)
                    )
                    """
                )
                cur.execute(
                    """
                    CREATE INDEX IF NOT EXISTS legal_document_chunks_embedding_idx
                    ON legal_document_chunks USING hnsw (embedding vector_cosine_ops)
                    """
                )
                cur.execute(
                    """
                    CREATE TABLE IF NOT EXISTS pdf_ingest_files (
                        source_path text PRIMARY KEY,
                        source_sha256 text NOT NULL,
                        status text NOT NULL,
                        chunk_count integer NOT NULL DEFAULT 0,
                        error text,
                        attempts integer NOT NULL DEFAULT 0,
                        updated_at timestamptz NOT NULL DEFAULT now()
                    )
                    """
                )
                cur.execute("ALTER TABLE pdf_ingest_files ADD COLUMN IF NOT EXISTS document_id bigint")
            conn.commit()
        self._ready = True

    def already_indexed(self, source_path: str, source_sha256: str) -> bool:
        self.ensure_schema()
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT status = 'indexed'
                    FROM pdf_ingest_files
                    WHERE source_path = %s AND source_sha256 = %s
                    """,
                    (source_path, source_sha256),
                )
                row = cur.fetchone()
                return bool(row and row[0])

    def mark_processing(self, record: FileRecord) -> None:
        self.ensure_schema()
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO pdf_ingest_files (source_path, source_sha256, status, attempts)
                    VALUES (%s, %s, 'processing', 1)
                    ON CONFLICT (source_path) DO UPDATE
                    SET source_sha256 = EXCLUDED.source_sha256,
                        status = 'processing',
                        attempts = pdf_ingest_files.attempts + 1,
                        error = NULL,
                        updated_at = now()
                    """,
                    (str(record.path), record.sha256),
                )
            conn.commit()

    def mark_indexed(self, record: FileRecord, document_id: int, chunk_count: int) -> None:
        self._update_file(record, "indexed", document_id, chunk_count, None)

    def mark_failed(self, record: FileRecord, error: str) -> None:
        self._update_file(record, "failed", None, 0, error[:2000])

    def create_document(self, record: FileRecord, text: str) -> int:
        self.ensure_schema()
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO legal_documents (filename, content_type, size_bytes, stored_path, extracted_text)
                    VALUES (%s, %s, %s, %s, %s)
                    RETURNING id
                    """,
                    (record.path.name, "application/pdf", record.path.stat().st_size, str(record.path), text),
                )
                document_id = int(cur.fetchone()[0])
            conn.commit()
        return document_id

    def insert_chunks(self, document_id: int, chunks: list[PdfChunk], embeddings: list[list[float]]) -> list[StoredChunk]:
        self.ensure_schema()
        rows = [
            (
                document_id,
                chunk.chunk_index,
                chunk.content,
                self._to_vector_literal(embedding),
            )
            for chunk, embedding in zip(chunks, embeddings)
        ]
        stored: list[StoredChunk] = []
        with self._connect() as conn:
            with conn.cursor() as cur:
                for row in rows:
                    cur.execute(
                        """
                        INSERT INTO legal_document_chunks (document_id, chunk_index, content, embedding)
                        VALUES (%s, %s, %s, %s::vector)
                        ON CONFLICT (document_id, chunk_index) DO UPDATE SET
                            content = EXCLUDED.content,
                            embedding = EXCLUDED.embedding
                        RETURNING id, document_id, chunk_index, content
                        """,
                        row,
                    )
                    returned = cur.fetchone()
                    stored.append(StoredChunk(int(returned[0]), int(returned[1]), int(returned[2]), str(returned[3])))
            conn.commit()
        return stored

    def delete_document_chunks(self, document_id: int) -> None:
        self.ensure_schema()
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute("DELETE FROM legal_document_chunks WHERE document_id = %s", (document_id,))
            conn.commit()

    def _update_file(
        self,
        record: FileRecord,
        status: str,
        document_id: int | None,
        chunk_count: int,
        error: str | None,
    ) -> None:
        self.ensure_schema()
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO pdf_ingest_files (source_path, source_sha256, status, document_id, chunk_count, error)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    ON CONFLICT (source_path) DO UPDATE
                    SET
                        source_sha256 = EXCLUDED.source_sha256,
                        status = EXCLUDED.status,
                        document_id = EXCLUDED.document_id,
                        chunk_count = EXCLUDED.chunk_count,
                        error = EXCLUDED.error,
                        updated_at = now()
                    """,
                    (str(record.path), record.sha256, status, document_id, chunk_count, error),
                )
            conn.commit()

    def _connect(self):
        return psycopg.connect(settings.database_url)

    def _to_vector_literal(self, embedding: list[float]) -> str:
        return "[" + ",".join(f"{value:.8f}" for value in embedding) + "]"


def file_record(path: Path) -> FileRecord:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return FileRecord(path=path.resolve(), sha256=digest.hexdigest())
