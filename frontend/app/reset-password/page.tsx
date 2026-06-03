"use client";

import { Suspense, FormEvent, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft, LoaderCircle, Lock } from "lucide-react";
import { authResetPassword } from "@/lib/api";

function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = useMemo(() => searchParams.get("token") ?? "", [searchParams]);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [form, setForm] = useState({
    password: "",
    confirmPassword: ""
  });

  async function submitReset(event: FormEvent) {
    event.preventDefault();
    setError("");
    setSuccess("");

    if (!token) {
      setError("Sifirlama baglantisi gecersiz.");
      return;
    }
    if (!form.password.trim()) {
      setError("Yeni sifre gerekli.");
      return;
    }
    if (form.password !== form.confirmPassword) {
      setError("Sifreler eslesmiyor.");
      return;
    }

    setLoading(true);
    try {
      await authResetPassword({ token, newPassword: form.password });
      setSuccess("Sifre guncellendi. Giris ekranina yonlendiriliyorsunuz...");
      router.push("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sifre guncellenemedi.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-card panel auth-reset-card">
        <div className="auth-reset-head">
          <span className="eyebrow">Sifre yenileme</span>
          <h1>Yeni sifrenizi belirleyin.</h1>
          <p>Bu sayfa e-posta ile gelen sifirlama baglantisindan acilir. Islem tamamlandiginda hesaba tekrar giris yapabilirsiniz.</p>
        </div>

        <form className="auth-form" onSubmit={submitReset}>
          <div className="auth-form-head">
            <span className="eyebrow">Baglanti</span>
            <h2>Baglanti dogrulandi</h2>
            <p>{token ? "Lutfen yeni sifrenizi belirleyin." : "Token bulunamadi. Lutfen e-postadaki baglantiyi tekrar acin."}</p>
          </div>

          <label className="field-label">
            Yeni sifre
            <input autoComplete="new-password" disabled={!token} onChange={(event) => setForm({ ...form, password: event.target.value })} type="password" value={form.password} />
          </label>

          <label className="field-label">
            Yeni sifre tekrar
            <input autoComplete="new-password" disabled={!token} onChange={(event) => setForm({ ...form, confirmPassword: event.target.value })} type="password" value={form.confirmPassword} />
          </label>

          {error ? <div className="error">{error}</div> : null}
          {success ? <div className="auth-preview">{success}</div> : null}

          <div className="auth-actions">
            <button disabled={loading || !token} type="submit">
              {loading ? <LoaderCircle className="spin" size={17} /> : <Lock size={17} />}
              Sifreyi guncelle
            </button>
            <button className="secondary-button" type="button" onClick={() => router.push("/")}>
              <ArrowLeft size={17} />
              Giris ekranina don
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
            <p>Sifirlama sayfasi yukleniyor...</p>
          </section>
        </main>
      }
    >
      <ResetPasswordForm />
    </Suspense>
  );
}
