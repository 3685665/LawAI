import hashlib
import re

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from langchain_ollama import ChatOllama, OllamaEmbeddings
from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings

from app.models.schemas import (
    ChatRequest,
    ChatResponse,
    KnowledgeDocumentRequest,
    KnowledgeIngestRequest,
    KnowledgeIngestResponse,
    PetitionRequest,
    PetitionResponse,
    PrecedentDto,
    PetitionCaseContext,
    PrecedentApplyRequest,
    PrecedentApplyResponse,
    PrecedentSummarizeRequest,
    PrecedentSummarizeResponse,
    LegalResearchSynthesizeRequest,
    LegalResearchSynthesizeResponse,
    ResearchSourceFinding,
)
from app.services.vector_store import normalize, vector_store
from app.settings import settings

DISCLAIMER = "Bu yanit hukuki danismanlik degildir; avukat denetimi ve guncel mevzuat kontrolu gerekir."
AI_REQUEST_TIMEOUT_SECONDS = 90

PETITION_META_LINE_PATTERNS = (
    r"(?i)\bai\s*model",
    r"(?i)olusturma\s*yontem",
    r"(?i)baglam\s*ekleme",
    r"(?i)premium\s*model",
    r"(?i)standart\s*model",
    r"(?i)dilekce\s*kalitesi",
    r"(?i)hizli\s*form",
    r"(?i)detayli\s*form",
    r"(?i)mevcut\s*belgeler",
    r"(?i)belge\s*yukle",
)


def _strip_petition_metadata_lines(text: str) -> str:
    lines = []
    for line in text.splitlines():
        if any(re.search(pattern, line) for pattern in PETITION_META_LINE_PATTERNS):
            continue
        lines.append(line)
    return "\n".join(lines).strip()


def _strip_markdown(text: str) -> str:
    cleaned = text.replace("**", "").replace("__", "")
    cleaned = re.sub(r"^#{1,6}\s*", "", cleaned, flags=re.MULTILINE)
    return cleaned.strip()


def _format_court_line(court: str) -> str:
    court_text = court.strip()
    if not court_text:
        return "İLGİLİ MAHKEMEYE"
    upper = court_text.upper()
    if "MAHKEMES" in upper:
        return upper
    return f"{upper} MAHKEMESİNE"


def _format_parties_block(parties: str) -> str:
    raw = parties.strip()
    if not raw:
        return "DAVACI      : ...\n             ...\n\nDAVALI      : ...\n             ..."

    lines = [line.strip() for line in raw.splitlines() if line.strip()]
    davaci: list[str] = []
    davali: list[str] = []
    current: list[str] | None = None

    for line in lines:
        lowered = line.lower()
        if lowered.startswith(("davac", "basvuru", "müvekkil", "muvekkil", "başvuru")):
            current = davaci
            cleaned = re.sub(r"^(davac[ıi]|basvuru sahibi|müvekkil|muvekkil|başvuru sahibi)\s*:\s*", "", line, flags=re.IGNORECASE).strip()
            if cleaned:
                davaci.append(cleaned)
            continue
        if lowered.startswith(("daval", "karsi taraf", "karşı taraf")):
            current = davali
            cleaned = re.sub(r"^(daval[ıi]|karsi taraf|karşı taraf)\s*:\s*", "", line, flags=re.IGNORECASE).strip()
            if cleaned:
                davali.append(cleaned)
            continue
        if current is not None:
            current.append(line)
        else:
            davaci.append(line)

    if not davali and len(davaci) > 1:
        davali = davaci[1:]
        davaci = davaci[:1]

    blocks: list[str] = []
    if davaci:
        blocks.append("DAVACI      : " + davaci[0])
        for extra in davaci[1:]:
            blocks.append("             " + extra)
    else:
        blocks.append("DAVACI      : ...")
        blocks.append("             ...")

    blocks.append("")
    if davali:
        blocks.append("DAVALI      : " + davali[0])
        for extra in davali[1:]:
            blocks.append("             " + extra)
    else:
        blocks.append("DAVALI      : ...")
        blocks.append("             ...")

    return "\n".join(blocks)


def _format_numbered_facts(facts: str) -> str:
    text = facts.strip()
    if not text:
        return "1- Olaylar kullanıcı tarafından henüz detaylandırılmamıştır."
    if re.search(r"(?m)^\s*\d+[\).\-]\s+", text):
        return text
    chunks = [chunk.strip() for chunk in re.split(r"\n\s*\n", text) if chunk.strip()]
    if len(chunks) <= 1:
        chunks = [line.strip() for line in text.splitlines() if line.strip()]
    if not chunks:
        return "1- Olaylar kullanıcı tarafından henüz detaylandırılmamıştır."
    return "\n\n".join(f"{index}- {chunk}" for index, chunk in enumerate(chunks, start=1))


