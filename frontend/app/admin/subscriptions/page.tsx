"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ArrowLeft, BriefcaseBusiness, Bot, ChevronRight, ClipboardList, CreditCard, FileSearch, FileText, FileUp, LoaderCircle, MessageSquareMore, Plus, Save, Scale, ScrollText, ShieldAlert, Trash2, UserRound } from "lucide-react";
import { authLogout, authMe, createSubscriptionPlan, deleteSubscriptionPlan, listAdminSubscriptions, listAdminUserSubscriptions, updateSubscriptionPlan, updateUserSubscriptionStatus, type AuthUser, type SubscriptionPlan, type SubscriptionPlanPayload, type UserSubscription } from "@/lib/api";

type AdminSubscriptionTab = "plans" | "users";
type PlanForm = {
  name: string;
  slug: string;
  badge: string;
  description: string;
  monthlyPrice: string;
  yearlyPrice: string;
  currency: string;
  usageLimit: string;
  usagePeriod: string;
  highlighted: boolean;
  active: boolean;
  sortOrder: string;
  featuresText: string;
  lockedFeaturesText: string;
  ctaLabel: string;
  stripeProductId: string;
  stripeMonthlyPriceId: string;
  stripeYearlyPriceId: string;
};

const emptyForm: PlanForm = {
  name: "",
  slug: "",
  badge: "",
  description: "",
  monthlyPrice: "0",
  yearlyPrice: "0",
  currency: "TRY",
  usageLimit: "",
  usagePeriod: "",
  highlighted: false,
  active: true,
  sortOrder: "100",
  featuresText: "",
  lockedFeaturesText: "",
  ctaLabel: "",
  stripeProductId: "",
  stripeMonthlyPriceId: "",
  stripeYearlyPriceId: ""
};

