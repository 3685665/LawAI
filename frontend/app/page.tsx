"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import {
  AlertCircle,
  Bot,
  CheckCircle2,
  Database,
  FileSearch,
  FileText,
  FolderOpen,
  LoaderCircle,
  Lock,
  Scale,
  Search,
  Send,
  Upload,
  X
} from "lucide-react";
import {
  CaseDocument as ApiCaseDocument,
  CaseRecord,
  CaseTemplate,
  CaseTemplatesResponse,
  checkHealth,
  deleteJson,
  getJson,
  patchJson,
  postJson,
  Precedent,
  seedSamples,
  uploadMultipart
} from "@/lib/api";

type Tab = "chat" | "search" | "petition" | "cases" | "document" | "knowledge";
type ChatResponse = { answer: string; citations: Precedent[]; disclaimer: string };
type PetitionResponse = { title: string; body: string; citedPrecedents: Precedent[] };
type KnowledgeResponse = { indexed: number; storage: string; message: string };
type CaseType = "genel" | "is" | "sozlesme" | "icra" | "aile";
type CaseScreen = "list" | "create" | "detail";
type CaseDocument = {
  id: string;
  title: string;
  detail: string;
  required: boolean;
  group: string;
};
type UploadResponse = {
  filename: string;
  size: number;
  contentType: string;
  detectedIssues?: string[];
  summary?: string;
  extractedCharacters?: number;
  chunkCount?: number;
  indexed?: number;
  storage?: string;
  message?: string;
  textPreview?: string;
  warnings?: string[];
};

const acceptedExtensions = [".pdf", ".doc", ".docx", ".txt"];
const maxFileBytes = 25 * 1024 * 1024;

const caseTypeLabels: Record<CaseType, string> = {
  genel: "Genel hukuk",
  is: "Is hukuku",
  sozlesme: "Sozlesme / alacak",
  icra: "Icra takibi",
  aile: "Aile hukuku"
};

const caseTemplates: Record<CaseType, { title: string; courtHint: string; summary: string }> = {
  genel: {
    title: "Genel hukuk dosyasi",
    courtHint: "Nobetci Asliye Hukuk Mahkemesi",
    summary: "Dava dilekcesi, delil listesi, vekaletname ve ekler klasoru ile baslanir."
  },
  is: {
    title: "Is hukuku dosyasi",
    courtHint: "Is Mahkemesi",
    summary: "Hizmet dokumu, fesih bildirimi, bordrolar ve arabuluculuk son tutanagi onerilir."
  },
  sozlesme: {
    title: "Sozlesme / alacak dosyasi",
    courtHint: "Nobetci Asliye Hukuk Mahkemesi",
    summary: "Sozlesme, fatura, dekont, ihtarname ve teslim / kabul belgeleri bir arada tutulur."
  },
  icra: {
    title: "Icra takibi dosyasi",
    courtHint: "Icra Mudurlugu / Icra Hukuk Mahkemesi",
    summary: "Takip dayanagi belge, hesap cetveli, senet / cek ve tebligat evraki gerekir."
  },
  aile: {
    title: "Aile hukuku dosyasi",
    courtHint: "Aile Mahkemesi",
    summary: "Nufus kayit ornegi, evlilik belgeleri, velayet / nafaka belgeleri ve sosyal inceleme destegi gerekir."
  }
};

