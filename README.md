# LawAI Next LangChain

Bu proje, web/API katmanini Spring Boot'a, AI/RAG katmanini Python'a ayiracak sekilde kurgulanmistir.

## Kapsam

- Hukuki soru-cevap paneli
- Emsal karar arama ve ozetleri
- Dilekce taslagi uretimi
- PDF/Word/TXT dokuman on kontrolu
- Dokuman parcalama ve LangChain embedding hattina indeksleme
- OpenAI, Gemini veya Ollama ile calisma; ayar yoksa yerel ornek emsal fallback'i

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

OpenAI icin `.env`:

```env
AI_PROVIDER=openai
OPENAI_API_KEY=sk-...
OPENAI_CHAT_MODEL=gpt-4o-mini
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
```

Gemini icin `.env`:

```env
LAWAI_AI_PROVIDER=gemini
GOOGLE_API_KEY=...
GEMINI_CHAT_MODEL=gemini-2.5-flash
GEMINI_EMBEDDING_MODEL=models/embedding-001
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

## Endpointler

- `GET /api/health`
- `POST /api/chat`
- `POST /api/precedents/search`
- `POST /api/petitions`
- `POST /api/documents/analyze`
- `POST /api/documents/ingest`
- `POST /api/knowledge/documents`
- `POST /api/knowledge/seed-precedents`

## Notlar

Python servisi AI saglayici, embedding ve vektor depo islerini uzerine alir; Spring Boot ise dosya kabul etme, metin cikarma, is kurallari ve REST orkestrasyonunu yapar.
