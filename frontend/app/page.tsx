"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { DataGrid, type GridColDef, type GridRowParams } from "@mui/x-data-grid";
import {
  ArrowRight,
  AlertCircle,
  BarChart3,
  Bot,
  CheckCircle2,
  ChevronRight,
  Database,
  GraduationCap,
  FileSearch,
  FileText,
  FolderOpen,
  LoaderCircle,
  Lock,
  Clock3,
  ClipboardList,
  MessageSquareMore,
  Scale,
  Search,
  Send,
  ShieldAlert,
  Upload,
  X
} from "lucide-react";
import { TrainingPanel, type TrainingTabResponse } from "./training-panel";
import {
  authChangePassword,
  authForgotPassword,
  authLogin,
  authLogout,
  authMe,
  authRegister,
  CaseDocument as ApiCaseDocument,
  CaseRecord,
  CaseTemplate,
  CaseTemplatesResponse,
  type AuthPasswordResetResponse,
  type AuthSessionResponse,
  type AuthUser,
  checkHealth,
  deleteJson,
  getJson,
  patchJson,
  postJson,
  listFeedback,
  Precedent,
  seedSamples,
  submitFeedback as postFeedback,
  type FeedbackStatus,
  type FeedbackRecord,
  uploadMultipart
} from "@/lib/api";

type Tab = "dashboard" | "chat" | "search" | "petition" | "training" | "cases" | "document" | "knowledge" | "feedback";
type AuthMode = "login" | "register" | "forgot";
type ChatResponse = { answer: string; citations: Precedent[]; disclaimer: string };
type PetitionResponse = { title: string; body: string; citedPrecedents: Precedent[] };
type KnowledgeResponse = { indexed: number; storage: string; message: string };
type FeedbackType = "hata" | "ozellik" | "genel";
type FeedbackFilter = "all" | FeedbackType;
type FeedbackStatusFilter = "all" | FeedbackStatus;
type FeedbackGridRow = FeedbackRecord & {
  typeLabel: string;
  statusLabel: string;
  createdAtLabel: string;
  messagePreview: string;
};
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

const feedbackTypeLabels: Record<FeedbackType, string> = {
  hata: "Hata bildirimi",
  ozellik: "Ozellik istegi",
  genel: "Genel geri bildirim"
};

const feedbackStatusLabels: Record<FeedbackStatus, string> = {
  received: "Alindi",
  read: "Incelendi",
  resolved: "Cozuldu"
};

function isFeedbackType(value: string): value is FeedbackType {
  return value === "hata" || value === "ozellik" || value === "genel";
}

function isFeedbackStatus(value: string): value is FeedbackStatus {
  return value === "received" || value === "read" || value === "resolved";
}

function getFeedbackTypeLabel(value: string) {
  return isFeedbackType(value) ? feedbackTypeLabels[value] : value;
}

function getFeedbackStatusLabel(value: string) {
  return isFeedbackStatus(value) ? feedbackStatusLabels[value] : value;
}

