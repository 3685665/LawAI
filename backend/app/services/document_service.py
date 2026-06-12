import io
from pathlib import Path

import docx2txt
from fastapi import UploadFile
from langchain_text_splitters import RecursiveCharacterTextSplitter
from pypdf import PdfReader

from app.models.schemas import (
    DocumentAnalysisResponse,
    DocumentIngestResponse,
    KnowledgeDocumentRequest,
    KnowledgeIngestRequest,
    PdfTextExtractionResponse,
)
from app.i18n import t
from app.services.legal_service import legal_service
from app.settings import settings

SUPPORTED_EXTENSIONS = {".pdf", ".doc", ".docx", ".txt"}
MIN_EXTRACTED_CHARACTERS = 40


class DocumentService:
    async def extract_pdf_text(self, file: UploadFile) -> PdfTextExtractionResponse:
        return await self.extract_text(file)

    async def extract_text(self, file: UploadFile) -> PdfTextExtractionResponse:
        content = await file.read()
        filename = file.filename or "document"
        extension = Path(filename).suffix.lower()
        if extension not in SUPPORTED_EXTENSIONS:
            raise ValueError(t("document_unsupported"))
        if not content:
            raise ValueError(t("document_empty"))

        text = self._extract_text(filename, content)
        return PdfTextExtractionResponse(
            filename=filename,
            extractedCharacters=len(text),
            text=text,
        )

    async def analyze(self, file: UploadFile) -> DocumentAnalysisResponse:
        content = await file.read()
        filename = file.filename or t("uploaded_document")
        content_type = file.content_type or "application/octet-stream"
        issues = self._validate(filename, content)
        summary = t("document_review_warning")

        if not issues:
            try:
                text = self._extract_text(filename, content)
                summary = (
                    t("document_accepted", characters=len(text))
                    if text else t("document_no_readable_text")
                )
            except Exception as exc:
                issues.append(t("document_extraction_failed", detail=exc))
                summary = t("document_unreadable")

        return DocumentAnalysisResponse(
            filename=filename,
            size=len(content),
            contentType=content_type,
            detectedIssues=issues,
            summary=summary,
        )

    async def ingest(self, file: UploadFile, topic: str | None, court: str | None) -> DocumentIngestResponse:
        content = await file.read()
        filename = file.filename or t("uploaded_document")
        content_type = file.content_type or "application/octet-stream"
        warnings = self._validate(filename, content)
        if warnings:
            raise ValueError(" ".join(warnings))

        extracted_text = self._extract_text(filename, content)
        if len(extracted_text) < MIN_EXTRACTED_CHARACTERS:
            raise ValueError(t("document_insufficient_text"))

        cleaned_text = self._clean_text(extracted_text)
        splitter = RecursiveCharacterTextSplitter(chunk_size=1200, chunk_overlap=150)
        chunks = splitter.split_text(cleaned_text)
        document_topic = topic.strip() if topic and topic.strip() else Path(filename).stem
        documents = [
            KnowledgeDocumentRequest(
                sourceType="upload",
                court=court,
                topic=t("chunk_topic", topic=document_topic, index=index + 1, total=len(chunks)),
                summary=self._preview(chunk, 700),
                content=chunk,
            )
            for index, chunk in enumerate(chunks)
        ]
        ingest_response = legal_service.ingest_knowledge(KnowledgeIngestRequest(documents=documents))
        if ingest_response.indexed == 0:
            raise ValueError(ingest_response.message)

        return DocumentIngestResponse(
            filename=filename,
            size=len(content),
            contentType=content_type,
            extractedCharacters=len(cleaned_text),
            chunkCount=len(chunks),
            indexed=ingest_response.indexed,
            storage=ingest_response.storage,
            message=ingest_response.message,
            textPreview=self._preview(cleaned_text, 500),
            warnings=[],
        )

    def _validate(self, filename: str, content: bytes) -> list[str]:
        issues: list[str] = []
        extension = Path(filename).suffix.lower()
        max_bytes = settings.max_upload_mb * 1024 * 1024
        if not content:
            issues.append(t("document_empty"))
        if len(content) > max_bytes:
            issues.append(t("document_too_large", max_mb=settings.max_upload_mb))
        if extension not in SUPPORTED_EXTENSIONS:
            issues.append(t("document_unsupported"))
        return issues

    def _extract_text(self, filename: str, content: bytes) -> str:
        extension = Path(filename).suffix.lower()
        if extension == ".pdf":
            reader = PdfReader(io.BytesIO(content))
            return "\n".join(page.extract_text() or "" for page in reader.pages).strip()
        if extension == ".txt":
            return content.decode("utf-8", errors="ignore").strip()
        if extension in {".doc", ".docx"}:
            return docx2txt.process(io.BytesIO(content)).strip()
        return ""

    def _preview(self, content: str, length: int) -> str:
        return content if len(content) <= length else f"{content[:length]}..."

    def _clean_text(self, content: str) -> str:
        lines = [" ".join(line.split()) for line in content.splitlines()]
        paragraphs: list[str] = []
        current: list[str] = []
        for line in lines:
            if not line:
                if current:
                    paragraphs.append(" ".join(current))
                    current = []
                continue
            current.append(line)
        if current:
            paragraphs.append(" ".join(current))
        return "\n\n".join(paragraphs).strip()


document_service = DocumentService()
