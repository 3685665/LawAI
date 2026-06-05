"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import {
  AlertCircle,
  ArrowLeft,
  BarChart3,
  Bot,
  BookOpenCheck,
  BriefcaseBusiness,
  ChevronRight,
  ClipboardList,
  CreditCard,
  FileUp,
  FileSearch,
  FileText,
  FolderOpen,
  GraduationCap,
  LoaderCircle,
  MessageSquareMore,
  Palette,
  Scale,
  ShieldAlert,
  ScrollText,
  Trash2,
  Upload,
  UserRound
} from "lucide-react";
import { getMessages, isLocale, localeToDateTag, type Locale } from "@/lib/i18n";
import {
  authLogout,
  authMe,
  deleteFeedback,
  listFeedback,
  updateFeedback,
  updateFeedbackStatus,
  type AuthUser,
  type FeedbackRecord,
  type FeedbackStatus,
  type FeedbackType
} from "@/lib/api";

type FilterStatus = "all" | FeedbackStatus;
type FilterType = "all" | FeedbackType;
type FeedbackManagementView = "list" | "detail";

function isFeedbackType(value: string): value is FeedbackType {
  return value === "hata" || value === "ozellik" || value === "genel";
}

function isFeedbackStatus(value: string): value is FeedbackStatus {
  return value === "received" || value === "read" || value === "resolved";
}

function badgeClassType(value: string) {
  return `feedback-pill feedback-pill-type feedback-type-${value}`;
}

function badgeClassStatus(value: string) {
  return `feedback-pill feedback-pill-status feedback-status-${value}`;
}

