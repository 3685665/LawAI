"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Link from "next/link";
import { authVerify } from "@/lib/api";
import { getMessages, isLocale, type Locale } from "@/lib/i18n";

function useStoredLocale(): Locale {
  const [locale, setLocale] = useState<Locale>("tr");

  useEffect(() => {
    const storedLocale = window.localStorage.getItem("lawai-locale");
    if (isLocale(storedLocale)) {
      setLocale(storedLocale);
    }
  }, []);

  return locale;
}

function VerifyPageContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const locale = useStoredLocale();
  const t = getMessages(locale).verify;
  const token = searchParams.get("token");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setError(t.missingToken);
      return;
    }
    setLoading(true);
    void (async () => {
      try {
        const res = await authVerify(token);
        setMessage(res.message ?? t.success);
        setTimeout(() => {
          router.push("/");
        }, 3000);
      } catch (err) {
        setError(err instanceof Error ? err.message : t.failed);
      } finally {
        setLoading(false);
      }
    })();
  }, [token, router, t]);

  return (
    <main className="auth-shell">
      <section className="auth-card panel">
        <h1>{t.title}</h1>
        {loading ? <p>{t.loading}</p> : null}
        {message ? (
          <>
            <div className="success">{message}</div>
            <p>
              {t.redirecting} <Link href="/">{t.redirectLink}</Link>.
            </p>
          </>
        ) : null}
        {error ? (
          <>
            <div className="error">{error}</div>
            <p>{t.help}</p>
          </>
        ) : null}
      </section>
    </main>
  );
}

function VerifyFallback() {
  const locale = useStoredLocale();
  const t = getMessages(locale).verify;

  return (
    <main className="auth-shell">
      <section className="auth-card panel">
        <h1>{t.title}</h1>
        <p>{t.pageLoading}</p>
      </section>
    </main>
  );
}

export default function VerifyPage() {
  return (
    <Suspense fallback={<VerifyFallback />}>
      <VerifyPageContent />
    </Suspense>
  );
}
