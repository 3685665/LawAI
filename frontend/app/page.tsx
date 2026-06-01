"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import {
  AlertCircle,
  Bot,
  CheckCircle2,
  Database,
  FileSearch,
  FileText,
  LoaderCircle,
  Lock,
  Scale,
  Search,
  Send,
  Upload,
  X
} from "lucide-react";
import { checkHealth, postJson, Precedent, uploadMultipart } from "@/lib/api";

type Tab = "chat" | "search" | "petition" | "document" | "knowledge";
type ChatResponse = { answer: string; citations: Precedent[]; disclaimer: string };
type PetitionResponse = { title: string; body: string; citedPrecedents: Precedent[] };
type KnowledgeResponse = { indexed: number; storage: string; message: string };
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
            <p>Soru yanitlama, emsal arama, dilekce taslagi, dokuman kontrolu ve bilgi bankasi tek ekranda.</p>
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
