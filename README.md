# LawAI Next LangChain

Bu proje, web/API katmanini Spring Boot'a, AI/RAG katmanini Python'a ayiracak sekilde kurgulanmistir.

## Kapsam

- Hukuki soru-cevap paneli
- Ictihat arama ve karar ozetleri
- Dilekce taslagi uretimi
- PDF/Word/TXT dokuman on kontrolu
- Dokuman parcalama ve LangChain embedding hattina indeksleme
- Varsayilan olarak Ollama ile calisma; OpenAI/Gemini adaptorleri alternatif olarak korunur.

## Klasorler

- `frontend`: Next.js 15, React 19, TypeScript
- `backend`: Python AI mikroservisi, LangChain, OpenAI/Gemini/Ollama adaptorleri
- `springboot-backend`: Spring Boot ana API, dosya islemleri ve orkestrasyon

## Python AI Servisi

Python kurulu olduktan sonra:

```powershell
cd C:\Users\Asus\IdeaProjects\LawAI-NextLangChain\backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload --port 8001
```

Ollama icin once gerekli modelleri indirin:

```powershell
ollama pull qwen2.5:3b
ollama pull nomic-embed-text
```

Sonra `.env`:

```env
LAWAI_AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_CHAT_MODEL=qwen2.5:3b
OLLAMA_EMBEDDING_MODEL=nomic-embed-text
```

## Frontend

```powershell
cd C:\Users\Asus\IdeaProjects\LawAI-NextLangChain\frontend
npm.cmd install
copy .env.local.example .env.local
npm.cmd run dev
```

## Spring Boot API

```powershell
cd C:\Users\Asus\IdeaProjects\LawAI-NextLangChain\springboot-backend
.\mvnw.cmd spring-boot:run
```

Spring Boot ana API varsayilan olarak `http://localhost:8080`, Python AI servisi `http://localhost:8001/api` adresinde calisir.

Manuel derleme icin:

```powershell
cd C:\Users\Asus\IdeaProjects\LawAI-NextLangChain\springboot-backend
.\mvnw.cmd -DskipTests compile
```

Frontend varsayilan olarak `http://localhost:3000`, ana API `http://localhost:8080/api` adresinde calisir.

## Demo Hesaplari

Uygulama ilk acilista (`app.data.seed-enabled=true`) ornek verileri PostgreSQL'e yukler. Asagidaki hesaplarla giris yapabilirsiniz:

| Rol | E-posta | Sifre |
| --- | --- | --- |
| Yonetici | `admin@lawai.local` | `ChangeMe123!` |
| Avukat | `avukat@demo.lawai` | `Demo1234!` |
| Stajyer | `stajyer@demo.lawai` | `Demo1234!` |

Otomatik seed'i kapatmak icin `DATA_SEED_ENABLED=false` kullanin.

## Endpointler

- `GET /api/health`
- `POST /api/chat`
- `POST /api/petitions`
- `POST /api/documents/analyze`
- `POST /api/documents/ingest`
- `POST /api/knowledge/documents`
- `POST /api/knowledge/seed-precedents`

## Notlar

Python servisi AI saglayici, embedding ve vektor depo islerini uzerine alir; Spring Boot ise dosya kabul etme, metin cikarma, is kurallari ve REST orkestrasyonunu yapar.