const caseDocuments: Record<CaseType, CaseDocument[]> = {
  genel: [
    { id: "genel-vekalet", title: "Vekaletname / yetki belgesi", detail: "Musteri vekaleti ve temsil yetkisini gosteren ana belge.", required: true, group: "Yetki" },
    { id: "genel-kimlik", title: "Taraf kimlik ve iletisim bilgileri", detail: "TCKN / VKN, adres, telefon ve tebligat bilgileri.", required: true, group: "Taraf bilgileri" },
    { id: "genel-dilekce", title: "Dava dilekcesi", detail: "Talep sonucu, vakialar ve hukuki nedenler.", required: true, group: "Dava evraki" },
    { id: "genel-delil", title: "Delil listesi", detail: "Belgeler, taniklar, bilirkiisi ve diger ispat vasitalari.", required: true, group: "Dava evraki" },
    { id: "genel-ekler", title: "Ekler klasoru", detail: "Belgelerin numarali ve duzenli sekilde dosyalanmis hali.", required: true, group: "Ekler" },
    { id: "genel-harc", title: "Harc ve gider avansi makbuzlari", detail: "Basvuru harci, pesin harc ve gider avansi kayitlari.", required: true, group: "Usul" },
    { id: "genel-arabuluculuk", title: "Arabuluculuk son tutanagi", detail: "Zorunlu dava sartina tabi dosyalarda eklenir.", required: false, group: "Usul" },
    { id: "genel-tebligat", title: "Tebligat / ihtarname evraki", detail: "Karsi tarafa gonderilen ihtar ve tebligat belgeleri.", required: false, group: "Ekler" }
  ],
  is: [
    { id: "is-vekalet", title: "Vekaletname / yetki belgesi", detail: "Avukatlik yetkisini ve temsil kapsamini gosterir.", required: true, group: "Yetki" },
    { id: "is-hizmet", title: "Hizmet dokumu / SGK kayitlari", detail: "Calisma suresi ve prim kayitlarini teyit eder.", required: true, group: "Calisma kaydi" },
    { id: "is-fesih", title: "Fesih bildirimi / cikis evragi", detail: "Fesih tarihini ve sebebini netlestirir.", required: true, group: "Fesih" },
    { id: "is-bordro", title: "Bordro ve ucret belgeleri", detail: "Maas, fazla mesai ve kesintilerin ispatinda kullanilir.", required: true, group: "Ucret" },
    { id: "is-sozlesme", title: "Is sozlesmesi / gorev tanimi", detail: "Gorev, unvan ve calisma duzenini ortaya koyar.", required: true, group: "Calisma kaydi" },
    { id: "is-arabuluculuk", title: "Arabuluculuk son tutanagi", detail: "Dava sartidir; sure ve taraf bilgileri kontrol edilmelidir.", required: true, group: "Usul" },
    { id: "is-izin", title: "Izin ve puantaj kayitlari", detail: "Yillik izin, devamsizlik ve fazla mesai icin destek belge.", required: false, group: "Ekler" },
    { id: "is-tanik", title: "Tanik listesi", detail: "Calisma kosullari ve alacaklar icin taniklar.", required: false, group: "Delil" }
  ],
  sozlesme: [
    { id: "s-vekalet", title: "Vekaletname / yetki belgesi", detail: "Temsil yetkisini ve dava acma yetkisini gosterir.", required: true, group: "Yetki" },
    { id: "s-sozlesme", title: "Sozlesme / siparis formu", detail: "Taraflar arasindaki borc ve edimleri belirler.", required: true, group: "Sozlesme" },
    { id: "s-fatura", title: "Fatura / irsaliye / teslim belgeleri", detail: "Edimin yerine getirildigini veya alacagin dogdugunu destekler.", required: true, group: "Delil" },
    { id: "s-dekont", title: "Odeme dekontlari / cari hesap", detail: "Yapilan veya yapilmayan odemeleri gosteren belgeler.", required: true, group: "Delil" },
    { id: "s-ihtar", title: "Ihtarname ve tebligat evraki", detail: "Temerrut, ihtar ve bildirimin ispatinda kullanilir.", required: true, group: "Usul" },
    { id: "s-delil", title: "Ek deliller klasoru", detail: "Mail yazismalari, teslim tutanaklari, WhatsApp ciktilari vb.", required: true, group: "Ekler" },
    { id: "s-arabuluculuk", title: "Arabuluculuk son tutanagi", detail: "Konuya gore zorunlu olabilir.", required: false, group: "Usul" },
    { id: "s-hesap", title: "Hesap cetveli", detail: "Faiz ve ana para hesabini tablo halinde verir.", required: false, group: "Delil" }
  ],
  icra: [
    { id: "i-dayanak", title: "Takip dayanagi belge", detail: "Ilam, senet, sozlesme veya faturaya dayali evrak.", required: true, group: "Dayanak" },
    { id: "i-vekalet", title: "Vekaletname / yetki belgesi", detail: "Icra islemlerinde temsil yetkisi icin gerekir.", required: true, group: "Yetki" },
    { id: "i-hesap", title: "Hesap cetveli", detail: "Faiz, vekalet ucreti ve toplam alacak hesabi.", required: true, group: "Hesap" },
    { id: "i-tebligat", title: "Tebligat / ihtar evraki", detail: "Borc bildirimi ve temerrut icin kullanilir.", required: true, group: "Usul" },
    { id: "i-senet", title: "Senet / cek / bono", detail: "Ilamsiz takipte dayanak olarak kullanilir.", required: false, group: "Dayanak" },
    { id: "i-kesinlesme", title: "Kesinlesme / ilam serhi", detail: "Ilamli takipte gerektiginde eklenir.", required: false, group: "Dayanak" },
    { id: "i-adres", title: "Alacakli / borclu adres bilgileri", detail: "Tebligat ve takip islemleri icin gerekir.", required: true, group: "Taraf bilgileri" }
  ],
  aile: [
    { id: "a-vekalet", title: "Vekaletname / yetki belgesi", detail: "Temsil yetkisini gosterir.", required: true, group: "Yetki" },
    { id: "a-nufus", title: "Nufus kayit ornegi", detail: "Taraflarin aile bagini ve baglantili kayitlarini ortaya koyar.", required: true, group: "Kimlik" },
    { id: "a-evlilik", title: "Evlilik / bosanma belgeleri", detail: "Nikah, bosanma, ayrilik veya aile birligi belgeleri.", required: true, group: "Aile kaydi" },
    { id: "a-velayet", title: "Velayet / nafaka destek belgeleri", detail: "Cocuklarin bakimi, egitimi ve giderlerine iliskin belgeler.", required: true, group: "Cocuk ve destek" },
    { id: "a-gelir", title: "Gelir ve gider belgeleri", detail: "Nafaka veya katilma alacagi taleplerinde kullanilir.", required: false, group: "Mali durum" },
    { id: "a-sosyal", title: "Sosyal inceleme / adres belgeleri", detail: "Gerekirse mahkemeye sunulacak destek evraki.", required: false, group: "Ekler" },
    { id: "a-tedbir", title: "Tedbir / koruma talebi belgeleri", detail: "Acil koruma, uzaklastirma veya gecici onlem icin.", required: false, group: "Usul" }
  ]
};

