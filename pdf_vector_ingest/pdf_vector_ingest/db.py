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
                    CREATE TABLE IF NOT EXISTS knowledge_documents (
                        id bigserial PRIMARY KEY,
                        source_type text NOT NULL,
                        court text,
                        chamber text,
                        docket_no text,
                        decision_no text,
                        decision_date text,
                        topic text NOT NULL,
                        summary text NOT NULL,
                        content text NOT NULL,
                        embedding vector({settings.embedding_dimensions}) NOT NULL,
                        created_at timestamptz NOT NULL DEFAULT now()
                    )
                    """
                )
                cur.execute("ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS source_path text")
                cur.execute("ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS source_sha256 text")
                cur.execute("ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS chunk_index integer")
                cur.execute("ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS chunk_count integer")
                cur.execute(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS knowledge_documents_source_chunk_idx
                    ON knowledge_documents (source_path, chunk_index)
                    WHERE source_path IS NOT NULL AND chunk_index IS NOT NULL
                    """
                )
                cur.execute(
                    """
                    CREATE INDEX IF NOT EXISTS knowledge_documents_embedding_idx
                    ON knowledge_documents USING hnsw (embedding vector_cosine_ops)
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

    def mark_indexed(self, record: FileRecord, chunk_count: int) -> None:
        self._update_file(record, "indexed", chunk_count, None)

    def mark_failed(self, record: FileRecord, error: str) -> None:
        self._update_file(record, "failed", 0, error[:2000])

    def insert_chunks(self, chunks: list[PdfChunk], embeddings: list[list[float]]) -> int:
        self.ensure_schema()
        rows = [
            (
                "pdf_bulk",
                chunk.topic,
                chunk.summary,
                chunk.content,
                self._to_vector_literal(embedding),
                chunk.source_path,
                chunk.source_sha256,
                chunk.chunk_index,
                chunk.chunk_count,
            )
            for chunk, embedding in zip(chunks, embeddings)
        ]
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.executemany(
                    """
                    INSERT INTO knowledge_documents (
                        source_type, topic, summary, content, embedding,
                        source_path, source_sha256, chunk_index, chunk_count
                    )
                    VALUES (%s, %s, %s, %s, %s::vector, %s, %s, %s, %s)
                    ON CONFLICT (source_path, chunk_index)
                    WHERE source_path IS NOT NULL AND chunk_index IS NOT NULL
                    DO UPDATE SET
                        source_sha256 = EXCLUDED.source_sha256,
                        chunk_count = EXCLUDED.chunk_count,
                        topic = EXCLUDED.topic,
                        summary = EXCLUDED.summary,
                        content = EXCLUDED.content,
                        embedding = EXCLUDED.embedding
                    """,
                    rows,
                )
            conn.commit()
        return len(rows)

    def _update_file(self, record: FileRecord, status: str, chunk_count: int, error: str | None) -> None:
        self.ensure_schema()
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO pdf_ingest_files (source_path, source_sha256, status, chunk_count, error)
                    VALUES (%s, %s, %s, %s, %s)
                    ON CONFLICT (source_path) DO UPDATE
                    SET source_sha256 = EXCLUDED.source_sha256,
                        status = EXCLUDED.status,
                        chunk_count = EXCLUDED.chunk_count,
                        error = EXCLUDED.error,
                        updated_at = now()
                    """,
                    (str(record.path), record.sha256, status, chunk_count, error),
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