export default function Home() {
  const [activeTab, setActiveTab] = useState<Tab>("dashboard");
  const [privateMode, setPrivateMode] = useState(true);
  const [loading, setLoading] = useState("");
  const [error, setError] = useState("");
  const [backendOnline, setBackendOnline] = useState<boolean | null>(null);
  const [training, setTraining] = useState<TrainingTabResponse | null>(null);
  const [trainingLoading, setTrainingLoading] = useState(false);
  const [trainingError, setTrainingError] = useState("");
  const [selectedTrainingModule, setSelectedTrainingModule] = useState(0);
  const [selectedTrainingPrompt, setSelectedTrainingPrompt] = useState(0);
  const [trainingDraft, setTrainingDraft] = useState("");
  const [dashboardCases, setDashboardCases] = useState<CaseRecord[]>([]);
  const [dashboardTemplates, setDashboardTemplates] = useState<CaseTemplate[]>([]);
  const [dashboardLoading, setDashboardLoading] = useState(false);
  const [dashboardError, setDashboardError] = useState("");
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState("");
  const [authMode, setAuthMode] = useState<AuthMode>("login");
  const [authPreview, setAuthPreview] = useState<AuthPasswordResetResponse | null>(null);
  const [authForm, setAuthForm] = useState({
    name: "",
    email: "",
    password: "",
    confirmPassword: "",
    rememberMe: true,
    resetToken: "",
    currentPassword: "",
    newPassword: ""
  });
  const [accountOpen, setAccountOpen] = useState(false);
  const [accountLoading, setAccountLoading] = useState(false);
  const [accountError, setAccountError] = useState("");
  const [accountForm, setAccountForm] = useState({
    currentPassword: "",
    newPassword: "",
    confirmPassword: ""
  });

  const demoCredentials = {
    email: "admin@lawai.local",
    password: "ChangeMe123!"
  };

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
  const [feedbackItems, setFeedbackItems] = useState<FeedbackRecord[]>([]);
  const [feedbackLoading, setFeedbackLoading] = useState(false);
  const [feedbackError, setFeedbackError] = useState("");
  const [feedbackSubmitted, setFeedbackSubmitted] = useState<FeedbackRecord | null>(null);
  const [feedbackHistoryOpen, setFeedbackHistoryOpen] = useState(false);
  const [selectedFeedbackId, setSelectedFeedbackId] = useState<string | null>(null);
  const [feedbackSearch, setFeedbackSearch] = useState("");
  const [feedbackTypeFilter, setFeedbackTypeFilter] = useState<FeedbackFilter>("all");
  const [feedbackStatusFilter, setFeedbackStatusFilter] = useState<FeedbackStatusFilter>("all");
  const [feedbackForm, setFeedbackForm] = useState({
    type: "genel" as FeedbackType,
    subject: "",
    message: ""
  });

  const tabs = useMemo(() => [
    { id: "dashboard" as const, label: "Dashboard", icon: Scale },
    { id: "chat" as const, label: "Asistan", icon: Bot },
    { id: "search" as const, label: "Emsal Arama", icon: FileSearch },
    { id: "petition" as const, label: "Dilekce Taslak", icon: FileText },
    { id: "training" as const, label: "Egitim", icon: GraduationCap },
    { id: "cases" as const, label: "Dosyalar", icon: FolderOpen },
    { id: "document" as const, label: "Belge Isleme", icon: Upload },
    { id: "knowledge" as const, label: "Bilgi Bankasi", icon: Database },
    { id: "feedback" as const, label: "Geri Bildirim", icon: MessageSquareMore }
  ], []);

  useEffect(() => {
    checkHealth().then(setBackendOnline);
  }, []);

  useEffect(() => {
    let cancelled = false;
    authMe()
      .then((user) => {
        if (!cancelled) {
          setAuthUser(user);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setAuthUser(null);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setAuthReady(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!authUser || activeTab !== "training" || training || trainingLoading) {
      return;
    }

    let cancelled = false;
    setTrainingLoading(true);
    setTrainingError("");
    getJson<TrainingTabResponse>("/training/petition-drafting")
      .then((data) => {
        if (cancelled) return;
        setTraining(data);
        setSelectedTrainingModule(0);
        setSelectedTrainingPrompt(0);
        setTrainingDraft(data.prompts[0]?.prompt ?? "");
      })
      .catch((err) => {
        if (!cancelled) {
          setTrainingError(err instanceof Error ? err.message : "Egitim verisi yuklenemedi.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setTrainingLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [activeTab, authUser, training, trainingLoading]);

  useEffect(() => {
    if (!authUser || activeTab !== "dashboard" || dashboardCases.length || dashboardTemplates.length || dashboardLoading) {
      return;
    }

    let cancelled = false;
    setDashboardLoading(true);
    setDashboardError("");
    Promise.all([
      getJson<CaseRecord[]>("/cases"),
      getJson<CaseTemplatesResponse>("/cases/templates")
    ])
      .then(([cases, templates]) => {
        if (cancelled) return;
        setDashboardCases(cases);
        setDashboardTemplates(templates.templates);
      })
      .catch((err) => {
        if (!cancelled) {
          setDashboardError(err instanceof Error ? err.message : "Dashboard verisi yuklenemedi.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setDashboardLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [activeTab, authUser, dashboardCases.length, dashboardTemplates.length, dashboardLoading]);

  useEffect(() => {
    if (!authUser || activeTab !== "feedback" || feedbackItems.length || feedbackLoading) {
      return;
    }

    let cancelled = false;
    setFeedbackLoading(true);
    setFeedbackError("");
    listFeedback()
      .then((items) => {
        if (!cancelled) {
          setFeedbackItems(items);
          setSelectedFeedbackId((current) => current ?? items[0]?.id ?? null);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setFeedbackError(err instanceof Error ? err.message : "Geri bildirimler yuklenemedi.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setFeedbackLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [activeTab, authUser, feedbackItems.length, feedbackLoading]);

  const dashboardMetrics = useMemo(() => {
    const totalCases = dashboardCases.length;
    const avgProgress = totalCases
      ? Math.round(dashboardCases.reduce((sum, item) => sum + item.progress, 0) / totalCases)
      : 0;
    const completedRequiredDocs = dashboardCases.reduce((sum, item) => sum + item.completedRequiredDocumentCount, 0);
    const requiredDocs = dashboardCases.reduce((sum, item) => sum + item.requiredDocumentCount, 0);
    const riskCases = dashboardCases.filter((item) => item.progress < 60);
    return {
      totalCases,
      avgProgress,
      completedRequiredDocs,
      requiredDocs,
      riskCases,
      recentCases: [...dashboardCases].slice(0, 3),
      templates: dashboardTemplates.slice(0, 4)
    };
  }, [dashboardCases, dashboardTemplates]);

  const filteredFeedbackItems = useMemo(() => {
    const query = feedbackSearch.trim().toLowerCase();
    return feedbackItems.filter((item) => {
      const matchesQuery = !query
        || [item.subject, item.message, item.type, item.status]
          .join(" ")
          .toLowerCase()
          .includes(query);
      const matchesType = feedbackTypeFilter === "all" || item.type === feedbackTypeFilter;
      const matchesStatus = feedbackStatusFilter === "all" || item.status === feedbackStatusFilter;
      return matchesQuery && matchesType && matchesStatus;
    });
  }, [feedbackItems, feedbackSearch, feedbackTypeFilter, feedbackStatusFilter]);

  

  const feedbackRows = useMemo<FeedbackGridRow[]>(() => {
    return filteredFeedbackItems.map((item) => ({
      ...item,
      typeLabel: getFeedbackTypeLabel(item.type),
      statusLabel: getFeedbackStatusLabel(item.status),
      createdAtLabel: new Date(item.createdAt).toLocaleString("tr-TR"),
      messagePreview: item.message.length > 120 ? `${item.message.slice(0, 120).trim()}...` : item.message
    }));
  }, [filteredFeedbackItems]);

  const selectedFeedback = useMemo(() => {
    if (!filteredFeedbackItems.length) {
      return null;
    }
    return filteredFeedbackItems.find((item) => item.id === selectedFeedbackId) ?? filteredFeedbackItems[0];
  }, [filteredFeedbackItems, selectedFeedbackId]);

  const feedbackColumns = useMemo<GridColDef<FeedbackGridRow>[]>(() => [
    {
        field: "subject",
        headerName: "Baslik",
        flex: 1.35,
        minWidth: 220,
        renderCell: (params) => (
          <div className="feedback-grid-cell">
            <strong>{String(params.value ?? "")}</strong>
            <span>{String(params.row.messagePreview ?? "")}</span>
          </div>
        )
      },
    {
      field: "typeLabel",
      headerName: "Tip",
      width: 180,
      sortable: false,
      renderCell: (params) => <span className={`feedback-pill feedback-pill-type feedback-type-${params.row.type}`}>{String(params.value ?? "")}</span>
    },
    {
      field: "statusLabel",
      headerName: "Durum",
      width: 150,
      sortable: false,
      renderCell: (params) => <span className={`feedback-pill feedback-pill-status feedback-status-${params.row.status}`}>{String(params.value ?? "")}</span>
    },
    {
      field: "createdAtLabel",
      headerName: "Tarih",
      width: 185
    }
  ], []);

  async function run(action: string, fn: () => Promise<void>) {
    setLoading(action);
    setError("");
    try {
      await fn();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Beklenmeyen hata";
      if (message.toLowerCase().includes("oturum") || message.includes("401")) {
        setAuthUser(null);
      }
      setError(message);
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

  function submitFeedback(event: FormEvent) {
    event.preventDefault();
    run("feedback", async () => {
      const response = await postFeedback({
        type: feedbackForm.type,
        subject: feedbackForm.subject,
        message: feedbackForm.message
      });
      setFeedbackSubmitted(response.feedback);
      setFeedbackItems((current) => [response.feedback, ...current.filter((item) => item.id !== response.feedback.id)]);
      setSelectedFeedbackId(response.feedback.id);
      setFeedbackHistoryOpen(true);
      setFeedbackForm({ type: "genel", subject: "", message: "" });
    });
  }

  function seedKnowledge() {
    run("knowledge", async () => {
      setKnowledgeResult(await postJson<KnowledgeResponse>("/knowledge/seed-precedents", {}));
    });
  }

  function fillDemoCredentials() {
    setAuthError("");
    setAuthMode("login");
    setAuthForm((current) => ({
      ...current,
      email: demoCredentials.email,
      password: demoCredentials.password,
      rememberMe: true
    }));
  }

  async function submitAuth(event: FormEvent) {
    event.preventDefault();
    setAuthError("");
    setAuthLoading(true);
    try {
      if (authMode === "login") {
        if (!authForm.email || !authForm.password) throw new Error("E-posta ve sifre gerekli.");
        const session = await authLogin({
          email: authForm.email,
          password: authForm.password,
          rememberMe: authForm.rememberMe
        });
        setAuthUser(session.user);
        setAuthPreview(null);
        setActiveTab("dashboard");
      } else if (authMode === "register") {
        if (!authForm.name.trim()) throw new Error("Ad soyad gerekli.");
        if (authForm.password !== authForm.confirmPassword) throw new Error("Sifreler eslesmiyor.");
        const session = await authRegister({
          name: authForm.name,
          email: authForm.email,
          password: authForm.password
        });
        setAuthUser(session.user);
        setAuthPreview(null);
        setActiveTab("dashboard");
      } else if (authMode === "forgot") {
        if (!authForm.email) throw new Error("E-posta gerekli.");
        const response = await authForgotPassword({ email: authForm.email });
        setAuthPreview(response);
      }
    } catch (err) {
      setAuthError(err instanceof Error ? err.message : "Giris islemi basarisiz.");
    } finally {
      setAuthLoading(false);
    }
  }

  async function handleLogout() {
    try {
      await authLogout();
    } finally {
      setAuthUser(null);
      setActiveTab("dashboard");
      setAuthPreview(null);
      setAuthMode("login");
      setAccountOpen(false);
      setChatResponse(null);
      setPrecedents([]);
      setPetitionResult(null);
      setKnowledgeResult(null);
      setFeedbackItems([]);
      setFeedbackError("");
      setFeedbackSubmitted(null);
      setFeedbackHistoryOpen(false);
      setSelectedFeedbackId(null);
      setFeedbackSearch("");
      setFeedbackTypeFilter("all");
      setFeedbackStatusFilter("all");
      setTraining(null);
      setDashboardCases([]);
      setDashboardTemplates([]);
      setDashboardError("");
      setTrainingError("");
      setAuthForm({
        name: "",
        email: "",
        password: "",
        confirmPassword: "",
        rememberMe: true,
        resetToken: "",
        currentPassword: "",
        newPassword: ""
      });
      setAccountForm({
        currentPassword: "",
        newPassword: "",
        confirmPassword: ""
      });
    }
  }

  async function handleChangePassword(event: FormEvent) {
    event.preventDefault();
    setAccountError("");
    setAccountLoading(true);
    try {
      if (accountForm.newPassword !== accountForm.confirmPassword) {
        throw new Error("Yeni sifreler eslesmiyor.");
      }
      const session = await authChangePassword({
        currentPassword: accountForm.currentPassword,
        newPassword: accountForm.newPassword
      });
      setAuthUser(session.user);
      setAccountOpen(false);
      setAccountForm({
        currentPassword: "",
        newPassword: "",
        confirmPassword: ""
      });
    } catch (err) {
      setAccountError(err instanceof Error ? err.message : "Sifre degistirilemedi.");
    } finally {
      setAccountLoading(false);
    }
  }

  if (!authReady) {
    return (
      <main className="auth-shell">
        <section className="auth-card panel">
          <LoaderCircle className="spin" size={32} />
          <h1>LawAI Studio</h1>
          <p>Oturum bilgisi kontrol ediliyor...</p>
        </section>
      </main>
    );
  }

  if (!authUser) {
    return (
      <main className="auth-shell">
        <section className="auth-card panel auth-card-split">
          <div className="auth-hero">
            <div className="auth-hero-head">
              <span className="eyebrow">Kurumsal erisim</span>
              <h1>Avukat ofisi icin guvenli oturum.</h1>
              <p>Oturumlar HttpOnly cookie ile korunur. Hesap yonetimi, sifre kurtarma ve ilk kullanim akisi tek bir resmi panelde sunulur.</p>
            </div>

            <div className="auth-points">
              <div>
                <strong>HttpOnly cookie</strong>
                <span>Parola tarayicida tutulmaz, oturum sunucu tarafinda dogrulanir.</span>
              </div>
              <div>
                <strong>Resmi akis</strong>
                <span>Giris, hesap acma ve sifre kurtarma adimlari tek duzende ilerler.</span>
              </div>
              <div>
                <strong>Yetkili erisim</strong>
                <span>Dogrulama olmadan hicbir uygulama ekrani acilmaz.</span>
              </div>
            </div>

            <div className="auth-credentials panel">
              <div className="auth-credentials-head">
                <strong>Ilk kullanim hesabi</strong>
                <span>Gelistirme ortami icin hazirlandi.</span>
              </div>
              <div className="auth-credentials-grid">
                <div>
                  <small>E-posta</small>
                  <strong>{demoCredentials.email}</strong>
                </div>
                <div>
                  <small>Sifre</small>
                  <strong>{demoCredentials.password}</strong>
                </div>
              </div>
              <button type="button" className="secondary-button auth-secondary" onClick={fillDemoCredentials}>
                Ornek hesabi doldur
              </button>
            </div>
          </div>

          <form className="auth-form" onSubmit={submitAuth}>
            <div className="auth-form-head">
              <span className="eyebrow">Oturum</span>
              <h2>{authMode === "login" ? "Giris yap" : authMode === "register" ? "Hesap olustur" : "Sifremi unuttum"}</h2>
              <p>{authMode === "login" ? "Kurum hesabiniza guvenli sekilde giris yapin." : authMode === "register" ? "Yeni kullanici hesabi olusturun." : "Sifirlama baglantisi e-posta ile iletilir."}</p>
            </div>
            <div className="auth-switch">
              <button type="button" className={authMode === "login" ? "active" : ""} onClick={() => setAuthMode("login")}>Giris</button>
              <button type="button" className={authMode === "register" ? "active" : ""} onClick={() => setAuthMode("register")}>Hesap ac</button>
              <button type="button" className={authMode === "forgot" ? "active" : ""} onClick={() => setAuthMode("forgot")}>Sifremi unuttum</button>
            </div>

            <div className="auth-fields">
              {authMode === "register" && (
                <label className="field-label">
                  Ad soyad
                  <input autoComplete="name" value={authForm.name} onChange={(event) => setAuthForm({ ...authForm, name: event.target.value })} />
                </label>
              )}
              {(authMode === "login" || authMode === "register" || authMode === "forgot") && (
                <label className="field-label">
                  E-posta
                  <input autoComplete="email" type="email" value={authForm.email} onChange={(event) => setAuthForm({ ...authForm, email: event.target.value })} />
                </label>
              )}
              {(authMode === "login" || authMode === "register") && (
                <label className="field-label">
                  Sifre
                  <input autoComplete={authMode === "login" ? "current-password" : "new-password"} type="password" value={authForm.password} onChange={(event) => setAuthForm({ ...authForm, password: event.target.value })} />
                </label>
              )}
              {authMode === "register" && (
                <label className="field-label">
                  Sifre tekrar
                  <input autoComplete="new-password" type="password" value={authForm.confirmPassword} onChange={(event) => setAuthForm({ ...authForm, confirmPassword: event.target.value })} />
                </label>
              )}
              {authMode === "login" && (
                <label className="remember-row">
                  <input type="checkbox" checked={authForm.rememberMe} onChange={(event) => setAuthForm({ ...authForm, rememberMe: event.target.checked })} />
                  <span>Beni hatirla</span>
                </label>
              )}
            </div>

            {authPreview?.resetLinkPreview ? (
              <div className="auth-recovery-card">
                <strong>Sifirlama baglantisi hazir</strong>
                <span>
                  E-posta ile iletilen baglanti:{" "}
                  <a href={authPreview.resetLinkPreview}>{authPreview.resetLinkPreview}</a>
                </span>
              </div>
            ) : null}
            {authError ? <div className="error">{authError}</div> : null}

            <div className="auth-actions">
              <button disabled={authLoading} type="submit">
                {authLoading ? <LoaderCircle className="spin" size={17} /> : null}
                {authMode === "login" ? "Giris yap" : authMode === "register" ? "Hesap olustur" : "Sifirlama baglantisi iste"}
              </button>
            </div>

            <p className="auth-note">
              {authMode === "forgot" ? "E-posta adresiniz eslesirse sifirlama baglantisi iletilir." : "Hesaplar cookie tabanli oturum ile korunur."}
            </p>
          </form>
        </section>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <Scale size={28} />
          <div>
            <strong>LawAI Studio</strong>
            <span>Avukat calisma alani</span>
          </div>
        </div>
        <div className="nav-label">Uygulamalar</div>
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

        <div className="sidebar-user">
          <div>
            <strong>{authUser.name}</strong>
            <span>{authUser.email}</span>
            <span className="sidebar-user-role">{authUser.role === "ADMIN" ? "Yonetici" : "Kullanici"}</span>
          </div>
          <div className="sidebar-user-actions">
            <button className="secondary-button" type="button" onClick={() => setAccountOpen(true)}>
              Sifre degistir
            </button>
            <button className="secondary-button" type="button" onClick={() => void handleLogout()}>
              Cikis
            </button>
          </div>
        </div>
      </aside>

      <section className="workspace">
        {activeTab === "dashboard" && (
          <header className="topbar">
            <div>
              <span className="eyebrow">Hukuk AI Calisma Sistemi</span>
              <h1>Dosya, dilekce ve emsal calismasi icin kurumsal arayuz.</h1>
              <p>Kaynakli yanit, belge inceleme, risk kontrolu ve taslak olusturma tek panelde; resmi ofis kullanimina uygun, sakin ve kontrollu bir akis.</p>
            </div>
            <span className={backendOnline === false ? "status offline" : "status"}>{loading ? "Isleniyor" : backendOnline === false ? "Backend yok" : "Hazir"}</span>
          </header>
        )}

        {activeTab === "dashboard" && (
          <section className="dashboard-hero panel">
            <div className="dashboard-hero-copy">
              <h2>Ofis standardinda calisan, kaynakli ve izlenebilir hukuk uygulamasi.</h2>
              <p>Dilekce, emsal, dosya ve egitim modulleri tek yapida; avukat icin hizli ama resmi is akisi sunar.</p>
              <div className="hero-actions">
                <button type="button" onClick={() => setActiveTab("training")}>
                  <GraduationCap size={17} />
                  Egitim
                </button>
                <button className="secondary-button" type="button" onClick={() => setActiveTab("petition")}>
                  <FileText size={17} />
                  Dilekce
                </button>
                <button className="ghost-button" type="button" onClick={() => setActiveTab("search")}>
                  Emsal
                  <ChevronRight size={16} />
                </button>
              </div>
            </div>
            <div className="dashboard-hero-side">
              <div className="hero-metric">
                <span>Durum</span>
                <strong>{backendOnline === false ? "Baglanti yok" : "AI servisi hazir"}</strong>
              </div>
              <div className="hero-metric">
                <span>Calisma modu</span>
                <strong>{privateMode ? "Gizli mod aktif" : "Standart mod"}</strong>
              </div>
              <div className="hero-metric">
                <span>Odak</span>
                <strong>Kaynak, risk, taslak</strong>
              </div>
            </div>
          </section>
        )}

        {activeTab === "dashboard" && (
          <section className="dashboard-grid">
            <div className="dashboard-stats">
              <article className="panel stat-card">
                <span>Toplam dosya</span>
                <strong>{dashboardLoading ? "..." : dashboardMetrics.totalCases}</strong>
                <small>Aktif dosya kayitlari</small>
              </article>
              <article className="panel stat-card">
                <span>Ortalama ilerleme</span>
                <strong>{dashboardLoading ? "..." : `${dashboardMetrics.avgProgress}%`}</strong>
                <small>Dosya bazli durum ortalamasi</small>
              </article>
              <article className="panel stat-card">
                <span>Tamamlanan zorunlu belge</span>
                <strong>{dashboardLoading ? "..." : `${dashboardMetrics.completedRequiredDocs}/${dashboardMetrics.requiredDocs}`}</strong>
                <small>Eksik evrak takibi</small>
              </article>
              <article className="panel stat-card">
                <span>Riskli dosya</span>
                <strong>{dashboardLoading ? "..." : dashboardMetrics.riskCases.length}</strong>
                <small>60% altinda kalan dosyalar</small>
              </article>
            </div>

            {dashboardError && <div className="error">{dashboardError}</div>}

            <div className="dashboard-panels">
              <article className="panel dashboard-panel dashboard-panel-large">
                <div className="section-head">
                  <div>
                    <span className="section-label">Son hareketler</span>
                    <h3>Son dosyalar</h3>
                  </div>
                  <button className="secondary-button" type="button" onClick={() => setActiveTab("cases")}>
                    Dosyalara git
                    <ArrowRight size={16} />
                  </button>
                </div>
                {dashboardLoading ? (
                  <p className="empty">Yukleniyor...</p>
                ) : dashboardMetrics.recentCases.length ? (
                  <div className="dashboard-list">
                    {dashboardMetrics.recentCases.map((item) => (
                      <article key={item.id} className="dashboard-row">
                        <div>
                          <strong>{item.caseLabel}</strong>
                          <span>{item.clientName} - {item.opponentName}</span>
                        </div>
                        <div className="dashboard-row-meta">
                          <span>{item.progress}%</span>
                          <small>{item.courtName}</small>
                        </div>
                      </article>
                    ))}
                  </div>
                ) : (
                  <p className="empty">Henuz dosya yok. Ornek kayitlari yukleyin.</p>
                )}
              </article>

              <article className="panel dashboard-panel">
                <div className="section-head">
                  <div>
                    <span className="section-label">Kontrol</span>
                    <h3>Risk ve gorevler</h3>
                  </div>
                  <button className="secondary-button" type="button" onClick={() => setActiveTab("training")}>
                    Egitim ac
                    <ArrowRight size={16} />
                  </button>
                </div>
                <div className="dashboard-checks">
                  <div className="dashboard-check">
                    <ShieldAlert size={18} />
                    <div>
                      <strong>Usul kontrolu</strong>
                      <span>Sure, harc ve yetki noktalarini dava acmadan once kontrol edin.</span>
                    </div>
                  </div>
                  <div className="dashboard-check">
                    <ClipboardList size={18} />
                    <div>
                      <strong>Belge hazirligi</strong>
                      <span>Eksik ekleri tamamlama ve dosya siralamasini standardize etme.</span>
                    </div>
                  </div>
                  <div className="dashboard-check">
                    <Clock3 size={18} />
                    <div>
                      <strong>Bugun icin takip</strong>
                      <span>Oncelikli isler, bekleyen revizyonlar ve acil dosyalar.</span>
                    </div>
                  </div>
                </div>
                {dashboardMetrics.riskCases.length ? (
                  <div className="dashboard-risk-list">
                    {dashboardMetrics.riskCases.slice(0, 3).map((item) => (
                      <div key={item.id} className="dashboard-risk-item">
                        <strong>{item.caseLabel}</strong>
                        <span>{item.progress}% tamamlandi</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="empty">Risk seviyesi dusuk dosya yok.</p>
                )}
              </article>

              <article className="panel dashboard-panel">
                <div className="section-head">
                  <div>
                    <span className="section-label">Hizli erisim</span>
                    <h3>Calisma kisa yollari</h3>
                  </div>
                </div>
                <div className="dashboard-actions">
                  <button type="button" onClick={() => setActiveTab("petition")}>
                    Dilekce baslat
                  </button>
                  <button className="secondary-button" type="button" onClick={() => setActiveTab("search")}>
                    Emsal ara
                  </button>
                  <button className="secondary-button" type="button" onClick={() => setActiveTab("document")}>
                    Belge yukle
                  </button>
                  <button className="secondary-button" type="button" onClick={() => setActiveTab("knowledge")}>
                    Bilgi bankasi
                  </button>
                </div>
                <div className="dashboard-template-list">
                  {dashboardMetrics.templates.map((template) => (
                    <article key={template.caseType} className="dashboard-template">
                      <strong>{template.label}</strong>
                      <span>{template.title}</span>
                      <small>{template.summary}</small>
                    </article>
                  ))}
                </div>
              </article>
            </div>
          </section>
        )}

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

        {activeTab === "training" && (
          <TrainingPanel
            loading={trainingLoading}
            error={trainingError}
            training={training}
            selectedModuleIndex={selectedTrainingModule}
            selectedPromptIndex={selectedTrainingPrompt}
            draft={trainingDraft}
            onSelectModule={setSelectedTrainingModule}
            onSelectPrompt={(index) => {
              setSelectedTrainingPrompt(index);
              setTrainingDraft(training?.prompts[index]?.prompt ?? "");
            }}
            onChangeDraft={setTrainingDraft}
            onReload={() => {
              setTraining(null);
              setTrainingError("");
            }}
          />
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

        {activeTab === "feedback" && (
          <section className="feedback-workspace">
            <form className="panel primary-panel feedback-compose" onSubmit={submitFeedback}>
              <PanelTitle icon={<MessageSquareMore size={20} />} title="Geri bildirim gonder" />
              <p className="panel-subtitle">Hata bildirimi, ozellik istegi veya genel geri bildirim ilet; kayit gecmisin burada saklansin.</p>
              <div className="feedback-types">
                <button type="button" className={feedbackForm.type === "hata" ? "active" : ""} onClick={() => setFeedbackForm((current) => ({ ...current, type: "hata" }))}>
                  Hata bildirimi
                </button>
                <button type="button" className={feedbackForm.type === "ozellik" ? "active" : ""} onClick={() => setFeedbackForm((current) => ({ ...current, type: "ozellik" }))}>
                  Ozellik istegi
                </button>
                <button type="button" className={feedbackForm.type === "genel" ? "active" : ""} onClick={() => setFeedbackForm((current) => ({ ...current, type: "genel" }))}>
                  Genel geri bildirim
                </button>
              </div>
              <label className="field-label">
                Baslik
                <input value={feedbackForm.subject} onChange={(event) => setFeedbackForm((current) => ({ ...current, subject: event.target.value }))} placeholder="Orn: Belge yukleme ekraninda hata" />
              </label>
              <label className="field-label">
                Detay
                <textarea rows={11} value={feedbackForm.message} onChange={(event) => setFeedbackForm((current) => ({ ...current, message: event.target.value }))} placeholder="Sorunu, istegi veya gorusunuzu detayli yazin." />
              </label>
              <div className="row feedback-compose-actions">
                <button disabled={loading === "feedback"} type="submit">
                  <Send size={17} />
                  Gonder
                </button>
                {feedbackSubmitted ? <div className="auth-preview feedback-success">Son kayit: <strong>{feedbackSubmitted.subject}</strong></div> : null}
              </div>
            </form>

            <section className="panel dashboard-panel feedback-history-panel">
              <div className="section-head feedback-history-head">
                <div>
                  <span className="section-label">Gecmis</span>
                  <h3>Sikayet gecmisi</h3>
                </div>
                <div className="feedback-history-actions">
                  <span className="status">{filteredFeedbackItems.length} kayit</span>
                  <button
                    className="secondary-button"
                    type="button"
                    onClick={() => setFeedbackHistoryOpen((current) => !current)}
                  >
                    <ClipboardList size={16} />
                    {feedbackHistoryOpen ? "Gecmisi gizle" : "Gecmisi goster"}
                  </button>
                </div>
              </div>

              {feedbackHistoryOpen ? (
                <>
                  <div className="feedback-toolbar">
                    <label className="field-label">
                      Arama
                      <input
                        value={feedbackSearch}
                        onChange={(event) => setFeedbackSearch(event.target.value)}
                        placeholder="Baslik, mesaj, durum veya tip ara"
                      />
                    </label>
                    <label className="field-label">
                      Tip
                      <select value={feedbackTypeFilter} onChange={(event) => setFeedbackTypeFilter(event.target.value as FeedbackFilter)}>
                        <option value="all">Tum tipler</option>
                        <option value="hata">Hata bildirimi</option>
                        <option value="ozellik">Ozellik istegi</option>
                        <option value="genel">Genel geri bildirim</option>
                      </select>
                    </label>
                    <label className="field-label">
                      Durum
                      <select value={feedbackStatusFilter} onChange={(event) => setFeedbackStatusFilter(event.target.value as FeedbackStatusFilter)}>
                        <option value="all">Tum durumlar</option>
                        <option value="received">Alindi</option>
                        <option value="read">Incelendi</option>
                        <option value="resolved">Cozuldu</option>
                      </select>
                    </label>
                  </div>
                  {feedbackError ? <div className="error">{feedbackError}</div> : null}
                  <div className="feedback-datagrid-wrap">
                    <DataGrid
                      rows={feedbackRows}
                      columns={feedbackColumns}
                      loading={feedbackLoading}
                      autoHeight
                      hideFooter
                      disableRowSelectionOnClick
                      disableColumnMenu
                      rowHeight={72}
                      columnHeaderHeight={42}
                      onRowClick={(params: GridRowParams<FeedbackGridRow>) => setSelectedFeedbackId(String(params.id))}
                      sx={{
                        border: "none",
                        color: "var(--ink)",
                        fontFamily: "inherit",
                        "& .MuiDataGrid-columnHeaders": {
                          backgroundColor: "#f6f8fb",
                          borderBottom: "1px solid var(--line)",
                          color: "var(--muted)",
                          fontSize: "12px",
                          fontWeight: 700,
                          letterSpacing: "0.04em",
                          textTransform: "uppercase"
                        },
                        "& .MuiDataGrid-cell": {
                          borderBottom: "1px solid rgba(212, 220, 230, 0.72)",
                          outline: "none"
                        },
                        "& .MuiDataGrid-row": {
                          cursor: "pointer"
                        },
                        "& .MuiDataGrid-row:hover": {
                          backgroundColor: "#f4f8fc"
                        },
                        "& .MuiDataGrid-cell:focus, & .MuiDataGrid-columnHeader:focus": {
                          outline: "none"
                        }
                      }}
                    />
                  </div>
                  {selectedFeedback ? (
                    <article className="panel feedback-detail-panel feedback-detail-panel-wide">
                      <div className="section-head">
                        <div>
                          <span className="section-label">Detay</span>
                          <h3>Secili kayit</h3>
                        </div>
                      </div>
                      <div className="feedback-detail">
                        <div className="feedback-detail-head">
                          <strong>{selectedFeedback.subject}</strong>
                          <span className={`feedback-pill feedback-pill-type feedback-type-${selectedFeedback.type}`}>{getFeedbackTypeLabel(selectedFeedback.type)}</span>
                        </div>
                        <div className="feedback-detail-meta">
                          <div>
                            <small>Durum</small>
                            <strong className={`feedback-pill feedback-pill-status feedback-status-${selectedFeedback.status}`}>{getFeedbackStatusLabel(selectedFeedback.status)}</strong>
                          </div>
                          <div>
                            <small>Tarih</small>
                            <strong>{new Date(selectedFeedback.createdAt).toLocaleString("tr-TR")}</strong>
                          </div>
                        </div>
                        <div className="feedback-detail-body">
                          <small>Mesaj</small>
                          <p>{selectedFeedback.message}</p>
                        </div>
                      </div>
                    </article>
                  ) : null}
                </>
              ) : (
                <div className="feedback-history-closed">
                  <p>Gecmis gizli. Eski kayitlari ve detaylarini goruntulemek icin butonu kullan.</p>
                </div>
              )}
            </section>
          </section>
        )}

        {accountOpen && (
          <div className="account-overlay" role="dialog" aria-modal="true" aria-label="Sifre degistir">
            <form className="panel account-card" onSubmit={handleChangePassword}>
              <div className="section-head">
                <div>
                  <span className="section-label">Hesap</span>
                  <h3>Sifre degistir</h3>
                </div>
                <button className="secondary-button" type="button" onClick={() => setAccountOpen(false)}>
                  Kapat
                </button>
              </div>
              <label className="field-label">
                Mevcut sifre
                <input type="password" value={accountForm.currentPassword} onChange={(event) => setAccountForm({ ...accountForm, currentPassword: event.target.value })} />
              </label>
              <label className="field-label">
                Yeni sifre
                <input type="password" value={accountForm.newPassword} onChange={(event) => setAccountForm({ ...accountForm, newPassword: event.target.value })} />
              </label>
              <label className="field-label">
                Yeni sifre tekrar
                <input type="password" value={accountForm.confirmPassword} onChange={(event) => setAccountForm({ ...accountForm, confirmPassword: event.target.value })} />
              </label>
              {accountError ? <div className="error">{accountError}</div> : null}
              <button disabled={accountLoading} type="submit">
                {accountLoading ? <LoaderCircle className="spin" size={17} /> : null}
                Sifreyi guncelle
              </button>
            </form>
          </div>
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
                <p>Listeyi test etmek icin ornek kayitlari yukleyin veya yeni dava ekleyin.</p>
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






