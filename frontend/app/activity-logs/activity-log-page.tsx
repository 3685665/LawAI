"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import {
  AlertCircle,
  BarChart3,
  Bot,
  BriefcaseBusiness,
  ChevronRight,
  ClipboardList,
  CreditCard,
  FileUp,
  FileSearch,
  FileText,
  FolderOpen,
  KeyRound,
  LoaderCircle,
  MessageSquareMore,
  Palette,
  Scale,
  Settings,
  ShieldAlert,
  ScrollText,
  Upload,
  UserRound,
  UsersRound,
  type LucideIcon
} from "lucide-react";
import { getMessages, isLocale, localeToDateTag, type Locale } from "@/lib/i18n";
import {
  authLogout,
  authMe,
  createActivityLog,
  listActivityLogs,
  listMyActivityLogs,
  type ActivityLogRecord,
  type AuthUser
} from "@/lib/api";

type Mode = "user" | "admin";
type ActivityLogRow = ActivityLogRecord & {
  createdAtLabel: string;
  sourceLabel: string;
  userLabel: string;
};
type NavGroup = {
  id: string;
  label: string;
  icon: LucideIcon;
  href?: string;
  children?: NavItem[];
};
type NavItem = {
  id: string;
  label: string;
  icon: LucideIcon;
  href: string;
};

const dataGridSx = {
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
};

