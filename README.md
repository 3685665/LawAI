# LawAI Next LangChain

LawAI, avukat ve hukuk profesyonelleri için yapay zeka destekli bir çalışma alanıdır. Proje **mono-repo** yapısındadır ve sorumlulukları katmanlara ayırır:

| Katman | Teknoloji | Rol |
|--------|-----------|-----|
| **Frontend** | Next.js 15, React 19, TypeScript | Kullanıcı arayüzü, oturum yönetimi, API istemcisi |
| **Spring Boot** | Java 24, microservices | REST API, iş kuralları, dosya kabulü, orkestrasyon |
| **Python AI** | FastAPI, LangChain | LLM, embedding, RAG, vektör arama |
| **Altyapı** | PostgreSQL + pgvector, OpenSearch | Kalıcı veri, vektör ve tam metin indeksi |

Temel ilke: **web/API katmanı Spring Boot'ta, AI/RAG katmanı Python'da** çalışır. Frontend yalnızca API Gateway (`:8080`) ile konuşur; Spring servisleri gerektiğinde Python servisine (`:8000`) HTTP çağrısı yapar.

---

## Mimari Genel Bakış

```mermaid
flowchart TB
  subgraph client [İstemci]
    FE[Next.js Frontend :3000]
  end

  subgraph gateway [API Katmanı]
    GW[API Gateway :8080]
  end

  subgraph spring [Spring Microservices]
    AUTH[auth-service :8081]
    BILL[billing-service :8082]
    LEGAL[legal-service :8083]
    DOC[document-service :8084]
    RES[research-service :8085]
    PLAT[platform-service :8086]
  end

  subgraph ai [AI Katmanı]
    PY[Python AI :8000]
    OLL[Ollama / OpenAI / Gemini]
  end

  subgraph data [Veri Katmanı]
    PG[(PostgreSQL + pgvector :5433)]
    OS[OpenSearch :9200]
  end

  FE -->|REST + cookie oturum| GW
  GW --> AUTH & BILL & LEGAL & DOC & RES & PLAT
  LEGAL & RES & DOC -->|AI_BASE_URL| PY
  PY --> OLL
  AUTH & BILL & LEGAL & RES & PLAT & DOC --> PG
  DOC & RES --> OS
  PY --> PG
```

### Servisler arası iletişim

- **Oturum doğrulama:** `auth-service` `/internal/session/validate` endpoint'i; diğer servisler `lawai-common` içindeki `AuthSessionClient` ile çağırır.
- **Aktivite logu:** Servisler `platform-service` `/internal/activity-logs` endpoint'ine HTTP POST yapar (`ActivityLogClient`).
- **AI çağrıları:** `legal-service`, `research-service` ve `document-service` Python servisine `AI_BASE_URL` (varsayılan `http://localhost:8000/api`) üzerinden istek atar.

Detaylı microservice dokümantasyonu: [`springboot-backend/MICROSERVICES.md`](springboot-backend/MICROSERVICES.md)

---

## Uygulama Modülleri

### 1. Frontend (`frontend/`)

**Ne yapar:** LawAI Studio kullanıcı arayüzü. Tek sayfa uygulaması (`app/page.tsx`) üzerinde sekmeli çalışma alanları sunar; ayrı sayfalar abonelik, aktivite logları ve yönetim ekranları içindir.

**Teknoloji:** Next.js 15 App Router, React 19, MUI DataGrid, Lucide ikonları, çok dilli destek (`lib/i18n.ts`).

**Ana ekranlar ve sekmeler:**

| Sekme / Sayfa | Açıklama |
|---------------|----------|
| **Asistan (Chat)** | Hukuki soru-cevap; RAG ile içtihat atıfları |
| **İçtihat Arama** | Yargıtay, Danıştay, AYM karar arama ve özetleme |
| **Dilekçe Taslağı** | Olay özeti ve taleplerden dilekçe üretimi |
| **Dava Dosyaları** | Dava şablonları, belge kontrol listesi, ilerleme takibi |
| **Belge İşleme** | PDF/Word/TXT ön kontrol ve bilgi bankasına indeksleme |
| **Hukuki Araştırma** | Çok kaynaklı araştırma (mevzuat + içtihat + web), SSE ile canlı ilerleme |
| **Abonelik** (`/subscriptions`) | Plan listesi, iyzico ödeme akışı |
| **Aktivite Logları** (`/activity-logs`) | Kullanıcının kendi işlem geçmişi |
| **Yönetim** (ADMIN) | Kullanıcı yönetimi, geri bildirim, abonelik planları, toplu belge işleri, sistem logları |

