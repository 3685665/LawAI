import json
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from pdf_vector_ingest.db import StoredChunk
from pdf_vector_ingest.settings import settings


class OpenSearchClient:
    def __init__(self) -> None:
        self._ready = False

    def index_chunks(self, filename: str, chunks: list[StoredChunk]) -> int:
        if not settings.opensearch_enabled:
            return 0
        self.ensure_index()
        indexed = 0
        for chunk in chunks:
            payload = {
                "documentId": chunk.document_id,
                "chunkId": chunk.id,
                "filename": filename,
                "chunkIndex": chunk.chunk_index,
                "content": chunk.content,
            }
            self._request("PUT", f"/{settings.opensearch_index}/_doc/{chunk.id}", payload)
            indexed += 1
        self._request("POST", f"/{settings.opensearch_index}/_refresh", {})
        return indexed

    def ensure_index(self) -> None:
        if self._ready:
            return
        payload = {
            "mappings": {
                "properties": {
                    "documentId": {"type": "long"},
                    "chunkId": {"type": "long"},
                    "filename": {"type": "keyword"},
                    "chunkIndex": {"type": "integer"},
                    "content": {"type": "text"},
                }
            }
        }
        self._request("PUT", f"/{settings.opensearch_index}", payload, ignore_already_exists=True)
        self._ready = True

    def _request(
        self,
        method: str,
        path: str,
        payload: dict,
        ignore_already_exists: bool = False,
    ) -> str:
        url = settings.opensearch_base_url.rstrip("/") + path
        body = json.dumps(payload).encode("utf-8")
        request = Request(url, data=body, method=method, headers={"Content-Type": "application/json"})
        try:
            with urlopen(request, timeout=10) as response:
                return response.read().decode("utf-8")
        except HTTPError as exc:
            error_body = exc.read().decode("utf-8", errors="replace")
            if exc.code == 400 and ignore_already_exists and "resource_already_exists_exception" in error_body:
                return error_body
            raise RuntimeError(f"OpenSearch {exc.code} dondu: {error_body}") from exc
        except URLError as exc:
            raise RuntimeError(f"OpenSearch baglantisi basarisiz: {exc.reason}") from exc
