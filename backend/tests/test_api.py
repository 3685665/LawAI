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


def test_precedent_search_filters_weak_keyword_matches() -> None:
    client.post(
        "/api/knowledge/documents",
        json={
            "documents": [
                {
                    "sourceType": "upload",
                    "court": "Yuklenen kaynak",
                    "topic": "Kira uyusmazligi",
                    "summary": "Kira alacagi ve temerrut ihtari birlikte incelenir.",
                    "content": "Kira alacagi, temerrut ihtari ve odeme kayitlari uyusmazligin temelidir.",
                },
                {
                    "sourceType": "upload",
                    "court": "Yuklenen kaynak",
                    "topic": "Kira sozlesmesi yenileme",
                    "summary": "Kira sozlesmesinin yenilenmesi hakkinda genel aciklama.",
                    "content": "Kira sozlesmesi yenileme sureci ve bildirim tarihleri degerlendirilir.",
                },
            ]
        },
    )

    response = client.post("/api/precedents/search", json={"query": "kira alacagi", "limit": 5})

    assert response.status_code == 200
    body = response.json()
    assert [item["topic"] for item in body["results"]] == ["Kira uyusmazligi"]


def test_precedent_search_groups_chunks_by_source() -> None:
    client.post(
        "/api/knowledge/documents",
        json={
            "documents": [
                {
                    "sourceType": "upload",
                    "topic": "Ayipli arac dosyasi (bolum 1/3)",
                    "summary": "Ekspertiz raporu gizli ayip ve motor arizasi tespiti iceriyor.",
                    "content": "Ekspertiz raporu gizli ayip ve motor arizasi nedeniyle arac satisinda uyusmazlik oldugunu gosterir.\n\nYargitay Ictihat Merkezinde yayimlanan kararlardaki kisisel veriler anonim hale getirilmistir.",
                },
                {
                    "sourceType": "upload",
                    "topic": "Ayipli arac dosyasi (bolum 2/3)",
                    "summary": "Noter ihtari ve servis kayitlari gizli ayip iddiasini destekliyor.",
                    "content": "Ekspertiz raporu gizli ayip ve motor arizasi nedeniyle arac satisinda uyusmazlik oldugunu gosterir.\n\nNoter ihtari, servis kayitlari ve motor arizasi gizli ayip iddiasinda birlikte degerlendirilir.\n\nYargitay Ictihat Merkezinde yayimlanan kararlardaki kisisel veriler anonim hale getirilmistir.",
                },
                {
                    "sourceType": "upload",
                    "topic": "Ayipli arac dosyasi (bolum 3/3)",
                    "summary": "Satis sozlesmesi ve taraf beyanlari dosyanin devaminda yer aliyor.",
                    "content": "Satis sozlesmesi, teslim tutanagi ve taraf beyanlari karar metninin son bolumunde incelenir.",
                },
            ]
        },
    )

    response = client.post("/api/precedents/search", json={"query": "gizli ayip motor arizasi", "limit": 5})

    assert response.status_code == 200
    body = response.json()
    assert len(body["results"]) == 1
    assert body["results"][0]["topic"] == "Ayipli arac dosyasi"
    assert "bolum" not in body["results"][0]["summary"]
    assert "Ayipli arac dosyasi (bolum" not in body["results"][0]["content"]
    assert "Ekspertiz raporu gizli ayip" in body["results"][0]["content"]
    assert "Noter ihtari" in body["results"][0]["content"]
    assert "Satis sozlesmesi" in body["results"][0]["content"]
    assert "Yargitay Ictihat Merkezinde" not in body["results"][0]["content"]
    assert body["results"][0]["content"].count("Ekspertiz raporu gizli ayip") == 1


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