export default function Home() {
  const [activeTab, setActiveTab] = useState<Tab>("chat");
  const [privateMode, setPrivateMode] = useState(true);
  const [loading, setLoading] = useState("");
  const [error, setError] = useState("");
  const [backendOnline, setBackendOnline] = useState<boolean | null>(null);

  const [chatQuestion, setChatQuestion] = useState("Kira alacagi icin nasil bir yol izlemeliyim?");
  const [chatMode, setChatMode] = useState("analysis");
  const [chatResponse, setChatResponse] = useState<ChatResponse | null>(null);
  const [searchQuery, setSearchQuery] = useState("ise iade fesih ispat");
  const [precedents, setPrecedents] = useState<Precedent[]>([]);
  const [petition, setPetition] = useState({
    petitionType: "Alacak",
    court: "Istanbul Nobetci Asliye Hukuk Mahkemesi",
    parties: "Davaci: ...\nDavali: ...",
    facts: "Taraflar arasinda akdedilen sozlesme kapsaminda edimler yerine getirilmemis, ihtara ragmen borc odenmemistir.",
    demands: "Alacagin yasal faiziyle birlikte tahsiline karar verilmesini talep ederiz."
  });
  const [petitionResult, setPetitionResult] = useState<PetitionResponse | null>(null);
  const [knowledgeJson, setKnowledgeJson] = useState(`[
  {
    "sourceType": "precedent",
    "court": "Yargitay",
    "chamber": "3. Hukuk Dairesi",
    "docketNo": "2024/100",
    "decisionNo": "2024/250",
    "date": "2024-03-12",
    "topic": "Kira alacagi",
    "summary": "Temerrut ve kira alacagi yazili delillerle degerlendirilir.",
    "content": "Kira sozlesmesi, ihtarname ve odeme kayitlari birlikte incelenmelidir."
  }
]`);
  const [knowledgeResult, setKnowledgeResult] = useState<KnowledgeResponse | null>(null);

  const tabs = useMemo(() => [
    { id: "chat" as const, label: "Sohbet", icon: Bot },
    { id: "search" as const, label: "Emsal", icon: FileSearch },
    { id: "petition" as const, label: "Dilekce", icon: FileText },
    { id: "cases" as const, label: "Davalar", icon: FolderOpen },
    { id: "document" as const, label: "Dokuman", icon: Upload },
    { id: "knowledge" as const, label: "Bilgi", icon: Database }
  ], []);

  useEffect(() => {
    checkHealth().then(setBackendOnline);
  }, []);

  async function run(action: string, fn: () => Promise<void>) {
    setLoading(action);
    setError("");
    try {
      await fn();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Beklenmeyen hata");
    } finally {
      setLoading("");
    }
  }

  function submitChat(event: FormEvent) {
    event.preventDefault();
    run("chat", async () => {
      setChatResponse(await postJson<ChatResponse>("/chat", { question: chatQuestion, mode: chatMode, privateMode }));
    });
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    run("search", async () => {
      const data = await postJson<{ results: Precedent[] }>("/precedents/search", { query: searchQuery, limit: 5 });
      setPrecedents(data.results);
    });
  }

  function submitPetition(event: FormEvent) {
    event.preventDefault();
    run("petition", async () => {
      setPetitionResult(await postJson<PetitionResponse>("/petitions", petition));
    });
  }

  function submitKnowledge(event: FormEvent) {
    event.preventDefault();
    run("knowledge", async () => {
      const documents = JSON.parse(knowledgeJson);
      setKnowledgeResult(await postJson<KnowledgeResponse>("/knowledge/documents", { documents }));
    });
  }

  function seedKnowledge() {
    run("knowledge", async () => {
      setKnowledgeResult(await postJson<KnowledgeResponse>("/knowledge/seed-precedents", {}));
    });
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <Scale size={28} />
          <div>
            <strong>LawAI</strong>
            <span>Next.js + LangChain</span>
          </div>
        </div>
        <nav className="tabs">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            return (
              <button key={tab.id} className={activeTab === tab.id ? "active" : ""} onClick={() => setActiveTab(tab.id)} type="button" title={tab.label}>
                <Icon size={18} />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </nav>
        <label className="privacy-toggle">
          <input checked={privateMode} onChange={(event) => setPrivateMode(event.target.checked)} type="checkbox" />
          <Lock size={17} />
          <span>Gizli mod</span>
        </label>
      </aside>

      <section className="workspace">
        <header className="topbar">
        <div>
          <h1>Hukuki calisma paneli</h1>
          <p>Soru yanitlama, emsal arama, dilekce taslagi, dava dosyasi, dokuman kontrolu ve bilgi bankasi tek ekranda.</p>
        </div>
          <span className={backendOnline === false ? "status offline" : "status"}>{loading ? "Isleniyor" : backendOnline === false ? "Backend yok" : "Hazir"}</span>
        </header>

        {error && <div className="error">{error}</div>}

        {activeTab === "chat" && (
          <section className="tool-grid">
            <form className="panel primary-panel" onSubmit={submitChat}>
              <PanelTitle icon={<Bot size={20} />} title="Hukuk asistanina sor" />
              <textarea value={chatQuestion} onChange={(event) => setChatQuestion(event.target.value)} rows={8} />
              <div className="row">
                <select value={chatMode} onChange={(event) => setChatMode(event.target.value)}>
                  <option value="analysis">Analiz</option>
                  <option value="draft">Dilekce yardimi</option>
                </select>
                <button disabled={loading === "chat"} type="submit"><Send size={17} />Gonder</button>
              </div>
            </form>
            <ResultPanel title="Yanit">
              {chatResponse ? (
                <>
                  <pre>{chatResponse.answer}</pre>
                  <CitationList citations={chatResponse.citations} />
                  <small>{chatResponse.disclaimer}</small>
                </>
              ) : <EmptyState text="Soruyu gonderince analiz burada gorunur." />}
            </ResultPanel>
          </section>
        )}

        {activeTab === "search" && (
          <section className="tool-grid">
            <form className="panel primary-panel" onSubmit={submitSearch}>
              <PanelTitle icon={<Search size={20} />} title="Emsal karar ara" />
              <input value={searchQuery} onChange={(event) => setSearchQuery(event.target.value)} placeholder="Orn: is sozlesmesi fesih ispat" />
              <button disabled={loading === "search"} type="submit"><Search size={17} />Ara</button>
            </form>
            <ResultPanel title="Kararlar">
              {precedents.length ? <CitationList citations={precedents} /> : <EmptyState text="Arama sonucu burada listelenir." />}
            </ResultPanel>
          </section>
        )}

        {activeTab === "petition" && (
          <section className="tool-grid">
            <form className="panel primary-panel" onSubmit={submitPetition}>
              <PanelTitle icon={<FileText size={20} />} title="Dilekce uret" />
              <input value={petition.petitionType} onChange={(event) => setPetition({ ...petition, petitionType: event.target.value })} />
              <input value={petition.court} onChange={(event) => setPetition({ ...petition, court: event.target.value })} />
              <textarea rows={4} value={petition.parties} onChange={(event) => setPetition({ ...petition, parties: event.target.value })} />
              <textarea rows={6} value={petition.facts} onChange={(event) => setPetition({ ...petition, facts: event.target.value })} />
              <textarea rows={4} value={petition.demands} onChange={(event) => setPetition({ ...petition, demands: event.target.value })} />
              <button disabled={loading === "petition"} type="submit"><FileText size={17} />Taslak olustur</button>
            </form>
            <ResultPanel title={petitionResult?.title ?? "Taslak"}>
              {petitionResult ? (
                <>
                  <pre>{petitionResult.body}</pre>
                  <CitationList citations={petitionResult.citedPrecedents} />
                </>
              ) : <EmptyState text="Dilekce taslagi burada gorunur." />}
            </ResultPanel>
          </section>
        )}

        {activeTab === "cases" && <CasesPanel onGoToDocuments={() => setActiveTab("document")} />}

        {activeTab === "document" && <DocumentPanel loading={loading} run={run} onGoToChat={() => setActiveTab("chat")} />}

        {activeTab === "knowledge" && (
          <section className="tool-grid">
            <form className="panel primary-panel" onSubmit={submitKnowledge}>
              <PanelTitle icon={<Database size={20} />} title="Bilgi bankasi indeksle" />
              <textarea value={knowledgeJson} onChange={(event) => setKnowledgeJson(event.target.value)} rows={16} />
              <div className="row">
                <button disabled={loading === "knowledge"} type="button" onClick={seedKnowledge}><Database size={17} />Ornekleri indeksle</button>
                <button disabled={loading === "knowledge"} type="submit"><Upload size={17} />JSON indeksle</button>
              </div>
            </form>
            <ResultPanel title="Indeks sonucu">
              {knowledgeResult ? (
                <div className="document-summary">
                  <strong>{knowledgeResult.storage}</strong>
                  <span>{knowledgeResult.indexed} kayit</span>
                  <p>{knowledgeResult.message}</p>
                </div>
              ) : <EmptyState text="OpenAI, Gemini veya Ollama ayarlandiginda dokumanlar burada indekslenir." />}
            </ResultPanel>
          </section>
        )}
      </section>
    </main>
  );
}

function DocumentPanel({ loading, run, onGoToChat }: { loading: string; run: (action: string, fn: () => Promise<void>) => void; onGoToChat: () => void }) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [topic, setTopic] = useState("");
  const [court, setCourt] = useState("");
  const [localError, setLocalError] = useState("");
  const [result, setResult] = useState<UploadResponse | null>(null);

  function applyFile(nextFile: File | null) {
    const validationError = validateFile(nextFile);
    if (validationError) {
      setLocalError(validationError);
      setFile(null);
      setResult(null);
      return;
    }
    setLocalError("");
    setFile(nextFile);
    setResult(null);
    if (nextFile && !topic.trim()) setTopic(nextFile.name.replace(/\.[^.]+$/, ""));
  }

  function runUpload(action: "analyze" | "ingest") {
    if (!file) {
      setLocalError("Once bir dosya secin.");
      return;
    }
    run(action, async () => {
      const form = new FormData();
      form.append("file", file, file.name);
      if (action === "ingest") {
        if (topic.trim()) form.append("topic", topic.trim());
        if (court.trim()) form.append("court", court.trim());
      }
      setResult(await uploadMultipart<UploadResponse>(action === "ingest" ? "/documents/ingest" : "/documents/analyze", form));
    });
  }

  const isBusy = Boolean(loading);
  const issues = result?.detectedIssues ?? result?.warnings ?? [];
  const ingestSuccess = result?.chunkCount != null && Number(result.indexed) > 0;

  return (
    <section className="pdf-upload-layout">
      <div className="panel primary-panel pdf-upload-form">
        <PanelTitle icon={<Upload size={20} />} title="Dokuman yukle ve bilgi bankasina ekle" />
        <input ref={inputRef} accept={acceptedExtensions.join(",")} onChange={(event) => applyFile(event.target.files?.[0] ?? null)} type="file" />
        <div className="dropzone" onClick={() => inputRef.current?.click()} role="button" tabIndex={0}>
          {file ? (
            <div className="selected-file">
              <FileText size={28} />
              <div><strong>{file.name}</strong><span>{formatBytes(file.size)}</span></div>
              <button className="icon-button" disabled={isBusy} onClick={(event) => { event.stopPropagation(); applyFile(null); }} type="button"><X size={18} /></button>
            </div>
          ) : (
            <>
              <Upload size={32} />
              <strong>Dosyanizi secin</strong>
              <span>PDF, Word, TXT - en fazla 25 MB</span>
            </>
          )}
        </div>
        <label className="field-label">Konu basligi<input disabled={isBusy} onChange={(event) => setTopic(event.target.value)} value={topic} /></label>
        <label className="field-label">Mahkeme (opsiyonel)<input disabled={isBusy} onChange={(event) => setCourt(event.target.value)} value={court} /></label>
        <div className="upload-actions">
          <button className="secondary-button" disabled={!file || isBusy} onClick={() => runUpload("analyze")} type="button">{loading === "analyze" ? <LoaderCircle className="spin" size={17} /> : <FileText size={17} />}On kontrol</button>
          <button disabled={!file || isBusy} onClick={() => runUpload("ingest")} type="button">{loading === "ingest" ? <LoaderCircle className="spin" size={17} /> : <Upload size={17} />}Bilgi bankasina ekle</button>
        </div>
        {localError && <div className="inline-error"><AlertCircle size={18} /><span>{localError}</span></div>}
      </div>
      <ResultPanel title="Sonuc">
        {!result && !loading && <EmptyState text="Dosya yukleyip islem baslatinca ozet burada gorunur." />}
        {loading && <div className="result-loading"><LoaderCircle className="spin" size={36} /><strong>Isleniyor...</strong></div>}
        {result && !loading && (
          <div className="document-summary">
            {ingestSuccess && <div className="success-banner"><CheckCircle2 size={20} /><span>{result.indexed} parca indekslendi</span></div>}
            <div className="result-stats">
              <div><span>Dosya</span><strong>{result.filename}</strong></div>
              <div><span>Tip</span><strong>{result.contentType}</strong></div>
              <div><span>Boyut</span><strong>{formatBytes(result.size)}</strong></div>
              {result.extractedCharacters != null && <div><span>Cikarilan metin</span><strong>{result.extractedCharacters.toLocaleString("tr-TR")} karakter</strong></div>}
            </div>
            <p>{result.message || result.summary}</p>
            {result.textPreview && <pre>{result.textPreview}</pre>}
            {issues.length > 0 && <ul className="issue-list">{issues.map((issue) => <li key={issue}>{issue}</li>)}</ul>}
            {ingestSuccess && <button className="chat-cta" onClick={onGoToChat} type="button"><Bot size={17} />Sohbete git</button>}
          </div>
        )}
      </ResultPanel>
    </section>
  );
}