**API iletişimi:** `lib/api.ts` üzerinden `NEXT_PUBLIC_API_BASE` (varsayılan `http://localhost:8080/api`) adresine `credentials: "include"` ile istek atılır; oturum çerezi gateway üzerinden taşınır.

**Akış (tipik kullanıcı oturumu):**

```mermaid
sequenceDiagram
  participant U as Kullanıcı
  participant FE as Frontend
  participant GW as API Gateway
  participant AUTH as auth-service
  participant LEGAL as legal-service

  U->>FE: Giriş yap
  FE->>GW: POST /api/auth/login
  GW->>AUTH: Oturum oluştur
  AUTH-->>FE: Set-Cookie + kullanıcı bilgisi
  U->>FE: Hukuki soru sor
  FE->>GW: POST /api/chat
  GW->>LEGAL: Oturum doğrula + AI çağır
  LEGAL-->>FE: Yanıt + atıflar + sessionId
```

---

### 2. Python AI Servisi (`backend/`)

**Ne yapar:** Tüm yapay zeka iş yükünü üstlenir — sohbet, içtihat özetleme, dilekçe üretimi, bilgi bankası indeksleme, araştırma sentezi ve belge metin çıkarımı.

**Teknoloji:** FastAPI, LangChain text splitter, pgvector (PostgreSQL), çoklu AI sağlayıcı adaptörü.

**Giriş noktası:** `app/main.py`

**AI sağlayıcıları** (`app/settings.py`):

| Sağlayıcı | Ortam değişkeni | Kullanım |
|-----------|-----------------|----------|
| **Ollama** (varsayılan) | `LAWAI_AI_PROVIDER=ollama` | Yerel LLM + embedding |
| **OpenAI** | `LAWAI_AI_PROVIDER=openai` | GPT + text-embedding |
| **Gemini** | `LAWAI_AI_PROVIDER=gemini` | Google modelleri |
| **local** | `LAWAI_AI_PROVIDER=local` | Çevrimdışı deterministik embedding + kural tabanlı yanıt |

**Vektör deposu:** `app/services/vector_store.py` — `knowledge_documents` tablosunda pgvector HNSW indeksi ile kosinüs benzerliği araması.

**Endpoint'ler:**

| Endpoint | İşlev |
|----------|-------|
| `GET /api/health` | Sağlık kontrolü |
| `POST /api/chat` | RAG destekli hukuki sohbet |
| `POST /api/precedents/summarize` | Karar metni özeti |
| `POST /api/precedents/apply-to-petition` | Emsal kararı dilekçe bağlamına uygulama |
| `POST /api/petitions` | Dilekçe taslağı üretimi |
| `POST /api/documents/extract-text` | PDF/Word/TXT metin çıkarımı |
| `POST /api/knowledge/documents` | Metin parçalarını embedding ile indeksleme |
| `POST /api/knowledge/seed-precedents` | Örnek emsal verilerini yükleme |
| `POST /api/research/synthesize` | Çok kaynaklı araştırma sonuçlarını sentezleme |

**Sohbet (RAG) akışı:**

```mermaid
flowchart LR
  Q[Soru] --> E[Embedding üret]
  E --> V[pgvector arama]
  V --> C[En yakın içtihatlar]
  C --> L[LLM prompt + atıflar]
  L --> A[Yanıt + citations]
```

1. Kullanıcı sorusu embedding'e dönüştürülür.
2. `knowledge_documents` tablosunda vektör benzerliği ile en ilgili parçalar bulunur.
3. Bulunan içtihatlar LLM'e bağlam olarak verilir; yanıt atıflarla birlikte döner.
4. AI sağlayıcısı yanıt veremezse yerel kural tabanlı mod devreye girer.

**Belge indeksleme akışı:**

