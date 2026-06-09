import os

os.environ["LAWAI_AI_PROVIDER"] = "local"
os.environ["LAWAI_VECTOR_STORE"] = "memory"

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


def test_petition_local_uses_standard_petition_format() -> None:
    response = client.post(
        "/api/petitions",
        json={
            "petitionType": "Alacak",
            "court": "Ankara 5. Asliye Hukuk Mahkemesi",
            "parties": "Davaci: Ali Veli\nAdres: Ankara\nDavali: Beta Ltd. Sti.",
            "facts": "Davaci, sozlesmeye dayali alacagin odenmedigini belirtmektedir.",
            "demands": "Alacagin davalidan tahsiline karar verilmesini talep ederiz.",
        },
    )

    assert response.status_code == 200
    body = response.json()["body"]
    assert "T.C." in body
    assert "MAHKEMES" in body.upper()
    assert "DAVACI" in body.upper()
    assert "DAVALI" in body.upper()
    assert "ACIKLAMALAR" in body.upper() or "AÇIKLAMALAR" in body
    assert "HUKUK" in body.upper()
    assert "NET" in body.upper() and "TALEP" in body.upper()
    assert "ai model" not in body.lower()


def test_petition_strips_ui_metadata_from_output() -> None:
    response = client.post(
        "/api/petitions",
        json={
            "petitionType": "Kira alacagi",
            "court": "Istanbul Anadolu 2. Asliye Hukuk Mahkemesi",
            "parties": "Davaci: Ayse Yilmaz\nDavali: Mehmet Demir",
            "facts": "Kira bedeli odememis, ihtar gonderilmistir.",
            "demands": "Kira alacaginin tahsiline karar verilsin.",
            "supplementaryContext": "AI modeli: Premium model - daha detayli yaz.",
        },
    )

    assert response.status_code == 200
    body = response.json()["body"]
    assert "premium model" not in body.lower()
    assert "ai model" not in body.lower()


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
