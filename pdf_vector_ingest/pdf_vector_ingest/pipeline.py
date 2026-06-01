from dataclasses import dataclass
from pathlib import Path
import re
import subprocess

from pdf_vector_ingest.db import PgVectorRepository, file_record
from pdf_vector_ingest.embeddings import create_embeddings
from pdf_vector_ingest.pdf import PdfChunk, discover_pdfs, split_pdf
from pdf_vector_ingest.settings import settings


@dataclass
class IngestStats:
    discovered: int = 0
    skipped: int = 0
    indexed_files: int = 0
    failed_files: int = 0
    indexed_chunks: int = 0


def ingest_local(input_path: Path, dry_run: bool = False, limit_files: int | None = None) -> IngestStats:
    repository = PgVectorRepository()
    repository.ensure_schema()
    embeddings = create_embeddings()

    paths = discover_pdfs(input_path)
    if limit_files is not None:
        paths = paths[:limit_files]

    stats = IngestStats(discovered=len(paths))
    for path in paths:
        record = file_record(path)
        if repository.already_indexed(str(record.path), record.sha256):
            stats.skipped += 1
            continue
        if dry_run:
            stats.skipped += 1
            continue

        try:
            repository.mark_processing(record)
            chunks = split_pdf(record.path, record.sha256)
            inserted = _embed_and_insert(repository, embeddings, chunks)
            repository.mark_indexed(record, len(chunks))
            stats.indexed_files += 1
            stats.indexed_chunks += inserted
        except Exception as exc:
            repository.mark_failed(record, str(exc))
            stats.failed_files += 1
    return stats


def ingest_spark(
    input_path: Path,
    spark_master: str,
    dry_run: bool = False,
    limit_files: int | None = None,
) -> IngestStats:
    _validate_spark_java()

    from pyspark.sql import SparkSession

    try:
        spark = (
            SparkSession.builder.appName("lawai-pdf-vector-ingest")
            .master(spark_master)
            .getOrCreate()
        )
    except Exception as exc:
        if "getSubject is not supported" in str(exc):
            raise RuntimeError(
                "Spark Java uyumsuzlugu nedeniyle baslatilamadi. "
                "JDK 17 kullanin veya komutu --engine local ile calistirin."
            ) from exc
        raise
    try:
        paths = discover_pdfs(input_path)
        if limit_files is not None:
            paths = paths[:limit_files]
        # Spark is used here for scalable file partitioning. Inserts stay batched per partition.
        rows = spark.sparkContext.parallelize([str(path) for path in paths])
        partials = rows.mapPartitions(lambda partition: [_ingest_partition(list(partition), dry_run)]).collect()
        stats = IngestStats(discovered=len(paths))
        for partial in partials:
            stats.skipped += partial.skipped
            stats.indexed_files += partial.indexed_files
            stats.failed_files += partial.failed_files
            stats.indexed_chunks += partial.indexed_chunks
        return stats
    finally:
        spark.stop()


def _ingest_partition(paths: list[str], dry_run: bool) -> IngestStats:
    repository = PgVectorRepository()
    repository.ensure_schema()
    embeddings = create_embeddings()
    stats = IngestStats(discovered=len(paths))
    for raw_path in paths:
        record = file_record(Path(raw_path))
        if repository.already_indexed(str(record.path), record.sha256):
            stats.skipped += 1
            continue
        if dry_run:
            stats.skipped += 1
            continue
        try:
            repository.mark_processing(record)
            chunks = split_pdf(record.path, record.sha256)
            inserted = _embed_and_insert(repository, embeddings, chunks)
            repository.mark_indexed(record, len(chunks))
            stats.indexed_files += 1
            stats.indexed_chunks += inserted
        except Exception as exc:
            repository.mark_failed(record, str(exc))
            stats.failed_files += 1
    return stats


def _embed_and_insert(repository, embeddings, chunks: list[PdfChunk]) -> int:
    inserted = 0
    for start in range(0, len(chunks), settings.embedding_batch_size):
        chunk_batch = chunks[start : start + settings.embedding_batch_size]
        vectors = embeddings.embed_documents([chunk.content for chunk in chunk_batch])
        inserted += repository.insert_chunks(chunk_batch, vectors)
    return inserted


def _validate_spark_java() -> None:
    try:
        completed = subprocess.run(
            ["java", "-version"],
            check=False,
            capture_output=True,
            text=True,
            timeout=10,
        )
    except FileNotFoundError as exc:
        raise RuntimeError(
            "Spark icin Java gerekli. JDK 17 kurup JAVA_HOME ve PATH ayarlarini yapin."
        ) from exc

    version_output = "\n".join([completed.stderr, completed.stdout])
    match = re.search(r'version "([0-9]+)(?:\.([0-9]+))?', version_output)
    if not match:
        return

    major = int(match.group(1))
    if major == 1 and match.group(2):
        major = int(match.group(2))
    if major > 23:
        raise RuntimeError(
            f"Spark bu Java surumuyle baslatilamiyor: Java {major}. "
            "JDK 17 kurup JAVA_HOME'u JDK 17 klasorune ayarlayin veya --engine local kullanin."
        )
