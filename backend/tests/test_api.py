import os

os.environ["LAWAI_AI_PROVIDER"] = "local"
os.environ["LAWAI_VECTOR_STORE"] = "json"

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services.vector_store import vector_store


client = TestClient(app)


@pytest.fixture(autouse=True)
def clean_vector_store():
    vector_store.clear()
    yield
    vector_store.clear()


def test_health() -> None:
    response = client.get("/api/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_precedent_search_does_not_use_samples_without_index() -> None:
    response = client.post("/api/precedents/search", json={"query": "kira alacagi", "limit": 2})
    assert response.status_code == 200
    body = response.json()
    assert body["query"] == "kira alacagi"
    assert body["results"] == []


def test_precedent_search_uses_indexed_precedents() -> None:
    client.post("/api/knowledge/seed-precedents")

    response = client.post("/api/precedents/search", json={"query": "kira alacagi", "limit": 2})

    assert response.status_code == 200
    body = response.json()
    assert len(body["results"]) == 1
    assert body["results"][0]["topic"] == "Kira alacagi"


def test_knowledge_ingest_works_with_local_provider() -> None:
    response = client.post(
        "/api/knowledge/documents",
        json={
            "documents": [
                {
                    "sourceType": "upload",
                    "court": "Yuklenen kaynak",
                    "topic": "Kira uyusmazligi",
                    "summary": "Kira bedeli ve temerrut hakkinda test dokumani.",
                    "content": "Kira bedeli, temerrut ihtari ve odeme kayitlari birlikte incelenmelidir.",
                }
            ]
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["indexed"] == 1
    assert body["storage"] == "persistent/local"


def test_chat_uses_indexed_local_documents() -> None:
    client.post(
        "/api/knowledge/documents",
        json={
            "documents": [
                {
                    "sourceType": "upload",
                    "court": "Yuklenen kaynak",
                    "topic": "Ayipli arac raporu",
                    "summary": "Ekspertiz raporunda motor arizasi, gizli ayip ve noter ihtari birlikte yer aliyor.",
                    "content": "Ayipli arac uyusmazliginda motor arizasi, gizli ayip, ekspertiz raporu ve noter ihtari temel delillerdir.",
                }
            ]
        },
    )

    response = client.post("/api/chat", json={"question": "Ayipli arac motor arizasi icin hangi deliller var?"})

    assert response.status_code == 200
    body = response.json()
    assert "Ayipli arac raporu" in body["answer"]
    assert body["citations"][0]["topic"] == "Ayipli arac raporu"


def test_precedent_apply_to_petition() -> None:
    response = client.post(
        "/api/precedents/apply-to-petition",
        json={
            "court": "Yargitay",
            "chamber": "9. Hukuk Dairesi",
            "docketNo": "2022/1845",
            "decisionNo": "2022/7281",
            "date": "2022-06-14",
            "topic": "Ise iade",
            "summary": "Kisa liste ozeti",
            "content": "Mahkeme, is sozlesmesinin hakli nedenle feshedilmedigini ve ise iade kosullarinin olustugunu belirterek karari bozmustur.",
            "caseContext": {
                "caseId": "case-1",
                "caseLabel": "Ise iade",
                "clientName": "Ahmet Yilmaz",
                "opponentName": "ABC Ltd.",
                "courtName": "Ankara Is Mahkemesi",
                "subject": "Haksiz fesih nedeniyle ise iade",
                "summary": "Musteri 5 yillik calisma sonrasi haksiz fesih almis, ihtar gonderilmistir.",
                "petitionType": "Ise iade",
                "petitionFacts": "Musteri fesih gerekcesinin somut olmadigini ileri suruyor.",
                "petitionDemands": "Ise iade ve ucret alacagi talep edilmektedir.",
            },
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["citationLine"]
    assert body["applicationNote"]
    assert body["legalGroundsSnippet"]
    assert body["factsLinkSnippet"]


def test_precedent_summarize_uses_decision_text() -> None:
    response = client.post(
        "/api/precedents/summarize",
        json={
            "court": "Yargitay",
            "chamber": "9. Hukuk Dairesi",
            "docketNo": "2022/1845",
            "decisionNo": "2022/7281",
            "date": "2022-06-14",
            "topic": "Ise iade",
            "summary": "Kisa liste ozeti",
            "content": "Mahkeme, is sozlesmesinin hakli nedenle feshedilmedigini ve ise iade kosullarinin olustugunu belirterek karari bozmustur.",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert "ise iade" in body["summary"].lower() or "Ise iade" in body["summary"]
    assert body["disclaimer"]
