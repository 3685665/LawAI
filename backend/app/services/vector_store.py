import json
import math
import unicodedata
from dataclasses import dataclass
from pathlib import Path

import psycopg

from app.models.schemas import KnowledgeDocumentRequest, PrecedentDto
from app.settings import settings

STORE_PATH = Path(__file__).resolve().parents[2] / "data" / "vector_store.json"
STOPWORDS = {
    "bir", "bu", "su", "şu", "ve", "veya", "ile", "icin", "için", "gibi", "daha", "kisa", "kısa",
    "cevap", "ver", "nedir", "nelerdir", "hangi", "hakkinda", "hakkında", "merhaba", "selam",
    "bana", "bunu", "onu", "olarak", "gore", "göre", "var", "yok",
}


def normalize(value: str | None) -> str:
    if not value:
        return ""
    ascii_value = unicodedata.normalize("NFD", value.lower())
    ascii_value = "".join(ch for ch in ascii_value if unicodedata.category(ch) != "Mn")
    return ascii_value.translate(str.maketrans({"ı": "i", "ğ": "g", "ü": "u", "ş": "s", "ö": "o", "ç": "c"}))


def tokenize(value: str | None) -> set[str]:
    normalized = normalize(value)
    cleaned = "".join(ch if ch.isalnum() else " " for ch in normalized)
    return {token for token in cleaned.split() if len(token) > 2 and token not in STOPWORDS}


def cosine_similarity(left: list[float], right: list[float]) -> float:
    if not left or not right or len(left) != len(right):
        return 0.0
    dot = sum(a * b for a, b in zip(left, right))
    left_norm = math.sqrt(sum(a * a for a in left))
    right_norm = math.sqrt(sum(b * b for b in right))
    if not left_norm or not right_norm:
        return 0.0
    return dot / (left_norm * right_norm)


@dataclass
class VectorEntry:
    document: KnowledgeDocumentRequest
    embedding: list[float]


class JsonVectorStore:
    def __init__(self) -> None:
        self._entries: list[VectorEntry] = self._load()

    def save_all(self, documents: list[KnowledgeDocumentRequest], embeddings: list[list[float]]) -> int:
        for document, embedding in zip(documents, embeddings):
            self._entries.append(VectorEntry(document=document, embedding=embedding))
        self._persist()
        return len(documents)

    def has_entries(self) -> bool:
        return bool(self._entries)

    def clear(self) -> None:
        self._entries = []
        STORE_PATH.unlink(missing_ok=True)

    def search(
        self,
        query_embedding: list[float],
        court: str | None,
        chamber: str | None,
        limit: int,
        query_text: str | None = None,
    ) -> list[PrecedentDto]:
        court_filter = normalize(court)
        chamber_filter = normalize(chamber)
        query_tokens = tokenize(query_text)
        ranked: list[tuple[float, VectorEntry]] = []

        for entry in self._entries:
            doc = entry.document
            if court_filter and court_filter not in normalize(doc.court):
                continue
            if chamber_filter and chamber_filter not in normalize(doc.chamber):
                continue
            if query_tokens:
                document_tokens = tokenize(" ".join([doc.topic, doc.summary, doc.content]))
                if not query_tokens.intersection(document_tokens):
                    continue
            score = cosine_similarity(query_embedding, entry.embedding)
            if score > 0:
                ranked.append((score, entry))

        ranked.sort(key=lambda item: item[0], reverse=True)
        return [self._to_precedent(entry.document) for _, entry in ranked[:limit]]

    def _to_precedent(self, doc: KnowledgeDocumentRequest) -> PrecedentDto:
        return PrecedentDto(
            court=doc.court or "Yuklenen kaynak",
            chamber=doc.chamber,
            docketNo=doc.docketNo,
            decisionNo=doc.decisionNo,
            date=doc.date,
            topic=doc.topic,
            summary=doc.summary,
        )

    def _load(self) -> list[VectorEntry]:
        if not STORE_PATH.exists():
            return []
        try:
            raw_entries = json.loads(STORE_PATH.read_text(encoding="utf-8"))
            return [
                VectorEntry(
                    document=KnowledgeDocumentRequest.model_validate(item["document"]),
                    embedding=[float(value) for value in item["embedding"]],
                )
                for item in raw_entries
            ]
        except Exception:
            return []

    def _persist(self) -> None:
        STORE_PATH.parent.mkdir(parents=True, exist_ok=True)
        payload = [
            {
                "document": entry.document.model_dump(),
                "embedding": entry.embedding,
            }
            for entry in self._entries
        ]
        STORE_PATH.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")