1. Spring `legal-service` dosyayı kabul eder, Python'a metin çıkarımı için gönderir.
2. Metin LangChain `RecursiveCharacterTextSplitter` ile parçalara ayrılır.
3. Her parça embedding alır ve pgvector'a yazılır.
4. Sonraki sohbet sorgularında bu parçalar RAG bağlamı olarak kullanılır.

---

### 3. Spring Boot Microservices (`springboot-backend/`)

**Ne yapar:** Üretim API katmanı. Kimlik doğrulama, abonelik, dava yönetimi, belge işleme, araştırma orkestrasyonu ve platform işlemlerini ayrı deploy edilebilir servisler halinde sunar.

#### api-gateway (`:8080`)

Tek giriş noktası. CORS, route yönlendirme ve yanıt başlığı birleştirme yapar. Tüm `/api/**` istekleri ilgili servise proxy edilir.

#### auth-service (`:8081`)

**Sorumluluk:** Kayıt, e-posta doğrulama, oturum (cookie), Google OAuth, şifre sıfırlama, kullanıcı CRUD.

**Akış — kayıt ve giriş:**

```mermaid
sequenceDiagram
  participant FE as Frontend
  participant AUTH as auth-service
  participant PG as PostgreSQL

  FE->>AUTH: POST /api/auth/register
  AUTH->>PG: Kullanıcı + doğrulama token'ı kaydet
  AUTH-->>FE: Doğrulama e-postası (SMTP)
  FE->>AUTH: GET /api/auth/verify?token=...
  AUTH->>PG: verified=true
  FE->>AUTH: POST /api/auth/login
  AUTH-->>FE: HttpOnly session cookie
```

**Önemli endpoint'ler:** `/api/auth/login`, `/api/auth/register`, `/api/auth/google`, `/api/auth/me`, `/api/auth/users` (ADMIN).

#### billing-service (`:8082`)

**Sorumluluk:** Abonelik planları, kullanıcı abonelikleri, iyzico ödeme entegrasyonu.

**Akış — abonelik satın alma:**

1. Kullanıcı frontend'de plan seçer → `POST /api/billing/checkout`
2. `billing-service` iyzico checkout oturumu oluşturur
3. Kullanıcı iyzico ödeme sayfasına yönlendirilir (`/subscriptions/checkout`)
4. Ödeme sonrası iyzico callback/webhook ile abonelik durumu güncellenir (`ACTIVE`, `PAST_DUE`, vb.)

**Endpoint'ler:** `/api/subscriptions`, `/api/subscriptions/me`, `/api/billing/checkout`, `/api/billing/iyzico/callback`

#### legal-service (`:8083`)

**Sorumluluk:** Çekirdek hukuk işlevleri — sohbet, içtihat, dilekçe, belge analizi/indeksleme, dava dosyası yönetimi.

| Endpoint grubu | Akış |
|----------------|------|
| `/api/chat` | Python AI → yanıt → PostgreSQL'de sohbet geçmişi kaydı |
| `/api/precedents/yargitay/search` | Yargıtay/Danıştay/AYM dış API veya yerel indeks araması |
| `/api/precedents/summarize` | Python AI ile karar özeti |
| `/api/precedents/apply-to-petition` | Emsal kararı seçili dava bağlamına uygulama |
| `/api/petitions` | Python AI ile dilekçe taslağı |
| `/api/documents/analyze` | Dosya ön kontrolü (boyut, format, metin çıkarılabilirlik) |
| `/api/documents/ingest` | Metin çıkar → parçala → Python'a indeksleme |
| `/api/cases/**` | Dava şablonları, belge kontrol listesi, ilerleme |

**Dilekçe üretim akışı:**

```mermaid
flowchart TD
  A[Dava bağlamı + olay özeti] --> B[Frontend formu]
  B --> C[POST /api/petitions]
  C --> D[legal-service]
  D --> E[Python AI dilekçe prompt]
  E --> F[Yapılandırılmış dilekçe metni]
  F --> G[Frontend: önizleme + DOCX export]
```

#### document-service (`:8084`)

**Sorumluluk:** Kurumsal belge yükleme, pgvector + OpenSearch ile semantik arama, toplu belge işleme (batch jobs).

**Belge yükleme akışı:**

