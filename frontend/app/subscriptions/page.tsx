"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ArrowLeft, BriefcaseBusiness, Check, ChevronRight, ClipboardList, CreditCard, FileSearch, FileText, FileUp, LoaderCircle, Lock, MessageSquareMore, Scale, ScrollText, ShieldAlert, UserRound, Bot } from "lucide-react";
import { authLogout, authMe, listSubscriptions, type AuthUser, type SubscriptionPlan } from "@/lib/api";

type BillingCycle = "monthly" | "yearly";

export default function SubscriptionsPage() {
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  const [loadingAuth, setLoadingAuth] = useState(true);
  const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [cycle, setCycle] = useState<BillingCycle>("monthly");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [adminMenuOpen, setAdminMenuOpen] = useState(false);

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
    if (!authUser) return;
    let cancelled = false;
    setLoading(true);
    setError("");
    listSubscriptions()
      .then((items) => {
        if (!cancelled) setPlans(items);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Abonelik planlari yuklenemedi.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [authUser]);

  const sortedPlans = useMemo(() => [...plans].sort((left, right) => left.sortOrder - right.sortOrder), [plans]);

  async function handleLogout() {
    try {
      await authLogout();
    } finally {
      window.location.href = "/";
    }
  }

  if (loadingAuth) {
    return <LoadingCard title="Abonelik" text="Oturum kontrol ediliyor..." />;
  }

  if (!authUser) {
    return (
      <main className="auth-shell">
        <section className="auth-card panel">
          <h1>Abonelik</h1>
          <p>Bu sayfayi goruntulemek icin oturum gerekli.</p>
          <Link className="secondary-button" href="/">Giris ekranina don</Link>
        </section>
      </main>
    );
  }

  return (
    <main className={`app-shell ${sidebarCollapsed ? "sidebar-collapsed" : ""}`}>
      <SubscriptionSidebar authUser={authUser} adminMenuOpen={adminMenuOpen} active="subscriptions" onLogout={handleLogout} onToggleAdmin={() => setAdminMenuOpen((current) => !current)} onToggleSidebar={() => setSidebarCollapsed((current) => !current)} />
      <section className="workspace subscription-workspace">
        <div className="subscription-hero panel">
          <div className="subscription-logo" aria-hidden="true"><Scale size={34} /><span>LawAI</span></div>
          <span className="eyebrow">Abonelik</span>
          <h1>Abonelik planinizi secin</h1>
          <p>Hukuk asistaninizi ihtiyaciniza gore olceklendirin. Tum fiyatlar KDV harictir.</p>
          <div className="billing-toggle" role="group" aria-label="Odeme donemi">
            <button className={cycle === "monthly" ? "active" : ""} type="button" onClick={() => setCycle("monthly")}>Aylik Odeme</button>
            <button className={cycle === "yearly" ? "active" : ""} type="button" onClick={() => setCycle("yearly")}>Yillik Odeme <span>2 ay hediye</span></button>
          </div>
          <strong className="subscription-saving">Yillik odemede 2 ay ucretsiz kullanim hakki kazanin.</strong>
        </div>

        {error ? <div className="error">{error}</div> : null}

        {loading ? (
          <div className="panel subscription-loading"><LoaderCircle className="spin" size={24} /> Planlar yukleniyor...</div>
        ) : (
          <section className="pricing-grid">
            {sortedPlans.map((plan) => <PlanCard key={plan.id} cycle={cycle} plan={plan} />)}
          </section>
        )}

        <p className="subscription-contact">Her turlu sorunuz icin iletisim@lawai.local uzerinden bizimle iletisime gecebilirsiniz.</p>
      </section>
    </main>
  );
}

function PlanCard({ plan, cycle }: { plan: SubscriptionPlan; cycle: BillingCycle }) {
  const price = cycle === "yearly" ? plan.yearlyPrice : plan.monthlyPrice;
  const suffix = cycle === "yearly" ? "/ yil" : "/ ay";
  const isContact = price === 0 && plan.slug === "kurumsal";
  return (
    <article className={`pricing-card ${plan.highlighted ? "featured" : ""}`}>
      {plan.badge ? <span className="plan-badge">{plan.badge}</span> : null}
      <div className="pricing-card-head">
        <h2>{plan.name}</h2>
        <div className="plan-price">
          {isContact ? <strong>Fiyat icin</strong> : <><strong>{formatPrice(price)}</strong><span>{suffix}</span></>}
        </div>
        <small>{plan.description}</small>
        <p>{[plan.usagePeriod, plan.usageLimit].filter(Boolean).join(" ")}</p>
      </div>
      <div className="plan-divider" />
      <div className="plan-feature-list">
        <span className="section-label">Dahil olan ozellikler</span>
        {plan.features.map((feature) => (
          <div className="plan-feature" key={feature}><Check size={16} /><span>{feature}</span></div>
        ))}
      </div>
      {plan.lockedFeatures.length ? (
        <div className="plan-feature-list locked">
          <span className="section-label">Kilitli ozellikler</span>
          {plan.lockedFeatures.map((feature) => (
            <div className="plan-feature" key={feature}><Lock size={15} /><span>{feature}</span></div>
          ))}
        </div>
      ) : null}
      <button className={plan.highlighted ? "" : "secondary-button"} type="button">{plan.ctaLabel || `${plan.name} sec`}</button>
    </article>
  );
}

function SubscriptionSidebar({ active, authUser, adminMenuOpen, onLogout, onToggleAdmin, onToggleSidebar }: { active: "subscriptions"; authUser: AuthUser; adminMenuOpen: boolean; onLogout: () => void; onToggleAdmin: () => void; onToggleSidebar: () => void }) {
  const base = [
    { href: "/", label: "Asistan", icon: Bot },
    { href: "/", label: "Emsal Arama", icon: FileSearch },
    { href: "/", label: "Dilekce", icon: ScrollText },
    { href: "/", label: "Davalar", icon: BriefcaseBusiness },
    { href: "/", label: "Belge Isleme", icon: FileUp },
    { href: "/", label: "Geri Bildirim", icon: MessageSquareMore },
    { href: "/subscriptions", label: "Abonelik", icon: CreditCard, active: active === "subscriptions" },
    { href: "/", label: "Profil", icon: UserRound }
  ];
  return (
    <aside className="sidebar">
      <div className="brand"><Scale size={28} /><div><strong>LawAI Studio</strong><span>Hukuk AI Calisma Sistemi</span></div></div>
      <button aria-label="Yan menuyu ac/kapat" className="sidebar-toggle" onClick={onToggleSidebar} type="button"><ChevronRight size={18} /></button>
      <div className="nav-label">Uygulamalar</div>
      <nav className="tabs">
        {base.map((item) => {
          const Icon = item.icon;
          return <Link className={item.active ? "active" : ""} href={item.href} key={item.label} title={item.label}><Icon size={18} /><span>{item.label}</span></Link>;
        })}
        {authUser.role === "ADMIN" ? (
          <div className="sidebar-menu-group">
            <button aria-expanded={adminMenuOpen} className="" onClick={onToggleAdmin} type="button"><ShieldAlert size={18} /><span>Yonetim</span><ChevronRight className="sidebar-submenu-chevron" size={15} /></button>
        {adminMenuOpen ? <div className="sidebar-submenu"><Link href="/feedback-management"><MessageSquareMore size={15} /><span>Sikayet Yonetimi</span></Link><Link href="/admin/subscriptions"><CreditCard size={15} /><span>Abonelik Yonetimi</span></Link><Link href="/admin/activity-logs"><ClipboardList size={15} /><span>Islem Loglari</span></Link></div> : null}
          </div>
        ) : null}
      </nav>
      <div className="sidebar-user"><div className="sidebar-user-avatar">{authUser.name.slice(0, 1).toUpperCase()}</div><div><strong>{authUser.name}</strong><span>{authUser.email}</span><span className="sidebar-user-role">{authUser.role === "ADMIN" ? "Yonetici" : "Kullanici"}</span></div><div className="sidebar-user-actions"><button className="secondary-button" onClick={onLogout} type="button">Cikis</button></div></div>
    </aside>
  );
}

function LoadingCard({ title, text }: { title: string; text: string }) {
  return <main className="auth-shell"><section className="auth-card panel"><LoaderCircle className="spin" size={32} /><h1>{title}</h1><p>{text}</p></section></main>;
}

function formatPrice(value: number) {
  return new Intl.NumberFormat("tr-TR").format(value) + " TL";
}


