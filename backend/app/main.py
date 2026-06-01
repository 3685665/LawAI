from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

from app.models.schemas import (
    ChatRequest,
    ChatResponse,
    DocumentAnalysisResponse,
    DocumentIngestResponse,
    KnowledgeIngestRequest,
    KnowledgeIngestResponse,
    PetitionRequest,
    PetitionResponse,
    PrecedentSearchRequest,
    PrecedentSearchResponse,
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


@app.post("/api/precedents/search", response_model=PrecedentSearchResponse)
def search_precedents(request: PrecedentSearchRequest) -> PrecedentSearchResponse:
    return legal_service.search_precedents(request)


@app.post("/api/petitions", response_model=PetitionResponse)
def petitions(request: PetitionRequest) -> PetitionResponse:
    return legal_service.generate_petition(request)


@app.post("/api/documents/analyze", response_model=DocumentAnalysisResponse)
async def analyze_document(file: UploadFile = File(...)) -> DocumentAnalysisResponse:
    return await document_service.analyze(file)


@app.post("/api/documents/ingest", response_model=DocumentIngestResponse)
async def ingest_document(
    file: UploadFile = File(...),
    topic: str | None = Form(default=None),
    court: str | None = Form(default=None),
) -> DocumentIngestResponse:
    try:
        return await document_service.ingest(file, topic, court)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Dokuman indeksleme basarisiz: {exc}") from exc


@app.post("/api/knowledge/documents", response_model=KnowledgeIngestResponse)
def ingest_knowledge(request: KnowledgeIngestRequest) -> KnowledgeIngestResponse:
    return legal_service.ingest_knowledge(request)


@app.post("/api/knowledge/seed-precedents", response_model=KnowledgeIngestResponse)
def seed_precedents() -> KnowledgeIngestResponse:
    return legal_service.seed_precedents()
