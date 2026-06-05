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
    PrecedentSearchRequest,
    PrecedentSearchResponse,
)
from app.services.vector_store import base_topic, normalize, vector_store
from app.settings import settings

DISCLAIMER = "Bu yanit hukuki danismanlik degildir; avukat denetimi ve guncel mevzuat kontrolu gerekir."
LOCAL_EMBEDDING_DIMENSIONS = 384
AI_REQUEST_TIMEOUT_SECONDS = 12

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
        vector = [0.0] * LOCAL_EMBEDDING_DIMENSIONS
        tokens = normalize(text).split()
        if not tokens:
            return vector

        for token in tokens:
            digest = hashlib.sha256(token.encode("utf-8")).digest()
            index = int.from_bytes(digest[:4], "big") % LOCAL_EMBEDDING_DIMENSIONS
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

    def search_precedents(self, request: PrecedentSearchRequest) -> PrecedentSearchResponse:
        limit = min(max(request.limit or 5, 1), 20)
        return PrecedentSearchResponse(
            query=request.query,
            results=self.search(request.query, request.court, request.chamber, limit, use_samples=False),
        )

    def generate_petition(self, request: PetitionRequest) -> PetitionResponse:
        indexed_citations = self.search(f"{request.facts} {request.demands or ''}", None, None, 2, use_samples=False)
        citations = indexed_citations
        body = self._local_petition(request)

        if self.chat_model and indexed_citations:
            try:
                body = self._petition_with_ai(request, indexed_citations)
            except Exception:
                body += "\n\nNOT: AI saglayicisi su anda yanit vermedigi icin yerel taslak modu kullanildi."

        return PetitionResponse(
            title=f"{request.petitionType} Dilekcesi",
            body=body,
            citedPrecedents=citations,
        )

    def ingest_knowledge(self, request: KnowledgeIngestRequest) -> KnowledgeIngestResponse:
        if not self.embeddings:
            return KnowledgeIngestResponse(indexed=0, storage="disabled", message="AI saglayicisi yapilandirilmamis.")
        try:
            embeddings = self._embed_documents([doc.content for doc in request.documents])
        except Exception as exc:
            return KnowledgeIngestResponse(
                indexed=0,
                storage="disabled",
                message=f"Embedding saglayicisi hata verdi: {exc}",
            )
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

    def search(self, query: str, court: str | None, chamber: str | None, limit: int, use_samples: bool = True) -> list[PrecedentDto]:
        if self.embeddings:
            try:
                if vector_store.has_entries():
                    query_embedding = self._embed_query(query)
                    results = vector_store.search(query_embedding, court, chamber, max(limit * 4, limit), query)
                    if results:
                        return self._group_precedent_chunks(results, limit)
            except Exception:
                pass
        return self._group_precedent_chunks(self._search_samples(query, court, chamber, limit), limit) if use_samples else []

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

    def _petition_with_ai(self, request: PetitionRequest, citations: list[PrecedentDto]) -> str:
        prompt = (
            "Asagidaki bilgilere gore Turk hukuk uygulamasina uygun, avukat tarafindan kontrol edilecek bir dilekce taslagi hazirla. "
            "Uydurma mahkeme karari veya mevzuat atfi yapma; sadece verilen emsal baglamindan yararlan.\n\n"
            f"Dilekce turu: {request.petitionType}\nMahkeme: {request.court}\nTaraflar: {request.parties}\n"
            f"Vakialar: {request.facts}\nTalepler: {request.demands or ''}\n\nEmsal baglami:\n{self._format_citations(citations)}"
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

    def _group_precedent_chunks(self, precedents: list[PrecedentDto], limit: int) -> list[PrecedentDto]:
        grouped: dict[tuple[str, str, str, str, str, str], list[PrecedentDto]] = {}
        order: list[tuple[str, str, str, str, str, str]] = []
        for item in precedents:
            source_topic = base_topic(item.topic)
            key = (
                normalize(item.court),
                normalize(item.chamber),
                normalize(item.docketNo),
                normalize(item.decisionNo),
                normalize(item.date),
                normalize(source_topic),
            )
            if key not in grouped:
                grouped[key] = []
                order.append(key)
            grouped[key].append(item)

        merged: list[PrecedentDto] = []
        for key in order:
            items = grouped[key]
            first = items[0]
            source_items = vector_store.source_chunks(first) or items
            topic = base_topic(first.topic)
            summary = first.summary
            content = self._merge_source_content(source_items)
            merged.append(
                PrecedentDto(
                    court=first.court,
                    chamber=first.chamber,
                    docketNo=first.docketNo,
                    decisionNo=first.decisionNo,
                    date=first.date,
                    topic=topic,
                    summary=summary,
                    content=content,
                )
            )
            if len(merged) >= limit:
                break
        return merged

    def _merge_source_content(self, source_items: list[PrecedentDto]) -> str | None:
        merged: list[str] = []
        seen_keys: set[str] = set()
        for item in source_items:
            raw_content = (item.content or item.summary or "").strip()
            for paragraph in self._split_paragraphs(raw_content):
                if self._is_boilerplate_paragraph(paragraph):
                    continue
                paragraph_key = self._paragraph_key(paragraph)
                if not paragraph_key or paragraph_key in seen_keys:
                    continue
                if self._is_near_duplicate(paragraph_key, seen_keys):
                    continue
                seen_keys.add(paragraph_key)
                merged.append(paragraph)
        if not merged:
            return None
        return "\n\n".join(merged)

    def _split_paragraphs(self, content: str) -> list[str]:
        compact_lines = [" ".join(line.split()) for line in content.splitlines()]
        paragraphs: list[str] = []
        current: list[str] = []
        for line in compact_lines:
            if not line:
                if current:
                    paragraphs.append(" ".join(current))
                    current = []
                continue
            current.append(line)
        if current:
            paragraphs.append(" ".join(current))
        if len(paragraphs) <= 1:
            return [paragraph for paragraph in re.split(r"(?<=[.!?])\s+(?=\d+\.|[A-ZÇĞİÖŞÜ])", content.strip()) if paragraph.strip()]
        return paragraphs

    def _is_boilerplate_paragraph(self, paragraph: str) -> bool:
        normalized = normalize(paragraph)
        if len(normalized) < 12:
            return True
        boilerplate_markers = [
            "kullanici tarafindan",
            "yargitay ictihat merkezinde yayimlanan kararlardaki kisisel veriler",
            "kisisel verilerin anonim",
            "anonim hale getirilmesine dair yonerge",
        ]
        if any(marker in normalized for marker in boilerplate_markers):
            return True
        return bool(re.fullmatch(r"\d+\s*/\s*\d+", normalized))

    def _paragraph_key(self, paragraph: str) -> str:
        normalized = normalize(paragraph)
        return re.sub(r"\s+", " ", normalized).strip()

    def _is_near_duplicate(self, paragraph_key: str, seen_keys: set[str]) -> bool:
        if len(paragraph_key) < 80:
            return False
        for seen_key in seen_keys:
            if len(seen_key) < 80:
                continue
            if paragraph_key in seen_key or seen_key in paragraph_key:
                return True
            shorter, longer = sorted((paragraph_key, seen_key), key=len)
            if len(shorter) / len(longer) >= 0.85 and self._token_overlap_ratio(shorter, longer) >= 0.9:
                return True
        return False

    def _token_overlap_ratio(self, left: str, right: str) -> float:
        left_tokens = set(left.split())
        right_tokens = set(right.split())
        if not left_tokens or not right_tokens:
            return 0.0
        return len(left_tokens.intersection(right_tokens)) / min(len(left_tokens), len(right_tokens))

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
                num_ctx=2048,
                num_predict=96,
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
        demands = request.demands or "Yukarida aciklanan nedenlerle taleplerimizin kabulune karar verilmesini arz ve talep ederiz."
        return (
            f"{request.petitionType.upper()}\n\n"
            f"SAYIN {request.court.upper()}'NE\n\n"
            f"TARAFLAR\n{request.parties}\n\n"
            f"KONU\n{request.petitionType} konulu dilekce taslagimizin sunulmasindan ibarettir.\n\n"
            f"ACIKLAMALAR\n{request.facts}\n\n"
            "HUKUKI NEDENLER\nIlgili mevzuat, yargisal ictihatlar ve her turlu yasal delil.\n\n"
            "DELILLER\nSozlesmeler, yazismalar, ihtarnameler, odeme kayitlari, tanik beyanlari, bilirkisi incelemesi ve sair deliller.\n\n"
            f"SONUC VE TALEP\n{demands}\n\nSaygilarimizla."
        )


legal_service = LegalService()
