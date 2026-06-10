# AGENTS — Proje İçin Kısa Yönergeler (Türkçe)

Bu dosya, AI geliştirme ajanlarının bu depo içinde hızlıca üretken olmasına yönelik kısa, eyleme dönüştürülebilir kurallar ve bağlantılar içerir. Amaç: ajanın hangi komutları çalıştıracağını, hangi dizinlerin önemli olduğunu ve hangi proje kurallarına uyması gerektiğini açıkça belirtmektir.

Özet (kısa)
- Proje: Mono-repo — `backend` (Python API), `frontend` (Next.js), ek araçlar `pdf_vector_ingest` ve `springboot-backend`.
- Hedef ajan davranışı: Değişiklikleri minimal tut, mevcut dökümantasyona link ver, testleri çalıştır ve PR açıklaması için kısa özet oluştur.

Hızlı Çalıştırma (yerel) — temel komutlar
- Altyapı: `docker-compose up -d postgres opensearch` (PostgreSQL :5433, OpenSearch :9200)
- Backend (Python): `cd backend` → `python -m uvicorn app.main:app --reload --port 8000` (veya `pip install -r requirements.txt` önce)
- Spring microservices: `cd springboot-backend` → `.\mvnw.cmd install -DskipTests` → `start-microservices.bat` (API Gateway :8080)
- Frontend (Next.js): `cd frontend` → `npm install` → `npm run dev`
- Testler (örnek): `pytest tests` veya `pytest tests/test_api.py`

Önemli Dosyalar / Dizinler
- Backend ana giriş: `backend/app/main.py` (API rotaları ve uygulama başlatma)
- Backend ayarlar: `backend/app/settings.py`
- Frontend ana: `frontend/app/page.tsx`, konfigürasyon: `frontend/package.json`
- Proje seviyesinde compose: `docker-compose.yml` (yalnızca postgres + opensearch)
- Spring microservices: `springboot-backend/MICROSERVICES.md`, `springboot-backend/start-microservices.bat`
- Entegre ingest aracı: `pdf_vector_ingest/README.md`

Ajana Öneriler (kısa kurallar)
1. Yeni dosya eklerken veya büyük değişiklik yaparken önce testleri çalıştır. Değişiklik küçükse ilgili testleri çalıştır.
2. Mevcut dökümantasyonu kopyalamaktan kaçın; bunun yerine ilgili dosyaya veya README'ye link ver.
3. Backend için Python paketleri `backend/requirements.txt` içinde listelenir — paket eklemeyi önerirken bu dosyayı güncelle.
4. Frontend değişiklikleri için `npm run build` veya `npm run dev` komutlarını kullanıp hataları raporla.
5. Stil ve format: Python için `black`/`isort` kullanılmışsa README'lerde belirtilmemişse bile proje stiline uygun olmaya çalış.

Yapılabilecek geliştirmeler
- .github/copilot-instructions.md veya daha kapsamlı rol-temelli agent yönergeleri eklemek: `backend` / `frontend` / `ingest` için ayrı kısımlar.

İletişim
- Bu dosya güncellendikçe ajanlar otomatik olarak en son bilgiyi kullanmalıdır; değişiklik yapmamı isterseniz hangi alanı (örn. test komutları, build adımları, oryantasyon) Türkçeleştireyim belirtin.
