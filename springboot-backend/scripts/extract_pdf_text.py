import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: extract_pdf_text.py <pdf-path>", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    if not path.exists():
        print(f"PDF not found: {path}", file=sys.stderr)
        return 2

    try:
        text = extract_with_pypdf(path)
    except Exception as exc:
        print(f"PDF text extraction failed: {exc}", file=sys.stderr)
        return 1

    sys.stdout.write(text.strip())
    return 0


def extract_with_pypdf(path: Path) -> str:
    try:
        from pypdf import PdfReader
    except ImportError:
        from PyPDF2 import PdfReader

    reader = PdfReader(str(path))
    pages = []
    for page in reader.pages:
        pages.append(page.extract_text() or "")
    return "\n\n".join(pages)


if __name__ == "__main__":
    raise SystemExit(main())