def _format_demands_block(demands: str | None) -> str:
    text = (demands or "").strip()
    if not text:
        return (
            "1- Davanın kabulüne,\n"
            "2- Yargılama giderleri ve vekalet ücretinin davalı üzerinde bırakılmasına,"
        )
    if re.search(r"(?m)^\s*\d+[\).\-]\s+", text):
        return text
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    if len(lines) <= 1:
        return f"1- {text}\n2- Yargılama giderleri ve vekalet ücretinin davalı üzerinde bırakılmasına,"
    return "\n".join(f"{index}- {line}" for index, line in enumerate(lines, start=1))


def _format_precedent_legal_grounds(precedent_context: str | None) -> str:
    if not precedent_context or not precedent_context.strip():
        return ""
    return f"; dosyaya eklenen emsal değerlendirmeleri:\n{precedent_context.strip()}"


def _normalize_petition_body(body: str) -> str:
    cleaned = _strip_markdown(_strip_petition_metadata_lines(body))
    replacements = {
        r"(?i)^\s*sonuc\s+ve\s+talep\s*:?\s*$": "NETİCE VE TALEP :",
        r"(?i)^\s*netice\s+ve\s+talep\s*:?\s*$": "NETİCE VE TALEP :",
        r"(?i)^\s*hukuki\s+nedenler\s*:?\s*$": "HUKUKİ SEBEPLER :",
        r"(?i)^\s*hukuki\s+sebepler\s*:?\s*$": "HUKUKİ SEBEPLER :",
        r"(?i)^\s*aciklamalar\s*:?\s*$": "AÇIKLAMALAR :",
        r"(?i)^\s*açıklamalar\s*:?\s*$": "AÇIKLAMALAR :",
        r"(?i)^\s*deliller\s*:?\s*$": "DELİLLER :",
        r"(?i)^\s*konu\s*:?\s*$": "KONU :",
        r"(?i)^\s*taraflar\s*:?\s*$": "",
        r"(?i)^\s*mahkeme\s*:?\s*$": "",
    }
    lines = []
    for line in cleaned.splitlines():
        updated = line
        for pattern, replacement in replacements.items():
            if re.match(pattern, updated.strip()):
                updated = replacement
                break
        if updated == "":
            continue
        lines.append(updated.rstrip())
    normalized = "\n".join(lines)
    normalized = re.sub(r"\n{3,}", "\n\n", normalized).strip()
    if not normalized.startswith("T.C."):
        normalized = f"T.C.\n{normalized}"
    return normalized

SAMPLE_PRECEDENTS = [
    PrecedentDto(court="Yargitay", chamber="9. Hukuk Dairesi", docketNo="2022/1845", decisionNo="2022/7281", date="2022-06-14", topic="Ise iade", summary="Fesih nedeninin somut delillerle ispatlanamamasi halinde ise iade kosullari degerlendirilir."),
    PrecedentDto(court="Yargitay", chamber="3. Hukuk Dairesi", docketNo="2021/5120", decisionNo="2021/8944", date="2021-11-22", topic="Kira alacagi", summary="Kira bedeli ve temerrut iddiasi yazili deliller, ihtar ve odeme kayitlariyla birlikte incelenmelidir."),
    PrecedentDto(court="Danistay", chamber="8. Daire", docketNo="2020/3312", decisionNo="2021/4210", date="2021-09-30", topic="Idari islem", summary="Idari islemlerde yetki, sekil, sebep, konu ve maksat unsurlari yonunden hukuka uygunluk denetimi yapilir."),
    PrecedentDto(court="Anayasa Mahkemesi", chamber="Genel Kurul", docketNo="2019/12345", decisionNo="2022/4567", date="2022-03-18", topic="Adil yargilanma", summary="Gerekceli karar hakki ve makul sure ilkesi bireysel basvuruda birlikte degerlendirilebilir."),
    PrecedentDto(court="Rekabet Kurulu", chamber="Kurul", docketNo="21-33/446-222", decisionNo="21-33/446-222", date="2021-07-08", topic="Rekabet ihlali", summary="Pazar gucu, anlasma etkisi ve tuketici refahi uzerindeki sonuc birlikte analiz edilir."),
]


class LocalEmbeddings:
    """Small deterministic embedding fallback for offline MVP usage."""

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [self._embed(text) for text in texts]

    def embed_query(self, text: str) -> list[float]:
        return self._embed(text)

    def _embed(self, text: str) -> list[float]:
        vector = [0.0] * settings.embedding_dimensions
        tokens = normalize(text).split()
        if not tokens:
            return vector

        for token in tokens:
            digest = hashlib.sha256(token.encode("utf-8")).digest()
            index = int.from_bytes(digest[:4], "big") % settings.embedding_dimensions
            sign = 1.0 if digest[4] % 2 == 0 else -1.0
            vector[index] += sign

        norm = sum(value * value for value in vector) ** 0.5
        return [value / norm for value in vector] if norm else vector