export default function AdminSubscriptionsPage() {
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  const [loadingAuth, setLoadingAuth] = useState(true);
  const [items, setItems] = useState<SubscriptionPlan[]>([]);
  const [userSubscriptions, setUserSubscriptions] = useState<UserSubscription[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [form, setForm] = useState<PlanForm>(emptyForm);
  const [loading, setLoading] = useState(false);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [activeTab, setActiveTab] = useState<AdminSubscriptionTab>("plans");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [adminMenuOpen, setAdminMenuOpen] = useState(true);

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
    void loadPlans();
    void loadUserSubscriptions();
  }, [authUser]);

  const sortedItems = useMemo(() => [...items].sort((left, right) => left.sortOrder - right.sortOrder), [items]);
  const selected = useMemo(() => items.find((item) => item.id === selectedId) ?? null, [items, selectedId]);

  async function loadPlans() {
    setLoading(true);
    setError("");
    try {
      const plans = await listAdminSubscriptions();
      setItems(plans);
      if (!selectedId && plans.length) {
        selectPlan(plans[0]);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Abonelik planlari yuklenemedi.");
    } finally {
      setLoading(false);
    }
  }

  async function loadUserSubscriptions() {
    setLoadingUsers(true);
    setError("");
    try {
      const subscriptions = await listAdminUserSubscriptions();
      setUserSubscriptions(subscriptions);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Kullanici abonelikleri yuklenemedi.");
    } finally {
      setLoadingUsers(false);
    }
  }

  function selectPlan(plan: SubscriptionPlan) {
    setSelectedId(plan.id);
    setForm({
      name: plan.name,
      slug: plan.slug,
      badge: plan.badge ?? "",
      description: plan.description ?? "",
      monthlyPrice: String(plan.monthlyPrice),
      yearlyPrice: String(plan.yearlyPrice),
      currency: plan.currency || "TRY",
      usageLimit: plan.usageLimit ?? "",
      usagePeriod: plan.usagePeriod ?? "",
      highlighted: plan.highlighted,
      active: plan.active,
      sortOrder: String(plan.sortOrder),
      featuresText: plan.features.join("\n"),
      lockedFeaturesText: plan.lockedFeatures.join("\n"),
      ctaLabel: plan.ctaLabel ?? "",
      stripeProductId: plan.stripeProductId ?? "",
      stripeMonthlyPriceId: plan.stripeMonthlyPriceId ?? "",
      stripeYearlyPriceId: plan.stripeYearlyPriceId ?? ""
    });
  }

  function newPlan() {
    setSelectedId(null);
    setForm(emptyForm);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      const payload = toPayload(form);
      const saved = selectedId ? await updateSubscriptionPlan(selectedId, payload) : await createSubscriptionPlan(payload);
      const plans = await listAdminSubscriptions();
      setItems(plans);
      selectPlan(plans.find((item) => item.id === saved.id) ?? saved);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Abonelik plani kaydedilemedi.");
    } finally {
      setSaving(false);
    }
  }

  async function removeSelected() {
    if (!selectedId || !window.confirm("Bu abonelik planini silmek istiyor musunuz?")) return;
    setSaving(true);
    setError("");
    try {
      await deleteSubscriptionPlan(selectedId);
      const plans = await listAdminSubscriptions();
      setItems(plans);
      if (plans.length) selectPlan(plans[0]); else newPlan();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Abonelik plani silinemedi.");
    } finally {
      setSaving(false);
    }
  }

  async function changeUserSubscriptionStatus(id: string, status: UserSubscription["status"]) {
    setSaving(true);
    setError("");
    try {
      const updated = await updateUserSubscriptionStatus(id, status);
      setUserSubscriptions((current) => current.map((item) => item.id === id ? updated : item));
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
    return <GateCard title="Abonelik Yonetimi" text="Yetki kontrol ediliyor..." />;
  }

  if (!authUser) {
    return <GateCard title="Abonelik Yonetimi" text="Oturum gerekli." linkText="Ana ekrana don" />;
  }

  if (authUser.role !== "ADMIN") {
    return <GateCard title="Abonelik Yonetimi" text="Bu sayfa yalnizca yonetici hesabina aciktir." linkText="Ana ekrana don" />;
  }

  return (
    <main className={`app-shell ${sidebarCollapsed ? "sidebar-collapsed" : ""}`}>
      <AdminSidebar authUser={authUser} adminMenuOpen={adminMenuOpen} onLogout={handleLogout} onToggleAdmin={() => setAdminMenuOpen((current) => !current)} onToggleSidebar={() => setSidebarCollapsed((current) => !current)} />
      <section className="workspace admin-subscription-workspace">
        <div className="topbar">
          <div>
            <span className="eyebrow">Yonetici paneli</span>
            <h1>Abonelik Yonetimi</h1>
            <p>Kullanici tarafinda gorunen abonelik kartlarini buradan ekleyin, siralayin ve guncelleyin.</p>
          </div>
          <div className="hero-actions">
            <Link className="ghost-button" href="/subscriptions"><CreditCard size={17} /> Kullanici gorunumu</Link>
            <Link className="ghost-button" href="/"><ArrowLeft size={17} /> Ana uygulama</Link>
          </div>
        </div>

        {error ? <div className="error">{error}</div> : null}

        <div className="subscription-tabs panel">
          <button className={activeTab === "plans" ? "active" : ""} type="button" onClick={() => setActiveTab("plans")}>Planlar</button>
          <button className={activeTab === "users" ? "active" : ""} type="button" onClick={() => setActiveTab("users")}>Kullanici Abonelikleri</button>
        </div>

        {activeTab === "plans" ? <section className="admin-subscription-layout">
          <aside className="panel subscription-admin-list">
            <div className="section-head">
              <div><span className="section-label">Planlar</span><h3>{items.length} kayit</h3></div>
              <button type="button" onClick={newPlan}><Plus size={16} /> Yeni</button>
            </div>
            {loading ? <p className="empty">Planlar yukleniyor...</p> : null}
            <div className="subscription-plan-list">
              {sortedItems.map((item) => (
                <button className={item.id === selectedId ? "active" : ""} key={item.id} onClick={() => selectPlan(item)} type="button">
                  <strong>{item.name}</strong>
                  <span>{formatPrice(item.monthlyPrice)} / ay</span>
                  <em>{item.active ? "Aktif" : "Pasif"} - Sira {item.sortOrder}</em>
                </button>
              ))}
            </div>
          </aside>

          <form className="panel subscription-admin-form" onSubmit={submit}>
            <div className="section-head">
              <div><span className="section-label">Duzenleme</span><h3>{selected ? selected.name : "Yeni abonelik plani"}</h3></div>
              <div className="hero-actions">
                {selectedId ? <button className="danger-button" disabled={saving} onClick={() => void removeSelected()} type="button"><Trash2 size={16} /> Sil</button> : null}
                <button disabled={saving} type="submit">{saving ? <LoaderCircle className="spin" size={16} /> : <Save size={16} />} Kaydet</button>
              </div>
            </div>
            <div className="subscription-admin-grid">
              <Input label="Plan adi" value={form.name} onChange={(value) => setForm((current) => ({ ...current, name: value }))} />
              <Input label="Slug" value={form.slug} onChange={(value) => setForm((current) => ({ ...current, slug: value }))} />
              <Input label="Rozet" value={form.badge} onChange={(value) => setForm((current) => ({ ...current, badge: value }))} placeholder="ONERILEN" />
              <Input label="Para birimi" value={form.currency} onChange={(value) => setForm((current) => ({ ...current, currency: value }))} />
              <Input label="Aylik fiyat" type="number" value={form.monthlyPrice} onChange={(value) => setForm((current) => ({ ...current, monthlyPrice: value }))} />
              <Input label="Yillik fiyat" type="number" value={form.yearlyPrice} onChange={(value) => setForm((current) => ({ ...current, yearlyPrice: value }))} />
              <Input label="Kullanim periyodu" value={form.usagePeriod} onChange={(value) => setForm((current) => ({ ...current, usagePeriod: value }))} />
              <Input label="Kullanim hakki" value={form.usageLimit} onChange={(value) => setForm((current) => ({ ...current, usageLimit: value }))} />
              <Input label="Sira" type="number" value={form.sortOrder} onChange={(value) => setForm((current) => ({ ...current, sortOrder: value }))} />
              <Input label="Buton metni" value={form.ctaLabel} onChange={(value) => setForm((current) => ({ ...current, ctaLabel: value }))} />
              <Input label="Stripe Product ID" value={form.stripeProductId} onChange={(value) => setForm((current) => ({ ...current, stripeProductId: value }))} placeholder="prod_..." />
              <Input label="Stripe aylik Price ID" value={form.stripeMonthlyPriceId} onChange={(value) => setForm((current) => ({ ...current, stripeMonthlyPriceId: value }))} placeholder="price_..." />
              <Input label="Stripe yillik Price ID" value={form.stripeYearlyPriceId} onChange={(value) => setForm((current) => ({ ...current, stripeYearlyPriceId: value }))} placeholder="price_..." />
            </div>
            <label className="field-label">Aciklama<textarea rows={3} value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} /></label>
            <div className="subscription-admin-grid textareas">
              <label className="field-label">Dahil olan ozellikler<textarea rows={10} value={form.featuresText} onChange={(event) => setForm((current) => ({ ...current, featuresText: event.target.value }))} placeholder="Her satira bir ozellik" /></label>
              <label className="field-label">Kilitli ozellikler<textarea rows={10} value={form.lockedFeaturesText} onChange={(event) => setForm((current) => ({ ...current, lockedFeaturesText: event.target.value }))} placeholder="Her satira bir ozellik" /></label>
            </div>
            <div className="subscription-switch-row">
              <label><input checked={form.active} onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))} type="checkbox" /> Aktif</label>
              <label><input checked={form.highlighted} onChange={(event) => setForm((current) => ({ ...current, highlighted: event.target.checked }))} type="checkbox" /> One cikan plan</label>
            </div>
          </form>
        </section> : (
          <section className="panel user-subscription-admin-panel">
            <div className="section-head">
              <div><span className="section-label">Kullanici abonelikleri</span><h3>{userSubscriptions.length} kayit</h3></div>
              <button className="secondary-button" type="button" onClick={() => void loadUserSubscriptions()}>{loadingUsers ? <LoaderCircle className="spin" size={16} /> : null} Yenile</button>
            </div>
            {loadingUsers ? <p className="empty">Kullanici abonelikleri yukleniyor...</p> : null}
            <div className="user-subscription-table">
              <div className="user-subscription-row head"><span>Kullanici</span><span>Plan</span><span>Donem</span><span>Bitis</span><span>Durum</span></div>
              {userSubscriptions.map((item) => (
                <div className="user-subscription-row" key={item.id}>
                  <div><strong>{item.userName}</strong><small>{item.userEmail}</small></div>
                  <div><strong>{item.planName}</strong><small>{item.planId}</small></div>
                  <span>{cycleLabel(item.billingCycle)}</span>
                  <span>{formatDate(item.endsAt)}</span>
                  <select disabled={saving} value={item.status} onChange={(event) => void changeUserSubscriptionStatus(item.id, event.target.value as UserSubscription["status"])}>
                    <option value="ACTIVE">Aktif</option>
                    <option value="PENDING_PAYMENT">Odeme bekliyor</option>
                    <option value="PAST_DUE">Odeme gecikti</option>
                    <option value="PAUSED">Duraklatildi</option>
                    <option value="CANCELLED">Iptal</option>
                    <option value="EXPIRED">Suresi doldu</option>
                  </select>
                </div>
              ))}
              {!loadingUsers && !userSubscriptions.length ? <p className="empty">Henuz kullanici aboneligi yok.</p> : null}
            </div>
          </section>
        )}
      </section>
    </main>
  );
}

