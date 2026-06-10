"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { LoaderCircle } from "lucide-react";

const CHECKOUT_FORM_KEY = "lawai-checkout-form";

export default function SubscriptionCheckoutPage() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const [formContent, setFormContent] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!token) {
      setError("Odeme oturumu bulunamadi.");
      return;
    }
    const stored = sessionStorage.getItem(`${CHECKOUT_FORM_KEY}:${token}`);
    if (!stored) {
      setError("Odeme formu suresi dolmus olabilir. Lutfen plan secimine geri donun.");
      return;
    }
    setFormContent(stored);
  }, [token]);

  const safeContent = useMemo(() => formContent, [formContent]);

  return (
    <main className="auth-shell">
      <section className="panel subscription-checkout-panel">
        <span className="eyebrow">Odeme</span>
        <h1>iyzico ile guvenli odeme</h1>
        <p>Kart bilgileriniz iyzico altyapisi uzerinden islenir. Odeme tamamlandiginda aboneliginiz otomatik olarak aktif edilir.</p>
        {error ? (
          <div className="error">{error}</div>
        ) : safeContent ? (
          <div className="subscription-checkout-form" dangerouslySetInnerHTML={{ __html: safeContent }} />
        ) : (
          <div className="subscription-loading"><LoaderCircle className="spin" size={24} /> Odeme formu hazirlaniyor...</div>
        )}
        <Link className="secondary-button" href="/subscriptions">Planlara don</Link>
      </section>
    </main>
  );
}
