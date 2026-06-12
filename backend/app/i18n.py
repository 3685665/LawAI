from contextvars import ContextVar


SUPPORTED_LANGUAGES = {"tr", "en"}
DEFAULT_LANGUAGE = "tr"

_current_language: ContextVar[str] = ContextVar("current_language", default=DEFAULT_LANGUAGE)


MESSAGES: dict[str, dict[str, str]] = {
    "tr": {
        "disclaimer": "Bu yanit hukuki danismanlik degildir; avukat denetimi ve guncel mevzuat kontrolu gerekir.",
        "ai_answer_fallback": "AI saglayicisi su anda yanit vermedi; yerel analiz modu kullanildi. Detay: {detail}",
        "draft_mode_hint": "Dilekce modunda, olay ozetini 'vakialar', 'hukuki nedenler' ve 'sonuc-talep' basliklarina donusturebilirim.",
        "private_mode_hint": "Gizli mod acik: Bu MVP, soruyu kaydetmez; kalici sohbet gecmisi eklenirse ayrica sifreleme uygulanmali.",
        "next_step_specific_terms": "Soruda PDF icindeki ayirt edici ifadeleri, tarihleri ve taraf adlarini kullanin.",
        "next_step_clean_document": "Beklenen cevap cikmiyorsa dokumani daha temiz metin iceren PDF/TXT olarak tekrar yukleyin.",
        "next_step_enable_provider": "Daha guclu yorumlama icin OpenAI, Gemini veya Ollama saglayicisini etkinlestirin.",
        "ai_summary_fallback": "NOT: AI saglayicisi yanit vermedi; yerel ozet modu kullanildi. Detay: {detail}",
        "ai_apply_fallback": "NOT: AI saglayicisi yanit vermedi; yerel eslestirme modu kullanildi. Detay: {detail}",
        "ai_petition_fallback": "NOT: AI saglayicisi su anda yanit vermedigi icin yerel taslak modu kullanildi. Detay: {detail}",
        "ai_research_fallback": "NOT: AI saglayicisi yanit vermedi; yerel sentez modu kullanildi. Detay: {detail}",
        "ai_provider_not_configured": "AI saglayicisi yapilandirilmamis.",
        "embedding_fallback": "Embedding saglayicisi hata verdi; local embedding fallback kullanildi. Detay: {detail}",
        "vector_store_error": "Vektor deposu hata verdi: {detail}",
        "documents_indexed": "Dokumanlar kalici vektor deposuna indekslendi.",
        "local_embedding_note": " Not: local embedding yalnizca gelistirme icindir; emsal kalitesi icin OpenAI, Gemini veya Ollama embedding saglayicisi kullanin ve dokumanlari yeniden indeksleyin.",
        "no_findings": "- Sonuc bulunamadi.",
        "no_precedent_context": "Uygun emsal baglami bulunamadi.",
        "local_answer_with_citations_intro": "Yuklenen/indekslenen kaynaklarda soruyla en yakin eslesen bolumler sunlar:",
        "local_answer_with_citations_note": "Local mod bu parcalari ozetleyip eslestirir; hukuki muhakeme uretmek icin tam LLM saglayicisi etkinlestirilmelidir. Bu baglama gore cevabi, yukaridaki maddelerde gecen belge icerigiyle sinirli kurun; kaynakta olmayan sonuc, tarih veya karar numarasi eklemeyin.",
        "local_answer_no_match": "'{question}' sorusu icin yuklenen dokumanlarda yeterli eslesme bulunamadi.",
        "local_answer_no_match_steps": "1. Soruda PDF icindeki somut kelimeleri kullanin.\n2. PDF taranmis goruntuyse OCR yapilmis metinli dosya yukleyin.\n3. Dokuman yukledikten sonra uygulamayi yeniden baslattiysaniz kalici indeks dosyasinin olustugunu kontrol edin.",
        "local_summary_label": "Yerel ozet (AI yapilandirilmamis):",
        "text_length": "Metin uzunlugu: {length} karakter.",
        "text_excerpt": "Metinden alinti:",
        "enable_ai_summary": "Tam AI ozeti icin OpenAI, Gemini veya Ollama saglayicisini etkinlestirin.",
        "local_research_title": "## Hukuki Arastirma Ozeti: {query}",
        "research_findings_title": "### Arastirma Bulgulari",
        "note_title": "### Not",
        "enable_ai_synthesis": "Tam LLM sentezi icin OpenAI, Gemini veya Ollama saglayicisini etkinlestirin.",
        "document_unsupported": "Bu asamada PDF, Word ve metin dosyalari desteklenir.",
        "document_empty": "Dosya bos gorunuyor.",
        "uploaded_document": "yuklenen-dokuman",
        "document_review_warning": "Dosya yuklendi ancak inceleme oncesi kontrol edilmesi gereken noktalar var.",
        "document_accepted": "Dosya kabul edildi. {characters} karakter metin cikarildi.",
        "document_no_readable_text": "Dosya yuklendi ancak okunabilir metin cikarilamadi. Taranmis PDF olabilir.",
        "document_extraction_failed": "Metin cikarimi basarisiz: {detail}",
        "document_unreadable": "Dosya yuklendi ancak metin okunamadi.",
        "document_too_large": "Dosya {max_mb} MB sinirini asiyor.",
        "document_insufficient_text": "Dosyadan yeterli metin cikarilamadi. Taranmis PDF olabilir; OCR destegi henuz eklenmedi.",
        "chunk_topic": "{topic} (bolum {index}/{total})",
        "pdf_extraction_failed": "PDF metin cikarimi basarisiz: {detail}",
        "document_text_extraction_failed": "Dokuman metin cikarimi basarisiz: {detail}",
    },
    "en": {
        "disclaimer": "This response is not legal advice; attorney review and current law verification are required.",
        "ai_answer_fallback": "The AI provider did not respond right now; local analysis mode was used. Detail: {detail}",
        "draft_mode_hint": "In draft mode, I can turn the fact summary into 'facts', 'legal grounds', and 'relief requested' sections.",
        "private_mode_hint": "Private mode is on: this MVP does not store the question; if persistent chat history is added, encryption should also be applied.",
        "next_step_specific_terms": "Use distinctive phrases, dates, and party names from the PDF in your question.",
        "next_step_clean_document": "If the expected answer does not appear, upload the document again as a cleaner PDF/TXT with extractable text.",
        "next_step_enable_provider": "Enable OpenAI, Gemini, or Ollama for stronger interpretation.",
        "ai_summary_fallback": "NOTE: The AI provider did not respond; local summary mode was used. Detail: {detail}",
        "ai_apply_fallback": "NOTE: The AI provider did not respond; local matching mode was used. Detail: {detail}",
        "ai_petition_fallback": "NOTE: The AI provider did not respond right now, so local draft mode was used. Detail: {detail}",
        "ai_research_fallback": "NOTE: The AI provider did not respond; local synthesis mode was used. Detail: {detail}",
        "ai_provider_not_configured": "AI provider is not configured.",
        "embedding_fallback": "The embedding provider failed; local embedding fallback was used. Detail: {detail}",
        "vector_store_error": "Vector store failed: {detail}",
        "documents_indexed": "Documents were indexed into the persistent vector store.",
        "local_embedding_note": " Note: local embeddings are only for development; use OpenAI, Gemini, or Ollama embeddings and re-index documents for precedent quality.",
        "no_findings": "- No results found.",
        "no_precedent_context": "No suitable precedent context was found.",
        "local_answer_with_citations_intro": "The closest matching sections in the uploaded/indexed sources are:",
        "local_answer_with_citations_note": "Local mode summarizes and matches these fragments; enable a full LLM provider for legal reasoning. Build the answer only from the document content listed above, and do not add conclusions, dates, or decision numbers that are not in the source.",
        "local_answer_no_match": "No sufficient match was found in the uploaded documents for '{question}'.",
        "local_answer_no_match_steps": "1. Use concrete words from the PDF in the question.\n2. If the PDF is a scanned image, upload a text-based file after OCR.\n3. If you restarted the app after uploading the document, check that the persistent index file exists.",
        "local_summary_label": "Local summary (AI is not configured):",
        "text_length": "Text length: {length} characters.",
        "text_excerpt": "Excerpt from the text:",
        "enable_ai_summary": "Enable OpenAI, Gemini, or Ollama for a full AI summary.",
        "local_research_title": "## Legal Research Summary: {query}",
        "research_findings_title": "### Research Findings",
        "note_title": "### Note",
        "enable_ai_synthesis": "Enable OpenAI, Gemini, or Ollama for full LLM synthesis.",
        "document_unsupported": "PDF, Word, and text files are supported at this stage.",
        "document_empty": "The file appears to be empty.",
        "uploaded_document": "uploaded-document",
        "document_review_warning": "The file was uploaded, but there are issues to review before analysis.",
        "document_accepted": "File accepted. {characters} text characters were extracted.",
        "document_no_readable_text": "The file was uploaded, but no readable text could be extracted. It may be a scanned PDF.",
        "document_extraction_failed": "Text extraction failed: {detail}",
        "document_unreadable": "The file was uploaded, but the text could not be read.",
        "document_too_large": "The file exceeds the {max_mb} MB limit.",
        "document_insufficient_text": "Enough text could not be extracted from the file. It may be a scanned PDF; OCR support has not been added yet.",
        "chunk_topic": "{topic} (part {index}/{total})",
        "pdf_extraction_failed": "PDF text extraction failed: {detail}",
        "document_text_extraction_failed": "Document text extraction failed: {detail}",
    },
}


def normalize_language(value: str | None) -> str:
    if not value:
        return DEFAULT_LANGUAGE
    for part in value.split(","):
        language = part.split(";")[0].strip().lower()
        if language.startswith("en"):
            return "en"
        if language.startswith("tr"):
            return "tr"
    return DEFAULT_LANGUAGE


def set_current_language(value: str | None):
    return _current_language.set(normalize_language(value))


def reset_current_language(token) -> None:
    _current_language.reset(token)


def current_language() -> str:
    return _current_language.get()


def t(key: str, **kwargs: object) -> str:
    message = MESSAGES[current_language()].get(key) or MESSAGES[DEFAULT_LANGUAGE][key]
    return message.format(**kwargs) if kwargs else message


def ai_language_instruction() -> str:
    return "Write the response in English." if current_language() == "en" else "Yaniti Turkce yaz."