export default function FeedbackManagementPage() {
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  const [locale, setLocale] = useState<Locale>("tr");
  const [loadingAuth, setLoadingAuth] = useState(true);
  const [items, setItems] = useState<FeedbackRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState<FilterType>("all");
  const [statusFilter, setStatusFilter] = useState<FilterStatus>("all");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<FeedbackManagementView>("list");
  const [saving, setSaving] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [adminMenuOpen, setAdminMenuOpen] = useState(true);
  const [editForm, setEditForm] = useState({
    type: "genel" as FeedbackType,
    subject: "",
    message: "",
    status: "received" as FeedbackStatus
  });

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
  const tabs = useMemo(() => {
    const baseTabs = [
      { id: "chat", label: t.tabs.chat, icon: Bot },
      { id: "search", label: t.tabs.search, icon: FileSearch },
      { id: "petition", label: t.tabs.petition, icon: ScrollText },
      { id: "training", label: t.tabs.training, icon: BookOpenCheck },
      { id: "cases", label: t.tabs.cases, icon: BriefcaseBusiness },
      { id: "document", label: t.tabs.document, icon: FileUp },
      { id: "feedback", label: t.tabs.feedback, icon: MessageSquareMore },
      { id: "subscriptions", label: locale === "en" ? "Subscriptions" : "Abonelik", icon: CreditCard },
      { id: "profile", label: t.tabs.profile, icon: UserRound },
      { id: "settings", label: t.tabs.settings, icon: Palette }
    ];
    return authUser?.role === "ADMIN"
      ? [...baseTabs, { id: "admin", label: t.tabs.admin, icon: ShieldAlert }]
      : baseTabs;
  }, [authUser?.role, t]);

  useEffect(() => {
    if (!authUser || authUser.role !== "ADMIN") {
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError("");
    listFeedback()
      .then((data) => {
        if (cancelled) return;
        setItems(data);
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : t.adminFeedback.loadError);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [authUser]);

  const selected = useMemo(() => items.find((item) => item.id === selectedId) ?? null, [items, selectedId]);

  useEffect(() => {
    if (!selected) {
      return;
    }
    setEditForm({
      type: isFeedbackType(selected.type) ? selected.type : "genel",
      subject: selected.subject,
      message: selected.message,
      status: isFeedbackStatus(selected.status) ? selected.status : "received"
    });
  }, [selected]);

  const filteredItems = useMemo(() => {
    const query = search.trim().toLowerCase();
    return items.filter((item) => {
      const matchesQuery = !query || [item.subject, item.message, item.userName, item.userEmail, item.type, item.status].join(" ").toLowerCase().includes(query);
      const matchesType = typeFilter === "all" || item.type === typeFilter;
      const matchesStatus = statusFilter === "all" || item.status === statusFilter;
      return matchesQuery && matchesType && matchesStatus;
    });
  }, [items, search, typeFilter, statusFilter]);

  const rows = useMemo(() => filteredItems.map((item) => ({
    ...item,
    owner: `${item.userName ?? "-"}${item.userEmail ? ` (${item.userEmail})` : ""}`
  })), [filteredItems]);

  const columns = useMemo<GridColDef[]>(() => [
    {
      field: "subject",
      headerName: t.feedback.subject,
      flex: 1.4,
      minWidth: 220,
      editable: true,
      renderCell: (params) => (
        <div className="feedback-grid-cell">
          <strong>{String(params.value ?? "")}</strong>
          <span>{String((params.row as FeedbackRecord).message ?? "")}</span>
        </div>
      )
    },
    {
      field: "type",
      headerName: t.feedback.type,
      width: 170,
      editable: true,
      type: "singleSelect",
      valueOptions: ["hata", "ozellik", "genel"],
      renderCell: (params) => <span className={badgeClassType(String(params.value ?? ""))}>{t.feedback.types[String(params.value ?? "genel") as FeedbackType]}</span>
    },
    {
      field: "status",
      headerName: t.feedback.status,
      width: 150,
      editable: true,
      type: "singleSelect",
      valueOptions: ["received", "read", "resolved"],
      renderCell: (params) => <span className={badgeClassStatus(String(params.value ?? ""))}>{t.feedback.statuses[String(params.value ?? "received") as FeedbackStatus]}</span>
    },
    {
      field: "owner",
      headerName: t.adminFeedback.owner,
      minWidth: 220,
      flex: 1,
      renderCell: (params) => <span className="feedback-owner">{String(params.value ?? "-")}</span>
    },
    {
      field: "createdAt",
      headerName: t.adminFeedback.date,
      width: 180,
      valueGetter: (_, row) => new Date(String(row.createdAt)).toLocaleString(localeToDateTag(locale))
    },
    {
      field: "actions",
      headerName: t.adminFeedback.action,
      width: 170,
      sortable: false,
      filterable: false,
      renderCell: (params) => (
        <div className="feedback-row-actions">
          <button type="button" className="secondary-button" onClick={(event) => {
            event.stopPropagation();
            setSelectedId(String(params.id));
            setViewMode("detail");
          }}>
            {t.adminFeedback.open}
          </button>
          <button type="button" className="danger-button" onClick={(event) => {
            event.stopPropagation();
            handleDelete(String(params.id));
          }}>
            <Trash2 size={16} />
          </button>
        </div>
      )
    }
  ], [locale, t]);

  function refreshFrom(updated: FeedbackRecord) {
    setItems((current) => current.map((item) => (item.id === updated.id ? updated : item)));
    setSelectedId(updated.id);
  }

  async function handleRowUpdate(newRow: FeedbackRecord) {
    const updated = await updateFeedback(newRow.id, {
      type: isFeedbackType(newRow.type) ? newRow.type : "genel",
      subject: newRow.subject,
      message: newRow.message,
      status: isFeedbackStatus(newRow.status) ? newRow.status : "received"
    });
    refreshFrom(updated);
    return updated;
  }

  async function handleDelete(id: string) {
    if (!window.confirm(t.adminFeedback.deleteConfirm)) {
      return;
    }
    await deleteFeedback(id);
    setItems((current) => current.filter((item) => item.id !== id));
    setSelectedId((current) => (current === id ? null : current));
    setViewMode((current) => (selectedId === id ? "list" : current));
  }

  async function handleSave(event: FormEvent) {
    event.preventDefault();
    if (!selected) return;
    setSaving(true);
    setError("");
    try {
      const updated = await updateFeedback(selected.id, {
        ...editForm
      });
      refreshFrom(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : t.adminFeedback.updateError);
    } finally {
      setSaving(false);
    }
  }

  async function updateStatus(id: string, status: FeedbackStatus) {
    const updated = await updateFeedbackStatus(id, status);
    refreshFrom(updated);
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
          <h1>{t.adminFeedback.title}</h1>
          <p>{t.adminFeedback.checking}</p>
        </section>
      </main>
    );
  }

  if (!authUser) {
    return (
      <main className="auth-shell">
        <section className="auth-card panel">
          <AlertCircle size={32} />
          <h1>{t.adminFeedback.title}</h1>
          <p>{t.adminFeedback.sessionRequired}</p>
          <Link className="secondary-button" href="/">{t.adminFeedback.backHome}</Link>
        </section>
      </main>
    );
  }

  if (authUser.role !== "ADMIN") {
    return (
      <main className="auth-shell">
        <section className="auth-card panel">
          <AlertCircle size={32} />
          <h1>{t.adminFeedback.title}</h1>
          <p>{t.adminFeedback.adminOnly}</p>
          <Link className="secondary-button" href="/">{t.adminFeedback.backHome}</Link>
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
            return (
              <div className={isAdminTab ? "sidebar-menu-group" : ""} key={tab.id}>
                {isAdminTab ? (
                  <button
                    aria-expanded={adminMenuOpen}
                    className="active"
                    onClick={() => setAdminMenuOpen((current) => !current)}
                    title={tab.label}
                    type="button"
                  >
                    <Icon size={18} />
                    <span>{tab.label}</span>
                    <ChevronRight className="sidebar-submenu-chevron" size={15} />
                  </button>
                ) : (
                  <Link href={tab.id === "subscriptions" ? "/subscriptions" : "/"} title={tab.label}>
                    <Icon size={18} />
                    <span>{tab.label}</span>
                  </Link>
                )}
                {isAdminTab && adminMenuOpen && (
                  <div className="sidebar-submenu">
                    <Link className="active" href="/feedback-management" title={t.adminFeedback.title}>
                      <MessageSquareMore size={15} />
                      <span>{t.adminFeedback.title}</span>
                    </Link>
                    <Link href="/" title={locale === "en" ? "User Management" : "Kullanici Yonetimi"}>
                      <UserRound size={15} />
                      <span>{locale === "en" ? "User Management" : "Kullanici Yonetimi"}</span>
                    </Link>
                    <Link href="/admin/subscriptions" title={locale === "en" ? "Subscription Management" : "Abonelik Yonetimi"}>
                      <CreditCard size={15} />
                      <span>{locale === "en" ? "Subscription Management" : "Abonelik Yonetimi"}</span>
                    </Link>
                    <Link href="/admin/activity-logs" title={locale === "en" ? "Activity Logs" : "Islem Loglari"}>
                      <ClipboardList size={15} />
                      <span>{locale === "en" ? "Activity Logs" : "Islem Loglari"}</span>
                    </Link>
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

      <section className="workspace admin-feedback-shell">
      <div className="topbar">
        <div>
          <span className="eyebrow">{t.adminFeedback.eyebrow}</span>
          <h1>{t.adminFeedback.title}</h1>
          <p>{t.adminFeedback.description}</p>
        </div>
        <div className="hero-actions">
          <Link className="ghost-button" href="/">
            <ArrowLeft size={17} />
            {t.adminFeedback.backApp}
          </Link>
        </div>
      </div>

      {viewMode === "list" ? (
        <section className="panel feedback-admin-filters">
          <div className="feedback-filter-search">
            <label className="field-label">
              {t.feedback.search}
              <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder={t.adminFeedback.searchPlaceholder} />
            </label>
          </div>
          <div className="feedback-filter-group">
            <span className="section-label">{t.feedback.status}</span>
            <div className="feedback-quick-filters">
              <button type="button" className={statusFilter === "all" ? "active" : ""} onClick={() => setStatusFilter("all")}>{t.adminFeedback.all}</button>
              <button type="button" className={statusFilter === "received" ? "active" : ""} onClick={() => setStatusFilter("received")}>{t.feedback.statuses.received}</button>
              <button type="button" className={statusFilter === "read" ? "active" : ""} onClick={() => setStatusFilter("read")}>{t.feedback.statuses.read}</button>
              <button type="button" className={statusFilter === "resolved" ? "active" : ""} onClick={() => setStatusFilter("resolved")}>{t.feedback.statuses.resolved}</button>
            </div>
          </div>
          <div className="feedback-filter-group">
            <span className="section-label">{t.feedback.type}</span>
            <div className="feedback-quick-filters">
              <button type="button" className={typeFilter === "all" ? "active" : ""} onClick={() => setTypeFilter("all")}>{t.adminFeedback.allTypes}</button>
              <button type="button" className={typeFilter === "hata" ? "active" : ""} onClick={() => setTypeFilter("hata")}>{t.feedback.types.hata}</button>
              <button type="button" className={typeFilter === "ozellik" ? "active" : ""} onClick={() => setTypeFilter("ozellik")}>{t.feedback.types.ozellik}</button>
              <button type="button" className={typeFilter === "genel" ? "active" : ""} onClick={() => setTypeFilter("genel")}>{t.feedback.types.genel}</button>
            </div>
          </div>
        </section>
      ) : null}

      {error ? <div className="error">{error}</div> : null}

      <section className={viewMode === "detail" ? "feedback-admin-layout feedback-admin-layout-detail" : "feedback-admin-layout feedback-admin-layout-list"}>
        {viewMode === "list" ? (
        <div className="panel feedback-admin-table">
          <div className="section-head">
            <div>
              <span className="section-label">{t.adminFeedback.records}</span>
              <h3>{t.adminFeedback.visibleRecords}</h3>
            </div>
            <span className="status">{filteredItems.length}/{items.length} {t.feedback.recordCount}</span>
          </div>
          {loading ? (
            <p className="empty">{t.adminFeedback.loading}</p>
          ) : (
            <div className="feedback-datagrid-wrap">
              <DataGrid
                autoHeight
                rows={rows}
                columns={columns}
                disableRowSelectionOnClick
                processRowUpdate={handleRowUpdate}
                onProcessRowUpdateError={(err) => setError(err instanceof Error ? err.message : t.adminFeedback.rowUpdateError)}
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
                  }
                }}
              />
            </div>
          )}
        </div>
        ) : null}

        {viewMode === "detail" ? (
        <div className="panel feedback-admin-detail">
          <div className="section-head">
            <div>
              <span className="section-label">{t.adminFeedback.detail}</span>
              <h3>{t.adminFeedback.selectedRecord}</h3>
            </div>
            <button className="secondary-button" type="button" onClick={() => setViewMode("list")}>
              <ArrowLeft size={16} />
              {t.adminFeedback.records}
            </button>
          </div>
          {selected ? (
            <form className="feedback-edit-form" onSubmit={handleSave}>
              <div className="feedback-detail-meta">
                <div>
                  <small>{t.feedback.status}</small>
                  <strong className={badgeClassStatus(selected.status)}>{t.feedback.statuses[selected.status as FeedbackStatus] ?? selected.status}</strong>
                </div>
                <div>
                  <small>{t.feedback.type}</small>
                  <strong className={badgeClassType(selected.type)}>{t.feedback.types[selected.type as FeedbackType] ?? selected.type}</strong>
                </div>
                <div>
                  <small>{t.adminFeedback.owner}</small>
                  <strong>{selected.userName ?? "-"}{selected.userEmail ? ` (${selected.userEmail})` : ""}</strong>
                </div>
              </div>
              <div className="feedback-edit-grid">
                <label className="field-label">
                  {t.feedback.type}
                  <select value={editForm.type} onChange={(event) => setEditForm((current) => ({ ...current, type: event.target.value as FeedbackType }))}>
                    <option value="hata">{t.feedback.types.hata}</option>
                    <option value="ozellik">{t.feedback.types.ozellik}</option>
                    <option value="genel">{t.feedback.types.genel}</option>
                  </select>
                </label>
                <label className="field-label">
                  {t.feedback.status}
                  <select value={editForm.status} onChange={(event) => setEditForm((current) => ({ ...current, status: event.target.value as FeedbackStatus }))}>
                    <option value="received">{t.feedback.statuses.received}</option>
                    <option value="read">{t.feedback.statuses.read}</option>
                    <option value="resolved">{t.feedback.statuses.resolved}</option>
                  </select>
                </label>
              </div>
              <label className="field-label">
                {t.feedback.subject}
                <input value={editForm.subject} onChange={(event) => setEditForm((current) => ({ ...current, subject: event.target.value }))} />
              </label>
              <label className="field-label">
                {t.feedback.message}
                <textarea rows={8} value={editForm.message} onChange={(event) => setEditForm((current) => ({ ...current, message: event.target.value }))} />
              </label>
              <div className="feedback-detail-actions">
                <button type="submit" disabled={saving}>
                  {saving ? <LoaderCircle className="spin" size={17} /> : null}
                  {t.adminFeedback.save}
                </button>
                <button type="button" className="secondary-button" onClick={() => updateStatus(selected.id, "read")}>
                  {t.adminFeedback.markRead}
                </button>
                <button type="button" className="secondary-button" onClick={() => updateStatus(selected.id, "resolved")}>
                  {t.feedback.statuses.resolved}
                </button>
                <button type="button" className="danger-button" onClick={() => void handleDelete(selected.id)}>
                  {t.cases.delete}
                </button>
              </div>
            </form>
          ) : (
            <p className="empty">{t.adminFeedback.emptySelection}</p>
          )}
        </div>
        ) : null}
      </section>
      </section>
    </main>
  );
}



