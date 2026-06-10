from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

from app.models.schemas import (
    ChatRequest,
    ChatResponse,
    KnowledgeIngestRequest,
    KnowledgeIngestResponse,
    PetitionRequest,
    PetitionResponse,
    PdfTextExtractionResponse,
    PrecedentApplyRequest,
    PrecedentApplyResponse,
    PrecedentSummarizeRequest,
    PrecedentSummarizeResponse,
    LegalResearchSynthesizeRequest,
    LegalResearchSynthesizeResponse,
)
from app.services.document_service import document_service
from app.services.legal_service import legal_service

app = FastAPI(title="LawAI Next LangChain API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://localhost:3001",
        "http://127.0.0.1:3001",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "lawai-next-langchain"}


@app.post("/api/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    return legal_service.answer(request)


@app.post("/api/precedents/summarize", response_model=PrecedentSummarizeResponse)
def summarize_precedent(request: PrecedentSummarizeRequest) -> PrecedentSummarizeResponse:
    return legal_service.summarize_precedent(request)


@app.post("/api/precedents/apply-to-petition", response_model=PrecedentApplyResponse)
def apply_precedent_to_petition(request: PrecedentApplyRequest) -> PrecedentApplyResponse:
    return legal_service.apply_precedent_to_petition(request)


@app.post("/api/petitions", response_model=PetitionResponse)
def petitions(request: PetitionRequest) -> PetitionResponse:
    return legal_service.generate_petition(request)


@app.post("/api/documents/extract-pdf-text", response_model=PdfTextExtractionResponse)
async def extract_pdf_text(file: UploadFile = File(...)) -> PdfTextExtractionResponse:
    try:
        return await document_service.extract_pdf_text(file)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"PDF text extraction failed: {exc}") from exc


@app.post("/api/documents/extract-text", response_model=PdfTextExtractionResponse)
async def extract_text(file: UploadFile = File(...)) -> PdfTextExtractionResponse:
    try:
        return await document_service.extract_text(file)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Document text extraction failed: {exc}") from exc


@app.post("/api/knowledge/documents", response_model=KnowledgeIngestResponse)
def ingest_knowledge(request: KnowledgeIngestRequest) -> KnowledgeIngestResponse:
    return legal_service.ingest_knowledge(request)


@app.post("/api/knowledge/seed-precedents", response_model=KnowledgeIngestResponse)
def seed_precedents() -> KnowledgeIngestResponse:
    return legal_service.seed_precedents()


@app.post("/api/research/synthesize", response_model=LegalResearchSynthesizeResponse)
def synthesize_research(request: LegalResearchSynthesizeRequest) -> LegalResearchSynthesizeResponse:
    return legal_service.synthesize_research(request)
