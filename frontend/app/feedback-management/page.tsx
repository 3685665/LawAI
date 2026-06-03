"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { DataGrid, type GridColDef, type GridRowParams } from "@mui/x-data-grid";
import { AlertCircle, ArrowLeft, LoaderCircle, Trash2 } from "lucide-react";
import {
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

const typeLabels: Record<FeedbackType, string> = {
  hata: "Hata bildirimi",
  ozellik: "Ozellik istegi",
  genel: "Genel geri bildirim"
};

const statusLabels: Record<FeedbackStatus, string> = {
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

function badgeClassType(value: string) {
  return `feedback-pill feedback-pill-type feedback-type-${value}`;
}

function badgeClassStatus(value: string) {
  return `feedback-pill feedback-pill-status feedback-status-${value}`;
}

export default function FeedbackManagementPage() {
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  const [loadingAuth, setLoadingAuth] = useState(true);
  const [items, setItems] = useState<FeedbackRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState<FilterType>("all");
  const [statusFilter, setStatusFilter] = useState<FilterStatus>("all");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [editForm, setEditForm] = useState({
    type: "genel" as FeedbackType,
    subject: "",
    message: "",
    status: "received" as FeedbackStatus
  });

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
        setSelectedId((current) => current ?? data[0]?.id ?? null);
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Geri bildirimler yuklenemedi.");
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
      headerName: "Baslik",
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
      headerName: "Tip",
      width: 170,
      editable: true,
      type: "singleSelect",
      valueOptions: ["hata", "ozellik", "genel"],
      renderCell: (params) => <span className={badgeClassType(String(params.value ?? ""))}>{typeLabels[String(params.value ?? "genel") as FeedbackType]}</span>
    },
    {
      field: "status",
      headerName: "Durum",
      width: 150,
      editable: true,
      type: "singleSelect",
      valueOptions: ["received", "read", "resolved"],
      renderCell: (params) => <span className={badgeClassStatus(String(params.value ?? ""))}>{statusLabels[String(params.value ?? "received") as FeedbackStatus]}</span>
    },
    {
      field: "owner",
      headerName: "Sahip",
      minWidth: 220,
      flex: 1,
      renderCell: (params) => <span className="feedback-owner">{String(params.value ?? "-")}</span>
    },
    {
      field: "createdAt",
      headerName: "Tarih",
      width: 180,
      valueGetter: (_, row) => new Date(String(row.createdAt)).toLocaleString("tr-TR")
    },
    {
      field: "actions",
      headerName: "Islem",
      width: 170,
      sortable: false,
      filterable: false,
      renderCell: (params) => (
        <div className="feedback-row-actions">
          <button type="button" className="secondary-button" onClick={(event) => {
            event.stopPropagation();
            setSelectedId(String(params.id));
          }}>
            Ac
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
  ], []);

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
    if (!window.confirm("Bu kaydi silmek istiyor musunuz?")) {
      return;
    }
    await deleteFeedback(id);
    setItems((current) => current.filter((item) => item.id !== id));
    setSelectedId((current) => (current === id ? null : current));
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
      setError(err instanceof Error ? err.message : "Geri bildirim guncellenemedi.");
    } finally {
      setSaving(false);
    }
  }

  async function updateStatus(id: string, status: FeedbackStatus) {
    const updated = await updateFeedbackStatus(id, status);
    refreshFrom(updated);
  }

  if (loadingAuth) {
    return (
      <main className="auth-shell">
        <section className="auth-card panel">
          <LoaderCircle className="spin" size={32} />
          <h1>Şikayet Yönetimi</h1>
          <p>Yetki bilgisi kontrol ediliyor...</p>
        </section>
      </main>
    );
  }

  if (!authUser) {
    return (
      <main className="auth-shell">
        <section className="auth-card panel">
          <AlertCircle size={32} />
          <h1>Şikayet Yönetimi</h1>
          <p>Oturum gerekli.</p>
          <Link className="secondary-button" href="/">Ana ekrana dön</Link>
        </section>
      </main>
    );
  }

  if (authUser.role !== "ADMIN") {
    return (
      <main className="auth-shell">
        <section className="auth-card panel">
          <AlertCircle size={32} />
          <h1>Şikayet Yönetimi</h1>
          <p>Bu sayfa yalnızca yönetici hesabına açıktır.</p>
          <Link className="secondary-button" href="/">Ana ekrana dön</Link>
        </section>
      </main>
    );
  }

  return (
    <main className="workspace admin-feedback-shell">
      <div className="topbar">
        <div>
          <span className="eyebrow">Yonetici paneli</span>
          <h1>Şikayet Yönetimi</h1>
          <p>Arama, filtreleme, satır içi düzenleme ve durum yönetimi tek ekranda.</p>
        </div>
        <div className="hero-actions">
          <Link className="ghost-button" href="/">
            <ArrowLeft size={17} />
            Ana uygulamaya dön
          </Link>
        </div>
      </div>

      <div className="feedback-admin-filters">
        <label className="field-label">
          Arama
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Baslik, mesaj, sahip..." />
        </label>
        <div className="feedback-quick-filters">
          <button type="button" className={statusFilter === "all" ? "active" : ""} onClick={() => setStatusFilter("all")}>Tümü</button>
          <button type="button" className={statusFilter === "received" ? "active" : ""} onClick={() => setStatusFilter("received")}>Alındı</button>
          <button type="button" className={statusFilter === "read" ? "active" : ""} onClick={() => setStatusFilter("read")}>İncelendi</button>
          <button type="button" className={statusFilter === "resolved" ? "active" : ""} onClick={() => setStatusFilter("resolved")}>Çözüldü</button>
        </div>
        <div className="feedback-quick-filters">
          <button type="button" className={typeFilter === "all" ? "active" : ""} onClick={() => setTypeFilter("all")}>Tüm tipler</button>
          <button type="button" className={typeFilter === "hata" ? "active" : ""} onClick={() => setTypeFilter("hata")}>Hata</button>
          <button type="button" className={typeFilter === "ozellik" ? "active" : ""} onClick={() => setTypeFilter("ozellik")}>Özellik</button>
          <button type="button" className={typeFilter === "genel" ? "active" : ""} onClick={() => setTypeFilter("genel")}>Genel</button>
        </div>
      </div>

      {error ? <div className="error">{error}</div> : null}

      <section className="feedback-admin-layout">
        <div className="panel feedback-admin-table">
          <div className="section-head">
            <div>
              <span className="section-label">Kayıtlar</span>
              <h3>Görünen şikayetler</h3>
            </div>
            <span className="status">{filteredItems.length}/{items.length} kayıt</span>
          </div>
          {loading ? (
            <p className="empty">Geri bildirimler yükleniyor...</p>
          ) : (
            <div className="feedback-datagrid-wrap">
              <DataGrid
                autoHeight
                rows={rows}
                columns={columns}
                disableRowSelectionOnClick
                processRowUpdate={handleRowUpdate}
                onProcessRowUpdateError={(err) => setError(err instanceof Error ? err.message : "Satır güncellenemedi.")}
                onRowClick={(params: GridRowParams) => setSelectedId(String(params.id))}
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

        <div className="panel feedback-admin-detail">
          <div className="section-head">
            <div>
              <span className="section-label">Detay</span>
              <h3>Seçili kayıt</h3>
            </div>
          </div>
          {selected ? (
            <form className="feedback-edit-form" onSubmit={handleSave}>
              <div className="feedback-detail-meta">
                <div>
                  <small>Durum</small>
                  <strong className={badgeClassStatus(selected.status)}>{statusLabels[selected.status as FeedbackStatus] ?? selected.status}</strong>
                </div>
                <div>
                  <small>Tip</small>
                  <strong className={badgeClassType(selected.type)}>{typeLabels[selected.type as FeedbackType] ?? selected.type}</strong>
                </div>
                <div>
                  <small>Sahip</small>
                  <strong>{selected.userName ?? "-"}{selected.userEmail ? ` (${selected.userEmail})` : ""}</strong>
                </div>
              </div>
              <div className="feedback-edit-grid">
                <label className="field-label">
                  Tip
                  <select value={editForm.type} onChange={(event) => setEditForm((current) => ({ ...current, type: event.target.value as FeedbackType }))}>
                    <option value="hata">Hata bildirimi</option>
                    <option value="ozellik">Özellik isteği</option>
                    <option value="genel">Genel geri bildirim</option>
                  </select>
                </label>
                <label className="field-label">
                  Durum
                  <select value={editForm.status} onChange={(event) => setEditForm((current) => ({ ...current, status: event.target.value as FeedbackStatus }))}>
                    <option value="received">Alındı</option>
                    <option value="read">İncelendi</option>
                    <option value="resolved">Çözüldü</option>
                  </select>
                </label>
              </div>
              <label className="field-label">
                Başlık
                <input value={editForm.subject} onChange={(event) => setEditForm((current) => ({ ...current, subject: event.target.value }))} />
              </label>
              <label className="field-label">
                Mesaj
                <textarea rows={8} value={editForm.message} onChange={(event) => setEditForm((current) => ({ ...current, message: event.target.value }))} />
              </label>
              <div className="feedback-detail-actions">
                <button type="submit" disabled={saving}>
                  {saving ? <LoaderCircle className="spin" size={17} /> : null}
                  Kaydet
                </button>
                <button type="button" className="secondary-button" onClick={() => updateStatus(selected.id, "read")}>
                  Okundu
                </button>
                <button type="button" className="secondary-button" onClick={() => updateStatus(selected.id, "resolved")}>
                  Çözüldü
                </button>
                <button type="button" className="danger-button" onClick={() => void handleDelete(selected.id)}>
                  Sil
                </button>
              </div>
            </form>
          ) : (
            <p className="empty">Listeden bir kayıt seçin.</p>
          )}
        </div>
      </section>
    </main>
  );
}
