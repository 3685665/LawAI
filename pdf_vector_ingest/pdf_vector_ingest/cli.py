import argparse
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(prog="pdf-vector-ingest")
    subparsers = parser.add_subparsers(dest="command", required=True)

    ingest = subparsers.add_parser("ingest", help="PDF arsivini pgvector'a indeksler.")
    ingest.add_argument("--input", required=True, help="PDF dosyasi veya PDF klasoru.")
    ingest.add_argument("--engine", choices=["local", "spark"], default="local")
    ingest.add_argument("--spark-master", default="local[*]")
    ingest.add_argument("--dry-run", action="store_true")
    ingest.add_argument("--limit-files", type=int)

    args = parser.parse_args()
    input_path = Path(args.input).expanduser().resolve()
    if not input_path.exists():
        raise SystemExit(f"Girdi bulunamadi: {input_path}")

    from pdf_vector_ingest.pipeline import ingest_local, ingest_spark

    if args.command == "ingest" and args.engine == "spark":
        stats = ingest_spark(input_path, args.spark_master, args.dry_run, args.limit_files)
    else:
        stats = ingest_local(input_path, args.dry_run, args.limit_files)

    print(
        "discovered={discovered} skipped={skipped} indexed_files={indexed_files} "
        "failed_files={failed_files} indexed_chunks={indexed_chunks}".format(**stats.__dict__)
    )