1. `POST /api/upload` — dosya kabul edilir
2. Python servisi metin çıkarır (`/api/documents/extract-text`)
3. Metin parçalanır, embedding üretilir
4. Parçalar PostgreSQL (pgvector) ve OpenSearch'a indekslenir
5. `POST /api/search` ile semantik + tam metin arama yapılır

**Toplu belge işleme (ADMIN):**

- Yönetici bir dizin yolu ve zamanlama tanımlar (`/api/batch-documents/jobs`)
- Scheduler belirlenen aralıklarla dizindeki dosyaları tarar
- Her çalıştırma için başarı/başarısızlık raporu üretilir (`/api/batch-documents/runs`)

#### research-service (`:8085`)

**Sorumluluk:** Çok kaynaklı hukuki araştırma motoru. Mevzuat, içtihat ve web kaynaklarını paralel tarar; sonuçları Python AI ile sentezler.

**Araştırma akışı:**

```mermaid
flowchart TD
  Q[Sorgu] --> P[QueryPlanner: kaynak planı]
  P --> L[Mevzuat araması<br/>OpenSearch]
  P --> C[İçtihat araması<br/>paralel]
  P --> W[Web araması<br/>paralel]
  L & C & W --> S[Python AI sentez]
  S --> A[Nihai yanıt + adım özeti]
```

**İlerleme bildirimi:** `POST /api/research/run/stream` SSE (Server-Sent Events) ile her adımı (`PLAN_CREATED`, `LEGISLATION_IN_PROGRESS`, `CASE_LAW_COMPLETED`, `FINAL_ANSWER`, vb.) frontend'e canlı iletir.

#### platform-service (`:8086`)

**Sorumluluk:** Aktivite logları ve kullanıcı geri bildirimleri.

- `POST /api/activity-logs` — frontend veya diğer servislerden işlem kaydı
- `GET /api/activity-logs` — ADMIN tüm loglar
- `GET /api/activity-logs/me` — kullanıcının kendi logları
- `POST /api/feedback` — hata/özellik/genel geri bildirim

---

## Altyapı (`docker-compose.yml`)

| Servis | Port | Açıklama |
|--------|------|----------|
| **postgres** (pgvector) | 5433 | Kullanıcılar, abonelikler, sohbet geçmişi, dava dosyaları, vektör indeksi |
| **opensearch** | 9200 | Mevzuat ve belge tam metin / vektör araması |

```powershell
docker-compose up -d postgres opensearch
```

---

## Yerel Geliştirme — Tam Başlatma Sırası

### 1. Altyapı

```powershell
cd C:\Users\Asus\IdeaProjects\LawAI-NextLangChain
docker-compose up -d postgres opensearch
```

### 2. Python AI Servisi

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload --port 8000
```

Ollama için gerekli modeller:

```powershell
ollama pull qwen2.5:3b
ollama pull nomic-embed-text
```

`.env` örneği:

```env
LAWAI_AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_CHAT_MODEL=qwen2.5:3b
OLLAMA_EMBEDDING_MODEL=nomic-embed-text
DATABASE_URL=postgresql://lawai:lawai@localhost:5433/lawai
```

### 3. Spring Boot Microservices

```powershell
cd springboot-backend
.\mvnw.cmd install -DskipTests
start-microservices.bat
```

| Servis | Port |
|--------|------|
| api-gateway | 8080 |
| auth-service | 8081 |
| billing-service | 8082 |
| legal-service | 8083 |
| document-service | 8084 |
| research-service | 8085 |
| platform-service | 8086 |

### 4. Frontend

```powershell
cd frontend
npm.cmd install
copy .env.local.example .env.local
npm.cmd run dev
```

| Adres | Açıklama |
|-------|----------|
| `http://localhost:3000` | Frontend |
| `http://localhost:8080/api` | API Gateway |
| `http://localhost:8000/api` | Python AI (doğrudan erişim gerekmez) |

---

## Demo Hesapları

Uygulama ilk açılışta (`app.data.seed-enabled=true`) örnek verileri PostgreSQL'e yükler:

| Rol | E-posta | Şifre |
| --- | --- | --- |
| Yönetici | `admin@lawai.local` | `ChangeMe123!` |
| Avukat | `avukat@demo.lawai` | `Demo1234!` |
| Stajyer | `stajyer@demo.lawai` | `Demo1234!` |

