from pydantic import BaseModel, Field


class PrecedentDto(BaseModel):
    court: str
    chamber: str | None = None
    docketNo: str | None = None
    decisionNo: str | None = None
    date: str | None = None
    topic: str
    summary: str
    content: str | None = None


class ChatRequest(BaseModel):
    question: str = Field(min_length=3)
    mode: str = "analysis"
    privateMode: bool = True


class ChatResponse(BaseModel):
    answer: str
    citations: list[PrecedentDto]
    nextSteps: list[str]
    disclaimer: str


class PrecedentSearchRequest(BaseModel):
    query: str = ""
    court: str | None = None
    chamber: str | None = None
    docketNo: str | None = None
    decisionNo: str | None = None
    dateFrom: str | None = None
    dateTo: str | None = None
    limit: int | None = 5


class PrecedentSearchResponse(BaseModel):
    query: str
    results: list[PrecedentDto]


class PrecedentSummarizeRequest(BaseModel):
    court: str = Field(min_length=1)
    chamber: str | None = None
    docketNo: str | None = None
    decisionNo: str | None = None
    date: str | None = None
    topic: str = Field(min_length=1)
    summary: str | None = None
    content: str = Field(min_length=20)


class PrecedentSummarizeResponse(BaseModel):
    summary: str
    disclaimer: str


class PetitionCaseContext(BaseModel):
    caseId: str | None = None
    caseType: str | None = None
    caseLabel: str | None = None
    clientName: str | None = None
    opponentName: str | None = None
    courtName: str | None = None
    subject: str | None = None
    summary: str | None = None
    petitionType: str | None = None
    petitionFacts: str | None = None
    petitionDemands: str | None = None


class PrecedentApplyRequest(BaseModel):
    court: str = Field(min_length=1)
    chamber: str | None = None
    docketNo: str | None = None
    decisionNo: str | None = None
    date: str | None = None
    topic: str = Field(min_length=1)
    summary: str | None = None
    content: str = Field(min_length=20)
    aiSummary: str | None = None
    caseContext: PetitionCaseContext


class PrecedentApplyResponse(BaseModel):
    applicationNote: str
    legalGroundsSnippet: str
    factsLinkSnippet: str
    citationLine: str
    disclaimer: str


class PetitionRequest(BaseModel):
    petitionType: str
    court: str
    parties: str
    facts: str
    demands: str | None = None


class PetitionResponse(BaseModel):
    title: str
    body: str
    citedPrecedents: list[PrecedentDto]


class KnowledgeDocumentRequest(BaseModel):
    sourceType: str = "precedent"
    court: str | None = None
    chamber: str | None = None
    docketNo: str | None = None
    decisionNo: str | None = None
    date: str | None = None
    topic: str
    summary: str
    content: str


class KnowledgeIngestRequest(BaseModel):
    documents: list[KnowledgeDocumentRequest]


class KnowledgeIngestResponse(BaseModel):
    indexed: int
    storage: str
    message: str


class DocumentAnalysisResponse(BaseModel):
    filename: str
    size: int
    contentType: str
    detectedIssues: list[str]
    summary: str


class DocumentIngestResponse(BaseModel):
    filename: str
    size: int
    contentType: str
    extractedCharacters: int
    chunkCount: int
    indexed: int
    storage: str
    message: str
    textPreview: str
    warnings: list[str]


class PdfTextExtractionResponse(BaseModel):
    filename: str
    extractedCharacters: int
    text: str
