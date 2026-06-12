"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import {
  AlertCircle,
  BarChart3,
  LoaderCircle
} from "lucide-react";
import { AppSidebar } from "@/components/app-sidebar";
import { useRouteAppSidebar } from "@/hooks/use-route-app-sidebar";
import { getMessages, localeToDateTag } from "@/lib/i18n";
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
  const [loadingAuth, setLoadingAuth] = useState(true);
  const [logs, setLogs] = useState<ActivityLogRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const {
    locale,
    groups: navGroups,
    collapsed: sidebarCollapsed,
    toggleCollapsed: toggleSidebarCollapsed,
    openNavGroup,
    toggleNavGroup,
    pathname
  } = useRouteAppSidebar(authUser);

  useEffect(() => {
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
  const logText = t.activityLogs;
  const isAdminMode = mode === "admin";
  const title = isAdminMode ? logText.allTitle : logText.myTitle;

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
    { field: "createdAtLabel", headerName: logText.date, width: 185 },
    {
      field: "screen",
      headerName: logText.screen,
      minWidth: 170,
      flex: 0.8,
      renderCell: (params) => (
        <div className="feedback-grid-cell">
          <strong>{String(params.value ?? "-")}</strong>
          <span>{params.row.path || "-"}</span>
        </div>
      )
    },
    { field: "action", headerName: logText.action, minWidth: 170, flex: 0.8 },
    { field: "detail", headerName: logText.detail, minWidth: 260, flex: 1.3 },
    {
      field: "sourceLabel",
      headerName: logText.layer,
      width: 130,
      renderCell: (params) => <span className="feedback-pill feedback-pill-status">{String(params.value ?? "-")}</span>
    }
  ], [logText]);

  const columns = useMemo<GridColDef<ActivityLogRow>[]>(() => {
    if (!isAdminMode) return baseColumns;
    return [
      {
        field: "userLabel",
        headerName: logText.user,
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
  }, [baseColumns, isAdminMode, logText]);

  async function loadLogs() {
    setLoading(true);
    setError("");
    try {
      const data = isAdminMode ? await listActivityLogs() : await listMyActivityLogs();
      setLogs(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : logText.loadFailed);
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
          <p>{logText.checkingSession}</p>
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
          <p>{logText.sessionRequired}</p>
          <Link className="secondary-button" href="/">{logText.backHome}</Link>
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
          <p>{logText.adminOnly}</p>
          <Link className="secondary-button" href="/">{logText.backHome}</Link>
        </section>
      </main>
    );
  }

  return (
    <main className={`app-shell ${sidebarCollapsed ? "sidebar-collapsed" : ""}`}>
      <AppSidebar
        authUser={authUser}
        collapsed={sidebarCollapsed}
        groups={navGroups}
        locale={locale}
        onLogout={handleLogout}
        onToggleCollapsed={toggleSidebarCollapsed}
        onToggleNavGroup={toggleNavGroup}
        openNavGroup={openNavGroup}
        pathname={pathname}
      />

      <section className="workspace">
        <section className="settings-workspace">
          <header className="panel settings-header">
            <div>
              <span className="eyebrow">{isAdminMode ? logText.systemAudit : logText.userActivity}</span>
              <h1>{title}</h1>
              <p>{logText.description}</p>
            </div>
          </header>

          <section className="panel admin-card">
            <div className="section-head">
              <div>
                <span className="section-label">{logText.sectionLabel}</span>
                <h3>{logText.visibleRecords}</h3>
              </div>
              <span className="status">{rows.length}/{logs.length} {t.tools.records}</span>
            </div>
            <div className="admin-actions">
              <label className="field-label">
                {t.feedback.search}
                <input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder={isAdminMode ? logText.adminSearchPlaceholder : logText.userSearchPlaceholder}
                />
              </label>
              <button className="secondary-button" type="button" onClick={() => void loadLogs()} disabled={loading}>
                {loading ? <LoaderCircle className="spin" size={17} /> : <BarChart3 size={17} />}
                {logText.refresh}
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