class LegalService:
    def __init__(self) -> None:
        self.provider = settings.ai_provider.lower()
        self.chat_model = self._chat_model()
        self.embeddings = self._embeddings()

    def is_configured(self) -> bool:
        return self.chat_model is not None and self.embeddings is not None

    def answer(self, request: ChatRequest) -> ChatResponse:
        citations = self.search(request.question, None, None, 4, use_samples=False)
        answer = self._local_answer(request, citations)

        if self.chat_model and citations:
            try:
                answer = self._answer_with_ai(request, citations)
            except Exception as exc:
                answer += f"\nAI saglayicisi su anda yanit vermedi; yerel analiz modu kullanildi. Detay: {exc}"

        if request.mode.lower() == "draft":
            answer += "\nDilekce modunda, olay ozetini 'vakialar', 'hukuki nedenler' ve 'sonuc-talep' basliklarina donusturebilirim."
        if request.privateMode:
            answer += "\n\nGizli mod acik: Bu MVP, soruyu kaydetmez; kalici sohbet gecmisi eklenirse ayrica sifreleme uygulanmali."

        return ChatResponse(
            answer=answer,
            citations=citations,
            nextSteps=[
                "Soruda PDF icindeki ayirt edici ifadeleri, tarihleri ve taraf adlarini kullanin.",
                "Beklenen cevap cikmiyorsa dokumani daha temiz metin iceren PDF/TXT olarak tekrar yukleyin.",
                "Daha guclu yorumlama icin OpenAI, Gemini veya Ollama saglayicisini etkinlestirin.",
            ],
            disclaimer=DISCLAIMER,
        )

    def summarize_precedent(self, request: PrecedentSummarizeRequest) -> PrecedentSummarizeResponse:
        content = request.content.strip()
        metadata = self._format_precedent_metadata(request)
        if self.chat_model:
            try:
                summary = self._summarize_precedent_with_ai(metadata, content)
                return PrecedentSummarizeResponse(summary=summary, disclaimer=DISCLAIMER)
            except Exception as exc:
                fallback = self._local_precedent_summary(metadata, content)
                fallback += f"\n\nNOT: AI saglayicisi yanit vermedi; yerel ozet modu kullanildi. Detay: {exc}"
                return PrecedentSummarizeResponse(summary=fallback, disclaimer=DISCLAIMER)
        return PrecedentSummarizeResponse(
            summary=self._local_precedent_summary(metadata, content),
            disclaimer=DISCLAIMER,
        )

    def apply_precedent_to_petition(self, request: PrecedentApplyRequest) -> PrecedentApplyResponse:
        content = request.content.strip()
        metadata = self._format_precedent_metadata(request)
        citation_line = self._format_precedent_citation(request)
        case_context = request.caseContext
        if self.chat_model:
            try:
                applied = self._apply_precedent_with_ai(metadata, content, request.aiSummary, case_context, citation_line)
                return PrecedentApplyResponse(
                    applicationNote=applied["applicationNote"],
                    legalGroundsSnippet=applied["legalGroundsSnippet"],
                    factsLinkSnippet=applied["factsLinkSnippet"],
                    citationLine=citation_line,
                    disclaimer=DISCLAIMER,
                )
            except Exception as exc:
                fallback = self._local_precedent_application(metadata, content, case_context, citation_line)
                fallback["applicationNote"] += f"\n\nNOT: AI saglayicisi yanit vermedi; yerel eslestirme modu kullanildi. Detay: {exc}"
                return PrecedentApplyResponse(
                    applicationNote=fallback["applicationNote"],
                    legalGroundsSnippet=fallback["legalGroundsSnippet"],
                    factsLinkSnippet=fallback["factsLinkSnippet"],
                    citationLine=citation_line,
                    disclaimer=DISCLAIMER,
                )
        fallback = self._local_precedent_application(metadata, content, case_context, citation_line)
        return PrecedentApplyResponse(
            applicationNote=fallback["applicationNote"],
            legalGroundsSnippet=fallback["legalGroundsSnippet"],
            factsLinkSnippet=fallback["factsLinkSnippet"],
            citationLine=citation_line,
            disclaimer=DISCLAIMER,
        )

    def generate_petition(self, request: PetitionRequest) -> PetitionResponse:
        citations: list[PrecedentDto] = []
        body = self._local_petition(request)

        if self.chat_model:
            try:
                body = self._petition_with_ai(request)
            except Exception as exc:
                body += f"\n\nNOT: AI saglayicisi su anda yanit vermedigi icin yerel taslak modu kullanildi. Detay: {exc}"

        return PetitionResponse(
            title=f"{request.petitionType} Dilekcesi",
            body=_normalize_petition_body(body),
            citedPrecedents=citations,
        )

    def ingest_knowledge(self, request: KnowledgeIngestRequest) -> KnowledgeIngestResponse:
        if not self.embeddings:
            return KnowledgeIngestResponse(indexed=0, storage="disabled", message="AI saglayicisi yapilandirilmamis.")
        try:
            embeddings = self._embed_documents([doc.content for doc in request.documents])
        except Exception as exc:
            embeddings = LocalEmbeddings().embed_documents([doc.content for doc in request.documents])
            fallback_message = f"Embedding saglayicisi hata verdi; local embedding fallback kullanildi. Detay: {exc}"
        else:
            fallback_message = None
        try:
            indexed = vector_store.save_all(request.documents, embeddings)
        except Exception as exc:
            return KnowledgeIngestResponse(
                indexed=0,
                storage="disabled",
                message=f"Vektor deposu hata verdi: {exc}",
            )
        storage = f"pgvector/{self.provider}" if settings.vector_store.lower() == "pgvector" else f"persistent/{self.provider}"
        message = "Dokumanlar kalici vektor deposuna indekslendi."
        if fallback_message:
            storage = f"{storage}+local-fallback"
            message = fallback_message
        if self.provider == "local":
            message += " Not: local embedding yalnizca gelistirme icindir; emsal kalitesi icin OpenAI, Gemini veya Ollama embedding saglayicisi kullanin ve dokumanlari yeniden indeksleyin."
        return KnowledgeIngestResponse(indexed=indexed, storage=storage, message=message)

    def seed_precedents(self) -> KnowledgeIngestResponse:
        documents = [
            KnowledgeDocumentRequest(
                sourceType="precedent",
                court=item.court,
                chamber=item.chamber,
                docketNo=item.docketNo,
                decisionNo=item.decisionNo,
                date=item.date,
                topic=item.topic,
                summary=item.summary,
                content="\n".join([item.topic, item.summary, item.court, item.chamber or ""]),
            )
            for item in SAMPLE_PRECEDENTS
        ]
        return self.ingest_knowledge(KnowledgeIngestRequest(documents=documents))

    def synthesize_research(self, request: LegalResearchSynthesizeRequest) -> LegalResearchSynthesizeResponse:
        context = self._format_research_findings(request.sourceResults)
        if self.chat_model:
            try:
                answer = self._synthesize_research_with_ai(request.query, context)
                return LegalResearchSynthesizeResponse(answer=answer, disclaimer=DISCLAIMER)
            except Exception as exc:
                fallback = self._local_research_synthesis(request.query, context)
                fallback += f"\n\nNOT: AI saglayicisi yanit vermedi; yerel sentez modu kullanildi. Detay: {exc}"
                return LegalResearchSynthesizeResponse(answer=fallback, disclaimer=DISCLAIMER)
        return LegalResearchSynthesizeResponse(
            answer=self._local_research_synthesis(request.query, context),
            disclaimer=DISCLAIMER,
        )

    def search(self, query: str, court: str | None, chamber: str | None, limit: int, use_samples: bool = True) -> list[PrecedentDto]:
        if self.embeddings:
            try:
                if vector_store.has_entries():
                    try:
                        query_embedding = self._embed_query(query)
                    except Exception:
                        query_embedding = LocalEmbeddings().embed_query(query)
                    results = vector_store.search(query_embedding, court, chamber, limit, query)
                    if results:
                        return results
            except Exception:
                pass
        return self._search_samples(query, court, chamber, limit) if use_samples else []

    def _embed_documents(self, texts: list[str]) -> list[list[float]]:
        if self.provider == "gemini":
            return self.embeddings.embed_documents(texts, output_dimensionality=settings.embedding_dimensions)
        return self.embeddings.embed_documents(texts)

    def _embed_query(self, text: str) -> list[float]:
        if self.provider == "gemini":
            return self.embeddings.embed_query(text, output_dimensionality=settings.embedding_dimensions)
        return self.embeddings.embed_query(text)

    def _answer_with_ai(self, request: ChatRequest, citations: list[PrecedentDto]) -> str:
        prompt = (
            "Turk hukuku odakli, temkinli ve kaynak uydurmayan bir hukuk asistanisin.\n\n"
            f"Kullanici sorusu:\n{request.question}\n\n"
            f"Calisma modu: {request.mode}\nGizli mod: {request.privateMode}\n\n"
            f"RAG baglami olarak bulunan kararlar:\n{self._format_citations(citations)}\n\n"
            "Sadece verilen RAG baglamina dayan. RAG baglaminda bilgi varsa onu kullanarak cevap ver. "
            "Baglam yetersizse hangi bilginin eksik oldugunu acikca soyle. Yaniti en fazla 250 kelimeyle, net ve maddeli yaz."
        )
        response = self.chat_model.invoke(prompt)
        return str(response.content).strip() or self._local_answer(request, citations)

    def _petition_with_ai(self, request: PetitionRequest) -> str:
        precedent_context = (request.precedentContext or "").strip()
        supplementary_context = (request.supplementaryContext or "").strip()
        prompt = (
            "Sen Türkiye'de avukatlarin mahkemeye sundugu HMK m.119 uyarınca dava dilekçesi taslagı hazırlayan bir hukuk asistanısın.\n"
            "Çıktı gerçek bir dava dilekçesi gibi olmalı; özet, genel tavsiye, sistem notu veya kullanıcı arayüzü metni yazma.\n"
            "Markdown, tablo, kod bloğu veya madde işareti dışı süslü biçim kullanma.\n"
            "Olay, tarih, tutar, karar numarası, taraf adresi veya emsal uydurma. Yalnızca kullanıcı verisini kullan; eksik alanları '...' ile bırak.\n"
            "Emsal bağlamı verilmişse yalnızca o metni kullan; verilmemişse Yargıtay/Danıştay kararı uydurma.\n"
            "Hukuk davalarında ceza hukuku dili (sanık, mağdur, suç unsuru vb.) kullanma.\n"
            "AÇIKLAMALAR bölümünü numaralı paragraflar halinde yaz (1-, 2-, 3-).\n"
            "NETİCE VE TALEP bölümünde talepleri numaralı yaz; yargılama gideri ve vekalet ücretinin davalıya yükletilmesini ekle.\n"
            "Metni tam olarak şu yapıda üret:\n\n"
            "T.C.\n"
            "[MAHKEME ADI] MAHKEMESİNE\n\n"
            "DAVACI      : ...\n"
            "             ...\n\n"
            "DAVALI      : ...\n"
            "             ...\n\n"
            "KONU          : ... (HMK m.119 uyarınca)\n\n"
            "AÇIKLAMALAR   :\n\n"
            "1- ...\n\n"
            "HUKUKİ SEBEPLER : ...\n\n"
            "DELİLLER      : ...\n\n"
            "NETİCE VE TALEP :\n\n"
            "Yukarıda arz ve izah edilen nedenlerle;\n\n"
            "1- ...\n"
            "2- Yargılama giderleri ve vekalet ücretinin davalı üzerinde bırakılmasına,\n\n"
            "Karar verilmesini saygılarımla arz ve talep ederim.\n\n"
            f"Dilekçe türü: {request.petitionType}\n"
            f"Mahkeme: {request.court}\n"
            f"Taraflar:\n{request.parties}\n"
            f"Vakıalar:\n{request.facts}\n"
            f"Talepler:\n{request.demands or ''}\n"
            f"Ek dosya/emsal bağlamı:\n{precedent_context or 'Yok'}\n"
            f"Ek dosya özeti (yalnızca içerik için, metne aynen kopyalama):\n{supplementary_context or 'Yok'}"
        )
        response = self.chat_model.invoke(prompt)
        return str(response.content).strip() or self._local_petition(request)

    def _search_samples(self, query: str, court: str | None, chamber: str | None, limit: int) -> list[PrecedentDto]:
        normalized_query = normalize(query)

        def score(item: PrecedentDto) -> int:
            haystack = normalize(" ".join([item.court, item.chamber or "", item.topic, item.summary]))
            return sum(1 for term in normalized_query.split() if term and term in haystack)

        filtered = [
            item for item in SAMPLE_PRECEDENTS
            if (not court or normalize(court) in normalize(item.court))
            and (not chamber or normalize(chamber) in normalize(item.chamber))
        ]
        scored = [(score(item), item) for item in filtered]
        return [item for item_score, item in sorted(scored, key=lambda pair: pair[0], reverse=True) if item_score > 0][:limit]

    def _chat_model(self) -> BaseChatModel | None:
        if self.provider == "openai" and settings.openai_api_key:
            return ChatOpenAI(
                model=settings.openai_chat_model,
                api_key=settings.openai_api_key,
                temperature=0.2,
                timeout=AI_REQUEST_TIMEOUT_SECONDS,
                max_retries=1,
            )
        if self.provider == "gemini" and settings.google_api_key:
            return ChatGoogleGenerativeAI(
                model=settings.gemini_chat_model,
                google_api_key=settings.google_api_key,
                temperature=0.2,
                request_timeout=AI_REQUEST_TIMEOUT_SECONDS,
                retries=1,
            )
        if self.provider == "ollama":
            return ChatOllama(
                model=settings.ollama_chat_model,
                base_url=settings.ollama_base_url,
                temperature=0.2,
                num_ctx=4096,
                num_predict=900,
                keep_alive="5m",
                sync_client_kwargs={"timeout": AI_REQUEST_TIMEOUT_SECONDS},
            )
        return None

    def _embeddings(self):
        if self.provider == "openai" and settings.openai_api_key:
            return OpenAIEmbeddings(model=settings.openai_embedding_model, api_key=settings.openai_api_key)
        if self.provider == "gemini" and settings.google_api_key:
            return GoogleGenerativeAIEmbeddings(
                model=settings.gemini_embedding_model,
                google_api_key=settings.google_api_key,
                request_options={"timeout": AI_REQUEST_TIMEOUT_SECONDS},
            )
        if self.provider == "ollama":
            return OllamaEmbeddings(model=settings.ollama_embedding_model, base_url=settings.ollama_base_url)
        if self.provider == "local":
            return LocalEmbeddings()
        return None

    def _format_precedent_metadata(self, request: PrecedentSummarizeRequest) -> str:
        return "\n".join(
            [
                f"Mahkeme: {request.court}",
                f"Daire/Kurul: {request.chamber or '-'}",
                f"Esas No: {request.docketNo or '-'}",
                f"Karar No: {request.decisionNo or '-'}",
                f"Tarih: {request.date or '-'}",
                f"Konu: {request.topic}",
            ]
        )

    def _format_precedent_citation(self, request: PrecedentSummarizeRequest | PrecedentApplyRequest) -> str:
        parts = [request.court.strip()]
        if request.chamber:
            parts.append(request.chamber.strip())
        docket = request.docketNo or "-"
        decision = request.decisionNo or "-"
        return f"{' '.join(parts)}, E. {docket}, K. {decision}, T. {request.date or '-'}"

    def _format_case_context(self, case: PetitionCaseContext) -> str:
        return "\n".join(
            [
                f"Dosya: {case.caseLabel or case.caseType or '-'}",
                f"Dava konusu: {case.subject or '-'}",
                f"Dosya ozeti: {case.summary or '-'}",
                f"Davaci/Muvekkil: {case.clientName or '-'}",
                f"Davali/Karsi taraf: {case.opponentName or '-'}",
                f"Mahkeme: {case.courtName or '-'}",
                f"Dilekce turu: {case.petitionType or '-'}",
                f"Mevcut vakialar: {case.petitionFacts or '-'}",
                f"Mevcut talepler: {case.petitionDemands or '-'}",
            ]
        )

    def _apply_precedent_with_ai(
        self,
        metadata: str,
        content: str,
        ai_summary: str | None,
        case: PetitionCaseContext,
        citation_line: str,
    ) -> dict[str, str]:
        max_chars = 18000
        decision_text = content if len(content) <= max_chars else content[:max_chars] + "\n\n[Metin uzunlugu nedeniyle kirpildi...]"
        summary_block = f"\n\nKarar AI ozeti:\n{ai_summary.strip()}" if ai_summary and ai_summary.strip() else ""
        prompt = (
            "Turk hukuku baglaminda bir avukat asistanisin. Asagidaki emsal karari, secili dava dosyasi "
            "ve mevcut dilekce taslagi bilgileriyle eslestir.\n"
            "Metinde ve dosyada olmayan olay, tarih, karar veya taraf uydurma.\n"
            "Yalnizca gercekten iliskili noktalari bagla; zorla benzerlik kurma.\n"
            "Ciktiyi su basliklarla ver:\n"
            "UYGULAMA NOTU:\n"
            "(Bu emsalin bu davaya neden ve nasil uygulanabilecegi; 120-180 kelime)\n\n"
            "HUKUKI DAYANAK TASLAGI:\n"
            "(Dilekcede HUKUKI NEDENLER bolumune eklenebilecek 2-4 cumle; emsal atfi dahil)\n\n"
            "VAKIA BAGLANTISI:\n"
            "(Dosyadaki somut vakialar ile karar gerekcesi arasindaki bag; 2-3 cumle)\n\n"
            f"Emsal atif satiri: {citation_line}\n\n"
            f"Emsal bilgileri:\n{metadata}\n\n"
            f"Dava dosyasi:\n{self._format_case_context(case)}\n\n"
            f"Emsal karar metni:\n{decision_text}{summary_block}"
        )
        response = self.chat_model.invoke(prompt)
        text = str(response.content).strip()
        return self._parse_precedent_application(text, metadata, case, citation_line)

    def _parse_precedent_application(
        self,
        text: str,
        metadata: str,
        case: PetitionCaseContext,
        citation_line: str,
    ) -> dict[str, str]:
        def extract(label: str, next_labels: list[str]) -> str:
            start = text.find(label)
            if start < 0:
                return ""
            start += len(label)
            end = len(text)
            for marker in next_labels:
                index = text.find(marker, start)
                if index >= 0:
                    end = min(end, index)
            return text[start:end].strip()

        application_note = extract("UYGULAMA NOTU:", ["HUKUKI DAYANAK TASLAGI:", "VAKIA BAGLANTISI:"]) or text.strip()
        legal_grounds = extract("HUKUKI DAYANAK TASLAGI:", ["VAKIA BAGLANTISI:"])
        facts_link = extract("VAKIA BAGLANTISI:", [])
        if not legal_grounds:
            legal_grounds = f"{citation_line} sayili kararda benzer olayda benimsenen hukuki ilke, isbu dosyada da dikkate alinmalidir."
        if not facts_link:
            facts_link = f"Dosya konusu ({case.subject or '-'}) ile emsal kararin konusu ({metadata.splitlines()[-1].replace('Konu: ', '')}) arasinda bag kurulabilir."
        return {
            "applicationNote": application_note,
            "legalGroundsSnippet": legal_grounds,
            "factsLinkSnippet": facts_link,
        }

    def _local_precedent_application(
        self,
        metadata: str,
        content: str,
        case: PetitionCaseContext,
        citation_line: str,
    ) -> dict[str, str]:
        excerpt = content[:900].strip()
        if len(content) > 900:
            excerpt += "..."
        application_note = (
            f"Dosya: {case.caseLabel or case.caseType or '-'}\n"
            f"Dava konusu: {case.subject or '-'}\n"
            f"Emsal: {citation_line}\n"
            f"Karar konusu: {metadata.splitlines()[-1].replace('Konu: ', '')}\n\n"
            "Yerel eslestirme: Emsal karar metni ile dosya ozeti birlikte incelenmelidir. "
            "Tam baglanti metni icin AI saglayicisini etkinlestirin.\n\n"
            f"Metinden alinti:\n{excerpt}"
        )
        return {
            "applicationNote": application_note,
            "legalGroundsSnippet": (
                f"Somut olayda {citation_line} sayili Yargitay kararinda benimsenen degerlendirme "
                f"isbu {case.subject or 'dosya'} kapsaminda da dikkate alinmalidir."
            ),
            "factsLinkSnippet": (
                f"Dosya ozetinde yer alan {case.summary or 'vakialar'}, emsal karardaki olay ve hukuki "
                "degerlendirme ile karsilastirilmalidir."
            ),
        }

    def _summarize_precedent_with_ai(self, metadata: str, content: str) -> str:
        max_chars = 24000
        decision_text = content if len(content) <= max_chars else content[:max_chars] + "\n\n[Metin uzunlugu nedeniyle kirpildi...]"
        prompt = (
            "Turk hukuku baglaminda calisan bir hukuk asistanisin. Asagidaki mahkeme kararinin "
            "TAM METNINI okuyup ozet cikar.\n"
            "Metinde olmayan bilgi, karar numarasi, tarih veya sonuc uydurma.\n"
            "Ozeti su basliklarla ver:\n"
            "1. Kararin konusu\n"
            "2. Taraflar ve uyuasmazlik (metinde varsa)\n"
            "3. Mahkemenin olay degerlendirmesi\n"
            "4. Hukuki gerekce ve dayanak\n"
            "5. Sonuc / hukum\n"
            "6. Emsal degeri (dilekcede nasil kullanilabilecegi)\n\n"
            f"Karar bilgileri:\n{metadata}\n\n"
            f"Karar metni:\n{decision_text}\n\n"
            "En fazla 450 kelime, Turkce, net maddeler halinde yaz."
        )
        response = self.chat_model.invoke(prompt)
        return str(response.content).strip() or self._local_precedent_summary(metadata, content)

    def _local_precedent_summary(self, metadata: str, content: str) -> str:
        excerpt = content[:1800].strip()
        if len(content) > 1800:
            excerpt += "..."
        return (
            f"{metadata}\n\n"
            "Yerel ozet (AI yapilandirilmamis):\n"
            f"Metin uzunlugu: {len(content)} karakter.\n"
            f"Metinden alinti:\n{excerpt}\n\n"
            "Tam AI ozeti icin OpenAI, Gemini veya Ollama saglayicisini etkinlestirin."
        )

    def _format_research_findings(self, source_results: list[ResearchSourceFinding]) -> str:
        labels = {
            "LEGISLATION": "Mevzuat",
            "CASE_LAW": "Ictihat",
            "WEB": "Web/Kaynak",
        }
        blocks: list[str] = []
        for item in source_results:
            label = labels.get(item.source, item.source)
            if item.findings:
                findings = "\n".join(f"- {finding}" for finding in item.findings)
            else:
                findings = "- Sonuc bulunamadi."
            blocks.append(f"[{label}]\n{findings}")
        return "\n\n".join(blocks)

    def _synthesize_research_with_ai(self, query: str, context: str) -> str:
        prompt = (
            "Turk hukuku odakli bir hukuki arastirma asistanisin.\n"
            f"Kullanici sorusu:\n{query}\n\n"
            f"Arastirma bulgulari:\n{context}\n\n"
            "Yalnizca verilen bulgulara dayanarak kapsamli bir hukuki analiz yaz. "
            "Basliklar halinde duzenle: Genel Degerlendirme, Mevzuat, Ictihat, Web Kaynaklari, Sonuc ve Oneriler. "
            "Kaynak uydurma; bulgu yoksa acikca belirt. En fazla 600 kelime."
        )
        response = self.chat_model.invoke(prompt)
        content = str(response.content).strip()
        return content or self._local_research_synthesis(query, context)

    def _local_research_synthesis(self, query: str, context: str) -> str:
        return (
            f"## Hukuki Arastirma Ozeti: {query}\n\n"
            f"### Arastirma Bulgulari\n{context}\n\n"
            "### Not\n"
            "Tam LLM sentezi icin OpenAI, Gemini veya Ollama saglayicisini etkinlestirin."
        )

    def _format_citations(self, citations: list[PrecedentDto]) -> str:
        if not citations:
            return "Uygun emsal baglami bulunamadi."
        return "\n".join(
            f"{c.court} {c.chamber or ''}, {c.docketNo or '-'} E., {c.decisionNo or '-'} K., {c.date or '-'}, konu: {c.topic}, ozet: {c.summary}"
            for c in citations
        )

    def _local_answer(self, request: ChatRequest, citations: list[PrecedentDto]) -> str:
        if citations:
            source_lines = "\n".join(
                f"{index}. {citation.topic}: {citation.summary}"
                for index, citation in enumerate(citations, start=1)
            )
            return (
                "Yuklenen/indekslenen kaynaklarda soruyla en yakin eslesen bolumler sunlar:\n\n"
                f"{source_lines}\n\n"
                "Local mod bu parcalari ozetleyip eslestirir; hukuki muhakeme uretmek icin tam LLM saglayicisi etkinlestirilmelidir. "
                "Bu baglama gore cevabi, yukaridaki maddelerde gecen belge icerigiyle sinirli kurun; kaynakta olmayan sonuc, tarih veya karar numarasi eklemeyin."
            )

        return (
            f"'{request.question}' sorusu icin yuklenen dokumanlarda yeterli eslesme bulunamadi.\n\n"
            "1. Soruda PDF icindeki somut kelimeleri kullanin.\n"
            "2. PDF taranmis goruntuyse OCR yapilmis metinli dosya yukleyin.\n"
            "3. Dokuman yukledikten sonra uygulamayi yeniden baslattiysaniz kalici indeks dosyasinin olustugunu kontrol edin."
        )

    def _local_petition(self, request: PetitionRequest) -> str:
        court_line = _format_court_line(request.court)
        parties_block = _format_parties_block(request.parties)
        facts_block = _format_numbered_facts(request.facts)
        demands_block = _format_demands_block(request.demands)
        precedent_block = _format_precedent_legal_grounds(request.precedentContext)
        return (
            f"T.C.\n{court_line}\n\n"
            f"{parties_block}\n\n"
            f"KONU          : {request.petitionType} (HMK m.119 uyarınca)\n\n"
            f"AÇIKLAMALAR   :\n\n{facts_block}\n\n"
            "HUKUKİ SEBEPLER : Türk Borçlar Kanunu, Hukuk Muhakemeleri Kanunu ve sair ilgili mevzuat hükümleri"
            f"{precedent_block}\n\n"
            "DELİLLER      : Sözleşmeler, yazışmalar, ihtarnameler, ödeme kayıtları, tanık beyanları, bilirkişi incelemesi ve sair her türlü yasal delil.\n\n"
            "NETİCE VE TALEP :\n\n"
            "Yukarıda arz ve izah edilen nedenlerle;\n\n"
            f"{demands_block}\n\n"
            "Karar verilmesini saygılarımla arz ve talep ederim."
        )


legal_service = LegalService()