Otomatik seed'i kapatmak için `DATA_SEED_ENABLED=false` kullanın.

---

## API Endpoint Özeti

Gateway üzerinden erişilen ana endpoint grupları:

| Grup | Örnek endpoint'ler | Servis |
|------|-------------------|--------|
| Sağlık | `GET /api/health` | legal-service |
| Kimlik | `POST /api/auth/login`, `GET /api/auth/me` | auth-service |
| Sohbet | `POST /api/chat`, `GET /api/chat/sessions` | legal-service |
| İçtihat | `POST /api/precedents/yargitay/search`, `POST /api/precedents/summarize` | legal-service |
| Dilekçe | `POST /api/petitions` | legal-service |
| Belge | `POST /api/documents/analyze`, `POST /api/documents/ingest` | legal-service |
| Bilgi bankası | `POST /api/knowledge/documents`, `POST /api/knowledge/seed-precedents` | legal-service |
| Belge arama | `POST /api/upload`, `POST /api/search` | document-service |
| Toplu belge | `POST /api/batch-documents/jobs` | document-service |
| Araştırma | `POST /api/research/run`, `POST /api/research/run/stream` | research-service |
| Dava | `GET /api/cases`, `POST /api/cases` | legal-service |
| Abonelik | `GET /api/subscriptions`, `POST /api/billing/checkout` | billing-service |
| Platform | `POST /api/activity-logs`, `POST /api/feedback` | platform-service |

---

## Ortam Değişkenleri

| Değişken | Katman | Açıklama |
|----------|--------|----------|
| `NEXT_PUBLIC_API_BASE` | Frontend | Gateway adresi (varsayılan `http://localhost:8080/api`) |
| `NEXT_PUBLIC_GOOGLE_CLIENT_ID` | Frontend | Google OAuth istemci kimliği |
| `LAWAI_AI_PROVIDER` | Python | `ollama`, `openai`, `gemini`, `local` |
| `DATABASE_URL` | Python + Spring | PostgreSQL bağlantı dizesi |
| `AI_BASE_URL` | Spring | Python AI adresi (varsayılan `http://localhost:8000/api`) |
| `DATA_SEED_ENABLED` | Spring | Demo veri yükleme (`true` / `false`) |

---

## Proje Dizin Yapısı

```
LawAI-NextLangChain/
├── frontend/                 # Next.js kullanıcı arayüzü
│   ├── app/                  # Sayfalar ve layout
│   ├── components/           # UI bileşenleri
│   └── lib/                  # API istemcisi, i18n, navigasyon
├── backend/                  # Python AI mikroservisi
│   └── app/
│       ├── main.py           # FastAPI giriş noktası
│       ├── settings.py       # Ortam yapılandırması
│       └── services/         # legal_service, document_service, vector_store
├── springboot-backend/       # Spring Boot microservices
│   ├── api-gateway/
│   ├── auth-service/
│   ├── billing-service/
│   ├── legal-service/
│   ├── document-service/
│   ├── research-service/
│   ├── platform-service/
│   ├── lawai-common/         # Ortak güvenlik ve HTTP istemcileri
│   └── MICROSERVICES.md      # Detaylı microservice dokümantasyonu
├── docker-compose.yml        # PostgreSQL + OpenSearch
└── AGENTS.md                 # AI geliştirme ajanları için kısa yönergeler
```

---

## Sorumluluk Ayrımı (Özet)

| İş | Kim yapar? |
|----|-----------|
| Kullanıcı arayüzü, form, navigasyon | Frontend |
| Oturum, yetkilendirme, kullanıcı CRUD | auth-service |
| Abonelik ve ödeme | billing-service |
| REST orkestrasyon, dosya kabul, iş kuralları | legal / document / research / platform |
| LLM çağrısı, embedding, RAG, vektör arama | Python AI |
| Kalıcı veri (kullanıcı, dava, sohbet, abonelik) | PostgreSQL |
| Mevzuat ve belge tam metin araması | OpenSearch |

Python servisi AI sağlayıcı, embedding ve vektör depo işlerini üstlenir; Spring Boot ise dosya kabul etme, metin çıkarma, iş kuralları ve REST orkestrasyonunu yapar.
