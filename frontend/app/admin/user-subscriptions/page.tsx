"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import { ArrowLeft, BriefcaseBusiness, Bot, ChevronRight, ClipboardList, CreditCard, FileSearch, FileUp, LoaderCircle, MessageSquareMore, Scale, ScrollText, ShieldAlert, UserRound } from "lucide-react";
import { authLogout, authMe, listAdminUserSubscriptions, updateUserSubscriptionStatus, type AuthUser, type UserSubscription } from "@/lib/api";

const dataGridSx = {
  border: 0,
  color: "var(--ink)",
  fontFamily: "inherit",
  "& .MuiDataGrid-columnHeaders": {
    backgroundColor: "#f7f9fb",
    borderBottom: "1px solid var(--line)",
    color: "var(--muted)",
    fontSize: "12px",
    fontWeight: 800,
    textTransform: "uppercase"
  },
  "& .MuiDataGrid-cell": {
    borderBottom: "1px solid rgba(215, 222, 232, 0.7)",
    outline: "none"
  },
  "& .MuiDataGrid-row:hover": {
    backgroundColor: "#f7fafc"
  },
  "& .MuiDataGrid-cell:focus, & .MuiDataGrid-columnHeader:focus": {
    outline: "none"
  }
};

export default function AdminUserSubscriptionsPage() {
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  const [loadingAuth, setLoadingAuth] = useState(true);
  const [items, setItems] = useState<UserSubscription[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [adminMenuOpen, setAdminMenuOpen] = useState(true);

  const columns = useMemo<GridColDef<UserSubscription>[]>(() => [
    {
      field: "userName",
      headerName: "Kullanici",
      flex: 1.2,
      minWidth: 230,
      renderCell: (params) => (
        <div className="feedback-grid-cell">
          <strong>{params.row.userName || "-"}</strong>
          <span>{params.row.userEmail || "-"}</span>
        </div>
      )
    },
    {
      field: "planName",
      headerName: "Plan",
      flex: 1,
      minWidth: 210,
      renderCell: (params) => (
        <div className="feedback-grid-cell">
          <strong>{params.row.planName || "-"}</strong>
          <span>{params.row.providerSubscriptionId || params.row.planId}</span>
        </div>
      )
    },
    {
      field: "billingCycle",
      headerName: "Donem",
      width: 120,
      valueGetter: (_, row) => cycleLabel(row.billingCycle)
    },
    {
      field: "lastPaymentStatus",
      headerName: "Odeme",
      width: 150,
      valueGetter: (_, row) => row.lastPaymentStatus || row.provider || "-"
    },
    {
      field: "endsAt",
      headerName: "Bitis",
      width: 140,
      valueGetter: (_, row) => formatDate(row.endsAt)
    },
    {
      field: "status",
      headerName: "Durum",
      minWidth: 190,
      renderCell: (params) => (
        <select
          className="mui-grid-select"
          disabled={saving}
          value={params.row.status}
          onChange={(event) => void changeStatus(params.row.id, event.target.value as UserSubscription["status"])}
        >
          <option value="ACTIVE">Aktif</option>
          <option value="PENDING_PAYMENT">Odeme bekliyor</option>
          <option value="PAST_DUE">Odeme gecikti</option>
          <option value="PAUSED">Duraklatildi</option>
          <option value="CANCELLED">Iptal</option>
          <option value="EXPIRED">Suresi doldu</option>
        </select>
      )
    }
  ], [saving]);

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

  useEffect(() => {
    if (!authUser || authUser.role !== "ADMIN") return;
    void loadItems();
  }, [authUser]);

  async function loadItems() {
    setLoading(true);
    setError("");
    try {
      setItems(await listAdminUserSubscriptions());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Kullanici abonelikleri yuklenemedi.");
    } finally {
      setLoading(false);
    }
  }

  async function changeStatus(id: string, status: UserSubscription["status"]) {
    setSaving(true);
    setError("");
    try {
      const updated = await updateUserSubscriptionStatus(id, status);
      setItems((current) => current.map((item) => item.id === id ? updated : item));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Kullanici aboneligi guncellenemedi.");
    } finally {
      setSaving(false);
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
    return <GateCard title="Kullanici Abonelikleri" text="Yetki kontrol ediliyor..." />;
  }

  if (!authUser) {
    return <GateCard title="Kullanici Abonelikleri" text="Oturum gerekli." linkText="Ana ekrana don" />;
  }

  if (authUser.role !== "ADMIN") {
    return <GateCard title="Kullanici Abonelikleri" text="Bu sayfa yalnizca yonetici hesabina aciktir." linkText="Ana ekrana don" />;
  }

  return (
    <main className={`app-shell ${sidebarCollapsed ? "sidebar-collapsed" : ""}`}>
      <AdminSidebar authUser={authUser} adminMenuOpen={adminMenuOpen} onLogout={handleLogout} onToggleAdmin={() => setAdminMenuOpen((current) => !current)} onToggleSidebar={() => setSidebarCollapsed((current) => !current)} />
      <section className="workspace admin-subscription-workspace">
        <div className="topbar">
          <div>
            <span className="eyebrow">Yonetici paneli</span>
            <h1>Kullanici Abonelikleri</h1>
            <p>Kullanici bazli abonelik kayitlarini, Stripe odeme durumunu ve manuel durum guncellemelerini buradan yonetin.</p>
          </div>
          <div className="hero-actions">
            <Link className="ghost-button" href="/admin/subscriptions"><CreditCard size={17} /> Abonelik planlari</Link>
            <Link className="ghost-button" href="/"><ArrowLeft size={17} /> Ana uygulama</Link>
          </div>
        </div>

        {error ? <div className="error">{error}</div> : null}

        <section className="panel user-subscription-admin-panel">
          <div className="section-head">
            <div><span className="section-label">Kayitlar</span><h3>{items.length} abonelik</h3></div>
            <button className="secondary-button" type="button" onClick={() => void loadItems()}>{loading ? <LoaderCircle className="spin" size={16} /> : null} Yenile</button>
          </div>
          <div className="feedback-datagrid-wrap">
            <DataGrid
              autoHeight
              rows={items}
              columns={columns}
              loading={loading}
              disableRowSelectionOnClick
              rowHeight={68}
              columnHeaderHeight={42}
              initialState={{ pagination: { paginationModel: { page: 0, pageSize: 10 } } }}
              pageSizeOptions={[10, 25, 50]}
              localeText={{ noRowsLabel: "Henuz kullanici aboneligi yok." }}
              sx={dataGridSx}
            />
          </div>
        </section>
      </section>
    </main>
  );
}

function AdminSidebar({ authUser, adminMenuOpen, onLogout, onToggleAdmin, onToggleSidebar }: { authUser: AuthUser; adminMenuOpen: boolean; onLogout: () => void; onToggleAdmin: () => void; onToggleSidebar: () => void }) {
  const base = [
    { href: "/", label: "Asistan", icon: Bot },
    { href: "/", label: "Emsal Arama", icon: FileSearch },
    { href: "/", label: "Dilekce", icon: ScrollText },
    { href: "/", label: "Davalar", icon: BriefcaseBusiness },
    { href: "/", label: "Belge Isleme", icon: FileUp },
    { href: "/", label: "Geri Bildirim", icon: MessageSquareMore },
    { href: "/subscriptions", label: "Abonelik", icon: CreditCard },
    { href: "/", label: "Profil", icon: UserRound }
  ];
  return (
    <aside className="sidebar">
      <div className="brand"><Scale size={28} /><div><strong>LawAI Studio</strong><span>Hukuk AI Calisma Sistemi</span></div></div>
      <button aria-label="Yan menuyu ac/kapat" className="sidebar-toggle" onClick={onToggleSidebar} type="button"><ChevronRight size={18} /></button>
      <div className="nav-label">Uygulamalar</div>
      <nav className="tabs">
        {base.map((item) => { const Icon = item.icon; return <Link href={item.href} key={item.label} title={item.label}><Icon size={18} /><span>{item.label}</span></Link>; })}
        <div className="sidebar-menu-group">
          <button aria-expanded={adminMenuOpen} className="active" onClick={onToggleAdmin} type="button"><ShieldAlert size={18} /><span>Yonetim</span><ChevronRight className="sidebar-submenu-chevron" size={15} /></button>
          {adminMenuOpen ? <div className="sidebar-submenu"><Link href="/feedback-management"><MessageSquareMore size={15} /><span>Sikayet Yonetimi</span></Link><Link href="/admin/subscriptions"><CreditCard size={15} /><span>Abonelik Planlari</span></Link><Link className="active" href="/admin/user-subscriptions"><CreditCard size={15} /><span>Kullanici Abonelikleri</span></Link><Link href="/admin/activity-logs"><ClipboardList size={15} /><span>Islem Loglari</span></Link></div> : null}
        </div>
      </nav>
      <div className="sidebar-user"><div className="sidebar-user-avatar">{authUser.name.slice(0, 1).toUpperCase()}</div><div><strong>{authUser.name}</strong><span>{authUser.email}</span><span className="sidebar-user-role">Yonetici</span></div><div className="sidebar-user-actions"><button className="secondary-button" onClick={onLogout} type="button">Cikis</button></div></div>
    </aside>
  );
}

function GateCard({ title, text, linkText }: { title: string; text: string; linkText?: string }) {
  return <main className="auth-shell"><section className="auth-card panel"><LoaderCircle className="spin" size={32} /><h1>{title}</h1><p>{text}</p>{linkText ? <Link className="secondary-button" href="/">{linkText}</Link> : null}</section></main>;
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString("tr-TR");
}

function cycleLabel(value: string) {
  return value === "yearly" ? "Yillik" : "Aylik";
}
