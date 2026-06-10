"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ArrowLeft, CreditCard, LoaderCircle, Plus, Save, Trash2 } from "lucide-react";
import { AppSidebar } from "@/components/app-sidebar";
import { useRouteAppSidebar } from "@/hooks/use-route-app-sidebar";
import { authLogout, authMe, createSubscriptionPlan, deleteSubscriptionPlan, listAdminSubscriptions, updateSubscriptionPlan, type AuthUser, type SubscriptionPlan, type SubscriptionPlanPayload } from "@/lib/api";

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
  iyzicoProductRef: string;
  iyzicoMonthlyPlanRef: string;
  iyzicoYearlyPlanRef: string;
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
  iyzicoProductRef: "",
  iyzicoMonthlyPlanRef: "",
  iyzicoYearlyPlanRef: ""
};

export default function AdminSubscriptionsPage() {
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  const [loadingAuth, setLoadingAuth] = useState(true);
  const [items, setItems] = useState<SubscriptionPlan[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [form, setForm] = useState<PlanForm>(emptyForm);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const {
    locale,
    groups,
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

  useEffect(() => {
    if (!authUser || authUser.role !== "ADMIN") return;
    void loadPlans();
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
      iyzicoProductRef: plan.iyzicoProductRef ?? "",
      iyzicoMonthlyPlanRef: plan.iyzicoMonthlyPlanRef ?? "",
      iyzicoYearlyPlanRef: plan.iyzicoYearlyPlanRef ?? ""
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
      <AppSidebar
        authUser={authUser}
        collapsed={sidebarCollapsed}
        groups={groups}
        locale={locale}
        onLogout={handleLogout}
        onToggleCollapsed={toggleSidebarCollapsed}
        onToggleNavGroup={toggleNavGroup}
        openNavGroup={openNavGroup}
        pathname={pathname}
      />
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

        <section className="admin-subscription-layout">
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
              <Input label="iyzico urun referansi" value={form.iyzicoProductRef} onChange={(value) => setForm((current) => ({ ...current, iyzicoProductRef: value }))} placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" />
              <Input label="iyzico aylik plan referansi" value={form.iyzicoMonthlyPlanRef} onChange={(value) => setForm((current) => ({ ...current, iyzicoMonthlyPlanRef: value }))} placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" />
              <Input label="iyzico yillik plan referansi" value={form.iyzicoYearlyPlanRef} onChange={(value) => setForm((current) => ({ ...current, iyzicoYearlyPlanRef: value }))} placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" />
            </div>
            <small>
              Iyzico abonelik modulu merchant hesabinizda acik olmalidir. Urun ve odeme planlarini iyzico panelinden olusturup buradaki UUID referans kodlarini girin.
            </small>
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
        </section>
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
    iyzicoProductRef: form.iyzicoProductRef.trim(),
    iyzicoMonthlyPlanRef: form.iyzicoMonthlyPlanRef.trim(),
    iyzicoYearlyPlanRef: form.iyzicoYearlyPlanRef.trim()
  };
}

function lines(value: string) {
  return value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
}

function GateCard({ title, text, linkText }: { title: string; text: string; linkText?: string }) {
  return <main className="auth-shell"><section className="auth-card panel"><LoaderCircle className="spin" size={32} /><h1>{title}</h1><p>{text}</p>{linkText ? <Link className="secondary-button" href="/">{linkText}</Link> : null}</section></main>;
}

function formatPrice(value: number) {
  return new Intl.NumberFormat("tr-TR").format(value) + " TL";
}