function Input({ label, value, onChange, placeholder, type = "text" }: { label: string; value: string; onChange: (value: string) => void; placeholder?: string; type?: string }) {
  return <label className="field-label">{label}<input type={type} value={value} placeholder={placeholder} onChange={(event) => onChange(event.target.value)} /></label>;
}

function toPayload(form: PlanForm): SubscriptionPlanPayload {
  return {
    name: form.name.trim(),
    slug: form.slug.trim(),
    badge: form.badge.trim(),
    description: form.description.trim(),
    monthlyPrice: Number(form.monthlyPrice || 0),
    yearlyPrice: Number(form.yearlyPrice || 0),
    currency: form.currency.trim() || "TRY",
    usageLimit: form.usageLimit.trim(),
    usagePeriod: form.usagePeriod.trim(),
    highlighted: form.highlighted,
    active: form.active,
    sortOrder: Number(form.sortOrder || 0),
    features: lines(form.featuresText),
    lockedFeatures: lines(form.lockedFeaturesText),
    ctaLabel: form.ctaLabel.trim(),
    stripeProductId: form.stripeProductId.trim(),
    stripeMonthlyPriceId: form.stripeMonthlyPriceId.trim(),
    stripeYearlyPriceId: form.stripeYearlyPriceId.trim()
  };
}

