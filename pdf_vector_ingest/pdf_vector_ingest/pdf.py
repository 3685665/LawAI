from dataclasses import dataclass
from pathlib import Path

from langchain_text_splitters import RecursiveCharacterTextSplitter
from pypdf import PdfReader

from pdf_vector_ingest.settings import settings


@dataclass(frozen=True)
class PdfChunk:
    source_path: str
    source_sha256: str
    chunk_index: int
    chunk_count: int
    topic: str
    summary: str
    content: str


def discover_pdfs(input_path: Path) -> list[Path]:
    if input_path.is_file() and input_path.suffix.lower() == ".pdf":
        return [input_path]
    return [path for path in input_path.rglob("*.pdf") if path.is_file()]


def extract_text(path: Path) -> str:
    reader = PdfReader(str(path))
    return "\n".join(page.extract_text() or "" for page in reader.pages).strip()


def split_pdf(path: Path, source_sha256: str) -> list[PdfChunk]:
    text = extract_text(path)
    return split_text(path, source_sha256, text)


def split_text(path: Path, source_sha256: str, text: str) -> list[PdfChunk]:
    if len(text) < settings.min_extracted_characters:
        raise ValueError("PDF icinden yeterli metin cikarilamadi; taranmis PDF olabilir.")

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=settings.chunk_size,
        chunk_overlap=settings.chunk_overlap,
    )
    chunks = splitter.split_text(text)
    topic = path.stem
    return [
        PdfChunk(
            source_path=str(path),
            source_sha256=source_sha256,
            chunk_index=index,
            chunk_count=len(chunks),
            topic=f"{topic} (bolum {index + 1}/{len(chunks)})",
            summary=_preview(chunk, 700),
            content=chunk,
        )
        for index, chunk in enumerate(chunks)
    ]


def _preview(content: str, length: int) -> str:
    return content if len(content) <= length else f"{content[:length]}..."
