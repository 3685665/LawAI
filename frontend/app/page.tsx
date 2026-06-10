"use client";

import { FormEvent, SyntheticEvent, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { Drawer, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from "@mui/material";
import { DataGrid, type GridColDef, type GridRowParams } from "@mui/x-data-grid";
import {
  AlertCircle,
  ArrowLeft,
  BarChart3,
  Bot,
  BriefcaseBusiness,
  CheckCircle2,
  ClipboardList,
  CreditCard,
  Clock3,
  FileUp,
  FileSearch,
  FileText,
  FolderOpen,
  Printer,
  KeyRound,
  LoaderCircle,
  Mic,
  MessageSquareMore,
  Plus,
  Save,
  Trash2,
  Palette,
  Scale,
  Search,
  Send,
  SlidersHorizontal,
  Settings,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  ScrollText,
  Upload,
  UserRound,
  UsersRound,
  X,
  type LucideIcon
} from "lucide-react";
import { AppSidebar } from "@/components/app-sidebar";
import { useSidebarCollapsed } from "@/hooks/use-sidebar-collapsed";
import { findNavGroupForTab } from "@/lib/app-navigation";
import { getMessages, isLocale, type Locale } from "@/lib/i18n";
import { buildPetitionDocxBlob, sanitizePetitionFileName } from "@/lib/petitionExport";
import {
  authChangePassword,
  authForgotPassword,
  authGoogle,
  authLogin,
  authLogout,
  authMe,
  authRegister,
  authUpdateProfile,
  createActivityLog,
  CaseDocument as ApiCaseDocument,
  CaseRecord,
  CaseTemplate,
  CaseTemplatesResponse,
  type AuthPasswordResetResponse,
  type AuthSessionResponse,
  type AuthUser,
  type ChatHistorySession,
  checkHealth,
  createUser,
  deleteJson,
  deleteUser,
  getJson,
  getUser,
  patchJson,
  postJson,
  listFeedback,
  listUsers,
  updateUser,
  Precedent,
  seedSamples,
  summarizePrecedent,
  applyPrecedentToPetition,
  submitFeedback as postFeedback,
  type FeedbackStatus,
  type FeedbackRecord,
  uploadMultipart,
  runLegalResearch,
  streamLegalResearch,
  type ResearchStep
} from "@/lib/api";

type Tab = "chat" | "caseLaw" | "petition" | "cases" | "document" | "feedback" | "profile" | "settings" | "admin";
type AuthMode = "login" | "register" | "forgot";
type GoogleCredentialResponse = { credential?: string };
type GoogleAccountsId = {
  initialize: (options: { client_id: string; callback: (response: GoogleCredentialResponse) => void }) => void;
  renderButton: (parent: HTMLElement, options: Record<string, string | number | boolean>) => void;
};
declare global {
  interface Window {
    google?: {
      accounts?: {
        id?: GoogleAccountsId;
      };
    };
  }
}
type ChatResponse = { answer: string; citations: Precedent[]; disclaimer: string; sessionId?: string | null };
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
type FeedbackType = "hata" | "ozellik" | "genel";
type FeedbackFilter = "all" | FeedbackType;
type FeedbackStatusFilter = "all" | FeedbackStatus;
type PetitionMethod = "case" | "quick" | "detailed";
type PetitionModel = "standard" | "premium";
type PrecedentSourceKey = "all" | "yargitay" | "danistay" | "aym" | "rekabet";
type CaseLawSearchMode = "simple" | "advanced";
type NavigationGroup = {
  id: string;
  label: string;
  icon: LucideIcon;
  tab?: Tab;
  children?: NavigationChild[];
};
type NavigationChild = {
  id: string;
  label: string;
  icon: LucideIcon;
  tab?: Tab;
  href?: string;
  onSelect?: () => void;
};
type FeedbackGridRow = FeedbackRecord & {
  typeLabel: string;
  statusLabel: string;
  createdAtLabel: string;
  messagePreview: string;
};
type CaseLawGridRow = Precedent & {
  id: string;
  rowIndex: number;
};
type PrecedentDetailRow = {
  label: string;
  value: string;
};

type PetitionPrecedentBlock = {
  id: string;
  caseId: string;
  caseLabel: string;
  precedentKey: string;
  precedentTitle: string;
  citationLine: string;
  applicationNote: string;
  legalGroundsSnippet: string;
  factsLinkSnippet: string;
};

function getPrecedentKey(item: Precedent, index: number) {
  if (item.sourceId) {
    return `${item.court}:${item.sourceId}`;
  }
  return `${item.court}:${item.docketNo ?? ""}:${item.decisionNo ?? ""}:${index}`;
}
type AdminSection = "feedback" | "users";
type AdminUserView = "list" | "detail" | "create";
type AdminUserForm = {
  name: string;
  email: string;
  role: "USER" | "ADMIN";
  verified: boolean;
  password: string;
};
const emptyAdminUserForm = (): AdminUserForm => ({
  name: "",
  email: "",
  role: "USER",
  verified: true,
  password: ""
});
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
  documentId?: number;
  filename: string;
  storedPath?: string;
  size?: number;
  contentType?: string;
  detectedIssues?: string[];
  summary?: string;
  extractedCharacters?: number;
  chunkCount?: number;
  indexed?: number;
  postgresChunks?: number;
  opensearchIndexed?: number;
  pgvectorEmbeddings?: number;
  storage?: string;
  message?: string;
  textPreview?: string;
  warnings?: string[];
};

const acceptedExtensions = [".pdf"];
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

function getTabLabel(tab: Tab, locale: Locale) {
  const labels: Record<Tab, { tr: string; en: string }> = {
    chat: { tr: "Asistan", en: "Assistant" },
    caseLaw: { tr: "Ictihat Arama", en: "Case-Law Search" },
    petition: { tr: "Dilekce Taslak", en: "Petition Draft" },
    cases: { tr: "Davalar", en: "Cases" },
    document: { tr: "Belge Isleme", en: "Document Processing" },
    feedback: { tr: "Geri Bildirim", en: "Feedback" },
    profile: { tr: "Profil", en: "Profile" },
    settings: { tr: "Ayarlar", en: "Settings" },
    admin: { tr: "Yonetim", en: "Management" }
  };
  return labels[tab][locale];
}

export default function Home() {
  const [activeTab, setActiveTab] = useState<Tab>("chat");
  const [privateMode] = useState(true);
  const [themeMode, setThemeMode] = useState<ThemeMode>("original");
  const [locale, setLocale] = useState<Locale>("tr");
  const [loading, setLoading] = useState("");
  const [error, setError] = useState("");
  const [backendOnline, setBackendOnline] = useState<boolean | null>(null);
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState("");
  const [googleAuthError, setGoogleAuthError] = useState("");
  const [authMode, setAuthMode] = useState<AuthMode>("login");
  const [authPreview, setAuthPreview] = useState<AuthPasswordResetResponse | null>(null);
  const [registerResult, setRegisterResult] = useState<import("@/lib/api").AuthRegisterResponse | null>(null);
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
  const googleButtonRef = useRef<HTMLDivElement | null>(null);
  const googleClientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? "";

  const t = useMemo(() => getMessages(locale), [locale]);

  const [smartNoteCaseTitle, setSmartNoteCaseTitle] = useState("Genel analiz");
  const [smartNoteDraft, setSmartNoteDraft] = useState("");
  const [smartNotes, setSmartNotes] = useState<SmartNote[]>([]);
  const [chatResponse, setChatResponse] = useState<ChatResponse | null>(null);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatSessions, setChatSessions] = useState<ChatHistorySession[]>([]);
  const [activeChatSessionId, setActiveChatSessionId] = useState<string | null>(null);
  const [chatHistoryLoading, setChatHistoryLoading] = useState(false);
  const [chatHistoryError, setChatHistoryError] = useState("");
  const [chatHistoryOpen, setChatHistoryOpen] = useState(false);
  const [chatAttachment, setChatAttachment] = useState<ChatAttachment | null>(null);
  const [chatAttachmentLoading, setChatAttachmentLoading] = useState(false);
  const [voiceListening, setVoiceListening] = useState(false);
  const [voiceNotice, setVoiceNotice] = useState("");
  const [researchMode, setResearchMode] = useState(true);
  const [researchSteps, setResearchSteps] = useState<ResearchStep[]>([]);
  const chatFileInputRef = useRef<HTMLInputElement | null>(null);
  const speechRecognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const voiceBaseDraftRef = useRef("");
  const [searchQuery, setSearchQuery] = useState("Guncel kararlara gore bosanmada ziynet esyalari nasil paylasilir?");
  const [searchCourt, setSearchCourt] = useState("");
  const [caseLawSearchMode, setCaseLawSearchMode] = useState<CaseLawSearchMode>("simple");
  const [advancedSearch, setAdvancedSearch] = useState({
    chamber: "",
    docketNo: "",
    decisionNo: "",
    dateFrom: "",
    dateTo: ""
  });
  const [precedentSource, setPrecedentSource] = useState<PrecedentSourceKey>("all");
  const [precedents, setPrecedents] = useState<Precedent[]>([]);
  const [precedentSummaries, setPrecedentSummaries] = useState<Record<string, string>>({});
  const [selectedPrecedentIndex, setSelectedPrecedentIndex] = useState<number | null>(null);
  const [precedentDetailOpen, setPrecedentDetailOpen] = useState(false);
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
  const [savedPetitionCases, setSavedPetitionCases] = useState<CaseRecord[]>([]);
  const [petitionLinkedCaseId, setPetitionLinkedCaseId] = useState<string | null>(null);
  const [petitionPrecedentBlocks, setPetitionPrecedentBlocks] = useState<PetitionPrecedentBlock[]>([]);
  const [petitionApplySuccess, setPetitionApplySuccess] = useState("");
  const [petitionContextSources, setPetitionContextSources] = useState({
    upload: false,
    existing: true
  });
  const [petitionEditInstruction, setPetitionEditInstruction] = useState("");
  const [selectedPetitionText, setSelectedPetitionText] = useState("");
  const [petitionEditPreview, setPetitionEditPreview] = useState<{ before: string; after: string } | null>(null);
  const [petitionResult, setPetitionResult] = useState<PetitionResponse | null>(null);
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
  const [adminUserForm, setAdminUserForm] = useState<AdminUserForm>(emptyAdminUserForm);
  const [adminUserSaving, setAdminUserSaving] = useState(false);
  const [settingsSection, setSettingsSection] = useState<"view" | "account">("view");
  const { collapsed: sidebarCollapsed, toggleCollapsed: toggleSidebarCollapsed } = useSidebarCollapsed();
  const [openNavGroup, setOpenNavGroup] = useState<string | null>(null);
  const navigationGroups = useMemo<NavigationGroup[]>(() => {
    const groups: NavigationGroup[] = [
      { id: "assistant", label: t.tabs.chat, icon: Bot, tab: "chat" },
      { id: "case-law", label: t.tabs.caseLaw, icon: Scale, tab: "caseLaw" },
      { id: "petition", label: t.tabs.petition, icon: ScrollText, tab: "petition" },
      { id: "cases", label: t.tabs.cases, icon: BriefcaseBusiness, tab: "cases" },
      { id: "document", label: t.tabs.document, icon: FileUp, tab: "document" },
      {
        id: "account",
        label: locale === "en" ? "Account" : "Hesap",
        icon: UserRound,
        children: [
          { id: "profile", label: t.tabs.profile, icon: UserRound, tab: "profile" },
          { id: "activity", label: locale === "en" ? "User Activity" : "Kullanici Islemleri", icon: ClipboardList, href: "/activity-logs" },
          { id: "subscriptions", label: locale === "en" ? "Subscriptions" : "Abonelik", icon: CreditCard, href: "/subscriptions" },
          { id: "feedback", label: t.tabs.feedback, icon: MessageSquareMore, tab: "feedback" },
          {
            id: "settings-view",
            label: t.settings.sections.view,
            icon: Palette,
            tab: "settings",
            onSelect: () => setSettingsSection("view")
          },
          {
            id: "settings-account",
            label: t.settings.sections.account,
            icon: KeyRound,
            tab: "settings",
            onSelect: () => setSettingsSection("account")
          }
        ]
      }
    ];
    if (authUser?.role === "ADMIN") {
      groups.push({
        id: "admin",
        label: t.tabs.admin,
        icon: ShieldAlert,
        children: [
          { id: "admin-feedback", label: t.adminFeedback.title, icon: MessageSquareMore, href: "/feedback-management" },
          { id: "admin-subscriptions", label: locale === "en" ? "Subscription Plans" : "Abonelik Planlari", icon: CreditCard, href: "/admin/subscriptions" },
          { id: "admin-user-subscriptions", label: locale === "en" ? "User Subscriptions" : "Kullanici Abonelikleri", icon: CreditCard, href: "/admin/user-subscriptions" },
          {
            id: "admin-users",
            label: locale === "en" ? "User Management" : "Kullanici Yonetimi",
            icon: UsersRound,
            tab: "admin",
            onSelect: () => {
              setAdminSection("users");
              setAdminUserView("list");
            }
          },
          {
            id: "admin-logs",
            label: locale === "en" ? "Activity Logs" : "Islem Loglari",
            icon: ClipboardList,
            href: "/admin/activity-logs"
          }
        ]
      });
    }
    return groups;
  }, [authUser?.role, locale, t]);

  useEffect(() => {
    setOpenNavGroup(findNavGroupForTab(navigationGroups, activeTab));
  }, [activeTab, navigationGroups]);

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
      setSavedPetitionCases([]);
      return;
    }
    let cancelled = false;
    getJson<CaseRecord[]>("/cases")
      .then((cases) => {
        if (cancelled) return;
        setSavedPetitionCases(cases);
        setPetitionLinkedCaseId((current) => {
          if (current && cases.some((item) => item.id === current)) {
            return current;
          }
          const stored = window.localStorage.getItem("lawai-petition-case-id");
          if (stored && cases.some((item) => item.id === stored)) {
            return stored;
          }
          return cases[0]?.id ?? null;
        });
      })
      .catch(() => {
        if (!cancelled) {
          setSavedPetitionCases([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [authUser]);

  useEffect(() => {
    if (petitionLinkedCaseId) {
      window.localStorage.setItem("lawai-petition-case-id", petitionLinkedCaseId);
      return;
    }
    window.localStorage.removeItem("lawai-petition-case-id");
  }, [petitionLinkedCaseId]);

  useEffect(() => {
    if (!authReady || authUser || authMode === "forgot" || !googleClientId || !googleButtonRef.current) {
      return;
    }

    let cancelled = false;
    const renderGoogleButton = () => {
      if (cancelled || !googleButtonRef.current || !window.google?.accounts?.id) {
        return;
      }
      googleButtonRef.current.innerHTML = "";
      window.google.accounts.id.initialize({
        client_id: googleClientId,
        callback: (response) => void handleGoogleCredential(response)
      });
      const buttonWidth = Math.min(googleButtonRef.current.parentElement?.clientWidth ?? 360, 400);
      window.google.accounts.id.renderButton(googleButtonRef.current, {
        theme: "outline",
        size: "large",
        type: "standard",
        shape: "rectangular",
        text: authMode === "register" ? "signup_with" : "signin_with",
        width: buttonWidth
      });
    };

    if (window.google?.accounts?.id) {
      renderGoogleButton();
    } else {
      const existingScript = document.querySelector<HTMLScriptElement>("script[src='https://accounts.google.com/gsi/client']");
      if (existingScript) {
        existingScript.addEventListener("load", renderGoogleButton, { once: true });
      } else {
        const script = document.createElement("script");
        script.src = "https://accounts.google.com/gsi/client";
        script.async = true;
        script.defer = true;
        script.addEventListener("load", renderGoogleButton, { once: true });
        document.head.appendChild(script);
      }
    }

    return () => {
      cancelled = true;
    };
  }, [authReady, authUser, authMode, googleClientId]);

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
    if (!authUser || (activeTab !== "feedback" && activeTab !== "admin") || feedbackLoaded || feedbackLoading) {
      return;
    }
    void loadFeedbackHistory();
  }, [activeTab, authUser, feedbackLoaded, feedbackLoading]);

  useEffect(() => {
    if (!authUser) {
      setChatSessions([]);
      setActiveChatSessionId(null);
      setChatMessages([]);
      setChatResponse(null);
      return;
    }
    void loadChatSessions();
  }, [authUser]);

  useEffect(() => {
    if (!authUser || authUser.role !== "ADMIN" || activeTab !== "admin" || adminSection !== "users" || adminUsers.length || adminUsersLoading) {
      return;
    }
    void loadAdminUsers();
  }, [activeTab, adminSection, adminUsers.length, adminUsersLoading, authUser]);

  useEffect(() => {
    if (!authUser) {
      return;
    }
    const screen = getTabLabel(activeTab, locale);
    void createActivityLog({
      action: "screen-view",
      screen,
      detail: `${screen} ekrani goruntulendi.`,
      path: window.location.pathname
    }).catch(() => undefined);
  }, [activeTab, authUser, locale]);

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
      field: "verified",
      headerName: locale === "en" ? "Verified" : "Dogrulandi",
      width: 130,
      renderCell: (params) => (
        <span className={`feedback-pill ${params.row.verified ? "feedback-pill-status" : ""}`}>
          {params.row.verified ? (locale === "en" ? "Yes" : "Evet") : (locale === "en" ? "No" : "Hayir")}
        </span>
      )
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
    if (selectedPrecedentIndex === null || !precedents.length) return null;
    return precedents[selectedPrecedentIndex] ?? null;
  }, [precedents, selectedPrecedentIndex]);

  const selectedPrecedentKey = useMemo(() => {
    if (selectedPrecedentIndex === null || !selectedPrecedent) return null;
    return getPrecedentKey(selectedPrecedent, selectedPrecedentIndex);
  }, [selectedPrecedent, selectedPrecedentIndex]);

  const selectedPrecedentSummary = useMemo(() => {
    if (!selectedPrecedentKey) return "";
    return precedentSummaries[selectedPrecedentKey] ?? "";
  }, [precedentSummaries, selectedPrecedentKey]);

  const linkedPetitionCase = useMemo(
    () => savedPetitionCases.find((item) => item.id === petitionLinkedCaseId) ?? null,
    [petitionLinkedCaseId, savedPetitionCases]
  );

  const selectedPrecedentDetailRows = useMemo<PrecedentDetailRow[]>(() => {
    if (!selectedPrecedent) return [];
    return [
      { label: t.tools.researchCourt, value: selectedPrecedent.court },
      { label: t.tools.researchChamber, value: selectedPrecedent.chamber ?? "-" },
      { label: t.tools.researchDocket, value: [selectedPrecedent.docketNo, selectedPrecedent.decisionNo].filter(Boolean).join(" / ") || "-" },
      { label: t.feedback.date, value: selectedPrecedent.date ?? "-" }
    ];
  }, [selectedPrecedent, t.feedback.date, t.tools.researchChamber, t.tools.researchCourt, t.tools.researchDocket]);

  const caseLawRows = useMemo<CaseLawGridRow[]>(() => precedents.map((item, index) => ({
    ...item,
    id: item.sourceId ?? `${item.court}-${item.chamber}-${item.docketNo}-${item.decisionNo}-${index}`,
    rowIndex: index
  })), [precedents]);

  const caseLawColumns = useMemo<GridColDef<CaseLawGridRow>[]>(() => [
    {
      field: "court",
      headerName: locale === "en" ? "Court" : "Mahkeme",
      minWidth: 160,
      flex: 0.9,
      renderCell: (params) => <span className="feedback-pill feedback-pill-status">{getCourtDisplayName(params.row.court)}</span>
    },
    {
      field: "chamber",
      headerName: locale === "en" ? "Chamber" : "Daire",
      flex: 1.2,
      minWidth: 190,
      renderCell: (params) => (
        <div className="feedback-grid-cell">
          <strong>{params.row.chamber ?? "-"}</strong>
          <span>{params.row.court}</span>
        </div>
      )
    },
    {
      field: "docketNo",
      headerName: locale === "en" ? "Docket No" : "Esas No",
      minWidth: 150,
      flex: 0.8,
      renderCell: (params) => <span className="feedback-owner">{params.row.docketNo ?? "-"}</span>
    },
    {
      field: "decisionNo",
      headerName: locale === "en" ? "Decision No" : "Karar No",
      minWidth: 150,
      flex: 0.8,
      renderCell: (params) => <span className="feedback-owner">{params.row.decisionNo ?? "-"}</span>
    },
    {
      field: "date",
      headerName: locale === "en" ? "Date" : "Tarih",
      minWidth: 130,
      flex: 0.65,
      renderCell: (params) => <span>{params.row.date ?? "-"}</span>
    },
    {
      field: "actions",
      headerName: locale === "en" ? "Open" : "Ac",
      width: 130,
      sortable: false,
      filterable: false,
      renderCell: (params) => (
        <button className="secondary-button" onClick={(event) => {
          event.stopPropagation();
          openPrecedent(params.row.rowIndex);
        }} type="button">
          {loading === "precedent-detail" && precedentDetailOpen && selectedPrecedentIndex === params.row.rowIndex ? <LoaderCircle className="spin" size={15} /> : null}
          {locale === "en" ? "Open" : "Ac"}
        </button>
      )
    }
  ], [loading, locale, precedentDetailOpen, selectedPrecedentIndex]);

  const precedentSources = useMemo(() => [
    { key: "all" as const, label: locale === "en" ? "All Decisions" : "Tum Kararlar", court: "" },
    { key: "yargitay" as const, label: "Yargitay", court: "Yargitay" },
    { key: "danistay" as const, label: "Danistay", court: "Danistay" },
    { key: "aym" as const, label: locale === "en" ? "Constitutional Court" : "Anayasa Mahkemesi", court: "AYM" }
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
    "Tapu iptali ve tescil davalarinda muris muvazaasi iddiasinin ispat kriterleri"
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

  function fillAdminUserForm(user: AuthUser) {
    setAdminUserForm({
      name: user.name,
      email: user.email,
      role: user.role === "ADMIN" ? "ADMIN" : "USER",
      verified: Boolean(user.verified),
      password: ""
    });
  }

  function openCreateAdminUser() {
    setAdminUsersError("");
    setSelectedAdminUserId(null);
    setSelectedAdminUser(null);
    setAdminUserForm(emptyAdminUserForm());
    setAdminUserView("create");
  }

  async function openAdminUser(id: string) {
    setAdminUsersError("");
    try {
      const user = await getUser(id);
      setSelectedAdminUserId(user.id);
      setSelectedAdminUser(user);
      fillAdminUserForm(user);
      setAdminUserView("detail");
    } catch (err) {
      setAdminUsersError(err instanceof Error ? err.message : "Kullanici acilamadi.");
    }
  }

  async function handleSaveAdminUser(event: FormEvent) {
    event.preventDefault();
    setAdminUserSaving(true);
    setAdminUsersError("");
    try {
      if (adminUserView === "create") {
        if (adminUserForm.password.trim().length < 10) {
          throw new Error(locale === "en" ? "Password must be at least 10 characters." : "Sifre en az 10 karakter olmali.");
        }
        const created = await createUser({
          name: adminUserForm.name.trim(),
          email: adminUserForm.email.trim(),
          role: adminUserForm.role,
          verified: adminUserForm.verified,
          password: adminUserForm.password
        });
        const users = await listUsers();
        setAdminUsers(users);
        setSelectedAdminUserId(created.id);
        setSelectedAdminUser(created);
        fillAdminUserForm(created);
        setAdminUserView("detail");
      } else if (selectedAdminUserId) {
        const payload = {
          name: adminUserForm.name.trim(),
          email: adminUserForm.email.trim(),
          role: adminUserForm.role,
          verified: adminUserForm.verified,
          ...(adminUserForm.password.trim() ? { password: adminUserForm.password } : {})
        };
        const updated = await updateUser(selectedAdminUserId, payload);
        const users = await listUsers();
        setAdminUsers(users);
        setSelectedAdminUser(updated);
        fillAdminUserForm(updated);
        setAdminUserForm((current) => ({ ...current, password: "" }));
      }
    } catch (err) {
      setAdminUsersError(err instanceof Error ? err.message : "Kullanici kaydedilemedi.");
    } finally {
      setAdminUserSaving(false);
    }
  }

  async function handleDeleteAdminUser() {
    if (!selectedAdminUserId || !selectedAdminUser) return;
    const confirmMessage = locale === "en"
      ? `Delete user ${selectedAdminUser.email}?`
      : `${selectedAdminUser.email} kullanicisi silinsin mi?`;
    if (!window.confirm(confirmMessage)) return;

    setAdminUserSaving(true);
    setAdminUsersError("");
    try {
      await deleteUser(selectedAdminUserId);
      const users = await listUsers();
      setAdminUsers(users);
      setSelectedAdminUserId(null);
      setSelectedAdminUser(null);
      setAdminUserForm(emptyAdminUserForm());
      setAdminUserView("list");
    } catch (err) {
      setAdminUsersError(err instanceof Error ? err.message : "Kullanici silinemedi.");
    } finally {
      setAdminUserSaving(false);
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

  function buildResearchStepTemplate(): ResearchStep[] {
    return [
      { type: "PLAN_CREATED", label: t.tools.legalResearchStepPlan, status: "PENDING" },
      { type: "LEGISLATION_IN_PROGRESS", label: t.tools.legalResearchStepLegislationIn, status: "PENDING" },
      { type: "LEGISLATION_COMPLETED", label: t.tools.legalResearchStepLegislationDone, status: "PENDING" },
      { type: "CASE_LAW_IN_PROGRESS", label: t.tools.legalResearchStepCaseLawIn, status: "PENDING" },
      { type: "CASE_LAW_COMPLETED", label: t.tools.legalResearchStepCaseLawDone, status: "PENDING" },
      { type: "WEB_IN_PROGRESS", label: t.tools.legalResearchStepWebIn, status: "PENDING" },
      { type: "WEB_COMPLETED", label: t.tools.legalResearchStepWebDone, status: "PENDING" },
      { type: "FINAL_ANSWER", label: t.tools.legalResearchStepFinal, status: "PENDING" }
    ];
  }

  function updateResearchStep(step: ResearchStep) {
    setResearchSteps((current) => {
      const base = current.length ? current : buildResearchStepTemplate();
      const stepIndex = base.findIndex((item) => item.type === step.type);
      return base.map((item, index) => {
        if (item.type === step.type) {
          return { ...item, label: step.label, status: step.status };
        }
        if (stepIndex >= 0 && index < stepIndex && item.status !== "COMPLETED") {
          return { ...item, status: "COMPLETED" };
        }
        return item;
      });
    });
  }

  function submitLegalResearch() {
    const query = smartNoteDraft.trim();
    if (!query) {
      setError(t.tools.legalResearchEmpty);
      return;
    }

    const userMessage = query;
    setChatMessages((current) => [
      ...current,
      { id: crypto.randomUUID(), role: "user", text: userMessage }
    ]);
    setSmartNoteDraft("");
    setChatAttachment(null);
    setResearchSteps(buildResearchStepTemplate());

    void (async () => {
      setLoading("legal-research");
      setError("");
      try {
        const response = await streamLegalResearch(query, activeChatSessionId, updateResearchStep).catch(async () => {
          return runLegalResearch(query, activeChatSessionId);
        });
        setResearchSteps(response.steps.map((step) => ({
          type: step.type,
          label: step.label,
          status: step.status
        })));
        setChatResponse({
          answer: response.answer,
          citations: [],
          disclaimer: response.disclaimer,
          sessionId: response.sessionId ?? undefined
        });
        if (response.sessionId) {
          setActiveChatSessionId(response.sessionId);
        }
        setChatMessages((current) => [
          ...current,
          {
            id: crypto.randomUUID(),
            role: "assistant",
            text: response.answer,
            citations: [],
            disclaimer: response.disclaimer
          }
        ]);
        await loadChatSessions();
      } catch (err) {
        setResearchSteps([]);
        const message = err instanceof Error ? err.message : "Beklenmeyen hata";
        if (message.toLowerCase().includes("oturum") || message.includes("401")) {
          setAuthUser(null);
        }
        setError(message);
      } finally {
        setLoading("");
      }
    })();
  }

  function submitChat(event: FormEvent) {
    event.preventDefault();
    if (researchMode) {
      submitLegalResearch();
      return;
    }
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
      const response = await postJson<ChatResponse>("/chat", {
        question: buildSmartNotesPrompt(notesForPrompt, attachmentForPrompt),
        displayQuestion: userMessage,
        mode: "smart-notes",
        privateMode,
        sessionId: activeChatSessionId
      });
      setChatResponse(response);
      if (response.sessionId) {
        setActiveChatSessionId(response.sessionId);
      }
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
      await loadChatSessions();
    });
  }

  async function loadChatSessions() {
    setChatHistoryLoading(true);
    setChatHistoryError("");
    try {
      const sessions = await getJson<ChatHistorySession[]>("/chat/sessions");
      setChatSessions(sessions);
    } catch (error) {
      setChatHistoryError(error instanceof Error ? error.message : "Sohbet gecmisi yuklenemedi.");
    } finally {
      setChatHistoryLoading(false);
    }
  }

  async function openChatSession(sessionId: string) {
    setChatHistoryError("");
    try {
      const session = await getJson<ChatHistorySession>(`/chat/sessions/${sessionId}`);
      setActiveChatSessionId(session.id);
      setChatMessages(session.messages.map((message) => ({
        id: message.id,
        role: message.role,
        text: message.text,
        citations: message.citations ?? [],
        disclaimer: message.disclaimer ?? undefined
      })));
      const lastAssistant = [...session.messages].reverse().find((message) => message.role === "assistant");
      setChatResponse(lastAssistant ? {
        answer: lastAssistant.text,
        citations: lastAssistant.citations ?? [],
        disclaimer: lastAssistant.disclaimer ?? "",
        sessionId: session.id
      } : null);
      setChatHistoryOpen(false);
    } catch (error) {
      setChatHistoryError(error instanceof Error ? error.message : "Sohbet acilamadi.");
    }
  }

  function startNewChat() {
    setResearchSteps([]);
    setActiveChatSessionId(null);
    setChatMessages([]);
    setChatResponse(null);
    setSmartNoteDraft("");
    setChatAttachment(null);
    setChatHistoryError("");
    setChatHistoryOpen(false);
  }

  async function deleteChatSession(sessionId: string, event?: SyntheticEvent) {
    event?.stopPropagation();
    setChatHistoryError("");
    try {
      const sessions = await deleteJson<ChatHistorySession[]>(`/chat/sessions/${sessionId}`);
      setChatSessions(sessions);
      if (activeChatSessionId === sessionId) {
        startNewChat();
      }
    } catch (error) {
      setChatHistoryError(error instanceof Error ? error.message : "Sohbet silinemedi.");
    }
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

  function executePrecedentSearch(query = searchQuery, court = searchCourt, sourceKey = precedentSource) {
    const normalizedQuery = query.trim();
    const advanced = caseLawSearchMode === "advanced";
    const advancedPayload = {
      chamber: advancedSearch.chamber.trim() || undefined,
      docketNo: advancedSearch.docketNo.trim() || undefined,
      decisionNo: advancedSearch.decisionNo.trim() || undefined,
      dateFrom: advancedSearch.dateFrom || undefined,
      dateTo: advancedSearch.dateTo || undefined
    };
    const hasAdvancedCriteria = Boolean(
      advancedPayload.chamber
      || advancedPayload.docketNo
      || advancedPayload.decisionNo
      || advancedPayload.dateFrom
      || advancedPayload.dateTo
    );
    setPrecedents([]);
    setSelectedPrecedentIndex(null);
    setPrecedentDetailOpen(false);
    setPrecedentSummaries({});
    if (!normalizedQuery && (!advanced || !hasAdvancedCriteria)) {
      setError(
        advanced
          ? (locale === "en" ? "Enter keywords or at least one advanced filter." : "Anahtar kelime veya en az bir gelismis filtre girin.")
          : (locale === "en" ? "Enter a query to search." : "Arama yapmak icin sorgu girin.")
      );
      return;
    }
    const sourceHint = precedentSources.find((item) => item.key === sourceKey);
    const effectiveCourt = court || sourceHint?.court || "";
    run("search", async () => {
      const data = await postJson<{ results: Precedent[] }>("/precedents/yargitay/search", {
        query: normalizedQuery,
        court: effectiveCourt || undefined,
        ...(advanced ? advancedPayload : {}),
        limit: 20
      });
      setPrecedents(data.results);
      setSelectedPrecedentIndex(data.results.length ? 0 : null);
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
    executePrecedentSearch(example, searchCourt, precedentSource);
  }

  function getPrecedentDetailPath(item: Precedent) {
    const court = item.court.toLowerCase();
    if (court.includes("yarg")) {
      return `/precedents/yargitay/${encodeURIComponent(item.sourceId ?? "")}`;
    }
    if (court.includes("danis")) {
      return `/precedents/danistay/${encodeURIComponent(item.sourceId ?? "")}`;
    }
    if (court.includes("anayasa") || court.includes("aym")) {
      const rawId = item.sourceId ?? "";
      const normalizedId = rawId.startsWith("BB/") ? rawId.slice(3) : rawId;
      const [year, number] = normalizedId.split("/");
      if (!year || !number) {
        return null;
      }
      return `/precedents/aym/${encodeURIComponent(year)}/${encodeURIComponent(number)}`;
    }
    return null;
  }

  function getCourtDisplayName(court: string) {
    const normalized = court.toLowerCase();
    if (normalized.includes("yarg")) return locale === "en" ? "Court of Cassation" : "Yargitay";
    if (normalized.includes("danis")) return locale === "en" ? "Council of State" : "Danistay";
    if (normalized.includes("anayasa") || normalized.includes("aym")) return locale === "en" ? "Constitutional Court" : "Anayasa Mahkemesi";
    return court || "-";
  }

  function mergePrecedentDetail(base: Precedent, detail: Precedent): Precedent {
    return {
      ...base,
      ...detail,
      court: detail.court || base.court,
      chamber: detail.chamber || base.chamber,
      docketNo: detail.docketNo || base.docketNo,
      decisionNo: detail.decisionNo || base.decisionNo,
      date: detail.date || base.date,
      topic: detail.topic || base.topic,
      summary: detail.summary || base.summary,
      content: detail.content || base.content
    };
  }

  function hasPrecedentDetailText(item: Precedent) {
    const content = item.content?.trim() ?? "";
    const summary = item.summary?.trim() ?? "";
    return content.length >= 80 && content.length > summary.length + 40;
  }

  async function ensurePrecedentDetailLoaded(index: number): Promise<Precedent | null> {
    const item = precedents[index];
    if (!item) {
      return null;
    }
    if (hasPrecedentDetailText(item)) {
      return item;
    }
    const detailPath = getPrecedentDetailPath(item);
    if (!detailPath) {
      const fallbackContent = item.content?.trim() || item.summary?.trim() || "";
      return fallbackContent.length >= 20 ? item : null;
    }
    const detail = await getJson<Precedent>(detailPath);
    const merged = mergePrecedentDetail(item, detail);
    setPrecedents((current) => current.map((entry, entryIndex) => entryIndex === index ? merged : entry));
    return merged;
  }

  function openPrecedent(index: number) {
    setSelectedPrecedentIndex(index);
    setPrecedentDetailOpen(true);
    const item = precedents[index];
    const detailPath = item ? getPrecedentDetailPath(item) : null;
    if (!item?.sourceId || item.content || !detailPath) {
      return;
    }
    run("precedent-detail", async () => {
      const detail = await getJson<Precedent>(detailPath);
      setPrecedents((current) => current.map((entry, entryIndex) => entryIndex === index ? mergePrecedentDetail(entry, detail) : entry));
    });
  }

  function closePrecedentDetail() {
    setPrecedentDetailOpen(false);
  }

  useEffect(() => {
    if (activeTab === "caseLaw") {
      setPrecedentSource("all");
      setSearchCourt("");
      setCaseLawSearchMode("simple");
    }
  }, [activeTab]);

  function buildPetitionContextFromBlocks(blocks: PetitionPrecedentBlock[]) {
    return blocks.map((block) => [
      `${block.caseLabel} / ${block.precedentTitle}`,
      `${t.tools.researchDocket}: ${block.citationLine}`,
      `${t.tools.petitionCaseContext}: ${block.factsLinkSnippet}`,
      block.applicationNote,
      `${t.tools.petitionDemands}: ${block.legalGroundsSnippet}`
    ].join("\n")).join("\n\n---\n\n");
  }

  function buildCaseContextPayload(caseRecord: CaseRecord) {
    const draftFacts = [caseRecord.subject, caseRecord.summary].filter(Boolean).join("\n\n");
    return {
      caseId: caseRecord.id,
      caseType: caseRecord.caseType,
      caseLabel: caseRecord.caseLabel,
      clientName: caseRecord.clientName,
      opponentName: caseRecord.opponentName,
      courtName: caseRecord.courtName,
      subject: caseRecord.subject,
      summary: caseRecord.summary,
      petitionType: caseRecord.caseLabel || petition.petitionType,
      petitionFacts: draftFacts || petition.facts,
      petitionDemands: petition.demands
    };
  }

  function syncPetitionFromCase(caseRecord: CaseRecord) {
    const parties = [
      caseRecord.clientName ? `${t.tools.petitionApplicant}: ${caseRecord.clientName}` : "",
      caseRecord.opponentName ? `${t.tools.petitionOpponent}: ${caseRecord.opponentName}` : ""
    ].filter(Boolean).join("\n");
    const facts = [caseRecord.subject, caseRecord.summary].filter(Boolean).join("\n\n");
    setPetition({
      petitionType: caseRecord.caseLabel || petition.petitionType,
      court: caseRecord.courtName || petition.court,
      parties: parties || petition.parties,
      facts: facts || petition.facts,
      demands: petition.demands
    });
    setPetitionMethod("case");
  }

  function summarizeSelectedPrecedent() {
    if (!selectedPrecedent || selectedPrecedentIndex === null) {
      setError(t.tools.researchSummarySelectDecision);
      return;
    }
    const key = getPrecedentKey(selectedPrecedent, selectedPrecedentIndex);
    run("precedent-summary", async () => {
      const item = await ensurePrecedentDetailLoaded(selectedPrecedentIndex);
      if (!item) {
        throw new Error(t.tools.researchSummaryNoContent);
      }
      const content = item.content?.trim() || item.summary?.trim() || "";
      if (content.length < 20) {
        throw new Error(t.tools.researchSummaryNoContent);
      }
      const response = await summarizePrecedent({
        court: item.court,
        chamber: item.chamber,
        docketNo: item.docketNo,
        decisionNo: item.decisionNo,
        date: item.date,
        topic: item.topic,
        summary: item.summary,
        content
      });
      setPrecedentSummaries((current) => ({ ...current, [key]: response.summary }));
    });
  }

  function renderPrecedentDetailActions() {
    return (
      <>
        <section className="precedent-summary-box">
          <div className="section-head">
            <div>
              <span className="section-label">{t.tools.researchAiSummary}</span>
              <h3>{t.tools.researchSummarize}</h3>
            </div>
          </div>
          <button
            className="secondary-button"
            disabled={
              !selectedPrecedent
              || loading === "precedent-summary"
              || (loading === "precedent-detail" && !hasPrecedentDetailText(selectedPrecedent))
            }
            type="button"
            onClick={summarizeSelectedPrecedent}
          >
            {loading === "precedent-summary" ? <LoaderCircle className="spin" size={17} /> : <Bot size={17} />}
            {loading === "precedent-detail" && !hasPrecedentDetailText(selectedPrecedent) ? t.tools.researchSummaryLoadingDetail : t.tools.researchSummarize}
          </button>
          {selectedPrecedentSummary ? <pre>{selectedPrecedentSummary}</pre> : <p>{t.tools.researchSummaryHint}</p>}
        </section>

        <section className="precedent-use-box">
          <span className="section-label">{t.tools.researchUse}</span>
          <p>{t.tools.researchUseText}</p>
          <label className="field-label">
            {t.tools.researchUseLinkedCase}
            <select
              value={petitionLinkedCaseId ?? ""}
              onChange={(event) => {
                setPetitionApplySuccess("");
                setPetitionLinkedCaseId(event.target.value || null);
              }}
            >
              <option value="">{t.tools.petitionSelectCase}</option>
              {savedPetitionCases.map((caseRecord) => (
                <option key={caseRecord.id} value={caseRecord.id}>
                  {caseRecord.caseLabel} — {caseRecord.clientName} / {caseRecord.opponentName}
                </option>
              ))}
            </select>
          </label>
          {!savedPetitionCases.length ? <p>{t.tools.researchUseNoCases}</p> : null}
          {petitionApplySuccess ? <p className="auth-preview">{petitionApplySuccess}</p> : null}
          <button
            disabled={!selectedPrecedent || !linkedPetitionCase || loading === "precedent-apply"}
            type="button"
            onClick={addSelectedPrecedentToPetition}
          >
            {loading === "precedent-apply" ? <LoaderCircle className="spin" size={17} /> : <FileText size={17} />}
            {t.tools.researchUseApply}
          </button>
        </section>
      </>
    );
  }

  function addSelectedPrecedentToPetition() {
    if (!selectedPrecedent || selectedPrecedentIndex === null) {
      setError(t.tools.researchSummarySelectDecision);
      return;
    }
    if (!linkedPetitionCase) {
      setError(t.tools.researchUseSelectCase);
      return;
    }
    const key = getPrecedentKey(selectedPrecedent, selectedPrecedentIndex);
    run("precedent-apply", async () => {
      setPetitionApplySuccess("");
      const item = await ensurePrecedentDetailLoaded(selectedPrecedentIndex);
      if (!item) {
        throw new Error(t.tools.researchSummaryNoContent);
      }
      const content = item.content?.trim() || item.summary?.trim() || "";
      if (content.length < 20) {
        throw new Error(t.tools.researchSummaryNoContent);
      }
      const response = await applyPrecedentToPetition({
        court: item.court,
        chamber: item.chamber,
        docketNo: item.docketNo,
        decisionNo: item.decisionNo,
        date: item.date,
        topic: item.topic,
        summary: item.summary,
        content,
        aiSummary: selectedPrecedentSummary || null,
        caseContext: buildCaseContextPayload(linkedPetitionCase)
      });
      const draftFacts = [linkedPetitionCase.subject, linkedPetitionCase.summary].filter(Boolean).join("\n\n");
      const draftParties = [
        linkedPetitionCase.clientName ? `${t.tools.petitionApplicant}: ${linkedPetitionCase.clientName}` : "",
        linkedPetitionCase.opponentName ? `${t.tools.petitionOpponent}: ${linkedPetitionCase.opponentName}` : ""
      ].filter(Boolean).join("\n");
      const block: PetitionPrecedentBlock = {
        id: `${key}:${linkedPetitionCase.id}:${Date.now()}`,
        caseId: linkedPetitionCase.id,
        caseLabel: linkedPetitionCase.caseLabel,
        precedentKey: key,
        precedentTitle: item.topic,
        citationLine: response.citationLine,
        applicationNote: response.applicationNote,
        legalGroundsSnippet: response.legalGroundsSnippet,
        factsLinkSnippet: response.factsLinkSnippet
      };
      const nextBlocks = [
        ...petitionPrecedentBlocks.filter((entry) => !(entry.caseId === block.caseId && entry.precedentKey === block.precedentKey)),
        block
      ];
      setPetitionPrecedentBlocks(nextBlocks);
      setPetitionContext(buildPetitionContextFromBlocks(nextBlocks));
      setPetition({
        petitionType: linkedPetitionCase.caseLabel || petition.petitionType,
        court: linkedPetitionCase.courtName || petition.court,
        parties: draftParties || petition.parties,
        facts: [draftFacts, response.factsLinkSnippet].filter(Boolean).join("\n\n"),
        demands: [petition.demands.trim(), response.legalGroundsSnippet].filter(Boolean).join("\n\n")
      });
      setPetitionMethod("case");
      setPetitionApplySuccess(t.tools.researchUseSuccess);
      setActiveTab("petition");
    });
  }

  function submitPetition(event: FormEvent) {
    event.preventDefault();
    const linkedPrecedents = petitionPrecedentBlocks.filter((block) => block.caseId === petitionLinkedCaseId);
    const precedentContext = [
      linkedPrecedents.length ? buildPetitionContextFromBlocks(linkedPrecedents) : "",
      petitionContext.trim()
    ].filter(Boolean).join("\n\n---\n\n").trim();
    const supplementaryContext = [
      linkedPetitionCase
        ? [
            `${t.tools.petitionLinkedCase}: ${linkedPetitionCase.caseLabel}`,
            linkedPetitionCase.subject ? `${t.tools.petitionCaseContext}: ${linkedPetitionCase.subject}` : "",
            linkedPetitionCase.summary ? linkedPetitionCase.summary : ""
          ].filter(Boolean).join("\n")
        : "",
      petitionContextSources.upload ? `${t.tools.petitionUploadContext}: evet` : "",
      petitionContextSources.existing ? `${t.tools.petitionExistingDocs}: evet` : "",
      petitionModel === "premium" ? t.tools.petitionPremiumDesc : ""
    ].filter(Boolean).join("\n\n").trim();
    const enrichedPetition = {
      ...petition,
      facts: petition.facts.trim(),
      demands: petition.demands.trim(),
      precedentContext: precedentContext || null,
      supplementaryContext: supplementaryContext || null
    };
    run("petition", async () => {
      setPetitionEditPreview(null);
      setPetitionResult(null);
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

  async function exportPetition(format: "docx" | "udf") {
    if (!petitionResult) return;
    const fileName = sanitizePetitionFileName(petitionResult.title || "dilekce");
    try {
      const blob =
        format === "docx"
          ? await buildPetitionDocxBlob(petitionResult.body)
          : new Blob([petitionResult.body], { type: "text/plain;charset=utf-8" });
      const extension = format === "docx" ? "docx" : "udf";
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `${fileName}.${extension}`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      setError(error instanceof Error ? error.message : t.tools.petitionExportFailed);
    }
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

  function seedAdminCases() {
    run("admin-cases", async () => {
      await seedSamples<CaseRecord[]>("/cases/seed-samples");
    });
  }

  function refreshAdminFeedback() {
    setFeedbackLoaded(false);
    void loadFeedbackHistory();
  }

  async function handleGoogleCredential(response: GoogleCredentialResponse) {
    setGoogleAuthError("");
    setAuthError("");
    if (!response.credential) {
      setGoogleAuthError("Google oturumu baslatilamadi.");
      return;
    }
    setAuthLoading(true);
    try {
      const session = await authGoogle({ credential: response.credential });
      setAuthUser(session.user);
      setAuthPreview(null);
      setActiveTab("chat");
    } catch (error) {
      setGoogleAuthError(error instanceof Error ? error.message : "Google ile giris basarisiz.");
    } finally {
      setAuthLoading(false);
    }
  }

  async function submitAuth(event: FormEvent) {
    event.preventDefault();
    setAuthError("");
    setGoogleAuthError("");
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
        if (!authForm.email.trim()) throw new Error("E-posta gerekli.");
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(authForm.email.trim())) throw new Error("Gecerli bir e-posta adresi girin.");
        if (authForm.password !== authForm.confirmPassword) throw new Error("Sifreler eslesmiyor.");
        // Enforce same password policy as backend: minimum 10 characters
        if (!authForm.password || authForm.password.length < 10) throw new Error("Sifre en az 10 karakter olmali.");
        const result = await authRegister({
          name: authForm.name.trim(),
          email: authForm.email.trim(),
          password: authForm.password
        });
        setRegisterResult(result);
        setAuthPreview(null);
        // stay on auth screen and show verification instructions
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
      setOpenNavGroup(null);
      setAuthPreview(null);
      setAuthMode("login");
      setChatResponse(null);
      setChatMessages([]);
      setChatSessions([]);
      setActiveChatSessionId(null);
      setChatHistoryError("");
      setPrecedents([]);
      setPetitionResult(null);
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
      setAdminUserForm(emptyAdminUserForm());
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

  const authTabIndex = authMode === "login" ? 0 : authMode === "register" ? 1 : 2;

  if (!authReady) {
    return (
      <main className="auth-shell">
        <div className="auth-ambient" aria-hidden="true">
          <span className="auth-orb auth-orb-1" />
          <span className="auth-orb auth-orb-2" />
          <span className="auth-orb auth-orb-3" />
        </div>
        <div className="auth-page-wrap auth-page-wrap-centered">
          <section className="auth-card auth-loading-card panel auth-card-animated">
            <div className="auth-brand-icon auth-brand-icon-loading">
              <Scale size={28} />
            </div>
            <LoaderCircle className="spin" size={28} />
            <div>
              <h1>LawAI Studio</h1>
              <p>{t.auth.loading}</p>
            </div>
          </section>
        </div>
      </main>
    );
  }

  if (!authUser) {
    return (
      <main className="auth-shell">
        <div className="auth-ambient" aria-hidden="true">
          <span className="auth-orb auth-orb-1" />
          <span className="auth-orb auth-orb-2" />
          <span className="auth-orb auth-orb-3" />
          <span className="auth-grid-glow" />
        </div>

        <div className="auth-page-wrap auth-page-wrap-centered">
          <section className="auth-card auth-card-centered panel auth-card-animated">
            <div className="auth-card-header auth-animate-in" style={{ animationDelay: "0.05s" }}>
              <div className="auth-topbar-brand">
                <div className="auth-brand-icon">
                  <Scale size={22} />
                </div>
                <div>
                  <strong>LawAI Studio</strong>
                  <span>{t.auth.heroEyebrow}</span>
                </div>
              </div>
            </div>

            <form className="auth-form" onSubmit={submitAuth}>
              <div className="auth-form-head auth-animate-in" style={{ animationDelay: "0.12s" }}>
                <h2 key={authMode}>{t.auth.title[authMode]}</h2>
                <p>{t.auth.description[authMode]}</p>
              </div>

              <div
                className="auth-switch auth-animate-in"
                role="tablist"
                aria-label="LawAI Studio"
                style={{ "--auth-tab-index": authTabIndex, animationDelay: "0.18s" } as React.CSSProperties}
              >
                <span aria-hidden="true" className="auth-switch-indicator" />
                <button type="button" role="tab" aria-selected={authMode === "login"} className={authMode === "login" ? "active" : ""} onClick={() => setAuthMode("login")}>
                  {t.auth.tabs.login}
                </button>
                <button type="button" role="tab" aria-selected={authMode === "register"} className={authMode === "register" ? "active" : ""} onClick={() => setAuthMode("register")}>
                  {t.auth.tabs.register}
                </button>
                <button type="button" role="tab" aria-selected={authMode === "forgot"} className={authMode === "forgot" ? "active" : ""} onClick={() => setAuthMode("forgot")}>
                  {t.auth.tabs.forgot}
                </button>
              </div>

              <div key={authMode} className="auth-mode-panel">
                {authMode !== "forgot" ? (
                  <div className="google-auth-panel">
                    <span className="auth-google-label">
                      {authMode === "register" ? t.auth.google.register : t.auth.google.login}
                    </span>
                    {googleClientId ? (
                      <div className="google-auth-button" ref={googleButtonRef} />
                    ) : (
                      <p className="auth-note auth-note-compact">Google girisi icin NEXT_PUBLIC_GOOGLE_CLIENT_ID ayarlayin.</p>
                    )}
                    {googleAuthError ? <div className="inline-error">{googleAuthError}</div> : null}
                    <div className="auth-divider"><span>{t.auth.divider}</span></div>
                  </div>
                ) : null}

                <div className="auth-fields">
                  {authMode === "register" && (
                    <label className="field-label auth-field-animate" style={{ animationDelay: "0.04s" }}>
                      {t.auth.labels.name}
                      <input autoComplete="name" value={authForm.name} onChange={(event) => setAuthForm({ ...authForm, name: event.target.value })} />
                    </label>
                  )}
                  {(authMode === "login" || authMode === "register" || authMode === "forgot") && (
                    <label className="field-label auth-field-animate" style={{ animationDelay: authMode === "register" ? "0.08s" : "0.04s" }}>
                      {t.auth.labels.email}
                      <input autoComplete="email" type="email" placeholder="ornek@firma.com" value={authForm.email} onChange={(event) => setAuthForm({ ...authForm, email: event.target.value })} />
                    </label>
                  )}
                  {(authMode === "login" || authMode === "register") && (
                    <label className="field-label auth-field-animate" style={{ animationDelay: "0.12s" }}>
                      {t.auth.labels.password}
                      <input autoComplete={authMode === "login" ? "current-password" : "new-password"} placeholder="En az 10 karakter" type="password" value={authForm.password} onChange={(event) => setAuthForm({ ...authForm, password: event.target.value })} />
                    </label>
                  )}
                  {authMode === "register" && (
                    <label className="field-label auth-field-animate" style={{ animationDelay: "0.16s" }}>
                      {t.auth.labels.confirm}
                      <input autoComplete="new-password" type="password" value={authForm.confirmPassword} onChange={(event) => setAuthForm({ ...authForm, confirmPassword: event.target.value })} />
                    </label>
                  )}
                  {authMode === "login" && (
                    <div className="auth-form-meta auth-field-animate" style={{ animationDelay: "0.18s" }}>
                      <label className="remember-row">
                        <input type="checkbox" checked={authForm.rememberMe} onChange={(event) => setAuthForm({ ...authForm, rememberMe: event.target.checked })} />
                        <span>{t.auth.labels.remember}</span>
                      </label>
                      <button className="auth-inline-link" type="button" onClick={() => setAuthMode("forgot")}>
                        {t.auth.tabs.forgot}
                      </button>
                    </div>
                  )}
                </div>
              </div>

              {authPreview ? (
                <div className="auth-recovery-card auth-status-animate">
                  <CheckCircle2 size={18} />
                  <div>
                    <strong>{t.auth.recovery.resetTitle}</strong>
                    <span>{t.auth.recovery.resetHint}</span>
                  </div>
                </div>
              ) : null}
              {registerResult ? (
                <div className="auth-recovery-card auth-status-animate">
                  <CheckCircle2 size={18} />
                  <div>
                    <strong>{t.auth.recovery.registerTitle}</strong>
                    <span>{t.auth.recovery.registerHint}</span>
                  </div>
                </div>
              ) : null}
              {authError ? <div className="error auth-status-animate">{authError}</div> : null}

              <div className="auth-actions auth-animate-in" style={{ animationDelay: "0.24s" }}>
                <button className="auth-submit-button" disabled={authLoading} type="submit">
                  {authLoading ? <LoaderCircle className="spin" size={17} /> : null}
                  <span>{t.auth.labels[authMode]}</span>
                </button>
              </div>
            </form>
          </section>
        </div>
      </main>
    );
  }

  return (
    <main className={`app-shell ${sidebarCollapsed ? "sidebar-collapsed" : ""}`}>
      <AppSidebar
        activeTab={activeTab}
        authUser={authUser}
        collapsed={sidebarCollapsed}
        groups={navigationGroups}
        isChildActive={(item) => {
          if (item.id === "settings-view") {
            return activeTab === "settings" && settingsSection === "view";
          }
          if (item.id === "settings-account") {
            return activeTab === "settings" && settingsSection === "account";
          }
          if (item.id === "admin-users") {
            return activeTab === "admin" && adminSection === "users";
          }
          return item.tab === activeTab;
        }}
        locale={locale}
        onGroupNavigate={(group) => {
          if (group.tab) {
            setActiveTab(group.tab as Tab);
          }
        }}
        onItemNavigate={(item) => {
          item.onSelect?.();
          if (item.tab) {
            setActiveTab(item.tab as Tab);
          }
        }}
        onLogout={handleLogout}
        onToggleCollapsed={toggleSidebarCollapsed}
        onToggleNavGroup={(groupId) => {
          setOpenNavGroup((current) => (current === groupId ? null : groupId));
        }}
        openNavGroup={openNavGroup}
      />

      <section className="workspace">
        {error && <div className="error">{error}</div>}

        {activeTab === "chat" && (
          <section className="smart-notes-workspace law-chat-home">
            <div className="law-chat-orb law-chat-orb-one" aria-hidden="true" />
            <div className="law-chat-orb law-chat-orb-two" aria-hidden="true" />
            <div className={`law-chat-layout ${chatHistoryOpen ? "history-open" : ""}`}>
              {!chatHistoryOpen ? (
                <button className="law-chat-history-toggle" type="button" onClick={() => setChatHistoryOpen(true)}>
                  <ScrollText size={16} />
                  <span>Sohbet gecmisi</span>
                  <strong>{chatSessions.length}</strong>
                </button>
              ) : null}

              {chatHistoryOpen ? (
              <aside className="law-chat-history" aria-label="Sohbet gecmisi">
                <div className="law-chat-history-head">
                  <div>
                    <span>Sohbet gecmisi</span>
                    <strong>{chatSessions.length} sohbet</strong>
                  </div>
                  <div className="law-chat-history-actions">
                    <button className="law-chat-new" type="button" onClick={startNewChat}>
                      <MessageSquareMore size={16} />
                      Yeni
                    </button>
                    <button className="law-chat-history-close" type="button" onClick={() => setChatHistoryOpen(false)} aria-label="Sohbet gecmisini kapat">
                      <X size={16} />
                    </button>
                  </div>
                </div>
                {chatHistoryError ? <p className="law-chat-history-error">{chatHistoryError}</p> : null}
                {chatHistoryLoading ? (
                  <p className="law-chat-history-empty"><LoaderCircle className="spin" size={16} /> Yukleniyor</p>
                ) : chatSessions.length ? (
                  <div className="law-chat-history-list">
                    {chatSessions.map((session) => (
                      <article
                        key={session.id}
                        className={`law-chat-history-item ${activeChatSessionId === session.id ? "active" : ""}`}
                      >
                        <button
                          className="law-chat-history-select"
                          onClick={() => void openChatSession(session.id)}
                          type="button"
                        >
                          <span>{session.title}</span>
                          <small><Clock3 size={12} /> {formatDateTime(session.updatedAt, locale, "-")}</small>
                        </button>
                        <button
                          aria-label="Sohbeti sil"
                          className="law-chat-history-delete"
                          onClick={(event) => void deleteChatSession(session.id, event)}
                          type="button"
                        >
                          <X size={14} />
                        </button>
                      </article>
                    ))}
                  </div>
                ) : (
                  <p className="law-chat-history-empty">Henuz kayitli sohbet yok.</p>
                )}
              </aside>
              ) : null}

              <div className="law-chat-main">
                <header className="law-chat-hero">
                  <div className="law-chat-mark">
                    <div className="law-chat-mark-icon">
                      <Scale size={70} strokeWidth={1.45} />
                    </div>
                    <strong>LawAI</strong>
                  </div>
                  <p className="law-chat-kicker">
                    <Sparkles size={16} />
                    {researchMode ? "Planli arastirma, coklu kaynak, kapsamli hukuki analiz" : "Belgeli, hizli ve izlenebilir hukuk analizi"}
                  </p>
                  <h1>{researchMode ? "Hukuki arastirma asistanina sorun" : "Hos geldiniz, ne yapmak istersiniz?"}</h1>
                  <div className="law-chat-signals" aria-label="Asistan ozellikleri">
                    <span><ShieldCheck size={16} /> Gizli calisma modu</span>
                    <span><Clock3 size={16} /> {researchMode ? "Adim adim arastirma akisi" : "Dakikalar icinde on analiz"}</span>
                    <span><FileSearch size={16} /> {researchMode ? "Mevzuat, ictihat ve web" : "Belge destekli yanit"}</span>
                  </div>
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
                        <p>
                          <LoaderCircle className="spin" size={18} />
                          Cevap hazirlaniyor
                          <i className="typing-dots" aria-hidden="true"><b></b><b></b><b></b></i>
                        </p>
                      </article>
                    ) : null}
                    {loading === "legal-research" && researchSteps.length ? (
                      <section className="law-research-steps" aria-live="polite" aria-label={t.tools.legalResearchRunning}>
                        <header>
                          <LoaderCircle className="spin" size={18} />
                          <strong>{t.tools.legalResearchRunning}</strong>
                        </header>
                        <ol>
                          {researchSteps.map((step) => (
                            <li key={step.type} className={`law-research-step ${step.status.toLowerCase()}`}>
                              {step.status === "COMPLETED" ? <CheckCircle2 size={16} /> : <LoaderCircle className={step.status === "IN_PROGRESS" ? "spin" : ""} size={16} />}
                              <span>{step.label}</span>
                            </li>
                          ))}
                        </ol>
                      </section>
                    ) : null}
                    {chatResponse ? (
                      <button className="law-chat-download" type="button" onClick={downloadSmartNotesPdf}>
                        <FileText size={17} />
                        {t.tools.smartNotesDownload}
                      </button>
                    ) : null}
                  </section>
                ) : (
                  <section className="law-chat-prompts" aria-label="Ornek asistan kullanimlari">
                    <article>
                      <Scale size={20} />
                      <strong>Agir cezalar</strong>
                      <p>Mevzuat, ictihat ve web kaynaklarinda planli arastirma yap.</p>
                    </article>
                    <article>
                      <FileText size={20} />
                      <strong>Kira artis orani</strong>
                      <p>Guncel mevzuat ve Yargitay kararlarini birlikte incele.</p>
                    </article>
                    <article>
                      <MessageSquareMore size={20} />
                      <strong>Ise iade davasi</strong>
                      <p>Is hukuku konusunda kapsamli hukuki arastirma baslat.</p>
                    </article>
                  </section>
                )}

                <form className="law-chat-composer" onSubmit={submitChat}>
                  <div className="law-research-mode-toggle">
                    <button
                      className={`law-chat-tool ${researchMode ? "active" : ""}`}
                      type="button"
                      onClick={() => setResearchMode((current) => !current)}
                      title={t.tools.legalResearchModeHint}
                    >
                      <Search size={18} />
                      <span>{t.tools.legalResearchMode}</span>
                    </button>
                    {researchMode ? <small>{t.tools.legalResearchModeHint}</small> : null}
                  </div>
                  {!researchMode ? (
                  <input
                    ref={chatFileInputRef}
                    accept={acceptedExtensions.join(",")}
                    className="law-chat-file-input"
                    onChange={(event) => void attachChatDocument(event.target.files?.[0] ?? null)}
                    type="file"
                  />
                  ) : null}
                  <textarea
                    value={smartNoteDraft}
                    onChange={(event) => setSmartNoteDraft(event.target.value)}
                    placeholder={researchMode ? "Orn: agir cezalar, nitelikli dolandiricilik, ise iade davasi..." : "Bana bir soru sor..."}
                    rows={4}
                  />
                  {chatAttachment && !researchMode ? (
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
                      {!researchMode ? (
                      <button className="law-chat-tool" type="button" onClick={() => chatFileInputRef.current?.click()} disabled={chatAttachmentLoading} title="Belge ekle">
                        {chatAttachmentLoading ? <LoaderCircle className="spin" size={18} /> : <Upload size={18} />}
                        <span>Belge ekle</span>
                      </button>
                      ) : null}
                      <button className={`law-chat-tool ${voiceListening ? "active" : ""}`} type="button" onClick={() => void startVoiceInput()} title="Sesli ara">
                        <Mic size={18} />
                        <span>{voiceListening ? "Dinleniyor" : "Sesli ara"}</span>
                      </button>
                    </div>
                    <button
                      className="law-chat-send"
                      disabled={
                        loading === "chat"
                        || loading === "legal-research"
                        || chatAttachmentLoading
                        || (researchMode ? !smartNoteDraft.trim() : (!smartNoteDraft.trim() && !chatAttachment))
                      }
                      type="submit"
                    >
                      {loading === "chat" || loading === "legal-research" ? <LoaderCircle className="spin" size={22} /> : <Send size={22} />}
                    </button>
                  </div>
                  {voiceNotice ? <p className="law-chat-voice-notice">{voiceNotice}</p> : null}
                </form>
              </div>
            </div>
          </section>
        )}
        {activeTab === "caseLaw" && (
          <section className="precedent-research-workspace">
            <form className="precedent-search-hero" onSubmit={submitSearch}>
              <div className="precedent-brand-mark" aria-hidden="true">
                <Scale size={30} />
                <span>LawAI Studio</span>
              </div>
              <div className="precedent-hero-copy">
                <span className="eyebrow">{locale === "en" ? "Court of Cassation, Council of State and Constitutional Court" : "Yargitay, Danistay ve Anayasa Mahkemesi ictihat arama"}</span>
                <h1>{locale === "en" ? "Case-Law Search" : "Ictihat Arama"}</h1>
                <p>{locale === "en" ? "Enter the legal issue, fetch matching Court of Cassation, Council of State and Constitutional Court decisions, and open any record to read the full decision text." : "Aramaniza gore Yargitay, Danistay ve Anayasa Mahkemesi kararlarini getirir; listeden bir karara basinca tam karar metni acilir."}</p>
              </div>
              <section className="precedent-search-modes" aria-label={t.tools.researchMethods}>
                <span className="section-label">{t.tools.researchMethods}</span>
                <div className="precedent-mode-grid">
                  <button
                    className={caseLawSearchMode === "simple" ? "active" : ""}
                    type="button"
                    onClick={() => setCaseLawSearchMode("simple")}
                  >
                    <Search size={18} />
                    <strong>{t.tools.researchAiSearch}</strong>
                    <span>{t.tools.researchAiSearchDesc}</span>
                  </button>
                  <button
                    className={caseLawSearchMode === "advanced" ? "active" : ""}
                    type="button"
                    onClick={() => setCaseLawSearchMode("advanced")}
                  >
                    <SlidersHorizontal size={18} />
                    <strong>{t.tools.researchAdvancedSearch}</strong>
                    <span>{t.tools.researchManualSearchDesc}</span>
                  </button>
                </div>
              </section>
              <div className="precedent-source-chips" aria-label={t.tools.researchCourt}>
                {precedentSources.map((source) => (
                  <button
                    key={source.key}
                    className={precedentSource === source.key ? "active" : ""}
                    type="button"
                    onClick={() => selectPrecedentSource(source)}
                  >
                    {precedentSource === source.key ? <CheckCircle2 size={18} /> : null}
                    {source.label}
                  </button>
                ))}
              </div>
              {caseLawSearchMode === "advanced" ? (
                <section className="precedent-advanced-search">
                  <div className="precedent-advanced-grid">
                    <label className="field-label precedent-advanced-field precedent-advanced-field-wide">
                      {t.tools.researchKeywords}
                      <input
                        value={searchQuery}
                        onChange={(event) => setSearchQuery(event.target.value)}
                        placeholder={locale === "en" ? "e.g. property right, demolition decision" : "Orn: mulkiyet hakki, yikim karari"}
                      />
                    </label>
                    <label className="field-label precedent-advanced-field">
                      {t.tools.researchChamber}
                      <input
                        value={advancedSearch.chamber}
                        onChange={(event) => setAdvancedSearch({ ...advancedSearch, chamber: event.target.value })}
                        placeholder={locale === "en" ? "e.g. 2nd Civil Chamber" : "Orn: 2. Hukuk Dairesi"}
                      />
                    </label>
                    <label className="field-label precedent-advanced-field">
                      {t.tools.researchDocketEsas}
                      <input
                        value={advancedSearch.docketNo}
                        onChange={(event) => setAdvancedSearch({ ...advancedSearch, docketNo: event.target.value })}
                        placeholder="2024/1234"
                      />
                    </label>
                    <label className="field-label precedent-advanced-field">
                      {t.tools.researchDocketKarar}
                      <input
                        value={advancedSearch.decisionNo}
                        onChange={(event) => setAdvancedSearch({ ...advancedSearch, decisionNo: event.target.value })}
                        placeholder="2025/2402"
                      />
                    </label>
                    <label className="field-label precedent-advanced-field">
                      {t.tools.researchDateFrom}
                      <input
                        type="date"
                        value={advancedSearch.dateFrom}
                        onChange={(event) => setAdvancedSearch({ ...advancedSearch, dateFrom: event.target.value })}
                      />
                    </label>
                    <label className="field-label precedent-advanced-field">
                      {t.tools.researchDateTo}
                      <input
                        type="date"
                        value={advancedSearch.dateTo}
                        onChange={(event) => setAdvancedSearch({ ...advancedSearch, dateTo: event.target.value })}
                      />
                    </label>
                  </div>
                  <button className="precedent-advanced-submit" disabled={loading === "search"} type="submit">
                    {loading === "search" ? <LoaderCircle className="spin" size={18} /> : <Search size={18} />}
                    {t.tools.searchSubmit}
                  </button>
                </section>
              ) : (
                <>
                  <div className="precedent-hero-search">
                    <input
                      value={searchQuery}
                      onChange={(event) => setSearchQuery(event.target.value)}
                      placeholder={locale === "en" ? "Enter the legal issue or click a suggestion" : "Hukuki konuyu yazin veya ornek sorgu secin"}
                    />
                    <button aria-label={t.tools.searchSubmit} disabled={loading === "search"} type="submit">
                      {loading === "search" ? <LoaderCircle className="spin" size={28} /> : <Search size={30} />}
                    </button>
                  </div>
                  <section className="precedent-suggestions">
                    <span>{locale === "en" ? "Example queries:" : "Ornek sorgular:"}</span>
                    <div>
                      {precedentExampleQueries.map((example) => (
                        <button key={example} type="button" onClick={() => runExampleSearch(example)}>
                          <CheckCircle2 size={18} />
                          {example}
                        </button>
                      ))}
                    </div>
                  </section>
                </>
              )}
            </form>

            <section className="precedent-research-layout precedent-research-layout-wide">
              <section className="panel precedent-results-panel">
                <div className="section-head precedent-results-head">
                  <div>
                    <span className="section-label">{t.tools.researchMaxResults}</span>
                    <h3>{t.tools.searchResults}</h3>
                  </div>
                  <strong className="precedent-count-pill">{precedents.length} {t.tools.records}</strong>
                </div>
                {precedents.length ? (
                  <div className="feedback-datagrid-wrap precedent-datagrid-wrap">
                    <DataGrid
                      autoHeight
                      rows={caseLawRows}
                      columns={caseLawColumns}
                      disableRowSelectionOnClick
                      onRowClick={(params) => openPrecedent(params.row.rowIndex)}
                      initialState={{ pagination: { paginationModel: { page: 0, pageSize: 10 } } }}
                      pageSizeOptions={[10, 20, 50]}
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
                ) : <EmptyState text={t.tools.searchEmpty} />}
              </section>
            </section>
          </section>
        )}

        {activeTab === "petition" && (
          <section className="petition-assistant-workspace">
            <header className="petition-hero panel">
              <div className="petition-hero-copy petition-animate-in">
                <div className="petition-brand-mark">
                  <ScrollText size={20} />
                  <span>{t.tabs.petition}</span>
                </div>
                <h1>{t.tools.petitionAssistantTitle}</h1>
                <p>{t.tools.petitionAssistantSubtitle}</p>
              </div>
              <div className="petition-hero-side petition-animate-in" style={{ animationDelay: "0.08s" }}>
                <div className="petition-stat-card">
                  <span>{t.tools.petitionLinkedCase}</span>
                  <strong>{linkedPetitionCase?.caseLabel ?? t.tools.petitionCaseNotSelected}</strong>
                </div>
                <div className="petition-stat-card">
                  <span>{t.tools.petitionLinkedPrecedents}</span>
                  <strong>{petitionPrecedentBlocks.filter((block) => block.caseId === petitionLinkedCaseId).length}</strong>
                </div>
              </div>
            </header>

            <section className="petition-assistant-layout">
              <form className="panel petition-builder-panel petition-animate-in" style={{ animationDelay: "0.12s" }} onSubmit={submitPetition}>
                <div className="petition-panel-head">
                  <PanelTitle icon={<FileText size={20} />} title={t.tools.petitionTitle} />
                  <p>{t.tools.petitionBuilderHint}</p>
                </div>

                <section className="petition-section">
                  <div className="petition-section-head">
                    <BriefcaseBusiness size={18} />
                    <h3>{t.tools.petitionCaseSection}</h3>
                  </div>

                  <div className="petition-form-grid">
                    <label className="field-label">
                      {t.tools.petitionLinkedCase}
                      <select
                        value={petitionLinkedCaseId ?? ""}
                        onChange={(event) => setPetitionLinkedCaseId(event.target.value || null)}
                      >
                        <option value="">{t.tools.petitionSelectCase}</option>
                        {savedPetitionCases.map((caseRecord) => (
                          <option key={caseRecord.id} value={caseRecord.id}>
                            {caseRecord.caseLabel} — {caseRecord.clientName} / {caseRecord.opponentName}
                          </option>
                        ))}
                      </select>
                    </label>
                    <div className="petition-sync-case-row">
                      <button
                        className="secondary-button"
                        disabled={!linkedPetitionCase}
                        type="button"
                        onClick={() => linkedPetitionCase && syncPetitionFromCase(linkedPetitionCase)}
                      >
                        {t.tools.petitionSyncCase}
                      </button>
                    </div>
                  </div>

                  {linkedPetitionCase ? (
                    <div className="petition-linked-case-card">
                      <strong>{linkedPetitionCase.caseLabel}</strong>
                      <span>{linkedPetitionCase.subject}</span>
                      <small>{linkedPetitionCase.clientName} / {linkedPetitionCase.opponentName} — {linkedPetitionCase.courtName}</small>
                    </div>
                  ) : null}

                  <div className="petition-linked-precedents">
                    <span className="section-label">{t.tools.petitionLinkedPrecedents}</span>
                    {petitionPrecedentBlocks.filter((block) => block.caseId === petitionLinkedCaseId).length ? (
                      <ul>
                        {petitionPrecedentBlocks
                          .filter((block) => block.caseId === petitionLinkedCaseId)
                          .map((block) => (
                            <li key={block.id}>
                              <strong>{block.precedentTitle}</strong>
                              <span>{block.citationLine}</span>
                            </li>
                          ))}
                      </ul>
                    ) : (
                      <p className="petition-muted-copy">{t.tools.petitionLinkedPrecedentsEmpty}</p>
                    )}
                  </div>

                  <label className="field-label">
                    {t.tools.petitionCaseContext}
                    <textarea
                      rows={5}
                      value={petitionContext}
                      onChange={(event) => setPetitionContext(event.target.value)}
                      placeholder={t.tools.petitionCaseContextPlaceholder}
                    />
                  </label>
                </section>

                <section className="petition-section">
                  <div className="petition-section-head">
                    <ScrollText size={18} />
                    <h3>{t.tools.petitionDetailsSection}</h3>
                  </div>

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
                </section>

                <div className="petition-submit-row">
                  <button className="petition-submit-button" disabled={loading === "petition"} type="submit">
                    {loading === "petition" ? <LoaderCircle className="spin" size={17} /> : <Sparkles size={17} />}
                    {t.tools.petitionSubmit}
                  </button>
                </div>
              </form>

              <section className="petition-draft-panel petition-animate-in" style={{ animationDelay: "0.18s" }}>
                <header className="petition-draft-header">
                  <div className="petition-draft-identity">
                    <div className="petition-draft-badge" aria-hidden="true">
                      <ScrollText size={18} />
                    </div>
                    <div className="petition-draft-copy">
                      <span className="petition-draft-eyebrow">{t.tools.petitionDraft}</span>
                      <h3 className="petition-draft-title">{petitionResult?.title ?? t.tools.petitionDraftPlaceholder}</h3>
                    </div>
                  </div>
                  <div className="petition-export-group" role="group" aria-label={t.tools.petitionExport}>
                    <button className="petition-export-btn" disabled={!petitionResult} type="button" onClick={() => exportPetition("docx")}>
                      <FileText size={15} />
                      <span>{t.tools.petitionWord}</span>
                    </button>
                    <button className="petition-export-btn" disabled={!petitionResult} type="button" onClick={printPetitionPdf}>
                      <Printer size={15} />
                      <span>{t.tools.petitionPdf}</span>
                    </button>
                    <button className="petition-export-btn" disabled={!petitionResult} type="button" onClick={() => exportPetition("udf")}>
                      <FileUp size={15} />
                      <span>{t.tools.petitionUdf}</span>
                    </button>
                  </div>
                </header>

                {petitionResult ? (
                  <div className="petition-draft-stats" aria-live="polite">
                    <span>
                      <strong>{petitionResult.body.split(/\r?\n/).length}</strong> {t.tools.petitionLineCount}
                    </span>
                    <span>
                      <strong>{petitionResult.body.length}</strong> {t.tools.petitionCharCount}
                    </span>
                  </div>
                ) : null}

                <div className={`petition-draft-surface ${petitionResult ? "is-filled" : "is-empty"}`}>
                  {petitionResult ? (
                    <textarea
                      className="petition-draft-textarea"
                      value={petitionResult.body}
                      onChange={(event) => setPetitionResult({ ...petitionResult, body: event.target.value })}
                      onSelect={selectedTextFromEvent}
                      spellCheck={false}
                    />
                  ) : (
                    <div className="petition-empty-state">
                      <div className="petition-empty-visual" aria-hidden="true">
                        <div className="petition-empty-sheet petition-empty-sheet-back" />
                        <div className="petition-empty-sheet petition-empty-sheet-front">
                          <ScrollText size={22} strokeWidth={1.6} />
                        </div>
                      </div>
                      <p className="petition-empty-title">{t.tools.petitionDraft}</p>
                      <p className="petition-empty-copy">{t.tools.petitionEmpty}</p>
                    </div>
                  )}
                </div>
              </section>
            </section>
          </section>
        )}

        <Drawer
          anchor="right"
          onClose={closePrecedentDetail}
          open={activeTab === "caseLaw" && precedentDetailOpen && Boolean(selectedPrecedent)}
          slotProps={{ paper: { className: "precedent-detail-drawer" } }}
        >
          <div className="precedent-detail-drawer-inner">
            <div className="section-head precedent-detail-drawer-head">
              <div>
                <span className="section-label">{t.tools.researchDetails}</span>
                <h3>{selectedPrecedent?.topic ?? t.tools.researchDetails}</h3>
              </div>
              <button className="secondary-button" type="button" onClick={closePrecedentDetail}>
                <X size={16} />
                {locale === "en" ? "Close" : "Kapat"}
              </button>
            </div>

            {selectedPrecedent ? (
              <section className="precedent-detail-box">
                <TableContainer component={Paper} className="precedent-detail-table" elevation={0}>
                  <Table size="small" aria-label={t.tools.researchDetails}>
                    <TableHead>
                      <TableRow>
                        <TableCell>{locale === "en" ? "Field" : "Alan"}</TableCell>
                        <TableCell>{locale === "en" ? "Value" : "Deger"}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {selectedPrecedentDetailRows.map((row) => (
                        <TableRow key={row.label} hover>
                          <TableCell component="th" scope="row">{row.label}</TableCell>
                          <TableCell>{row.value}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
                <pre>{loading === "precedent-detail" && !selectedPrecedent.content ? (locale === "en" ? "Loading decision text..." : "Karar metni yukleniyor...") : (selectedPrecedent.content || selectedPrecedent.summary)}</pre>
              </section>
            ) : <EmptyState text={t.tools.searchEmpty} />}

            {selectedPrecedent ? renderPrecedentDetailActions() : null}
          </div>
        </Drawer>

        {activeTab === "cases" && <CasesPanel locale={locale} onGoToDocuments={() => setActiveTab("document")} />}

        {activeTab === "document" && <DocumentPanel locale={locale} loading={loading} run={run} onGoToChat={() => setActiveTab("chat")} />}

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
                      <button className="secondary-button" type="button" onClick={openCreateAdminUser}>
                        <Plus size={17} />
                        {locale === "en" ? "Add user" : "Kullanici ekle"}
                      </button>
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

                {adminSection === "users" && (adminUserView === "detail" || adminUserView === "create") && (
                  <article className="panel admin-card">
                    <div className="section-head">
                      <div>
                        <span className="section-label">
                          {adminUserView === "create"
                            ? (locale === "en" ? "New user" : "Yeni kullanici")
                            : (locale === "en" ? "User detail" : "Kullanici detayi")}
                        </span>
                        <h3>
                          {adminUserView === "create"
                            ? (locale === "en" ? "Add User" : "Kullanici Ekle")
                            : (selectedAdminUser?.name ?? "-")}
                        </h3>
                      </div>
                      <button className="secondary-button" type="button" onClick={() => setAdminUserView("list")}>
                        <ArrowLeft size={16} />
                        {locale === "en" ? "Back to list" : "Listeye don"}
                      </button>
                    </div>
                    {adminUsersError ? <div className="error">{adminUsersError}</div> : null}
                    <form className="settings-card" onSubmit={(event) => void handleSaveAdminUser(event)}>
                      {selectedAdminUser ? (
                        <div className="case-stats" style={{ marginBottom: "1rem" }}>
                          <div><span>ID</span><strong>{selectedAdminUser.id}</strong></div>
                          <div><span>{locale === "en" ? "Created" : "Olusturma"}</span><strong>{formatDateTime(selectedAdminUser.createdAt, locale, "-")}</strong></div>
                          <div><span>{locale === "en" ? "Last login" : "Son giris"}</span><strong>{formatDateTime(selectedAdminUser.lastLoginAt, locale, "-")}</strong></div>
                          <div><span>{locale === "en" ? "Verified at" : "Dogrulama"}</span><strong>{formatDateTime(selectedAdminUser.verifiedAt, locale, "-")}</strong></div>
                        </div>
                      ) : null}
                      <label className="field-label">
                        {locale === "en" ? "Name" : "Ad soyad"}
                        <input
                          required
                          value={adminUserForm.name}
                          onChange={(event) => setAdminUserForm((current) => ({ ...current, name: event.target.value }))}
                        />
                      </label>
                      <label className="field-label">
                        E-posta
                        <input
                          required
                          type="email"
                          value={adminUserForm.email}
                          onChange={(event) => setAdminUserForm((current) => ({ ...current, email: event.target.value }))}
                        />
                      </label>
                      <label className="field-label">
                        {locale === "en" ? "Role" : "Rol"}
                        <select
                          value={adminUserForm.role}
                          onChange={(event) => setAdminUserForm((current) => ({ ...current, role: event.target.value as "USER" | "ADMIN" }))}
                        >
                          <option value="USER">{locale === "en" ? "User" : "Kullanici"}</option>
                          <option value="ADMIN">{locale === "en" ? "Admin" : "Yonetici"}</option>
                        </select>
                      </label>
                      <label className="setting-row">
                        <div>
                          <strong>{locale === "en" ? "Email verified" : "E-posta dogrulandi"}</strong>
                          <span>{locale === "en" ? "Allow login without email verification" : "E-posta dogrulamasi olmadan giris izni"}</span>
                        </div>
                        <input
                          type="checkbox"
                          checked={adminUserForm.verified}
                          onChange={(event) => setAdminUserForm((current) => ({ ...current, verified: event.target.checked }))}
                        />
                      </label>
                      <label className="field-label">
                        {adminUserView === "create"
                          ? (locale === "en" ? "Password" : "Sifre")
                          : (locale === "en" ? "New password (optional)" : "Yeni sifre (istege bagli)")}
                        <input
                          type="password"
                          required={adminUserView === "create"}
                          minLength={adminUserView === "create" ? 10 : undefined}
                          value={adminUserForm.password}
                          onChange={(event) => setAdminUserForm((current) => ({ ...current, password: event.target.value }))}
                        />
                      </label>
                      <div className="admin-actions">
                        <button disabled={adminUserSaving} type="submit">
                          {adminUserSaving ? <LoaderCircle className="spin" size={17} /> : <Save size={17} />}
                          {adminUserView === "create"
                            ? (locale === "en" ? "Create user" : "Kullanici olustur")
                            : (locale === "en" ? "Save changes" : "Degisiklikleri kaydet")}
                        </button>
                        {adminUserView === "detail" && selectedAdminUserId && authUser?.id !== selectedAdminUserId ? (
                          <button className="secondary-button" disabled={adminUserSaving} type="button" onClick={() => void handleDeleteAdminUser()}>
                            <Trash2 size={17} />
                            {locale === "en" ? "Delete user" : "Kullaniciyi sil"}
                          </button>
                        ) : null}
                      </div>
                    </form>
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
  const uploadInFlightRef = useRef(false);
  const [file, setFile] = useState<File | null>(null);
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
  }

  function runUpload() {
    if (uploadInFlightRef.current) {
      return;
    }
    if (!file) {
      setLocalError(t.errors.selectFirst);
      return;
    }
    uploadInFlightRef.current = true;
    run("document-upload", async () => {
      try {
        const form = new FormData();
        form.append("file", file, file.name);
        setResult(await uploadMultipart<UploadResponse>("/upload", form));
      } finally {
        uploadInFlightRef.current = false;
      }
    });
  }

  const isBusy = Boolean(loading);
  const issues = result?.detectedIssues ?? result?.warnings ?? [];
  const indexedCount = result?.postgresChunks ?? result?.indexed ?? result?.chunkCount ?? 0;
  const ingestSuccess = result?.chunkCount != null && indexedCount > 0;

  return (
    <section className="pdf-upload-layout">
      <div className="panel primary-panel pdf-upload-form">
        <PanelTitle icon={<Upload size={20} />} title={t.title} />
        <p className="panel-subtitle">{t.subtitle}</p>
        <div className="document-info-card">
          <strong>{t.beforeUploadTitle}</strong>
          <ul>
            {t.beforeUploadItems.map((item) => <li key={item}>{item}</li>)}
          </ul>
        </div>
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
        <div className="upload-actions">
          <button disabled={!file || isBusy} onClick={runUpload} type="button">{loading === "document-upload" ? <LoaderCircle className="spin" size={17} /> : <Upload size={17} />}{t.ingest}</button>
        </div>
        {localError && <div className="inline-error"><AlertCircle size={18} /><span>{localError}</span></div>}
        <div className="document-pipeline">
          <div>
            <strong>{t.pipelineTitle}</strong>
            <p>{t.pipelineIntro}</p>
          </div>
          <ol>
            {t.pipelineSteps.map((step, index) => (
              <li key={step}>
                <span>{index + 1}</span>
                <p>{step}</p>
              </li>
            ))}
          </ol>
        </div>
      </div>
      <ResultPanel title={t.result}>
        {!result && !loading && <EmptyState text={t.empty} />}
        {loading && <div className="result-loading"><LoaderCircle className="spin" size={36} /><strong>{t.processing}</strong></div>}
        {result && !loading && (
          <div className="document-summary">
            {ingestSuccess && <div className="success-banner"><CheckCircle2 size={20} /><span>{t.pipelineCompleted} {t.indexedParts.replace("{count}", String(indexedCount))}</span></div>}
            <div className="result-stats">
              {result.documentId != null && <div><span>{t.documentId}</span><strong>{result.documentId}</strong></div>}
              <div><span>{t.file}</span><strong>{result.filename}</strong></div>
              {result.contentType && <div><span>{t.type}</span><strong>{result.contentType}</strong></div>}
              {result.size != null && <div><span>{t.size}</span><strong>{formatBytes(result.size)}</strong></div>}
              {result.extractedCharacters != null && <div><span>{t.extractedText}</span><strong>{result.extractedCharacters.toLocaleString(locale === "en" ? "en-US" : "tr-TR")} {t.characters}</strong></div>}
              {result.chunkCount != null && <div><span>{locale === "en" ? "Chunks" : "Chunk"}</span><strong>{result.chunkCount}</strong></div>}
              {result.postgresChunks != null && <div><span>{t.postgres}</span><strong>{result.postgresChunks}</strong></div>}
              {result.opensearchIndexed != null && <div><span>OpenSearch</span><strong>{result.opensearchIndexed}</strong></div>}
              {result.pgvectorEmbeddings != null && <div><span>pgvector</span><strong>{result.pgvectorEmbeddings}</strong></div>}
            </div>
            <p className="document-result-help">{t.resultHelp}</p>
            {result.message && <p>{result.message}</p>}
            {result.summary && (
              <div className="document-content-summary">
                <span>{t.contentSummary}</span>
                <p>{result.summary}</p>
              </div>
            )}
            {result.storedPath && <div className="stored-path"><span>{t.storedPath}</span><code>{result.storedPath}</code></div>}
            {result.textPreview && (
              <div className="document-text-preview">
                <span>{t.textPreview}</span>
                <pre>{result.textPreview}</pre>
              </div>
            )}
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