function lines(value: string) {
  return value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
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
          {adminMenuOpen ? <div className="sidebar-submenu"><Link href="/feedback-management"><MessageSquareMore size={15} /><span>Sikayet Yonetimi</span></Link><Link className="active" href="/admin/subscriptions"><CreditCard size={15} /><span>Abonelik Yonetimi</span></Link><Link href="/admin/activity-logs"><ClipboardList size={15} /><span>Islem Loglari</span></Link></div> : null}
        </div>
      </nav>
      <div className="sidebar-user"><div className="sidebar-user-avatar">{authUser.name.slice(0, 1).toUpperCase()}</div><div><strong>{authUser.name}</strong><span>{authUser.email}</span><span className="sidebar-user-role">Yonetici</span></div><div className="sidebar-user-actions"><button className="secondary-button" onClick={onLogout} type="button">Cikis</button></div></div>
    </aside>
  );
}

function GateCard({ title, text, linkText }: { title: string; text: string; linkText?: string }) {
  return <main className="auth-shell"><section className="auth-card panel"><LoaderCircle className="spin" size={32} /><h1>{title}</h1><p>{text}</p>{linkText ? <Link className="secondary-button" href="/">{linkText}</Link> : null}</section></main>;
}

function formatPrice(value: number) {
  return new Intl.NumberFormat("tr-TR").format(value) + " TL";
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString("tr-TR");
}

function cycleLabel(value: string) {
  return value === "yearly" ? "Yillik" : "Aylik";
}


