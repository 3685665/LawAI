"use client";

import { Suspense, FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft, LoaderCircle, Lock } from "lucide-react";
import { authResetPassword } from "@/lib/api";
import { getMessages, isLocale, type Locale } from "@/lib/i18n";

function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = useMemo(() => searchParams.get("token") ?? "", [searchParams]);
  const [locale, setLocale] = useState<Locale>("tr");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [form, setForm] = useState({
    password: "",
    confirmPassword: ""
  });
  const t = getMessages(locale).resetPassword;

  useEffect(() => {
    const storedLocale = window.localStorage.getItem("lawai-locale");
    setLocale(isLocale(storedLocale) ? storedLocale : "tr");
  }, []);

  async function submitReset(event: FormEvent) {
    event.preventDefault();
    setError("");
    setSuccess("");

    if (!token) {
      setError(t.errors.invalidLink);
      return;
    }
    if (!form.password.trim()) {
      setError(t.errors.passwordRequired);
      return;
    }
    if (form.password !== form.confirmPassword) {
      setError(t.errors.passwordMismatch);
      return;
    }

    setLoading(true);
    try {
      await authResetPassword({ token, newPassword: form.password });
      setSuccess(t.success);
      router.push("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : t.errors.updateFailed);
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-card panel auth-reset-card">
        <div className="auth-reset-head">
          <span className="eyebrow">{t.eyebrow}</span>
          <h1>{t.title}</h1>
          <p>{t.description}</p>
        </div>

        <form className="auth-form" onSubmit={submitReset}>
          <div className="auth-form-head">
            <span className="eyebrow">{t.linkEyebrow}</span>
            <h2>{t.linkVerified}</h2>
            <p>{token ? t.enterPassword : t.missingToken}</p>
          </div>

          <label className="field-label">
            {t.newPassword}
            <input autoComplete="new-password" disabled={!token} onChange={(event) => setForm({ ...form, password: event.target.value })} type="password" value={form.password} />
          </label>

          <label className="field-label">
            {t.confirmPassword}
            <input autoComplete="new-password" disabled={!token} onChange={(event) => setForm({ ...form, confirmPassword: event.target.value })} type="password" value={form.confirmPassword} />
          </label>

          {error ? <div className="error">{error}</div> : null}
          {success ? <div className="auth-preview">{success}</div> : null}

          <div className="auth-actions">
            <button disabled={loading || !token} type="submit">
              {loading ? <LoaderCircle className="spin" size={17} /> : <Lock size={17} />}
              {t.submit}
            </button>
            <button className="secondary-button" type="button" onClick={() => router.push("/")}>
              <ArrowLeft size={17} />
              {t.backLogin}
            </button>
          </div>
        </form>
      </section>
    </main>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense
      fallback={
        <main className="auth-shell">
          <section className="auth-card panel">
            <LoaderCircle className="spin" size={32} />
            <h1>LawAI Studio</h1>
            <p>{getMessages("tr").resetPassword.loading}</p>
          </section>
        </main>
      }
    >
      <ResetPasswordForm />
    </Suspense>
  );
}
