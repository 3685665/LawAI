"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Link from "next/link";
import { authVerify } from "@/lib/api";

function VerifyPageContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const token = searchParams.get("token");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setError("Doğrulama tokeni bulunamadı.");
      return;
    }
    setLoading(true);
    void (async () => {
      try {
        const res = await authVerify(token);
        setMessage(res.message ?? "Doğrulama başarılı.");
        setTimeout(() => {
          router.push("/");
        }, 3000);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Doğrulama sırasında hata oluştu.");
      } finally {
        setLoading(false);
      }
    })();
  }, [token, router]);

  return (
    <main className="auth-shell">
      <section className="auth-card panel">
        <h1>Hesap Doğrulama</h1>
        {loading ? <p>Doğrulama yapılıyor…</p> : null}
        {message ? (
          <>
            <div className="success">{message}</div>
            <p>Yönlendiriliyorsunuz. Giriş sayfasına yönlendirilmiyorsanız <Link href="/">buraya tıklayın</Link>.</p>
          </>
        ) : null}
        {error ? (
          <>
            <div className="error">{error}</div>
            <p>Doğrulama linki işe yaramıyorsa, kayıt sırasında kullandığınız e-posta ile giriş yapıp tekrar doğrulama isteyebilirsiniz.</p>
          </>
        ) : null}
      </section>
    </main>
  );
}

export default function VerifyPage() {
  return (
    <Suspense
      fallback={
        <main className="auth-shell">
          <section className="auth-card panel">
            <h1>Hesap DoÄŸrulama</h1>
            <p>Yukleniyor...</p>
          </section>
        </main>
      }
    >
      <VerifyPageContent />
    </Suspense>
  );
}