function CasesPanel({ onGoToDocuments }: { onGoToDocuments: () => void }) {
  const [caseScreen, setCaseScreen] = useState<CaseScreen>("list");
  const [caseType, setCaseType] = useState<CaseType>("genel");
  const [clientName, setClientName] = useState("");
  const [opponentName, setOpponentName] = useState("");
  const [courtName, setCourtName] = useState("");
  const [subject, setSubject] = useState("Dava kaydi ve dosya hazirligi");
  const [summary, setSummary] = useState("Musteri gorusmesi, taraf bilgileri ve belge kontrolu tamamlanacak.");
  const [documents, setDocuments] = useState<Record<string, boolean>>({});
  const [templates, setTemplates] = useState<CaseTemplate[]>([]);
  const [savedCases, setSavedCases] = useState<CaseRecord[]>([]);
  const [selectedCaseId, setSelectedCaseId] = useState<string | null>(null);
  const [selectedCase, setSelectedCase] = useState<CaseRecord | null>(null);
  const [localError, setLocalError] = useState("");
  const [saving, setSaving] = useState(false);
  const [loadingCases, setLoadingCases] = useState(true);

  const selectedTemplate = useMemo(() => {
    const fetched = templates.find((item) => item.caseType === caseType);
    if (fetched) return fetched;
    return {
      caseType,
      label: caseTypeLabels[caseType],
      title: caseTemplates[caseType].title,
      courtHint: caseTemplates[caseType].courtHint,
      summary: caseTemplates[caseType].summary,
      documents: caseDocuments[caseType].map((item) => ({ ...item, completed: false }))
    } satisfies CaseTemplate;
  }, [caseType, templates]);

  const requiredDocs = selectedTemplate.documents.filter((item) => item.required);
  const optionalDocs = selectedTemplate.documents.filter((item) => !item.required);
  const missingRequired = requiredDocs.filter((item) => !documents[item.id]);
  const completion = requiredDocs.length === 0 ? 100 : Math.round(((requiredDocs.length - missingRequired.length) / requiredDocs.length) * 100);
  const groupedDocs = useMemo(() => groupDocuments(selectedTemplate.documents), [selectedTemplate.documents]);
  const allCases = useMemo(() => [...savedCases].sort((left, right) => right.updatedAt.localeCompare(left.updatedAt)), [savedCases]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoadingCases(true);
      setLocalError("");
      try {
        const [templateResponse, caseList] = await Promise.all([
          getJson<CaseTemplatesResponse>("/cases/templates"),
          getJson<CaseRecord[]>("/cases")
        ]);
        if (cancelled) return;
        setTemplates(templateResponse.templates);
        setSavedCases(caseList);
        const firstCase = caseList[0] ?? null;
        setSelectedCaseId(firstCase?.id ?? null);
        setSelectedCase(firstCase);
      } catch (error) {
        if (!cancelled) {
          setLocalError(error instanceof Error ? error.message : "Davalar yuklenemedi.");
        }
      } finally {
        if (!cancelled) {
          setLoadingCases(false);
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    setCourtName(selectedTemplate.courtHint);
    setDocuments(Object.fromEntries(selectedTemplate.documents.map((item) => [item.id, item.completed])) as Record<string, boolean>);
  }, [selectedTemplate]);

  function toggleDocument(id: string) {
    setDocuments((current) => ({ ...current, [id]: !current[id] }));
  }

  function markAll() {
    setDocuments(Object.fromEntries(selectedTemplate.documents.map((item) => [item.id, true])) as Record<string, boolean>);
  }

  async function openCase(caseId: string) {
    setLocalError("");
    try {
      const detail = await getJson<CaseRecord>(`/cases/${caseId}`);
      setSelectedCaseId(caseId);
      setSelectedCase(detail);
      setCaseScreen("detail");
    } catch (error) {
      setLocalError(error instanceof Error ? error.message : "Dava acilamadi.");
    }
  }

  function openCreateScreen() {
    setCaseScreen("create");
    setLocalError("");
  }

  function openListScreen() {
    setCaseScreen("list");
    setLocalError("");
  }

  async function submitCase(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true);
    setLocalError("");
    try {
      const created = await postJson<CaseRecord>("/cases", {
        caseType,
        clientName,
        opponentName,
        courtName,
        subject,
        summary,
        completedDocumentIds: Object.entries(documents)
          .filter(([, completed]) => completed)
          .map(([id]) => id)
      });
      const caseList = await getJson<CaseRecord[]>("/cases");
      setSavedCases(caseList);
      const nextSelected = caseList.find((item) => item.id === created.id) ?? caseList[0] ?? null;
      setSelectedCaseId(nextSelected?.id ?? null);
      setSelectedCase(nextSelected);
      setCaseScreen("list");
      setCaseType(created.caseType as CaseType);
    } catch (error) {
      setLocalError(error instanceof Error ? error.message : "Dava kaydedilemedi.");
    } finally {
      setSaving(false);
    }
  }

  async function toggleSavedDocument(caseId: string, documentId: string, completed: boolean) {
    setLocalError("");
    try {
      const response = await patchJson<{ caseRecord: CaseRecord; cases: CaseRecord[] }>(`/cases/${caseId}/documents/${documentId}`, { completed });
      setSavedCases(response.cases);
      setSelectedCase(response.caseRecord);
      setSelectedCaseId(response.caseRecord.id);
    } catch (error) {
      setLocalError(error instanceof Error ? error.message : "Belge durumu guncellenemedi.");
    }
  }

  async function deleteCase(caseId: string) {
    const confirmed = window.confirm("Bu davayi silmek istiyor musunuz?");
    if (!confirmed) return;
    setLocalError("");
    try {
      const caseList = await deleteJson<CaseRecord[]>(`/cases/${caseId}`);
      setSavedCases(caseList);
      const nextSelected = caseList[0] ?? null;
      setSelectedCaseId(nextSelected?.id ?? null);
      setSelectedCase(nextSelected);
      setCaseScreen("list");
    } catch (error) {
      setLocalError(error instanceof Error ? error.message : "Dava silinemedi.");
    }
  }

  async function loadSampleCases() {
    setLoadingCases(true);
    setLocalError("");
    try {
      const caseList = await seedSamples<CaseRecord[]>("/cases/seed-samples");
      setSavedCases(caseList);
      const firstCase = caseList[0] ?? null;
      setSelectedCaseId(firstCase?.id ?? null);
      setSelectedCase(firstCase);
      setCaseScreen("list");
    } catch (error) {
      setLocalError(error instanceof Error ? error.message : "Ornek davalar yuklenemedi.");
    } finally {
      setLoadingCases(false);
    }
  }

  return (
    <section className="cases-shell">
      <div className="cases-toolbar panel">
        <div>
          <h2>Davalar</h2>
          <p>Dava kaydi, listeleme, goruntuleme ve silme islemleri.</p>
        </div>
        <div className="cases-toolbar-actions">
          <button className={caseScreen === "list" ? "active" : ""} onClick={openListScreen} type="button">Liste</button>
          <button className={caseScreen === "create" ? "active" : ""} onClick={openCreateScreen} type="button">Dava ekle</button>
        </div>
      </div>

      {localError && <div className="error">{localError}</div>}

      {caseScreen === "create" && (
        <section className="cases-grid">
          <form className="panel primary-panel case-form case-form-large" onSubmit={submitCase}>
            <PanelTitle icon={<FolderOpen size={20} />} title="Dava ekle" />
            <label className="field-label">
              Dava turu
              <select value={caseType} onChange={(event) => setCaseType(event.target.value as CaseType)}>
                {Object.entries(caseTypeLabels).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </label>
            <label className="field-label">
              Musteri / vekil olunan kisi
              <input value={clientName} onChange={(event) => setClientName(event.target.value)} placeholder="Ad soyad / unvan" />
            </label>
            <label className="field-label">
              Karsi taraf
              <input value={opponentName} onChange={(event) => setOpponentName(event.target.value)} placeholder="Davali / alacakli / borclu" />
            </label>
            <label className="field-label">
              Mahkeme / kurum
              <input value={courtName} onChange={(event) => setCourtName(event.target.value)} />
            </label>
            <label className="field-label">
              Konu / talep
              <input value={subject} onChange={(event) => setSubject(event.target.value)} />
            </label>
            <label className="field-label">
              Dosya ozeti
              <textarea rows={5} value={summary} onChange={(event) => setSummary(event.target.value)} />
            </label>
            <div className="case-template">
              <strong>{selectedTemplate.title}</strong>
              <p>{selectedTemplate.summary}</p>
              <small>Mahkeme ipucu: {selectedTemplate.courtHint}</small>
            </div>
            <div className="upload-actions">
              <button className="secondary-button" type="button" onClick={onGoToDocuments}>
                <Upload size={17} />
                Belge yukleme
              </button>
              <button type="button" onClick={markAll}>
                <CheckCircle2 size={17} />
                Tumunu isaretle
              </button>
              <button disabled={saving} type="submit">
                {saving ? <LoaderCircle className="spin" size={17} /> : <FolderOpen size={17} />}
                Kaydet ve listele
              </button>
            </div>
          </form>

          <div className="panel result-panel case-summary-panel">
            <div className="cases-preview-head">
              <h2>Hazirlik ozeti</h2>
              <button className="secondary-button" onClick={openListScreen} type="button">Listeye don</button>
            </div>
            <div className="case-score">
              <strong>{completion}%</strong>
              <span>Zorunlu belgeler tamamlanma orani</span>
            </div>
            <div className="meter" aria-hidden="true">
              <div className="meter-fill" style={{ width: `${completion}%` }} />
            </div>
            <div className="case-stats">
              <div><span>Musteri</span><strong>{clientName || "-"}</strong></div>
              <div><span>Karsi taraf</span><strong>{opponentName || "-"}</strong></div>
              <div><span>Konu</span><strong>{subject || "-"}</strong></div>
              <div><span>Mahkeme</span><strong>{courtName || selectedTemplate.courtHint}</strong></div>
            </div>
            <div className="check-note">
              <CheckCircle2 size={18} />
              <span>{missingRequired.length === 0 ? "Zorunlu belgeler tamam." : `${missingRequired.length} zorunlu belge eksik.`}</span>
            </div>
            <div className="check-note muted">
              <AlertCircle size={18} />
              <span>Kayit tamamlandiginda sistem otomatik olarak liste ekranina donecek.</span>
            </div>
            <div className="case-detail-documents">
              {groupedDocs.map(([groupName, items]) => (
                <section key={groupName} className="case-group">
                  <h3>{groupName}</h3>
                  <div className="checklist">
                    {items.map((item) => (
                      <label key={item.id} className={`check-item ${item.required ? "required" : ""}`}>
                        <input checked={Boolean(documents[item.id])} onChange={() => toggleDocument(item.id)} type="checkbox" />
                        <div>
                          <strong>{item.title}</strong>
                          <span>{item.detail}</span>
                        </div>
                        <em>{item.required ? "Zorunlu" : "Opsiyonel"}</em>
                      </label>
                    ))}
                  </div>
                </section>
              ))}
            </div>
          </div>
        </section>
      )}

      {caseScreen !== "create" && (
        <section className="cases-grid">
          <div className="panel case-list-panel">
            <div className="case-list-head">
              <div>
                <h2>Listeleme</h2>
                <p>{savedCases.length} dava kaydi</p>
              </div>
              <div className="case-list-head-actions">
                <button className="secondary-button" onClick={() => void loadSampleCases()} type="button">
                  <CheckCircle2 size={17} />
                  Ornekleri yukle
                </button>
                <button className="secondary-button" onClick={openCreateScreen} type="button">
                  <FolderOpen size={17} />
                  Dava ekle
                </button>
              </div>
            </div>
            {loadingCases ? (
              <p className="empty">Davalar yukleniyor...</p>
            ) : allCases.length ? (
              <div className="case-list">
                {allCases.map((item) => (
                  <article key={item.id} className={`case-list-item ${selectedCaseId === item.id ? "selected" : ""}`}>
                    <button className="case-list-main" onClick={() => void openCase(item.id)} type="button">
                      <div className="case-list-title">
                        <strong>{item.caseLabel}</strong>
                        <span>{item.progress}% tamamlandi</span>
                      </div>
                      <p>{item.clientName} - {item.opponentName}</p>
                      <small>{item.courtName}</small>
                    </button>
                    <div className="case-list-actions">
                      <button className="secondary-button" onClick={() => void openCase(item.id)} type="button">Goruntule</button>
                      <button className="danger-button" onClick={() => void deleteCase(item.id)} type="button">Sil</button>
                    </div>
                  </article>
                ))}
              </div>
            ) : (
              <div className="case-empty-detail">
                <h3>Liste bos</h3>
                <p>Listeyi test etmek icin ornek kayıtları yukleyin veya yeni dava ekleyin.</p>
                <div className="upload-actions">
                  <button className="secondary-button" onClick={() => void loadSampleCases()} type="button">
                    <CheckCircle2 size={17} />
                    Ornekleri yukle
                  </button>
                  <button onClick={openCreateScreen} type="button">
                    <FolderOpen size={17} />
                    Dava ekle
                  </button>
                </div>
              </div>
            )}
          </div>

          <div className="panel case-detail-panel">
            {selectedCase ? (
              <>
                <div className="case-detail-head">
                  <div>
                    <h2>Goruntuleme</h2>
                    <p>{selectedCase.caseLabel}</p>
                  </div>
                  <div className="case-detail-actions">
                    <button className="secondary-button" onClick={openListScreen} type="button">Listeye don</button>
                    <button className="danger-button" onClick={() => void deleteCase(selectedCase.id)} type="button">Sil</button>
                  </div>
                </div>
                <div className="case-score compact">
                  <strong>{selectedCase.progress}%</strong>
                  <span>Genel tamamlanma</span>
                </div>
                <div className="meter" aria-hidden="true">
                  <div className="meter-fill" style={{ width: `${selectedCase.progress}%` }} />
                </div>
                <div className="case-stats case-detail-stats">
                  <div><span>Musteri</span><strong>{selectedCase.clientName}</strong></div>
                  <div><span>Karsi taraf</span><strong>{selectedCase.opponentName}</strong></div>
                  <div><span>Mahkeme</span><strong>{selectedCase.courtName}</strong></div>
                  <div><span>Konu</span><strong>{selectedCase.subject}</strong></div>
                </div>
                <p>{selectedCase.summary}</p>
                <div className="case-detail-documents">
                  {selectedCase.documents.map((document) => (
                    <label key={document.id} className={`check-item ${document.required ? "required" : ""}`}>
                      <input checked={document.completed} onChange={() => void toggleSavedDocument(selectedCase.id, document.id, !document.completed)} type="checkbox" />
                      <div>
                        <strong>{document.title}</strong>
                        <span>{document.detail}</span>
                      </div>
                      <em>{document.required ? "Zorunlu" : "Opsiyonel"}</em>
                    </label>
                  ))}
                </div>
              </>
            ) : (
              <div className="case-empty-detail">
                <h2>Goruntuleme</h2>
                <p>Listedeki bir davayi secerek detaylari burada goruntuleyin.</p>
              </div>
            )}
          </div>
        </section>
      )}
    </section>
  );
}

function groupDocuments(items: ApiCaseDocument[]) {
  return items.reduce<Array<[string, ApiCaseDocument[]]>>((acc, item) => {
    const entry = acc.find(([groupName]) => groupName === item.group);
    if (entry) {
      entry[1].push(item);
    } else {
      acc.push([item.group, [item]]);
    }
    return acc;
  }, []);
}

function PanelTitle({ icon, title }: { icon: React.ReactNode; title: string }) {
  return <div className="panel-title">{icon}<h2>{title}</h2></div>;
}

function ResultPanel({ title, children }: { title: string; children: React.ReactNode }) {
  return <section className="panel result-panel"><h2>{title}</h2>{children}</section>;
}

function CitationList({ citations = [] }: { citations: Precedent[] }) {
  if (!citations.length) return null;
  return (
    <div className="citations">
      {citations.map((item, index) => (
        <article key={`${item.court}-${item.docketNo}-${item.decisionNo}-${index}`}>
          <strong>{item.court} {item.chamber}</strong>
          <span>{item.docketNo ?? "-"} E. / {item.decisionNo ?? "-"} K. - {item.date ?? "-"}</span>
          <p>{item.summary}</p>
        </article>
      ))}
    </div>
  );
}

function EmptyState({ text }: { text: string }) {
  return <p className="empty">{text}</p>;
}

function validateFile(file: File | null) {
  if (!file) return "Dosya secilmedi.";
  const extension = file.name.includes(".") ? file.name.slice(file.name.lastIndexOf(".")).toLowerCase() : "";
  if (!acceptedExtensions.includes(extension)) return "Sadece PDF, Word ve metin dosyalari desteklenir.";
  if (file.size > maxFileBytes) return "Dosya 25 MB sinirini asiyor.";
  if (file.size === 0) return "Dosya bos gorunuyor.";
  return null;
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