class PgVectorStore:
    def __init__(self) -> None:
        self._ready = False

    def save_all(self, documents: list[KnowledgeDocumentRequest], embeddings: list[list[float]]) -> int:
        self._ensure_schema()
        rows = [
            (
                document.sourceType,
                document.court,
                document.chamber,
                document.docketNo,
                document.decisionNo,
                document.date,
                document.topic,
                document.summary,
                document.content,
                self._to_vector_literal(embedding),
            )
            for document, embedding in zip(documents, embeddings)
        ]
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.executemany(
                    """
                    INSERT INTO knowledge_documents (
                        source_type, court, chamber, docket_no, decision_no, decision_date,
                        topic, summary, content, embedding
                    )
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s::vector)
                    """,
                    rows,
                )
            conn.commit()
        return len(rows)

    def has_entries(self) -> bool:
        self._ensure_schema()
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT EXISTS (SELECT 1 FROM knowledge_documents LIMIT 1)")
                return bool(cur.fetchone()[0])

    def clear(self) -> None:
        self._ensure_schema()
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute("TRUNCATE TABLE knowledge_documents")
            conn.commit()

    def search(
        self,
        query_embedding: list[float],
        court: str | None,
        chamber: str | None,
        limit: int,
        query_text: str | None = None,
    ) -> list[PrecedentDto]:
        self._ensure_schema()
        query_tokens = tokenize(query_text)
        if query_text and not query_tokens:
            return []
        filters: list[str] = []
        params: list[object] = []
        if court:
            filters.append("lower(coalesce(court, '')) LIKE lower(%s)")
            params.append(f"%{court}%")
        if chamber:
            filters.append("lower(coalesce(chamber, '')) LIKE lower(%s)")
            params.append(f"%{chamber}%")
        where_clause = f"WHERE {' AND '.join(filters)}" if filters else ""
        params.extend([self._to_vector_literal(query_embedding), max(limit * 3, limit)])
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    f"""
                    SELECT court, chamber, docket_no, decision_no, decision_date, topic, summary, content
                    FROM knowledge_documents
                    {where_clause}
                    ORDER BY embedding <=> %s::vector
                    LIMIT %s
                    """,
                    params,
                )
                rows = cur.fetchall()

        results: list[PrecedentDto] = []
        for row in rows:
            row_court, row_chamber, docket_no, decision_no, decision_date, topic, summary, content = row
            if query_tokens and not query_tokens.intersection(tokenize(" ".join([topic, summary, content]))):
                continue
            results.append(
                PrecedentDto(
                    court=row_court or "Yuklenen kaynak",
                    chamber=row_chamber,
                    docketNo=docket_no,
                    decisionNo=decision_no,
                    date=str(decision_date) if decision_date else None,
                    topic=topic,
                    summary=summary,
                )
            )
            if len(results) >= limit:
                break
        return results

    def _ensure_schema(self) -> None:
        if self._ready:
            return
        with self._connect() as conn:
            with conn.cursor() as cur:
                cur.execute("CREATE EXTENSION IF NOT EXISTS vector")
                dimensions = int(settings.embedding_dimensions)
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
                        embedding vector({dimensions}) NOT NULL,
                        created_at timestamptz NOT NULL DEFAULT now()
                    )
                    """
                )
                cur.execute(
                    """
                    CREATE INDEX IF NOT EXISTS knowledge_documents_embedding_idx
                    ON knowledge_documents USING hnsw (embedding vector_cosine_ops)
                    """
                )
            conn.commit()
        self._ready = True

    def _connect(self):
        return psycopg.connect(settings.database_url)

    def _to_vector_literal(self, embedding: list[float]) -> str:
        return "[" + ",".join(f"{value:.8f}" for value in embedding) + "]"


def create_vector_store():
    if settings.vector_store.lower() == "pgvector":
        return PgVectorStore()
    return JsonVectorStore()


vector_store = create_vector_store()
