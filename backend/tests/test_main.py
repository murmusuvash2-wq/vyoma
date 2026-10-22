from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_health_endpoint_reports_service_liveness() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "service": "vyoma-backend",
    }


def test_capabilities_endpoint_marks_unimplemented_integrations() -> None:
    response = client.get("/v1/capabilities")

    assert response.status_code == 200
    assert response.json() == {
        "provider_inference": "not_configured",
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
