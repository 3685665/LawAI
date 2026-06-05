# PDF Vector Ingest

Mevcut `backend` ve `frontend` koduna dokunmadan, cok buyuk PDF arsivlerini tekil belge yukleme algoritmasiyla toplu isleyen ayri CLI projesi.

Bu proje su isleri yapar:

- PDF dosyalarini recursive bulur.
- Metin cikarir.
- Tam metni PostgreSQL `legal_documents` tablosuna yazar.
- Metni parcalara boler.
- OpenAI, Gemini, Ollama veya local fallback ile embedding uretir.
- Chunk metinlerini ve embeddinglerini PostgreSQL `legal_document_chunks` tablosuna yazar.
- Chunklari OpenSearch `legal-document-chunks` indexine yazar.
- `pdf_ingest_files` tablosuyla kaldigi yerden devam eder.

## Kurulum

```powershell
cd C:\Users\Asus\IdeaProjects\LawAI-NextLangChain\pdf_vector_ingest
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env
```

PostgreSQL/pgvector ve OpenSearch icin repo kokunden:

```powershell
docker compose up -d postgres opensearch
```

## Calistirma

```powershell
python -m pdf_vector_ingest ingest --input "D:\pdf-arsivi" --engine local
```

Dry-run:

```powershell
python -m pdf_vector_ingest ingest --input "D:\pdf-arsivi" --dry-run --limit-files 100
```

Spark ile dosya dagitimi:

```powershell
python -m pdf_vector_ingest ingest --input "D:\pdf-arsivi" --engine spark --spark-master local[*]
```

Windows'ta Spark icin JDK 17 kullanin. Java 24/25 gibi yeni surumlerde Hadoop tarafinda
`getSubject is not supported` hatasi alinabilir. Bu durumda:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.x.x-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
python -m pdf_vector_ingest ingest --input "D:\pdf-arsivi" --engine spark --spark-master "local[*]"
```

Kucuk testler icin Spark yerine su komut yeterlidir:

```powershell
python -m pdf_vector_ingest ingest --input "D:\pdf-arsivi" --engine local
```

## Notlar

- 10 milyon PDF icin tek makinede calistirmak pratik olmayabilir. `--engine spark` yerelde baslayabilir, sonra Spark cluster uzerinde ayni komutla calisacak sekilde tasarlandi.
- Taranmis/goruntu PDF'ler icin bu proje OCR yapmaz; metin cikarilamazsa dosya `failed` olarak isaretlenir.
- Embedding boyutu `LAWAI_EMBEDDING_DIMENSIONS` ile veritabanindaki vector boyutuyla ayni olmalidir.
- Toplu ingest ile tekil `/api/upload` ayni PostgreSQL tablolari ve ayni OpenSearch indexini kullanir.
- Embedding saglayicisi kota veya servis hatasi verirse chunk batch'i local embedding fallback ile yazilir.
