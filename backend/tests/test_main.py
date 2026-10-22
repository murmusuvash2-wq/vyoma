from typing import Any

import httpx
from fastapi.testclient import TestClient

from app import main
from app.main import app


client = TestClient(app)
TEST_KEY = "sk-test-not-a-real-secret"


def test_health_endpoint_reports_service_liveness() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "vyoma-backend"}


def test_capabilities_reports_byok_provider_and_missing_integrations() -> None:
    response = client.get("/v1/capabilities")
    assert response.status_code == 200
    assert response.json() == {
        "provider_inference": "openai_request_scoped_byok",
        "github_integration": "not_configured",
        "mcp_connections": "not_configured",
        "android_build_worker": "not_configured",
    }


def test_openapi_docs_are_available() -> None:
    response = client.get("/openapi.json")
    assert response.status_code == 200
    schema = response.json()
    assert "/health" in schema["paths"]
    assert "/v1/capabilities" in schema["paths"]
    assert "/v1/ai/plan" in schema["paths"]


def test_plan_requires_user_supplied_key() -> None:
    response = client.post("/v1/ai/plan", json={"idea": "A simple habit tracker"})
    assert response.status_code == 422


def test_plan_calls_provider_with_request_key_and_returns_plan(monkeypatch: Any) -> None:
    captured: dict[str, Any] = {}

    class FakeResponse:
        status_code = 200

        def raise_for_status(self) -> None:
            return None

        def json(self) -> dict[str, Any]:
            return {
                "model": "gpt-4o-mini",
                "choices": [{"message": {"content": "Summary\nA native habit tracker."}}],
            }

    class FakeAsyncClient:
        def __init__(self, **kwargs: Any) -> None:
            captured["timeout"] = kwargs.get("timeout")

        async def __aenter__(self) -> "FakeAsyncClient":
            return self

        async def __aexit__(self, *args: Any) -> None:
            return None

        async def post(self, url: str, *, headers: dict[str, str], json: dict[str, Any]) -> FakeResponse:
            captured["url"] = url
            captured["headers"] = headers
            captured["payload"] = json
            return FakeResponse()

    monkeypatch.setattr(main.httpx, "AsyncClient", FakeAsyncClient)
    response = client.post(
        "/v1/ai/plan",
        headers={"X-Vyoma-AI-Key": TEST_KEY},
        json={
            "idea": "Build a simple habit tracker",
            "app_name": "Habit Garden",
            "extra_requirements": ["Offline-first"],
        },
    )
    assert response.status_code == 200
    assert response.json()["provider"] == "openai"
    assert response.json()["plan"] == "Summary\nA native habit tracker."
    assert captured["headers"]["Authorization"] == f"Bearer {TEST_KEY}"
    assert captured["url"] == main.OPENAI_CHAT_COMPLETIONS_URL
    assert "Offline-first" in captured["payload"]["messages"][1]["content"]
    assert TEST_KEY not in response.text


def test_invalid_provider_key_is_reported_without_leaking_key(monkeypatch: Any) -> None:
    class FakeResponse:
        status_code = 401

        def raise_for_status(self) -> None:
            request = httpx.Request("POST", main.OPENAI_CHAT_COMPLETIONS_URL)
            response = httpx.Response(401, request=request)
            raise httpx.HTTPStatusError("invalid", request=request, response=response)

    class FakeAsyncClient:
        def __init__(self, **kwargs: Any) -> None:
            pass

        async def __aenter__(self) -> "FakeAsyncClient":
            return self

        async def __aexit__(self, *args: Any) -> None:
            return None

        async def post(self, *args: Any, **kwargs: Any) -> FakeResponse:
            return FakeResponse()

    monkeypatch.setattr(main.httpx, "AsyncClient", FakeAsyncClient)
    response = client.post(
        "/v1/ai/plan",
        headers={"X-Vyoma-AI-Key": TEST_KEY},
        json={"idea": "Build a simple habit tracker"},
    )
    assert response.status_code == 401
    assert TEST_KEY not in response.text