export function ActivityLogPage({ mode }: { mode: Mode }) {
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  const [locale, setLocale] = useState<Locale>("tr");
  const [loadingAuth, setLoadingAuth] = useState(true);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [openNavGroup, setOpenNavGroup] = useState<string | null>(mode === "admin" ? "admin" : "account");
  const [logs, setLogs] = useState<ActivityLogRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");

  useEffect(() => {
    const storedLocale = window.localStorage.getItem("lawai-locale");
    setLocale(isLocale(storedLocale) ? storedLocale : "tr");

    let cancelled = false;
    authMe()
      .then((user) => {
        if (!cancelled) setAuthUser(user);
      })
      .catch(() => {
        if (!cancelled) setAuthUser(null);
      })
      .finally(() => {
        if (!cancelled) setLoadingAuth(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const t = getMessages(locale);
  const isAdminMode = mode === "admin";
  const title = isAdminMode
    ? (locale === "en" ? "All user activity logs" : "Tum kullanici islem loglari")
    : (locale === "en" ? "Your activity history" : "Kendi islem gecmisiniz");

  useEffect(() => {
    if (!authUser) return;
    void createActivityLog({
      action: "screen-view",
      screen: isAdminMode ? "Islem Loglari" : "Kullanici Islemleri",
      detail: isAdminMode ? "Yonetici islem loglari ekrani goruntulendi." : "Kullanici islemleri ekrani goruntulendi.",
      path: window.location.pathname
    }).catch(() => undefined);
    void loadLogs();
  }, [authUser, isAdminMode]);

  const rows = useMemo<ActivityLogRow[]>(() => {
    const query = search.trim().toLowerCase();
    return logs
      .filter((item) => {
        if (!query) return true;
        return [item.userName, item.userEmail, item.source, item.action, item.screen, item.detail, item.path]
          .join(" ")
          .toLowerCase()
          .includes(query);
      })
      .map((item) => ({
        ...item,
        createdAtLabel: new Date(item.createdAt).toLocaleString(localeToDateTag(locale)),
        sourceLabel: item.source === "frontend" ? "Frontend" : "Backend",
        userLabel: `${item.userName || "-"} ${item.userEmail || ""}`.trim()
      }));
  }, [locale, logs, search]);

  const baseColumns = useMemo<GridColDef<ActivityLogRow>[]>(() => [
    { field: "createdAtLabel", headerName: locale === "en" ? "Date" : "Tarih", width: 185 },
    {
      field: "screen",
      headerName: locale === "en" ? "Screen" : "Ekran",
      minWidth: 170,
      flex: 0.8,
      renderCell: (params) => (
        <div className="feedback-grid-cell">
          <strong>{String(params.value ?? "-")}</strong>
          <span>{params.row.path || "-"}</span>
        </div>
      )
    },
    { field: "action", headerName: locale === "en" ? "Action" : "Islem", minWidth: 170, flex: 0.8 },
    { field: "detail", headerName: locale === "en" ? "Detail" : "Detay", minWidth: 260, flex: 1.3 },
    {
      field: "sourceLabel",
      headerName: locale === "en" ? "Layer" : "Seviye",
      width: 130,
      renderCell: (params) => <span className="feedback-pill feedback-pill-status">{String(params.value ?? "-")}</span>
    }
  ], [locale]);

  const columns = useMemo<GridColDef<ActivityLogRow>[]>(() => {
    if (!isAdminMode) return baseColumns;
    return [
      {
        field: "userLabel",
        headerName: locale === "en" ? "User" : "Kullanici",
        minWidth: 230,
        flex: 1,
        renderCell: (params) => (
          <div className="feedback-grid-cell">
            <strong>{params.row.userName || "-"}</strong>
            <span>{params.row.userEmail || "-"}</span>
          </div>
        )
      },
      ...baseColumns
    ];
  }, [baseColumns, isAdminMode, locale]);

  async function loadLogs() {
    setLoading(true);
    setError("");
    try {
      const data = isAdminMode ? await listActivityLogs() : await listMyActivityLogs();
      setLogs(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : (locale === "en" ? "Activity logs could not be loaded." : "Islem loglari yuklenemedi."));
    } finally {
      setLoading(false);
    }
  }

  async function handleLogout() {
    try {
      await authLogout();
    } finally {
      window.location.href = "/";
    }
  }

  if (loadingAuth) {
    return (
      <main className="auth-shell">
        <section className="auth-card panel">
          <LoaderCircle className="spin" size={32} />
          <h1>{title}</h1>
          <p>{locale === "en" ? "Checking session..." : "Oturum kontrol ediliyor..."}</p>
        </section>
      </main>
    );
  }

  if (!authUser) {
    return (
      <main className="auth-shell">
        <section className="auth-card panel">
          <AlertCircle size={32} />
          <h1>{title}</h1>
          <p>{locale === "en" ? "Session required." : "Oturum gerekli."}</p>
          <Link className="secondary-button" href="/">{locale === "en" ? "Back to home" : "Ana ekrana don"}</Link>
        </section>
      </main>
    );
  }

  if (isAdminMode && authUser.role !== "ADMIN") {
    return (
      <main className="auth-shell">
        <section className="auth-card panel">
          <AlertCircle size={32} />
          <h1>{title}</h1>
          <p>{locale === "en" ? "This page is only open to admin accounts." : "Bu sayfa yalnizca yonetici hesabina aciktir."}</p>
          <Link className="secondary-button" href="/">{locale === "en" ? "Back to home" : "Ana ekrana don"}</Link>
        </section>
      </main>
    );
  }

  const navGroups = buildNavGroups(locale, authUser.role === "ADMIN");

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
          {navGroups.map((group) => {
            const GroupIcon = group.icon;
            const isOpen = openNavGroup === group.id;
            if (!group.children?.length) {
              return (
                <Link href={group.href ?? "/"} key={group.id} title={group.label}>
                  <GroupIcon size={18} />
                  <span>{group.label}</span>
                </Link>
              );
            }
            return (
              <div className="sidebar-menu-group" key={group.id}>
                <button
                  aria-expanded={isOpen}
                  className={isOpen ? "active" : ""}
                  onClick={() => setOpenNavGroup((current) => current === group.id ? null : group.id)}
                  title={group.label}
                  type="button"
                >
                  <GroupIcon size={18} />
                  <span>{group.label}</span>
                  <ChevronRight className="sidebar-submenu-chevron" size={15} />
                </button>
                {isOpen && (
                  <div className="sidebar-submenu">
                    {group.children.map((item) => {
                      const ItemIcon = item.icon;
                      const active = isActiveRoute(item.href, mode);
                      return (
                        <Link className={active ? "active" : ""} href={item.href} key={item.id} title={item.label}>
                          <ItemIcon size={15} />
                          <span>{item.label}</span>
                        </Link>
                      );
                    })}
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
        <section className="settings-workspace">
          <header className="panel settings-header">
            <div>
              <span className="eyebrow">{isAdminMode ? (locale === "en" ? "System audit" : "Sistem denetimi") : (locale === "en" ? "User Activity" : "Kullanici Islemleri")}</span>
              <h1>{title}</h1>
              <p>{locale === "en" ? "Frontend screen views and backend operations are listed together." : "Frontend ekran gecisleri ve backend islemleri birlikte listelenir."}</p>
            </div>
          </header>

          <section className="panel admin-card">
            <div className="section-head">
              <div>
                <span className="section-label">{locale === "en" ? "Activity logs" : "Islem loglari"}</span>
                <h3>{locale === "en" ? "Visible records" : "Gorunen kayitlar"}</h3>
              </div>
              <span className="status">{rows.length}/{logs.length} {t.tools.records}</span>
            </div>
            <div className="admin-actions">
              <label className="field-label">
                {t.feedback.search}
                <input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder={isAdminMode
                    ? (locale === "en" ? "Search user, screen, action..." : "Kullanici, ekran, islem ara...")
                    : (locale === "en" ? "Search screen, action, detail..." : "Ekran, islem, detay ara...")}
                />
              </label>
              <button className="secondary-button" type="button" onClick={() => void loadLogs()} disabled={loading}>
                {loading ? <LoaderCircle className="spin" size={17} /> : <BarChart3 size={17} />}
                {locale === "en" ? "Refresh logs" : "Loglari yenile"}
              </button>
            </div>
            {error ? <div className="error">{error}</div> : null}
            <div className="feedback-datagrid-wrap">
              <DataGrid
                autoHeight
                rows={rows}
                columns={columns}
                loading={loading}
                disableRowSelectionOnClick
                initialState={{ pagination: { paginationModel: { page: 0, pageSize: 10 } } }}
                pageSizeOptions={[10, 25, 50]}
                rowHeight={68}
                columnHeaderHeight={42}
                sx={dataGridSx}
              />
            </div>
          </section>
        </section>
      </section>
    </main>
  );
}

function buildNavGroups(locale: Locale, isAdmin: boolean): NavGroup[] {
  const groups: NavGroup[] = [
    { id: "assistant", label: locale === "en" ? "Assistant" : "Asistan", icon: Bot, href: "/" },
    { id: "case-law", label: locale === "en" ? "Case-Law Search" : "Ictihat Arama", icon: Scale, href: "/" },
    { id: "petition", label: locale === "en" ? "Petition Draft" : "Dilekce Taslak", icon: ScrollText, href: "/" },
    { id: "cases", label: locale === "en" ? "Cases" : "Davalar", icon: BriefcaseBusiness, href: "/" },
    { id: "document", label: locale === "en" ? "Document Processing" : "Belge Isleme", icon: FileUp, href: "/" },
    {
      id: "account",
      label: locale === "en" ? "Account" : "Hesap",
      icon: UserRound,
      children: [
        { id: "profile", label: locale === "en" ? "Profile" : "Profil", icon: UserRound, href: "/" },
        { id: "activity", label: locale === "en" ? "User Activity" : "Kullanici Islemleri", icon: ClipboardList, href: "/activity-logs" },
        { id: "subscriptions", label: locale === "en" ? "Subscriptions" : "Abonelik", icon: CreditCard, href: "/subscriptions" },
        { id: "feedback", label: locale === "en" ? "Feedback" : "Geri Bildirim", icon: MessageSquareMore, href: "/" },
        { id: "settings-view", label: locale === "en" ? "Appearance" : "Gorunum", icon: Palette, href: "/" },
        { id: "settings-account", label: locale === "en" ? "Change Password" : "Sifre Degistir", icon: KeyRound, href: "/" }
      ]
    }
  ];

  if (isAdmin) {
    groups.push({
      id: "admin",
      label: locale === "en" ? "Management" : "Yonetim",
      icon: ShieldAlert,
      children: [
        { id: "admin-feedback", label: locale === "en" ? "Feedback Management" : "Sikayet Yonetimi", icon: MessageSquareMore, href: "/feedback-management" },
        { id: "admin-users", label: locale === "en" ? "User Management" : "Kullanici Yonetimi", icon: UsersRound, href: "/" },
        { id: "admin-subscriptions", label: locale === "en" ? "Subscription Plans" : "Abonelik Planlari", icon: CreditCard, href: "/admin/subscriptions" },
        { id: "admin-user-subscriptions", label: locale === "en" ? "User Subscriptions" : "Kullanici Abonelikleri", icon: CreditCard, href: "/admin/user-subscriptions" },
        { id: "admin-logs", label: locale === "en" ? "Activity Logs" : "Islem Loglari", icon: ClipboardList, href: "/admin/activity-logs" }
      ]
    });
  }

  return groups;
}

function isActiveRoute(href: string, mode: Mode) {
  if (mode === "user") {
    return href === "/activity-logs";
  }
  return href === "/admin/activity-logs";
}


