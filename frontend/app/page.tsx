"use client";

import { FormEvent, SyntheticEvent, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { DataGrid, type GridColDef, type GridRowParams } from "@mui/x-data-grid";
import {
  AlertCircle,
  ArrowLeft,
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
  Mic,
  Clock3,
  MessageSquareMore,
  Scale,
  Search,
  Send,
  Settings,
  ShieldAlert,
  Upload,
  UserRound,
  X
} from "lucide-react";
import { TrainingPanel, type TrainingTabResponse } from "./training-panel";
import { getMessages, isLocale, type Locale } from "@/lib/i18n";
import {
  authChangePassword,
  authForgotPassword,
  authLogin,
  authLogout,
  authMe,
  authRegister,
  authUpdateProfile,
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
  getUser,
  patchJson,
  postJson,
  listFeedback,
  listUsers,
  Precedent,
  seedSamples,
  submitFeedback as postFeedback,
  type FeedbackStatus,
  type FeedbackRecord,
  uploadMultipart
} from "@/lib/api";

type Tab = "chat" | "search" | "petition" | "training" | "cases" | "document" | "knowledge" | "feedback" | "profile" | "settings" | "admin";
type AuthMode = "login" | "register" | "forgot";
type ChatResponse = { answer: string; citations: Precedent[]; disclaimer: string };
type ChatMessage = {
  id: string;
  role: "user" | "assistant";
  text: string;
  citations?: Precedent[];
  disclaimer?: string;
};
type ChatAttachment = {
  filename: string;
  size: number;
  content: string;
};
type SpeechRecognitionLike = {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  maxAlternatives?: number;
  onstart: (() => void) | null;
  onresult: ((event: {
    resultIndex: number;
    results: ArrayLike<{
      0: { transcript: string };
      isFinal: boolean;
      length: number;
    }>;
  }) => void) | null;
  onaudiostart?: (() => void) | null;
  onspeechstart?: (() => void) | null;
  onerror: ((event: { error?: string; message?: string }) => void) | null;
  onend: (() => void) | null;
  abort: () => void;
  start: () => void;
  stop: () => void;
};
type SpeechRecognitionConstructor = new () => SpeechRecognitionLike;
type SmartNote = {
  id: string;
  text: string;
  createdAt: string;
};
type PetitionResponse = { title: string; body: string; citedPrecedents: Precedent[] };
type KnowledgeResponse = { indexed: number; storage: string; message: string };
type FeedbackType = "hata" | "ozellik" | "genel";
type FeedbackFilter = "all" | FeedbackType;
type FeedbackStatusFilter = "all" | FeedbackStatus;
type PetitionMethod = "case" | "quick" | "detailed";
type PetitionModel = "standard" | "premium";
type ResearchMode = "ai" | "manual";
type PrecedentSourceKey = "all" | "yargitay" | "danistay" | "aym" | "rekabet" | "literature" | "thesis";
type FeedbackGridRow = FeedbackRecord & {
  typeLabel: string;
  statusLabel: string;
  createdAtLabel: string;
  messagePreview: string;
};
type AdminSection = "feedback" | "users";
type AdminUserView = "list" | "detail";
type ThemeMode = "original" | "light" | "dark";
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
  const [activeTab, setActiveTab] = useState<Tab>("chat");
  const [privateMode] = useState(true);
  const [themeMode, setThemeMode] = useState<ThemeMode>("original");
  const [locale, setLocale] = useState<Locale>("tr");
  const [loading, setLoading] = useState("");
  const [error, setError] = useState("");
  const [backendOnline, setBackendOnline] = useState<boolean | null>(null);
  const [training, setTraining] = useState<TrainingTabResponse | null>(null);
  const [trainingLoading, setTrainingLoading] = useState(false);
  const [trainingError, setTrainingError] = useState("");
  const [selectedTrainingModule, setSelectedTrainingModule] = useState(0);
  const [selectedTrainingPrompt, setSelectedTrainingPrompt] = useState(0);
  const [trainingDraft, setTrainingDraft] = useState("");
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
  const [accountLoading, setAccountLoading] = useState(false);
  const [accountError, setAccountError] = useState("");
  const [accountForm, setAccountForm] = useState({
    currentPassword: "",
    newPassword: "",
    confirmPassword: ""
  });
  const [profileForm, setProfileForm] = useState({
    name: "",
    email: ""
  });
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileError, setProfileError] = useState("");
  const [profileSuccess, setProfileSuccess] = useState("");

  const demoCredentials = {
    email: "admin@lawai.local",
    password: "ChangeMe123!"
  };

  const t = useMemo(() => getMessages(locale), [locale]);

  const [smartNoteCaseTitle, setSmartNoteCaseTitle] = useState("Genel analiz");
  const [smartNoteDraft, setSmartNoteDraft] = useState("");
  const [smartNotes, setSmartNotes] = useState<SmartNote[]>([]);
  const [chatResponse, setChatResponse] = useState<ChatResponse | null>(null);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatAttachment, setChatAttachment] = useState<ChatAttachment | null>(null);
  const [chatAttachmentLoading, setChatAttachmentLoading] = useState(false);
  const [voiceListening, setVoiceListening] = useState(false);
  const [voiceNotice, setVoiceNotice] = useState("");
  const chatFileInputRef = useRef<HTMLInputElement | null>(null);
  const speechRecognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const voiceBaseDraftRef = useRef("");
  const [researchMode, setResearchMode] = useState<ResearchMode>("ai");
  const [searchQuery, setSearchQuery] = useState("Guncel kararlara gore bosanmada ziynet esyalari nasil paylasilir?");
  const [searchCourt, setSearchCourt] = useState("");
  const [searchChamber, setSearchChamber] = useState("");
  const [searchDocket, setSearchDocket] = useState("");
  const [searchDateFrom, setSearchDateFrom] = useState("");
  const [searchDateTo, setSearchDateTo] = useState("");
  const [precedentSource, setPrecedentSource] = useState<PrecedentSourceKey>("all");
  const [precedents, setPrecedents] = useState<Precedent[]>([]);
  const [precedentSummary, setPrecedentSummary] = useState("");
  const [selectedPrecedentIndex, setSelectedPrecedentIndex] = useState<number | null>(null);
  const [petition, setPetition] = useState({
    petitionType: "Alacak",
    court: "Istanbul Nobetci Asliye Hukuk Mahkemesi",
    parties: "Davaci: ...\nDavali: ...",
    facts: "Taraflar arasinda akdedilen sozlesme kapsaminda edimler yerine getirilmemis, ihtara ragmen borc odenmemistir.",
    demands: "Alacagin yasal faiziyle birlikte tahsiline karar verilmesini talep ederiz."
  });
  const [petitionMethod, setPetitionMethod] = useState<PetitionMethod>("quick");
  const [petitionModel, setPetitionModel] = useState<PetitionModel>("standard");
  const [petitionContext, setPetitionContext] = useState("");
  const [petitionContextSources, setPetitionContextSources] = useState({
    upload: false,
    existing: true,
    training: false
  });
  const [petitionEditInstruction, setPetitionEditInstruction] = useState("");
  const [selectedPetitionText, setSelectedPetitionText] = useState("");
  const [petitionEditPreview, setPetitionEditPreview] = useState<{ before: string; after: string } | null>(null);
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
  const [feedbackLoaded, setFeedbackLoaded] = useState(false);
  const [selectedFeedbackId, setSelectedFeedbackId] = useState<string | null>(null);
  const [feedbackSearch, setFeedbackSearch] = useState("");
  const [feedbackTypeFilter, setFeedbackTypeFilter] = useState<FeedbackFilter>("all");
  const [feedbackStatusFilter, setFeedbackStatusFilter] = useState<FeedbackStatusFilter>("all");
  const [feedbackForm, setFeedbackForm] = useState({
    type: "genel" as FeedbackType,
    subject: "",
    message: ""
  });
  const [adminSection, setAdminSection] = useState<AdminSection>("feedback");
  const [adminUsers, setAdminUsers] = useState<AuthUser[]>([]);
  const [adminUsersLoading, setAdminUsersLoading] = useState(false);
  const [adminUsersError, setAdminUsersError] = useState("");
  const [selectedAdminUserId, setSelectedAdminUserId] = useState<string | null>(null);
  const [selectedAdminUser, setSelectedAdminUser] = useState<AuthUser | null>(null);
  const [adminUserView, setAdminUserView] = useState<AdminUserView>("list");
  const [settingsSection, setSettingsSection] = useState<"view" | "account">("view");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [adminMenuOpen, setAdminMenuOpen] = useState(false);
  const [settingsMenuOpen, setSettingsMenuOpen] = useState(false);
  const tabs = useMemo(() => {
    const baseTabs = [
      { id: "chat" as const, label: t.tabs.chat, icon: Bot },
      { id: "search" as const, label: t.tabs.search, icon: FileSearch },
      { id: "petition" as const, label: t.tabs.petition, icon: FileText },
      { id: "training" as const, label: t.tabs.training, icon: GraduationCap },
      { id: "cases" as const, label: t.tabs.cases, icon: FolderOpen },
      { id: "document" as const, label: t.tabs.document, icon: Upload },
      { id: "feedback" as const, label: t.tabs.feedback, icon: MessageSquareMore },
      { id: "profile" as const, label: t.tabs.profile, icon: UserRound },
      { id: "settings" as const, label: t.tabs.settings, icon: Settings }
    ];
    return authUser?.role === "ADMIN"
      ? [...baseTabs, { id: "admin" as const, label: t.tabs.admin, icon: ShieldAlert }]
      : baseTabs;
  }, [authUser?.role, t]);

  useEffect(() => {
    const storedLocale = window.localStorage.getItem("lawai-locale");
    if (isLocale(storedLocale)) {
      setLocale(storedLocale);
    }
  }, []);

  useEffect(() => {
    document.documentElement.lang = locale;
    window.localStorage.setItem("lawai-locale", locale);
  }, [locale]);

  useEffect(() => {
    const storedNotes = window.localStorage.getItem("lawai-smart-notes");
    if (!storedNotes) return;
    try {
      const parsed = JSON.parse(storedNotes) as SmartNote[];
      if (Array.isArray(parsed)) {
        setSmartNotes(parsed.filter((item) => typeof item.id === "string" && typeof item.text === "string"));
      }
    } catch {
      setSmartNotes([]);
    }
  }, []);

  useEffect(() => {
    window.localStorage.setItem("lawai-smart-notes", JSON.stringify(smartNotes));
  }, [smartNotes]);

  useEffect(() => {
    checkHealth().then(setBackendOnline);
  }, []);

  useEffect(() => {
    const storedTheme = window.localStorage.getItem("lawai-theme");
    const initialTheme = storedTheme === "dark" || storedTheme === "light" || storedTheme === "original"
      ? storedTheme
      : "original";
    setThemeMode(initialTheme);
  }, []);

  useEffect(() => {
    if (themeMode === "original") {
      delete document.body.dataset.theme;
    } else {
      document.body.dataset.theme = themeMode;
    }
    window.localStorage.setItem("lawai-theme", themeMode);
  }, [themeMode]);

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
    if (!authUser) {
      setProfileForm({ name: "", email: "" });
      setProfileError("");
      setProfileSuccess("");
      return;
    }
    setProfileForm({
      name: authUser.name,
      email: authUser.email
    });
  }, [authUser]);

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
    if (!authUser || (activeTab !== "feedback" && activeTab !== "admin") || feedbackLoaded || feedbackLoading) {
      return;
    }
    void loadFeedbackHistory();
  }, [activeTab, authUser, feedbackLoaded, feedbackLoading]);

  useEffect(() => {
    if (!authUser || authUser.role !== "ADMIN" || activeTab !== "admin" || adminSection !== "users" || adminUsers.length || adminUsersLoading) {
      return;
    }
    void loadAdminUsers();
  }, [activeTab, adminSection, adminUsers.length, adminUsersLoading, authUser]);

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
      typeLabel: isFeedbackType(item.type) ? t.feedback.types[item.type] : item.type,
      statusLabel: isFeedbackStatus(item.status) ? t.feedback.statuses[item.status] : item.status,
      createdAtLabel: new Date(item.createdAt).toLocaleString(locale === "en" ? "en-US" : "tr-TR"),
      messagePreview: item.message.length > 120 ? `${item.message.slice(0, 120).trim()}...` : item.message
    }));
  }, [filteredFeedbackItems, locale, t]);

  const selectedFeedback = useMemo(() => {
    if (!selectedFeedbackId || !filteredFeedbackItems.length) {
      return null;
    }
    return filteredFeedbackItems.find((item) => item.id === selectedFeedbackId) ?? null;
  }, [filteredFeedbackItems, selectedFeedbackId]);

  const adminFeedbackMetrics = useMemo(() => {
    return {
      total: feedbackItems.length,
      open: feedbackItems.filter((item) => item.status !== "resolved").length,
      resolved: feedbackItems.filter((item) => item.status === "resolved").length
    };
  }, [feedbackItems]);

  const adminUserColumns = useMemo<GridColDef<AuthUser>[]>(() => [
    {
      field: "name",
      headerName: locale === "en" ? "Name" : "Ad soyad",
      flex: 1,
      minWidth: 200,
      renderCell: (params) => (
        <div className="feedback-grid-cell">
          <strong>{String(params.value ?? "-")}</strong>
          <span>{params.row.email}</span>
        </div>
      )
    },
    {
      field: "role",
      headerName: locale === "en" ? "Role" : "Rol",
      width: 140,
      renderCell: (params) => <span className="feedback-pill feedback-pill-status">{String(params.value ?? "USER")}</span>
    },
    {
      field: "createdAt",
      headerName: locale === "en" ? "Created" : "Olusturma",
      width: 180,
      valueGetter: (_, row) => formatDateTime(row.createdAt, locale, "-")
    },
    {
      field: "lastLoginAt",
      headerName: locale === "en" ? "Last login" : "Son giris",
      width: 180,
      valueGetter: (_, row) => formatDateTime(row.lastLoginAt, locale, "-")
    },
    {
      field: "actions",
      headerName: locale === "en" ? "Action" : "Islem",
      width: 130,
      sortable: false,
      filterable: false,
      renderCell: (params) => (
        <button className="secondary-button" onClick={(event) => {
          event.stopPropagation();
          void openAdminUser(params.row.id);
        }} type="button">
          {locale === "en" ? "Open" : "Ac"}
        </button>
      )
    }
  ], [locale]);

  const selectedPrecedent = useMemo(() => {
    if (!precedents.length) return null;
    const index = selectedPrecedentIndex ?? 0;
    return precedents[index] ?? precedents[0];
  }, [precedents, selectedPrecedentIndex]);

  const precedentSources = useMemo(() => [
    { key: "all" as const, label: locale === "en" ? "All Decisions" : "Tum Kararlar", court: "" },
    { key: "yargitay" as const, label: "Yargitay", court: "Yargitay" },
    { key: "danistay" as const, label: "Danistay", court: "Danistay" },
    { key: "aym" as const, label: locale === "en" ? "Constitutional Court" : "Anayasa Mahkemesi", court: "AYM" },
    { key: "rekabet" as const, label: locale === "en" ? "Competition Board" : "Rekabet Kurulu", court: "Rekabet Kurulu" },
    { key: "literature" as const, label: locale === "en" ? "Literature (Law Articles)" : "Literatur (Hukuk Makaleleri)", court: "" },
    { key: "thesis" as const, label: locale === "en" ? "Master/Doctoral Theses" : "Yuksek Lisans/Doktora Tezleri", court: "" }
  ], [locale]);

  const precedentExampleQueries = useMemo(() => locale === "en" ? [
    "Determining child living standard and parent income in child support amount",
    "Eviction in indefinite-term lease due to lessor's personal need",
    "Event characteristics and party fault rates in moral compensation amount",
    "Proof criteria for collusion claims in title cancellation and registration cases",
    "Limitation period start for severance and notice pay based on termination date",
    "Net income calculation and discount rates in loss of support compensation"
  ] : [
    "Istirak nafakasinin miktarinin saptanmasinda cocugun yasam standardi ve ebeveyn gelirleri",
    "Belirsiz sureli kira sozlesmesinde tahliye - kiraya verenin ihtiyac nedeniyle actigi davalar",
    "Manevi tazminat miktarinin belirlenmesinde olayin niteligi ve taraf kusur oranlari",
    "Tapu iptali ve tescil davalarinda muris muvazaasi iddiasinin ispat kriterleri",
    "Kidem ve ihbar tazminatlarinda zamanasimi baslangici - fesih tarihine gore uygulamalar",
    "Destekten yoksun kalma tazminatinda net gelir hesabi ve iskonto oranlarinin etkisi"
  ], [locale]);

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

  async function loadFeedbackHistory() {
    if (!authUser || feedbackLoading) {
      return;
    }

    setFeedbackLoading(true);
    setFeedbackError("");
    try {
      const items = await listFeedback();
      setFeedbackItems(items);
      setFeedbackLoaded(true);
    } catch (err) {
      setFeedbackError(err instanceof Error ? err.message : "Geri bildirimler yuklenemedi.");
    } finally {
      setFeedbackLoading(false);
    }
  }

  async function loadAdminUsers() {
    setAdminUsersLoading(true);
    setAdminUsersError("");
    try {
      setAdminUsers(await listUsers());
    } catch (err) {
      setAdminUsersError(err instanceof Error ? err.message : "Kullanicilar yuklenemedi.");
    } finally {
      setAdminUsersLoading(false);
    }
  }

  async function openAdminUser(id: string) {
    setAdminUsersError("");
    try {
      const user = await getUser(id);
      setSelectedAdminUserId(user.id);
      setSelectedAdminUser(user);
      setAdminUserView("detail");
    } catch (err) {
      setAdminUsersError(err instanceof Error ? err.message : "Kullanici acilamadi.");
    }
  }

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

  function addSmartNote() {
    const text = smartNoteDraft.trim();
    if (!text) return;
    setSmartNotes((current) => [
      {
        id: crypto.randomUUID(),
        text,
        createdAt: new Date().toISOString()
      },
      ...current
    ]);
    setSmartNoteDraft("");
  }

  function useSampleSmartNote() {
    setSmartNoteDraft("Taniklardan Ahmet Ornek dinlenecek. Dosyadaki tum belgeleri oku, analiz et ve bu taniga sorulabilecek soru onerileri hazirla.");
  }

  function buildSmartNotesPrompt(notes: SmartNote[] = smartNotes, attachment: ChatAttachment | null = chatAttachment) {
    const notesText = notes
      .map((note, index) => `${index + 1}. ${note.text}`)
      .join("\n");
    const parts = [
      "Kullanıcının aşağıdaki talebini hukuk asistanı gibi yanıtla.",
      "Ekli belge varsa önce belge içeriğini dikkate al; kullanıcının istediği konuya göre belgeyi araştır, incele ve analiz et.",
      "Yanıtı pratik, gerekçeli ve mümkünse başlıklar halinde ver. Belgeden emin olmadığın noktaları açıkça belirt.",
      "",
      "Kullanıcı talebi:",
      notesText || "Ekli belgeyi incele ve hukuki olarak değerlendir."
    ];
    if (attachment) {
      parts.push("", "Ek belge:", `Ad: ${attachment.filename}`, `Boyut: ${formatBytes(attachment.size)}`, "", attachment.content);
    }
    return parts.join("\n");
  }

  function submitChat(event: FormEvent) {
    event.preventDefault();
    const draft = smartNoteDraft.trim();
    const notesForPrompt = draft
      ? [{ id: "draft", text: draft, createdAt: new Date().toISOString() }]
      : [];
    if (!notesForPrompt.length && !chatAttachment) {
      setError(t.tools.smartNotesNoNotes);
      return;
    }
    const attachmentLine = chatAttachment ? `\n\n[Belge: ${chatAttachment.filename}]` : "";
    const userMessage = `${draft || "Ekli belgeyi incele ve hukuki olarak değerlendir."}${attachmentLine}`;
    const attachmentForPrompt = chatAttachment;
    setChatMessages((current) => [
      ...current,
      { id: crypto.randomUUID(), role: "user", text: userMessage }
    ]);
    setSmartNoteDraft("");
    setChatAttachment(null);
    run("chat", async () => {
      const response = await postJson<ChatResponse>("/chat", { question: buildSmartNotesPrompt(notesForPrompt, attachmentForPrompt), mode: "smart-notes", privateMode });
      setChatResponse(response);
      setChatMessages((current) => [
        ...current,
        {
          id: crypto.randomUUID(),
          role: "assistant",
          text: response.answer,
          citations: response.citations,
          disclaimer: response.disclaimer
        }
      ]);
    });
  }

  async function attachChatDocument(file: File | null) {
    const validationError = validateFile(file, locale);
    if (validationError || !file) {
      setError(validationError ?? t.document.errors.noFile);
      return;
    }
    setChatAttachmentLoading(true);
    setError("");
    try {
      const form = new FormData();
      form.append("file", file, file.name);
      const result = await uploadMultipart<UploadResponse>("/documents/analyze", form);
      const content = [
        result.summary ? `Özet:\n${result.summary}` : "",
        result.textPreview ? `Metin önizleme:\n${result.textPreview}` : "",
        result.detectedIssues?.length ? `Tespit edilen hususlar:\n${result.detectedIssues.join("\n")}` : "",
        result.warnings?.length ? `Uyarılar:\n${result.warnings.join("\n")}` : ""
      ].filter(Boolean).join("\n\n");
      setChatAttachment({
        filename: result.filename || file.name,
        size: result.size || file.size,
        content: content || "Belge analiz edildi ancak metin önizlemesi alınamadı. Kullanıcı sorusunu belge adı ve bağlamı ile birlikte değerlendir."
      });
    } catch (error) {
      if (file.type.startsWith("text/") || file.name.toLowerCase().endsWith(".txt")) {
        const text = await file.text();
        setChatAttachment({
          filename: file.name,
          size: file.size,
          content: text.slice(0, 12000)
        });
      } else {
        setError(error instanceof Error ? error.message : t.document.errors.unsupported);
      }
    } finally {
      setChatAttachmentLoading(false);
      if (chatFileInputRef.current) chatFileInputRef.current.value = "";
    }
  }

  async function startVoiceInput() {
    if (typeof window === "undefined") return;
    setVoiceNotice("");
    const isSecureContext = window.isSecureContext || window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";
    if (!isSecureContext) {
      const message = "Sesli arama icin uygulamayi HTTPS veya localhost uzerinden acin.";
      setError(message);
      setVoiceNotice(message);
      return;
    }
    const speechWindow = window as unknown as {
      SpeechRecognition?: SpeechRecognitionConstructor;
      webkitSpeechRecognition?: SpeechRecognitionConstructor;
    };
    const Recognition = speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition;
    if (!Recognition) {
      const message = "Tarayiciniz sesli aramayi desteklemiyor. Chrome veya Edge ile localhost/HTTPS uzerinden deneyin.";
      setError(message);
      setVoiceNotice(message);
      return;
    }
    if (voiceListening) {
      speechRecognitionRef.current?.stop();
      setVoiceListening(false);
      setVoiceNotice("Dinleme durduruldu.");
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      const message = "Tarayici mikrofon izni alamiyor. Chrome veya Edge ile deneyin.";
      setError(message);
      setVoiceNotice(message);
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.getTracks().forEach((track) => track.stop());
    } catch {
      const message = "Mikrofon izni verilmedi veya mikrofon kullanilamiyor.";
      setError(message);
      setVoiceNotice(message);
      return;
    }
    speechRecognitionRef.current?.abort();
    const recognition = new Recognition();
    recognition.lang = locale === "en" ? "en-US" : "tr-TR";
    recognition.interimResults = true;
    recognition.continuous = false;
    recognition.maxAlternatives = 1;
    voiceBaseDraftRef.current = smartNoteDraft.trim();
    setError("");
    setVoiceNotice("Mikrofon baslatiliyor...");
    recognition.onstart = () => {
      setVoiceListening(true);
      setVoiceNotice("Dinleniyor, konusabilirsiniz.");
    };
    recognition.onaudiostart = () => setVoiceNotice("Mikrofon dinleniyor.");
    recognition.onspeechstart = () => setVoiceNotice("Ses algilandi, metne cevriliyor.");
    recognition.onresult = (event) => {
      let finalTranscript = "";
      let interimTranscript = "";
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const result = event.results[index];
        const transcript = result[0]?.transcript ?? "";
        if (result.isFinal) {
          finalTranscript += transcript;
        } else {
          interimTranscript += transcript;
        }
      }
      const transcript = `${finalTranscript} ${interimTranscript}`.trim();
      if (transcript) {
        setSmartNoteDraft([voiceBaseDraftRef.current, transcript].filter(Boolean).join(" ").trim());
        setVoiceNotice("Konusma metne aktariliyor.");
      }
    };
    recognition.onerror = (event) => {
      setVoiceListening(false);
      const reason = event.error === "not-allowed"
        ? "Mikrofon izni verilmedi."
        : event.error === "no-speech"
          ? "Ses algilanamadi. Tekrar deneyin."
          : event.error === "network"
            ? "Sesli arama icin tarayici ag servisine ulasilamadi."
            : "Sesli arama tamamlanamadi.";
      setError(reason);
      setVoiceNotice(reason);
    };
    recognition.onend = () => {
      setVoiceListening(false);
      setVoiceNotice((current) => current || "Dinleme tamamlandi.");
    };
    speechRecognitionRef.current = recognition;
    try {
      setVoiceListening(true);
      recognition.start();
    } catch {
      setVoiceListening(false);
      const message = "Sesli arama baslatilamadi. Lutfen tekrar deneyin.";
      setError(message);
      setVoiceNotice(message);
    }
  }

  function downloadSmartNotesPdf() {
    const analysis = chatResponse?.answer ?? t.tools.chatEmpty;
    const notesHtml = smartNotes
      .map((note, index) => `<li><strong>${index + 1}.</strong> ${escapeHtml(note.text)}<br><small>${new Date(note.createdAt).toLocaleString(locale === "en" ? "en-US" : "tr-TR")}</small></li>`)
      .join("");
    const printWindow = window.open("", "_blank", "width=900,height=700");
    if (!printWindow) return;
    printWindow.document.write(`
      <!doctype html>
      <html lang="${locale}">
        <head>
          <meta charset="utf-8" />
          <title>${escapeHtml(t.tools.chatTitle)}</title>
          <style>
            body { color: #1f2a37; font-family: Georgia, serif; line-height: 1.55; margin: 36px; }
            h1, h2 { margin: 0 0 12px; }
            .meta { color: #5c6673; margin-bottom: 24px; }
            li { margin-bottom: 12px; }
            pre { white-space: pre-wrap; font-family: inherit; border-top: 1px solid #d8dee6; padding-top: 18px; }
          </style>
        </head>
        <body>
          <h1>${escapeHtml(t.tools.chatTitle)}</h1>
          <p class="meta">${escapeHtml(smartNoteCaseTitle || "-")}</p>
          <h2>${escapeHtml(t.tools.smartNotesSaved)}</h2>
          <ol>${notesHtml || `<li>${escapeHtml(t.tools.smartNotesEmpty)}</li>`}</ol>
          <h2>${escapeHtml(t.tools.chatAnswer)}</h2>
          <pre>${escapeHtml(analysis)}</pre>
          <script>window.onload = () => window.print();</script>
        </body>
      </html>
    `);
    printWindow.document.close();
  }

  function buildPrecedentQuery(query: string, sourceKey = precedentSource) {
    const sourceHint = precedentSources.find((item) => item.key === sourceKey);
    const manualParts = [
      query.trim(),
      searchDocket.trim() ? `${t.tools.researchDocket}: ${searchDocket.trim()}` : "",
      searchDateFrom ? `${t.tools.researchDateFrom}: ${searchDateFrom}` : "",
      searchDateTo ? `${t.tools.researchDateTo}: ${searchDateTo}` : ""
    ].filter(Boolean);
    const baseQuery = researchMode === "manual" ? manualParts.join(" ") : query;
    if (!sourceHint || sourceHint.key === "all" || sourceHint.court) {
      return baseQuery;
    }
    return `${sourceHint.label}: ${baseQuery}`;
  }

  function executePrecedentSearch(query = searchQuery, court = searchCourt, sourceKey = precedentSource) {
    run("search", async () => {
      const data = await postJson<{ results: Precedent[] }>("/precedents/search", {
        query: buildPrecedentQuery(query, sourceKey),
        court: court || undefined,
        chamber: searchChamber || undefined,
        limit: 50
      });
      setPrecedents(data.results);
      setSelectedPrecedentIndex(data.results.length ? 0 : null);
      setPrecedentSummary("");
    });
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    executePrecedentSearch();
  }

  function selectPrecedentSource(source: (typeof precedentSources)[number]) {
    setPrecedentSource(source.key);
    setSearchCourt(source.court);
  }

  function runExampleSearch(example: string) {
    setSearchQuery(example);
    executePrecedentSearch(example);
  }

  function summarizePrecedents() {
    if (!precedents.length) {
      setError(t.tools.researchSummaryEmpty);
      return;
    }
    const decisionList = precedents.slice(0, 10).map((item, index) => [
      `${index + 1}. ${item.court}${item.chamber ? ` - ${item.chamber}` : ""}`,
      `${t.tools.researchDocket}: ${[item.docketNo, item.decisionNo].filter(Boolean).join(" / ") || "-"}`,
      `${t.feedback.date}: ${item.date ?? "-"}`,
      `${t.feedback.subject}: ${item.topic}`,
      item.content || item.summary
    ].join("\n")).join("\n\n");
    run("precedent-summary", async () => {
      const response = await postJson<ChatResponse>("/chat", {
        question: [
          t.tools.researchAiSummary,
          "",
          `Arama: ${searchQuery}`,
          "",
          "Bulunan kararlar:",
          decisionList
        ].join("\n"),
        mode: "precedent-summary",
        privateMode
      });
      setPrecedentSummary(response.answer);
    });
  }

  function addSelectedPrecedentToPetition() {
    if (!selectedPrecedent) return;
    const reference = [
      `${selectedPrecedent.court}${selectedPrecedent.chamber ? ` - ${selectedPrecedent.chamber}` : ""}`,
      `${t.tools.researchDocket}: ${[selectedPrecedent.docketNo, selectedPrecedent.decisionNo].filter(Boolean).join(" / ") || "-"}`,
      `${t.feedback.date}: ${selectedPrecedent.date ?? "-"}`,
      `${t.feedback.subject}: ${selectedPrecedent.topic}`,
      selectedPrecedent.content || selectedPrecedent.summary
    ].join("\n");
    setPetitionContext((current) => [current.trim(), `${t.tools.researchUse}:\n${reference}`].filter(Boolean).join("\n\n"));
    setActiveTab("petition");
  }

  function submitPetition(event: FormEvent) {
    event.preventDefault();
    const selectedSources = [
      petitionContextSources.upload ? t.tools.petitionUploadContext : null,
      petitionContextSources.existing ? t.tools.petitionExistingDocs : null,
      petitionContextSources.training ? t.tools.petitionTrainingData : null
    ].filter(Boolean).join(", ");
    const modelInstruction = petitionModel === "premium"
      ? t.tools.petitionPremiumDesc
      : t.tools.petitionStandardDesc;
    const methodInstruction = {
      case: t.tools.petitionFromCase,
      quick: t.tools.petitionQuick,
      detailed: t.tools.petitionDetailed
    }[petitionMethod];
    const enrichedPetition = {
      ...petition,
      facts: [
        petition.facts,
        petitionContext.trim() ? `\n\n${t.tools.petitionCaseContext}:\n${petitionContext.trim()}` : "",
        selectedSources ? `\n\n${t.tools.petitionContext}: ${selectedSources}` : "",
        `\n\n${t.tools.petitionMethod}: ${methodInstruction}`,
        `${t.tools.petitionModel}: ${petitionModel === "premium" ? t.tools.petitionPremiumModel : t.tools.petitionStandardModel} - ${modelInstruction}`
      ].join("").trim(),
      demands: petitionModel === "premium"
        ? `${petition.demands}\n\n${t.tools.petitionQualityHint}`
        : petition.demands
    };
    run("petition", async () => {
      setPetitionEditPreview(null);
      setPetitionResult(await postJson<PetitionResponse>("/petitions", enrichedPetition));
    });
  }

  function selectedTextFromEvent(event: SyntheticEvent<HTMLTextAreaElement>) {
    const target = event.currentTarget;
    setSelectedPetitionText(target.value.slice(target.selectionStart, target.selectionEnd));
  }

  function runPetitionEdit(scope: "all" | "selection") {
    if (!petitionResult || !petitionEditInstruction.trim()) return;
    const before = scope === "selection" && selectedPetitionText.trim()
      ? selectedPetitionText
      : petitionResult.body;
    const prompt = [
      t.tools.petitionSmartEdit,
      t.tools.petitionEditInstruction + ": " + petitionEditInstruction.trim(),
      "",
      "Metin:",
      before
    ].join("\n");
    run("petition-edit", async () => {
      const response = await postJson<ChatResponse>("/chat", { question: prompt, mode: "petition-edit", privateMode });
      setPetitionEditPreview({ before, after: response.answer });
    });
  }

  function acceptPetitionEdit() {
    if (!petitionResult || !petitionEditPreview) return;
    const nextBody = petitionResult.body.includes(petitionEditPreview.before)
      ? petitionResult.body.replace(petitionEditPreview.before, petitionEditPreview.after)
      : petitionEditPreview.after;
    setPetitionResult({ ...petitionResult, body: nextBody });
    setPetitionEditPreview(null);
    setSelectedPetitionText("");
  }

  function exportPetition(format: "docx" | "udf") {
    if (!petitionResult) return;
    const extension = format === "docx" ? "docx" : "udf";
    const type = format === "docx"
      ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      : "text/plain;charset=utf-8";
    const blob = new Blob([petitionResult.body], { type });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${petitionResult.title || "dilekce"}.${extension}`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  function printPetitionPdf() {
    if (!petitionResult) return;
    const printWindow = window.open("", "_blank", "width=900,height=700");
    if (!printWindow) return;
    printWindow.document.write(`
      <!doctype html>
      <html lang="${locale}">
        <head><meta charset="utf-8" /><title>${escapeHtml(petitionResult.title)}</title></head>
        <body style="font-family: Georgia, serif; line-height: 1.6; margin: 40px;">
          <h1>${escapeHtml(petitionResult.title)}</h1>
          <pre style="white-space: pre-wrap; font-family: inherit;">${escapeHtml(petitionResult.body)}</pre>
          <script>window.onload = () => window.print();</script>
        </body>
      </html>
    `);
    printWindow.document.close();
  }

  function submitKnowledge(event: FormEvent) {
    event.preventDefault();
    indexKnowledge();
  }

  function indexKnowledge() {
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
      setFeedbackForm({ type: "genel", subject: "", message: "" });
    });
  }

  function seedKnowledge() {
    run("knowledge", async () => {
      setKnowledgeResult(await postJson<KnowledgeResponse>("/knowledge/seed-precedents", {}));
    });
  }

  function seedAdminCases() {
    run("admin-cases", async () => {
      await seedSamples<CaseRecord[]>("/cases/seed-samples");
    });
  }

  function refreshAdminFeedback() {
    setFeedbackLoaded(false);
    void loadFeedbackHistory();
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
        setActiveTab("chat");
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
        setActiveTab("chat");
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
      setActiveTab("chat");
      setAuthPreview(null);
      setAuthMode("login");
      setChatResponse(null);
      setChatMessages([]);
      setPrecedents([]);
      setPetitionResult(null);
      setKnowledgeResult(null);
        setFeedbackItems([]);
        setFeedbackError("");
        setFeedbackSubmitted(null);
        setFeedbackLoaded(false);
        setSelectedFeedbackId(null);
        setFeedbackSearch("");
        setFeedbackTypeFilter("all");
        setFeedbackStatusFilter("all");
        setAdminSection("feedback");
        setAdminUsers([]);
        setAdminUsersError("");
        setSelectedAdminUserId(null);
        setSelectedAdminUser(null);
        setAdminUserView("list");
        setTraining(null);
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
      setProfileForm({
        name: "",
        email: ""
      });
      setProfileError("");
      setProfileSuccess("");
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

  async function submitProfile(event: FormEvent) {
    event.preventDefault();
    setProfileError("");
    setProfileSuccess("");

    if (!profileForm.name.trim()) {
      setProfileError(t.profile.errors.nameRequired);
      return;
    }
    if (!profileForm.email.trim()) {
      setProfileError(t.profile.errors.emailRequired);
      return;
    }

    setProfileLoading(true);
    try {
      const updatedUser = await authUpdateProfile({
        name: profileForm.name,
        email: profileForm.email
      });
      setAuthUser(updatedUser);
      setProfileSuccess(t.profile.success);
    } catch (err) {
      setProfileError(err instanceof Error ? err.message : t.profile.errors.updateFailed);
    } finally {
      setProfileLoading(false);
    }
  }

  if (!authReady) {
    return (
      <main className="auth-shell">
        <section className="auth-card panel">
          <LoaderCircle className="spin" size={32} />
          <h1>LawAI Studio</h1>
          <p>{t.auth.loading}</p>
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
    <main className={`app-shell ${sidebarCollapsed ? "sidebar-collapsed" : ""}`}>
      <aside className="sidebar">
        <div className="brand">
          <Scale size={28} />
          <div>
            <strong>LawAI Studio</strong>
            <span>{t.dashboard.eyebrow}</span>
          </div>
        </div>
        <button
          aria-label={sidebarCollapsed ? "Yan menuyu ac" : "Yan menuyu kapat"}
          className="sidebar-toggle"
          onClick={() => setSidebarCollapsed((current) => !current)}
          title={sidebarCollapsed ? "Yan menuyu ac" : "Yan menuyu kapat"}
          type="button"
        >
          <ChevronRight size={18} />
        </button>
        <div className="nav-label">{t.common.apps}</div>
        <nav className="tabs">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            const isAdminTab = tab.id === "admin";
            const isSettingsTab = tab.id === "settings";
            const isSubmenuTab = isAdminTab || isSettingsTab;
            return (
              <div className={isSubmenuTab ? "sidebar-menu-group" : ""} key={tab.id}>
                <button
                  aria-expanded={isAdminTab ? adminMenuOpen : isSettingsTab ? settingsMenuOpen : undefined}
                  className={activeTab === tab.id ? "active" : ""}
                  onClick={() => {
                    if (isAdminTab) {
                      setAdminMenuOpen((current) => !current);
                      return;
                    }
                    if (isSettingsTab) {
                      setSettingsMenuOpen((current) => !current);
                      return;
                    }
                    setActiveTab(tab.id);
                  }}
                  type="button"
                  title={tab.label}
                >
                  <Icon size={18} />
                  <span>{tab.label}</span>
                  {isSubmenuTab ? <ChevronRight className="sidebar-submenu-chevron" size={15} /> : null}
                </button>
                {isSettingsTab && settingsMenuOpen && (
                  <div className="sidebar-submenu">
                    <button
                      className={activeTab === "settings" && settingsSection === "view" ? "active" : ""}
                      onClick={() => {
                        setSettingsMenuOpen(true);
                        setActiveTab("settings");
                        setSettingsSection("view");
                      }}
                      type="button"
                      title={t.settings.sections.view}
                    >
                      <Scale size={15} />
                      <span>{t.settings.sections.view}</span>
                    </button>
                    <button
                      className={activeTab === "settings" && settingsSection === "account" ? "active" : ""}
                      onClick={() => {
                        setSettingsMenuOpen(true);
                        setActiveTab("settings");
                        setSettingsSection("account");
                      }}
                      type="button"
                      title={t.settings.sections.account}
                    >
                      <BarChart3 size={15} />
                      <span>{t.settings.sections.account}</span>
                    </button>
                  </div>
                )}
                {isAdminTab && adminMenuOpen && (
                  <div className="sidebar-submenu">
                    <Link
                      href="/feedback-management"
                      title={t.adminFeedback.title}
                    >
                      <MessageSquareMore size={15} />
                      <span>{t.adminFeedback.title}</span>
                    </Link>
                    <button
                      className={activeTab === "admin" && adminSection === "users" ? "active" : ""}
                      onClick={() => {
                        setAdminMenuOpen(true);
                        setActiveTab("admin");
                        setAdminSection("users");
                        setAdminUserView("list");
                      }}
                      type="button"
                      title={locale === "en" ? "User Management" : "Kullanici Yonetimi"}
                    >
                      <UserRound size={15} />
                      <span>{locale === "en" ? "User Management" : "Kullanici Yonetimi"}</span>
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </nav>
        <div className="sidebar-user">
          <div className="sidebar-user-avatar" aria-hidden="true">{authUser.name.slice(0, 1).toUpperCase()}</div>
          <div>
            <strong>{authUser.name}</strong>
            <span>{authUser.email}</span>
            <span className="sidebar-user-role">{authUser.role === "ADMIN" ? t.common.admin : t.common.user}</span>
          </div>
          <div className="sidebar-user-actions">
            <button className="secondary-button" type="button" onClick={() => void handleLogout()}>
              {t.common.logout}
            </button>
          </div>
        </div>
      </aside>

      <section className="workspace">
        {error && <div className="error">{error}</div>}

        {activeTab === "chat" && (
          <section className="smart-notes-workspace law-chat-home">
            <header className="law-chat-hero">
              <div className="law-chat-mark">
                <Scale size={70} strokeWidth={1.45} />
                <strong>LawAI</strong>
              </div>
              <h1>Hoş geldiniz, ne yapmak istersiniz?</h1>
            </header>

            {chatMessages.length ? (
              <section className="law-chat-thread" aria-live="polite">
                {chatMessages.map((message) => (
                  <article key={message.id} className={`law-chat-message ${message.role}`}>
                    <span>{message.role === "user" ? "Siz" : "LawAI"}</span>
                    <pre>{message.text}</pre>
                    {message.citations ? <CitationList citations={message.citations} /> : null}
                    {message.disclaimer ? <small>{message.disclaimer}</small> : null}
                  </article>
                ))}
                {loading === "chat" ? (
                  <article className="law-chat-message assistant pending">
                    <span>LawAI</span>
                    <p><LoaderCircle className="spin" size={18} /> Cevap hazirlaniyor...</p>
                  </article>
                ) : null}
                {chatResponse ? (
                  <button className="law-chat-download" type="button" onClick={downloadSmartNotesPdf}>
                    <FileText size={17} />
                    {t.tools.smartNotesDownload}
                  </button>
                ) : null}
              </section>
            ) : null}

            <form className="law-chat-composer" onSubmit={submitChat}>
              <input
                ref={chatFileInputRef}
                accept={acceptedExtensions.join(",")}
                className="law-chat-file-input"
                onChange={(event) => void attachChatDocument(event.target.files?.[0] ?? null)}
                type="file"
              />
              <textarea
                value={smartNoteDraft}
                onChange={(event) => setSmartNoteDraft(event.target.value)}
                placeholder="Bana bir soru sor..."
                rows={4}
              />
              {chatAttachment ? (
                <div className="law-chat-attachment">
                  <FileText size={17} />
                  <span>{chatAttachment.filename}</span>
                  <small>{formatBytes(chatAttachment.size)}</small>
                  <button type="button" onClick={() => setChatAttachment(null)} aria-label="Belgeyi kaldir">
                    <X size={15} />
                  </button>
                </div>
              ) : null}
              <div className="law-chat-composer-tools">
                <div>
                  <button className="law-chat-tool" type="button" onClick={() => chatFileInputRef.current?.click()} disabled={chatAttachmentLoading} title="Belge ekle">
                    {chatAttachmentLoading ? <LoaderCircle className="spin" size={18} /> : <Upload size={18} />}
                    <span>Belge ekle</span>
                  </button>
                  <button className={`law-chat-tool ${voiceListening ? "active" : ""}`} type="button" onClick={() => void startVoiceInput()} title="Sesli ara">
                    <Mic size={18} />
                    <span>{voiceListening ? "Dinleniyor" : "Sesli ara"}</span>
                  </button>
                </div>
                <button className="law-chat-send" disabled={loading === "chat" || chatAttachmentLoading || (!smartNoteDraft.trim() && !chatAttachment)} type="submit">
                  {loading === "chat" ? <LoaderCircle className="spin" size={22} /> : <Send size={22} />}
                </button>
              </div>
              {voiceNotice ? <p className="law-chat-voice-notice">{voiceNotice}</p> : null}
            </form>
          </section>
        )}

        {activeTab === "search" && (
          <section className="precedent-research-workspace">
            <header className="panel precedent-research-header">
              <div>
                <span className="eyebrow">{t.tabs.search}</span>
                <h1>{t.tools.searchTitle}</h1>
              </div>
              <div className="smart-notes-summary">
                <div>
                  <span>{t.tools.searchResults}</span>
                  <strong>{precedents.length} {t.tools.records}</strong>
                </div>
                <div>
                  <span>{t.tools.researchAiSummary}</span>
                  <strong>{precedentSummary ? t.dashboard.statusReady : "-"}</strong>
                </div>
              </div>
            </header>

            <section className="precedent-research-layout">
              <form className="panel precedent-search-panel" onSubmit={submitSearch}>
                <PanelTitle icon={<Search size={20} />} title={t.tools.researchMethods} />
                <div className="precedent-segmented">
                  <button className={researchMode === "ai" ? "active" : ""} type="button" onClick={() => setResearchMode("ai")}>
                    <Bot size={16} />
                    {t.tools.researchAiSearch}
                  </button>
                  <button className={researchMode === "manual" ? "active" : ""} type="button" onClick={() => setResearchMode("manual")}>
                    <FileSearch size={16} />
                    {t.tools.researchManualSearch}
                  </button>
                </div>

                <label className="field-label">
                  {researchMode === "ai" ? t.tools.lawyerQuestion : t.tools.researchKeywords}
                  <textarea rows={researchMode === "ai" ? 6 : 4} value={searchQuery} onChange={(event) => setSearchQuery(event.target.value)} placeholder={t.tools.searchPlaceholder} />
                </label>

                {researchMode === "ai" ? (
                  <details className="compact-details">
                    <summary>{t.tools.showExamples}</summary>
                    <section className="precedent-examples">
                      {[t.tools.researchExampleJewelry, t.tools.researchExampleMunicipality, t.tools.researchExampleDocket].map((example) => (
                        <button key={example} className="secondary-button" type="button" onClick={() => setSearchQuery(example)}>
                          {example}
                        </button>
                      ))}
                    </section>
                  </details>
                ) : null}

                <div className="precedent-manual-grid">
                  <label className="field-label">
                    {t.tools.researchCourt}
                    <select value={searchCourt} onChange={(event) => setSearchCourt(event.target.value)}>
                      <option value="">{t.tools.researchCourtAll}</option>
                      <option value="Yargitay">Yargitay</option>
                      <option value="Danistay">Danistay</option>
                      <option value="AYM">AYM</option>
                    </select>
                  </label>
                  <label className="field-label">
                    {t.tools.researchChamber}
                    <input value={searchChamber} onChange={(event) => setSearchChamber(event.target.value)} placeholder="2. Hukuk Dairesi" />
                  </label>
                </div>

                {researchMode === "manual" ? (
                  <div className="precedent-manual-grid">
                    <label className="field-label">
                      {t.tools.researchDocket}
                      <input value={searchDocket} onChange={(event) => setSearchDocket(event.target.value)} placeholder="2025/2402" />
                    </label>
                    <label className="field-label">
                      {t.tools.researchDateFrom}
                      <input type="date" value={searchDateFrom} onChange={(event) => setSearchDateFrom(event.target.value)} />
                    </label>
                    <label className="field-label">
                      {t.tools.researchDateTo}
                      <input type="date" value={searchDateTo} onChange={(event) => setSearchDateTo(event.target.value)} />
                    </label>
                  </div>
                ) : null}

                <details className="compact-details">
                  <summary>{t.tools.technicalInfo}</summary>
                  <section className="precedent-notice">
                    <strong>{t.tools.researchTechnicalNotice}</strong>
                    <p>{t.tools.researchTechnicalNoticeText}</p>
                  </section>
                </details>

                <button disabled={loading === "search"} type="submit">
                  {loading === "search" ? <LoaderCircle className="spin" size={17} /> : <Search size={17} />}
                  {t.tools.searchSubmit}
                </button>
              </form>

              <section className="panel precedent-results-panel">
                <div className="section-head">
                  <div>
                    <span className="section-label">{t.tools.researchMaxResults}</span>
                    <h3>{t.tools.searchResults}</h3>
                  </div>
                  <strong>{precedents.length} {t.tools.records}</strong>
                </div>
                {precedents.length ? (
                  <div className="precedent-result-list">
                    {precedents.map((item, index) => (
                      <button
                        key={`${item.court}-${item.chamber}-${item.docketNo}-${item.decisionNo}-${index}`}
                        className={`precedent-result-card ${selectedPrecedent === item ? "active" : ""}`}
                        type="button"
                        onClick={() => setSelectedPrecedentIndex(index)}
                      >
                        <span>{item.court}{item.chamber ? ` / ${item.chamber}` : ""}</span>
                        <strong>{item.topic}</strong>
                        <small>{[item.docketNo, item.decisionNo, item.date].filter(Boolean).join(" - ") || "-"}</small>
                        <p>{item.summary}</p>
                      </button>
                    ))}
                  </div>
                ) : <EmptyState text={t.tools.searchEmpty} />}
              </section>

              <aside className="panel precedent-detail-panel">
                <div className="section-head">
                  <div>
                    <span className="section-label">{t.tools.researchDetails}</span>
                    <h3>{selectedPrecedent?.topic ?? t.tools.researchDetails}</h3>
                  </div>
                </div>

                {selectedPrecedent ? (
                  <section className="precedent-detail-box">
                    <dl>
                      <div><dt>{t.tools.researchCourt}</dt><dd>{selectedPrecedent.court}</dd></div>
                      <div><dt>{t.tools.researchChamber}</dt><dd>{selectedPrecedent.chamber ?? "-"}</dd></div>
                      <div><dt>{t.tools.researchDocket}</dt><dd>{[selectedPrecedent.docketNo, selectedPrecedent.decisionNo].filter(Boolean).join(" / ") || "-"}</dd></div>
                      <div><dt>{t.feedback.date}</dt><dd>{selectedPrecedent.date ?? "-"}</dd></div>
                    </dl>
                    <pre>{selectedPrecedent.content || selectedPrecedent.summary}</pre>
                  </section>
                ) : <EmptyState text={t.tools.searchEmpty} />}

                <section className="precedent-summary-box">
                  <div className="section-head">
                    <div>
                      <span className="section-label">{t.tools.researchAiSummary}</span>
                      <h3>{t.tools.researchSummarize}</h3>
                    </div>
                  </div>
                  <button className="secondary-button" disabled={!precedents.length || loading === "precedent-summary"} type="button" onClick={summarizePrecedents}>
                    {loading === "precedent-summary" ? <LoaderCircle className="spin" size={17} /> : <Bot size={17} />}
                    {t.tools.researchSummarize}
                  </button>
                  {precedentSummary ? <pre>{precedentSummary}</pre> : <p>{t.tools.researchSummaryEmpty}</p>}
                </section>

                <section className="precedent-use-box">
                  <span className="section-label">{t.tools.researchUse}</span>
                  <p>{t.tools.researchUseText}</p>
                  <button disabled={!selectedPrecedent} type="button" onClick={addSelectedPrecedentToPetition}>
                    <FileText size={17} />
                    {t.tools.petitionContext}
                  </button>
                </section>
              </aside>
            </section>
          </section>
        )}

        {activeTab === "petition" && (
          <section className="petition-assistant-workspace">
            <header className="panel smart-notes-header">
              <div>
                <span className="eyebrow">{t.tabs.petition}</span>
                <h1>{t.tools.petitionAssistantTitle}</h1>
              </div>
            </header>

            <section className="petition-assistant-layout">
              <form className="panel petition-builder-panel" onSubmit={submitPetition}>
                <PanelTitle icon={<FileText size={20} />} title={t.tools.petitionTitle} />

                <div className="petition-form-grid">
                  <label className="field-label">
                    {t.tools.petitionType}
                    <input value={petition.petitionType} onChange={(event) => setPetition({ ...petition, petitionType: event.target.value })} />
                  </label>
                  <label className="field-label">
                    {t.tools.petitionCourt}
                    <input value={petition.court} onChange={(event) => setPetition({ ...petition, court: event.target.value })} />
                  </label>
                </div>

                <label className="field-label">
                  {t.tools.petitionApplicant} / {t.tools.petitionOpponent}
                  <textarea rows={3} value={petition.parties} onChange={(event) => setPetition({ ...petition, parties: event.target.value })} />
                </label>

                <label className="field-label">
                  {t.tools.petitionDescription}
                  <textarea rows={6} value={petition.facts} onChange={(event) => setPetition({ ...petition, facts: event.target.value })} />
                </label>
                <label className="field-label">
                  {t.tools.petitionDemands}
                  <textarea rows={4} value={petition.demands} onChange={(event) => setPetition({ ...petition, demands: event.target.value })} />
                </label>

                <button disabled={loading === "petition"} type="submit">
                  {loading === "petition" ? <LoaderCircle className="spin" size={17} /> : <FileText size={17} />}
                  {t.tools.petitionSubmit}
                </button>
              </form>

              <section className="panel petition-draft-panel">
                <div className="section-head">
                  <div>
                    <span className="section-label">{t.tools.petitionDraft}</span>
                    <h3>{petitionResult?.title ?? t.tools.petitionDraft}</h3>
                  </div>
                  <div className="petition-export-actions">
                    <button className="secondary-button" disabled={!petitionResult} type="button" onClick={() => exportPetition("docx")}>{t.tools.petitionWord}</button>
                    <button className="secondary-button" disabled={!petitionResult} type="button" onClick={printPetitionPdf}>{t.tools.petitionPdf}</button>
                    <button className="secondary-button" disabled={!petitionResult} type="button" onClick={() => exportPetition("udf")}>{t.tools.petitionUdf}</button>
                  </div>
                </div>
                {petitionResult ? (
                  <>
                    <textarea className="petition-draft-textarea" value={petitionResult.body} onChange={(event) => setPetitionResult({ ...petitionResult, body: event.target.value })} onSelect={selectedTextFromEvent} />
                    <CitationList citations={petitionResult.citedPrecedents} />
                  </>
                ) : <EmptyState text={t.tools.petitionEmpty} />}
              </section>
            </section>
          </section>
        )}

        {activeTab === "training" && (
          <TrainingPanel
            locale={locale}
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

        {activeTab === "cases" && <CasesPanel locale={locale} onGoToDocuments={() => setActiveTab("document")} />}

        {activeTab === "document" && <DocumentPanel locale={locale} loading={loading} run={run} onGoToChat={() => setActiveTab("chat")} />}

        {activeTab === "knowledge" && (
          <section className="tool-grid">
            <form className="panel primary-panel" onSubmit={submitKnowledge}>
              <PanelTitle icon={<Database size={20} />} title={t.tools.knowledgeTitle} />
              <textarea value={knowledgeJson} onChange={(event) => setKnowledgeJson(event.target.value)} rows={16} />
              <div className="row">
                <button disabled={loading === "knowledge"} type="button" onClick={seedKnowledge}><Database size={17} />{t.tools.knowledgeSeed}</button>
                <button disabled={loading === "knowledge"} type="submit"><Upload size={17} />{t.tools.knowledgeSubmit}</button>
              </div>
            </form>
            <ResultPanel title={t.tools.knowledgeResult}>
              {knowledgeResult ? (
                <div className="document-summary">
                  <strong>{knowledgeResult.storage}</strong>
                  <span>{knowledgeResult.indexed} {t.tools.records}</span>
                  <p>{knowledgeResult.message}</p>
                </div>
              ) : <EmptyState text={t.tools.knowledgeEmpty} />}
            </ResultPanel>
          </section>
        )}

        {activeTab === "feedback" && (
          <section className="feedback-workspace">
            <form className="panel primary-panel feedback-compose" onSubmit={submitFeedback}>
              <PanelTitle icon={<MessageSquareMore size={20} />} title={t.feedback.composeTitle} />
              <p className="panel-subtitle">{t.feedback.composeSubtitle}</p>
              <div className="feedback-types">
                <button type="button" className={feedbackForm.type === "hata" ? "active" : ""} onClick={() => setFeedbackForm((current) => ({ ...current, type: "hata" }))}>
                  {t.feedback.types.hata}
                </button>
                <button type="button" className={feedbackForm.type === "ozellik" ? "active" : ""} onClick={() => setFeedbackForm((current) => ({ ...current, type: "ozellik" }))}>
                  {t.feedback.types.ozellik}
                </button>
                <button type="button" className={feedbackForm.type === "genel" ? "active" : ""} onClick={() => setFeedbackForm((current) => ({ ...current, type: "genel" }))}>
                  {t.feedback.types.genel}
                </button>
              </div>
              <label className="field-label">
                {t.feedback.subject}
                <input value={feedbackForm.subject} onChange={(event) => setFeedbackForm((current) => ({ ...current, subject: event.target.value }))} placeholder={t.feedback.placeholderSubject} />
              </label>
              <label className="field-label">
                {t.feedback.details}
                <textarea rows={11} value={feedbackForm.message} onChange={(event) => setFeedbackForm((current) => ({ ...current, message: event.target.value }))} placeholder={t.feedback.placeholderMessage} />
              </label>
              <div className="row feedback-compose-actions">
                <button disabled={loading === "feedback"} type="submit">
                  <Send size={17} />
                  {t.feedback.send}
                </button>
                {feedbackSubmitted ? <div className="auth-preview feedback-success">{t.feedback.lastRecord}: <strong>{feedbackSubmitted.subject}</strong></div> : null}
              </div>
            </form>

            {!selectedFeedback ? (
              <section className="panel dashboard-panel feedback-history-panel">
                <div className="section-head feedback-history-head">
                  <div>
                    <span className="section-label">{t.feedback.history}</span>
                    <h3>{t.feedback.historyTitle}</h3>
                  </div>
                  <div className="feedback-history-actions">
                    <span className="status">{filteredFeedbackItems.length} {t.feedback.recordCount}</span>
                  </div>
                </div>

                <div className="feedback-toolbar">
                  <label className="field-label">
                    {t.feedback.search}
                    <input
                      value={feedbackSearch}
                      onChange={(event) => setFeedbackSearch(event.target.value)}
                      placeholder={t.feedback.searchPlaceholder}
                    />
                  </label>
                  <label className="field-label">
                    {t.feedback.type}
                    <select value={feedbackTypeFilter} onChange={(event) => setFeedbackTypeFilter(event.target.value as FeedbackFilter)}>
                      <option value="all">{t.feedback.allTypes}</option>
                      <option value="hata">{t.feedback.types.hata}</option>
                      <option value="ozellik">{t.feedback.types.ozellik}</option>
                      <option value="genel">{t.feedback.types.genel}</option>
                    </select>
                  </label>
                  <label className="field-label">
                    {t.feedback.status}
                    <select value={feedbackStatusFilter} onChange={(event) => setFeedbackStatusFilter(event.target.value as FeedbackStatusFilter)}>
                      <option value="all">{t.feedback.allStatuses}</option>
                      <option value="received">{t.feedback.statuses.received}</option>
                      <option value="read">{t.feedback.statuses.read}</option>
                      <option value="resolved">{t.feedback.statuses.resolved}</option>
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
              </section>
            ) : (
              <section className="feedback-admin-layout feedback-admin-layout-detail">
                <article className="panel feedback-detail-panel feedback-admin-detail">
                  <div className="section-head">
                    <div>
                      <span className="section-label">{t.feedback.detail}</span>
                      <h3>{t.feedback.selectedRecord}</h3>
                    </div>
                    <button className="secondary-button" type="button" onClick={() => setSelectedFeedbackId(null)}>
                      <ArrowLeft size={16} />
                      {locale === "en" ? "Back to list" : "Listeye don"}
                    </button>
                  </div>
                  <div className="feedback-detail">
                    <div className="feedback-detail-head">
                      <strong>{selectedFeedback.subject}</strong>
                      <span className={`feedback-pill feedback-pill-type feedback-type-${selectedFeedback.type}`}>{isFeedbackType(selectedFeedback.type) ? t.feedback.types[selectedFeedback.type] : selectedFeedback.type}</span>
                    </div>
                    <div className="feedback-detail-meta">
                      <div>
                        <small>{t.feedback.status}</small>
                        <strong className={`feedback-pill feedback-pill-status feedback-status-${selectedFeedback.status}`}>{isFeedbackStatus(selectedFeedback.status) ? t.feedback.statuses[selectedFeedback.status] : selectedFeedback.status}</strong>
                      </div>
                      <div>
                        <small>{t.feedback.date}</small>
                        <strong>{new Date(selectedFeedback.createdAt).toLocaleString(locale === "en" ? "en-US" : "tr-TR")}</strong>
                      </div>
                    </div>
                    <div className="feedback-detail-body">
                      <small>{t.feedback.message}</small>
                      <p>{selectedFeedback.message}</p>
                    </div>
                  </div>
                </article>
              </section>
            )}
          </section>
        )}

        {activeTab === "profile" && (
          <section className="settings-workspace">
            <header className="panel settings-header">
              <div>
                <span className="eyebrow">{t.profile.title}</span>
                <h1>{t.profile.headline}</h1>
                <p>{t.profile.subtitle}</p>
              </div>
            </header>

            <section className="tool-grid">
              <form className="panel primary-panel" onSubmit={submitProfile}>
                <PanelTitle icon={<UserRound size={20} />} title={t.profile.formTitle} />
                <label className="field-label">
                  {t.profile.name}
                  <input
                    autoComplete="name"
                    value={profileForm.name}
                    onChange={(event) => setProfileForm((current) => ({ ...current, name: event.target.value }))}
                  />
                </label>
                <label className="field-label">
                  {t.profile.email}
                  <input
                    autoComplete="email"
                    type="email"
                    value={profileForm.email}
                    onChange={(event) => setProfileForm((current) => ({ ...current, email: event.target.value }))}
                  />
                </label>
                {profileError ? <div className="error">{profileError}</div> : null}
                {profileSuccess ? <div className="auth-preview">{profileSuccess}</div> : null}
                <button disabled={profileLoading} type="submit">
                  {profileLoading ? <LoaderCircle className="spin" size={17} /> : <CheckCircle2 size={17} />}
                  {profileLoading ? t.profile.saving : t.profile.save}
                </button>
              </form>

              <section className="panel result-panel">
                <h2>{t.profile.readonlyTitle}</h2>
                <div className="case-stats">
                  <div><span>{t.profile.name}</span><strong>{authUser.name}</strong></div>
                  <div><span>{t.profile.email}</span><strong>{authUser.email}</strong></div>
                  <div><span>{t.profile.role}</span><strong>{authUser.role === "ADMIN" ? t.common.admin : t.common.user}</strong></div>
                  <div><span>{t.profile.createdAt}</span><strong>{formatDateTime(authUser.createdAt, locale, t.profile.notAvailable)}</strong></div>
                  <div><span>{t.profile.lastLoginAt}</span><strong>{formatDateTime(authUser.lastLoginAt, locale, t.profile.notAvailable)}</strong></div>
                </div>
              </section>
            </section>
          </section>
        )}

        {activeTab === "admin" && authUser.role === "ADMIN" && (
          <section className="admin-workspace">
            <header className="panel admin-header">
              <div>
                <span className="eyebrow">{t.adminPanel.eyebrow}</span>
                <h1>{t.adminPanel.title}</h1>
                <p>{t.adminPanel.subtitle}</p>
              </div>
            </header>

            <section className="settings-content admin-content">
                {adminSection === "feedback" && (
                  <article className="panel admin-card">
                    <div className="section-head">
                      <div>
                        <span className="section-label">{t.adminPanel.userRequests}</span>
                        <h3>{t.adminFeedback.title}</h3>
                      </div>
                      <span className="status">{adminFeedbackMetrics.total} {t.tools.records}</span>
                    </div>
                    <div className="admin-metric-row">
                      <div><span>{t.adminPanel.openFeedback}</span><strong>{adminFeedbackMetrics.open}</strong></div>
                      <div><span>{t.adminPanel.resolvedFeedback}</span><strong>{adminFeedbackMetrics.resolved}</strong></div>
                    </div>
                    <div className="admin-actions">
                      <button className="secondary-button" type="button" onClick={refreshAdminFeedback} disabled={feedbackLoading}>
                        {feedbackLoading ? <LoaderCircle className="spin" size={17} /> : <MessageSquareMore size={17} />}
                        {t.adminPanel.refreshFeedback}
                      </button>
                      <Link className="admin-link-button" href="/feedback-management">
                        <ShieldAlert size={17} />
                        {t.adminPanel.openFeedbackManagement}
                      </Link>
                    </div>
                  </article>
                )}

                {adminSection === "users" && adminUserView === "list" && (
                  <article className="panel admin-card">
                    <div className="section-head">
                      <div>
                        <span className="section-label">{locale === "en" ? "System users" : "Sistem kullanicilari"}</span>
                        <h3>{locale === "en" ? "User Management" : "Kullanici Yonetimi"}</h3>
                      </div>
                      <span className="status">{adminUsers.length} {t.tools.records}</span>
                    </div>
                    <div className="admin-actions">
                      <button className="secondary-button" type="button" onClick={() => void loadAdminUsers()} disabled={adminUsersLoading}>
                        {adminUsersLoading ? <LoaderCircle className="spin" size={17} /> : <UserRound size={17} />}
                        {locale === "en" ? "Refresh users" : "Kullanicilari yenile"}
                      </button>
                    </div>
                    {adminUsersError ? <div className="error">{adminUsersError}</div> : null}
                    {adminUsersLoading ? (
                      <p className="empty">{locale === "en" ? "Loading users..." : "Kullanicilar yukleniyor..."}</p>
                    ) : (
                      <div className="feedback-datagrid-wrap">
                        <DataGrid
                          autoHeight
                          rows={adminUsers}
                          columns={adminUserColumns}
                          disableRowSelectionOnClick
                          initialState={{ pagination: { paginationModel: { page: 0, pageSize: 8 } } }}
                          pageSizeOptions={[8, 15, 25]}
                          rowHeight={68}
                          columnHeaderHeight={42}
                          onRowClick={(params: GridRowParams<AuthUser>) => void openAdminUser(String(params.id))}
                          sx={{
                            border: "none",
                            color: "var(--ink)",
                            fontFamily: "inherit",
                            "& .MuiDataGrid-columnHeaders": {
                              background: "#f7f9fb",
                              borderBottom: "1px solid var(--line)"
                            },
                            "& .MuiDataGrid-cell": {
                              borderBottom: "1px solid rgba(215, 222, 232, 0.7)"
                            },
                            "& .MuiDataGrid-row:hover": {
                              backgroundColor: "#f7fafc"
                            },
                            "& .MuiDataGrid-cell:focus, & .MuiDataGrid-columnHeader:focus": {
                              outline: "none"
                            }
                          }}
                        />
                      </div>
                    )}
                  </article>
                )}

                {adminSection === "users" && adminUserView === "detail" && (
                  <article className="panel admin-card">
                    <div className="section-head">
                      <div>
                        <span className="section-label">{locale === "en" ? "User detail" : "Kullanici detayi"}</span>
                        <h3>{selectedAdminUser?.name ?? "-"}</h3>
                      </div>
                      <button className="secondary-button" type="button" onClick={() => setAdminUserView("list")}>
                        <ArrowLeft size={16} />
                        {locale === "en" ? "Back to list" : "Listeye don"}
                      </button>
                    </div>
                    {adminUsersError ? <div className="error">{adminUsersError}</div> : null}
                    {selectedAdminUser ? (
                      <div className="case-stats">
                        <div><span>ID</span><strong>{selectedAdminUser.id}</strong></div>
                        <div><span>{locale === "en" ? "Name" : "Ad soyad"}</span><strong>{selectedAdminUser.name}</strong></div>
                        <div><span>E-posta</span><strong>{selectedAdminUser.email}</strong></div>
                        <div><span>{locale === "en" ? "Role" : "Rol"}</span><strong>{selectedAdminUser.role ?? "USER"}</strong></div>
                        <div><span>{locale === "en" ? "Created" : "Olusturma"}</span><strong>{formatDateTime(selectedAdminUser.createdAt, locale, "-")}</strong></div>
                        <div><span>{locale === "en" ? "Last login" : "Son giris"}</span><strong>{formatDateTime(selectedAdminUser.lastLoginAt, locale, "-")}</strong></div>
                      </div>
                    ) : (
                      <p className="empty">{locale === "en" ? "Select a user from the list." : "Listeden bir kullanici secin."}</p>
                    )}
                  </article>
                )}
            </section>
          </section>
        )}

        {activeTab === "settings" && (
          <section className="settings-workspace">
            <header className="panel settings-header">
              <div>
                <span className="eyebrow">{t.settings.title}</span>
                <h1>{t.settings.headline}</h1>
                <p>{t.settings.subtitle}</p>
              </div>
            </header>

            <section className="settings-content">
                {settingsSection === "view" && (
                  <article className="panel settings-card">
                    <div className="section-head">
                      <div>
                        <span className="section-label">{t.settings.sections.view}</span>
                        <h3>{t.settings.themeInterface}</h3>
                      </div>
                    </div>
                    <label className="field-label">
                      {t.settings.theme}
                      <select value={themeMode} onChange={(event) => setThemeMode(event.target.value as ThemeMode)}>
                        <option value="original">{t.settings.localeOriginal}</option>
                        <option value="light">{t.settings.themeLight}</option>
                        <option value="dark">{t.settings.themeDark}</option>
                      </select>
                    </label>
                    <label className="field-label">
                      {t.settings.localeView}
                      <select defaultValue="comfortable">
                        <option value="compact">{t.settings.themeCompact}</option>
                        <option value="comfortable">{t.settings.themeBalanced}</option>
                        <option value="spacious">{t.settings.themeSpacious}</option>
                      </select>
                    </label>
                    <label className="field-label">
                      {t.settings.language}
                      <select value={locale} onChange={(event) => setLocale(event.target.value as Locale)}>
                        <option value="tr">{t.settings.tr}</option>
                        <option value="en">{t.settings.en}</option>
                      </select>
                    </label>
                  </article>
                )}

                {settingsSection === "account" && (
                  <form className="panel settings-card" onSubmit={handleChangePassword}>
                    <div className="section-head">
                      <div>
                        <span className="section-label">{t.settings.sections.account}</span>
                        <h3>{t.settings.accountAccess}</h3>
                      </div>
                    </div>
                    <label className="setting-row">
                      <div>
                        <strong>{t.settings.currentUser}</strong>
                        <span>{authUser.name}</span>
                      </div>
                      <span className="status">{authUser.role === "ADMIN" ? t.common.admin : t.common.user}</span>
                    </label>
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
                )}

            </section>
          </section>
        )}
      </section>
    </main>
  );
}

function DocumentPanel({ locale, loading, run, onGoToChat }: { locale: Locale; loading: string; run: (action: string, fn: () => Promise<void>) => void; onGoToChat: () => void }) {
  const t = getMessages(locale).document;
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [topic, setTopic] = useState("");
  const [court, setCourt] = useState("");
  const [localError, setLocalError] = useState("");
  const [result, setResult] = useState<UploadResponse | null>(null);

  function applyFile(nextFile: File | null) {
    const validationError = validateFile(nextFile, locale);
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
      setLocalError(t.errors.selectFirst);
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
        <PanelTitle icon={<Upload size={20} />} title={t.title} />
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
              <strong>{t.chooseFile}</strong>
              <span>{t.fileHint}</span>
            </>
          )}
        </div>
        <label className="field-label">{t.topic}<input disabled={isBusy} onChange={(event) => setTopic(event.target.value)} value={topic} /></label>
        <label className="field-label">{t.court}<input disabled={isBusy} onChange={(event) => setCourt(event.target.value)} value={court} /></label>
        <div className="upload-actions">
          <button className="secondary-button" disabled={!file || isBusy} onClick={() => runUpload("analyze")} type="button">{loading === "analyze" ? <LoaderCircle className="spin" size={17} /> : <FileText size={17} />}{t.analyze}</button>
          <button disabled={!file || isBusy} onClick={() => runUpload("ingest")} type="button">{loading === "ingest" ? <LoaderCircle className="spin" size={17} /> : <Upload size={17} />}{t.ingest}</button>
        </div>
        {localError && <div className="inline-error"><AlertCircle size={18} /><span>{localError}</span></div>}
      </div>
      <ResultPanel title={t.result}>
        {!result && !loading && <EmptyState text={t.empty} />}
        {loading && <div className="result-loading"><LoaderCircle className="spin" size={36} /><strong>{t.processing}</strong></div>}
        {result && !loading && (
          <div className="document-summary">
            {ingestSuccess && <div className="success-banner"><CheckCircle2 size={20} /><span>{t.indexedParts.replace("{count}", String(result.indexed))}</span></div>}
            <div className="result-stats">
              <div><span>{t.file}</span><strong>{result.filename}</strong></div>
              <div><span>{t.type}</span><strong>{result.contentType}</strong></div>
              <div><span>{t.size}</span><strong>{formatBytes(result.size)}</strong></div>
              {result.extractedCharacters != null && <div><span>{t.extractedText}</span><strong>{result.extractedCharacters.toLocaleString(locale === "en" ? "en-US" : "tr-TR")} {t.characters}</strong></div>}
            </div>
            <p>{result.message || result.summary}</p>
            {result.textPreview && <pre>{result.textPreview}</pre>}
            {issues.length > 0 && <ul className="issue-list">{issues.map((issue) => <li key={issue}>{issue}</li>)}</ul>}
            {ingestSuccess && <button className="chat-cta" onClick={onGoToChat} type="button"><Bot size={17} />{t.goChat}</button>}
          </div>
        )}
      </ResultPanel>
    </section>
  );
}

function CasesPanel({ locale, onGoToDocuments }: { locale: Locale; onGoToDocuments: () => void }) {
  const t = getMessages(locale).cases;
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
  const caseColumns = useMemo<GridColDef<CaseRecord>[]>(() => [
    {
      field: "caseLabel",
      headerName: t.caseType,
      flex: 1,
      minWidth: 190,
      renderCell: (params) => (
        <div className="feedback-grid-cell">
          <strong>{String(params.value ?? "")}</strong>
          <span>{params.row.subject}</span>
        </div>
      )
    },
    {
      field: "clientName",
      headerName: t.client,
      flex: 1,
      minWidth: 180,
      renderCell: (params) => (
        <div className="feedback-grid-cell">
          <strong>{String(params.value ?? "")}</strong>
          <span>{params.row.opponentName}</span>
        </div>
      )
    },
    {
      field: "courtName",
      headerName: t.court,
      flex: 1,
      minWidth: 220,
      renderCell: (params) => <span className="feedback-owner">{String(params.value ?? "-")}</span>
    },
    {
      field: "progress",
      headerName: t.overallCompletion,
      width: 170,
      renderCell: (params) => <span className="case-progress-pill">{Number(params.value ?? 0)}%</span>
    },
    {
      field: "updatedAt",
      headerName: locale === "en" ? "Updated" : "Guncelleme",
      width: 180,
      valueGetter: (_, row) => formatDateTime(row.updatedAt, locale, "-")
    },
    {
      field: "actions",
      headerName: t.view,
      width: 180,
      sortable: false,
      filterable: false,
      renderCell: (params) => (
        <div className="feedback-row-actions">
          <button className="secondary-button" onClick={(event) => {
            event.stopPropagation();
            void openCase(params.row.id);
          }} type="button">
            {t.view}
          </button>
          <button className="danger-button" onClick={(event) => {
            event.stopPropagation();
            void deleteCase(params.row.id);
          }} type="button">
            {t.delete}
          </button>
        </div>
      )
    }
  ], [locale, t]);

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
      } catch (error) {
        if (!cancelled) {
          setLocalError(error instanceof Error ? error.message : t.errors.load);
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
      setLocalError(error instanceof Error ? error.message : t.errors.open);
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
      const nextSelected = caseList.find((item) => item.id === created.id) ?? null;
      setSelectedCaseId(nextSelected?.id ?? null);
      setSelectedCase(nextSelected);
      setCaseScreen("list");
      setCaseType(created.caseType as CaseType);
    } catch (error) {
      setLocalError(error instanceof Error ? error.message : t.errors.save);
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
      setLocalError(error instanceof Error ? error.message : t.errors.document);
    }
  }

  async function deleteCase(caseId: string) {
    const confirmed = window.confirm(t.confirmDelete);
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
      setLocalError(error instanceof Error ? error.message : t.errors.delete);
    }
  }

  async function loadSampleCases() {
    setLoadingCases(true);
    setLocalError("");
    try {
      const caseList = await seedSamples<CaseRecord[]>("/cases/seed-samples");
      setSavedCases(caseList);
      setCaseScreen("list");
    } catch (error) {
      setLocalError(error instanceof Error ? error.message : t.errors.samples);
    } finally {
      setLoadingCases(false);
    }
  }

  return (
    <section className="cases-shell">
      <div className="cases-toolbar panel">
        <div>
          <h2>{t.title}</h2>
          <p>{t.subtitle}</p>
        </div>
      </div>

      {localError && <div className="error">{localError}</div>}

      {caseScreen === "create" && (
        <section className="cases-grid">
          <form className="panel primary-panel case-form case-form-large" onSubmit={submitCase}>
            <PanelTitle icon={<FolderOpen size={20} />} title={t.addCase} />
            <label className="field-label">
              {t.caseType}
              <select value={caseType} onChange={(event) => setCaseType(event.target.value as CaseType)}>
                {Object.entries(caseTypeLabels).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </label>
            <label className="field-label">
              {t.client}
              <input value={clientName} onChange={(event) => setClientName(event.target.value)} placeholder={t.clientPlaceholder} />
            </label>
            <label className="field-label">
              {t.opponent}
              <input value={opponentName} onChange={(event) => setOpponentName(event.target.value)} placeholder={t.opponentPlaceholder} />
            </label>
            <label className="field-label">
              {t.court}
              <input value={courtName} onChange={(event) => setCourtName(event.target.value)} />
            </label>
            <label className="field-label">
              {t.subject}
              <input value={subject} onChange={(event) => setSubject(event.target.value)} />
            </label>
            <label className="field-label">
              {t.summary}
              <textarea rows={5} value={summary} onChange={(event) => setSummary(event.target.value)} />
            </label>
            <div className="case-template">
              <strong>{selectedTemplate.title}</strong>
              <p>{selectedTemplate.summary}</p>
              <small>{t.courtHint.replace("{court}", selectedTemplate.courtHint)}</small>
            </div>
            <div className="upload-actions">
              <button className="secondary-button" type="button" onClick={onGoToDocuments}>
                <Upload size={17} />
                {t.uploadDocument}
              </button>
              <button type="button" onClick={markAll}>
                <CheckCircle2 size={17} />
                {t.markAll}
              </button>
              <button disabled={saving} type="submit">
                {saving ? <LoaderCircle className="spin" size={17} /> : <FolderOpen size={17} />}
                {t.saveAndList}
              </button>
            </div>
          </form>

          <div className="panel result-panel case-summary-panel">
            <div className="cases-preview-head">
              <h2>{t.prepSummary}</h2>
              <button className="secondary-button" onClick={openListScreen} type="button">{t.backList}</button>
            </div>
            <div className="case-score">
              <strong>{completion}%</strong>
              <span>{t.requiredCompletion}</span>
            </div>
            <div className="meter" aria-hidden="true">
              <div className="meter-fill" style={{ width: `${completion}%` }} />
            </div>
            <div className="case-stats">
              <div><span>{t.client}</span><strong>{clientName || "-"}</strong></div>
              <div><span>{t.opponent}</span><strong>{opponentName || "-"}</strong></div>
              <div><span>{t.subject}</span><strong>{subject || "-"}</strong></div>
              <div><span>{t.court}</span><strong>{courtName || selectedTemplate.courtHint}</strong></div>
            </div>
            <div className="check-note">
              <CheckCircle2 size={18} />
              <span>{missingRequired.length === 0 ? t.requiredComplete : t.requiredMissing.replace("{count}", String(missingRequired.length))}</span>
            </div>
            <div className="check-note muted">
              <AlertCircle size={18} />
              <span>{t.saveNotice}</span>
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
                        <em>{item.required ? t.required : t.optional}</em>
                      </label>
                    ))}
                  </div>
                </section>
              ))}
            </div>
          </div>
        </section>
      )}

      {caseScreen === "list" && (
        <section className="feedback-admin-layout feedback-admin-layout-list">
          <div className="panel case-list-panel feedback-admin-table">
            <div className="case-list-head">
              <div>
                <h2>{t.listing}</h2>
                <p>{savedCases.length} {t.caseRecord}</p>
              </div>
              <div className="case-list-head-actions">
                <button className="secondary-button" onClick={() => void loadSampleCases()} type="button">
                  <CheckCircle2 size={17} />
                  {t.loadSamples}
                </button>
                <button className="secondary-button" onClick={openCreateScreen} type="button">
                  <FolderOpen size={17} />
                  {t.addCase}
                </button>
              </div>
            </div>
            {loadingCases ? (
              <p className="empty">{t.loading}</p>
            ) : allCases.length ? (
              <div className="feedback-datagrid-wrap">
                <DataGrid
                  autoHeight
                  rows={allCases}
                  columns={caseColumns}
                  disableRowSelectionOnClick
                  initialState={{ pagination: { paginationModel: { page: 0, pageSize: 8 } } }}
                  pageSizeOptions={[8, 15, 25]}
                  sx={{
                    border: 0,
                    "& .MuiDataGrid-columnHeaders": {
                      borderBottom: "1px solid var(--line)",
                      backgroundColor: "#f7f9fb"
                    },
                    "& .MuiDataGrid-cell": {
                      borderBottom: "1px solid rgba(215, 222, 232, 0.7)"
                    },
                    "& .MuiDataGrid-row:hover": {
                      backgroundColor: "#f7fafc"
                    },
                    "& .MuiDataGrid-cell:focus, & .MuiDataGrid-columnHeader:focus": {
                      outline: "none"
                    }
                  }}
                />
              </div>
            ) : (
              <div className="case-empty-detail">
                <h3>{t.emptyList}</h3>
                <p>{t.emptyListDesc}</p>
                <div className="upload-actions">
                  <button className="secondary-button" onClick={() => void loadSampleCases()} type="button">
                    <CheckCircle2 size={17} />
                    {t.loadSamples}
                  </button>
                  <button onClick={openCreateScreen} type="button">
                    <FolderOpen size={17} />
                    {t.addCase}
                  </button>
                </div>
              </div>
            )}
          </div>
        </section>
      )}

      {caseScreen === "detail" && (
        <section className="feedback-admin-layout feedback-admin-layout-detail">
          <div className="panel case-detail-panel feedback-admin-detail">
            <div className="case-detail-head">
              <div>
                <h2>{t.view}</h2>
                <p>{selectedCase?.caseLabel ?? t.selectCase}</p>
              </div>
              <div className="case-detail-actions">
                <button className="secondary-button" onClick={openListScreen} type="button">
                  <ArrowLeft size={16} />
                  {t.backList}
                </button>
                {selectedCase ? <button className="danger-button" onClick={() => void deleteCase(selectedCase.id)} type="button">{t.delete}</button> : null}
              </div>
            </div>
            {selectedCase ? (
              <>
                <div className="case-score compact">
                  <strong>{selectedCase.progress}%</strong>
                  <span>{t.overallCompletion}</span>
                </div>
                <div className="meter" aria-hidden="true">
                  <div className="meter-fill" style={{ width: `${selectedCase.progress}%` }} />
                </div>
                <div className="case-stats case-detail-stats">
                  <div><span>{t.client}</span><strong>{selectedCase.clientName}</strong></div>
                  <div><span>{t.opponent}</span><strong>{selectedCase.opponentName}</strong></div>
                  <div><span>{t.court}</span><strong>{selectedCase.courtName}</strong></div>
                  <div><span>{t.subject}</span><strong>{selectedCase.subject}</strong></div>
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
                      <em>{document.required ? t.required : t.optional}</em>
                    </label>
                  ))}
                </div>
              </>
            ) : (
              <div className="case-empty-detail">
                <p>{t.selectCase}</p>
                <button className="secondary-button" onClick={openListScreen} type="button">{t.backList}</button>
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

function validateFile(file: File | null, locale: Locale = "tr") {
  const t = getMessages(locale).document.errors;
  if (!file) return t.noFile;
  const extension = file.name.includes(".") ? file.name.slice(file.name.lastIndexOf(".")).toLowerCase() : "";
  if (!acceptedExtensions.includes(extension)) return t.unsupported;
  if (file.size > maxFileBytes) return t.tooLarge;
  if (file.size === 0) return t.empty;
  return null;
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDateTime(value: string | null | undefined, locale: Locale, fallback: string) {
  if (!value) return fallback;
  return new Date(value).toLocaleString(locale === "en" ? "en-US" : "tr-TR");
}

function escapeHtml(value: string) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}






