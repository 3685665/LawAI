"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ArrowLeft, Check, CreditCard, LoaderCircle, Lock, Scale } from "lucide-react";
import { AppSidebar } from "@/components/app-sidebar";
import { useRouteAppSidebar } from "@/hooks/use-route-app-sidebar";
import { authLogout, authMe, cancelMySubscription, createBillingCheckout, getMySubscription, listSubscriptions, type AuthUser, type SubscriptionPlan, type UserSubscription } from "@/lib/api";

type BillingCycle = "monthly" | "yearly";
type SubscriptionTab = "plans" | "mine";

export default function SubscriptionsPage() {
  const [authUser, setAuthUser] = useState<AuthUser | null>(null);
  const [loadingAuth, setLoadingAuth] = useState(true);
  const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
  const [currentSubscription, setCurrentSubscription] = useState<UserSubscription | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState("");
  const [error, setError] = useState("");
  const [cycle, setCycle] = useState<BillingCycle>("monthly");
  const [activeTab, setActiveTab] = useState<SubscriptionTab>("plans");
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
    if (!authUser) return;
    let cancelled = false;
    setLoading(true);
    setError("");
    Promise.all([listSubscriptions(), getMySubscription()])
      .then(([items, subscription]) => {
        if (!cancelled) {
          setPlans(items);
          setCurrentSubscription(subscription);
        }
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

  async function selectPlan(plan: SubscriptionPlan) {
    setActionLoading(plan.id);
    setError("");
    try {
      const checkout = await createBillingCheckout({ planId: plan.id, billingCycle: cycle });
      setCurrentSubscription(checkout.subscription);
      window.location.href = checkout.checkoutUrl;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Odeme oturumu olusturulamadi.");
    } finally {
      setActionLoading("");
    }
  }

  async function cancelSubscription() {
    if (!window.confirm("Mevcut aboneliginizi iptal etmek istiyor musunuz?")) return;
    setActionLoading("cancel");
    setError("");
    try {
      const subscription = await cancelMySubscription();
      setCurrentSubscription(subscription);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Abonelik iptal edilemedi.");
    } finally {
      setActionLoading("");
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

        <div className="subscription-tabs panel">
          <button className={activeTab === "plans" ? "active" : ""} type="button" onClick={() => setActiveTab("plans")}>Planlar</button>
          <button className={activeTab === "mine" ? "active" : ""} type="button" onClick={() => setActiveTab("mine")}>Aboneligim</button>
        </div>

        {loading ? (
          <div className="panel subscription-loading"><LoaderCircle className="spin" size={24} /> Planlar yukleniyor...</div>
        ) : activeTab === "plans" ? (
          <section className="pricing-grid">
            {sortedPlans.map((plan) => <PlanCard currentSubscription={currentSubscription} key={plan.id} loading={actionLoading === plan.id} cycle={cycle} onSelect={() => void selectPlan(plan)} plan={plan} />)}
          </section>
        ) : (
          <section className="panel subscription-current-panel">
            <div>
              <span className="section-label">Mevcut abonelik</span>
              <h2>{currentSubscription ? currentSubscription.planName : "Aktif abonelik yok"}</h2>
              <p>{currentSubscription ? `${statusLabel(currentSubscription.status)} - ${cycleLabel(currentSubscription.billingCycle)} odeme` : "Bir plan sectiginizde Stripe odeme sureci baslar ve abonelik kaydiniz burada tutulur."}</p>
            </div>
            {currentSubscription ? (
              <div className="subscription-current-grid">
                <div><span>Durum</span><strong>{statusLabel(currentSubscription.status)}</strong></div>
                <div><span>Donem</span><strong>{cycleLabel(currentSubscription.billingCycle)}</strong></div>
                <div><span>Odeme</span><strong>{currentSubscription.lastPaymentStatus || currentSubscription.provider || "-"}</strong></div>
                <div><span>Baslangic</span><strong>{formatDate(currentSubscription.startsAt)}</strong></div>
                <div><span>Bitis</span><strong>{formatDate(currentSubscription.endsAt)}</strong></div>
              </div>
            ) : null}
            <div className="hero-actions">
              <button className="secondary-button" type="button" onClick={() => setActiveTab("plans")}>Planlara don</button>
              {currentSubscription?.status === "ACTIVE" ? <button className="danger-button" disabled={actionLoading === "cancel"} type="button" onClick={() => void cancelSubscription()}>{actionLoading === "cancel" ? <LoaderCircle className="spin" size={16} /> : null} Iptal et</button> : null}
            </div>
          </section>
        )}

        <p className="subscription-contact">Her turlu sorunuz icin iletisim@lawai.local uzerinden bizimle iletisime gecebilirsiniz.</p>
      </section>
    </main>
  );
}

function PlanCard({ plan, cycle, currentSubscription, loading, onSelect }: { plan: SubscriptionPlan; cycle: BillingCycle; currentSubscription: UserSubscription | null; loading: boolean; onSelect: () => void }) {
  const price = cycle === "yearly" ? plan.yearlyPrice : plan.monthlyPrice;
  const suffix = cycle === "yearly" ? "/ yil" : "/ ay";
  const isContact = price === 0 && plan.slug === "kurumsal";
  const isCurrent = currentSubscription?.planId === plan.id && currentSubscription.status === "ACTIVE";
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
      <button className={plan.highlighted ? "" : "secondary-button"} disabled={loading || isCurrent} onClick={onSelect} type="button">
        {loading ? <LoaderCircle className="spin" size={16} /> : null}
        {isCurrent ? "Mevcut plan" : plan.ctaLabel || `${plan.name} sec`}
      </button>
    </article>
  );
}

function LoadingCard({ title, text }: { title: string; text: string }) {
  return <main className="auth-shell"><section className="auth-card panel"><LoaderCircle className="spin" size={32} /><h1>{title}</h1><p>{text}</p></section></main>;
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

function statusLabel(value: string) {
  const labels: Record<string, string> = {
    ACTIVE: "Aktif",
    PAUSED: "Duraklatildi",
    PENDING_PAYMENT: "Odeme bekliyor",
    PAST_DUE: "Odeme gecikti",
    CANCELLED: "Iptal edildi",
    EXPIRED: "Suresi doldu"
  };
  return labels[value] ?? value;
}


