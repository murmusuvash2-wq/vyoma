from pathlib import Path

from fastapi.testclient import TestClient

from app.coding_agent import api


def setup_client(tmp_path: Path, monkeypatch) -> tuple[TestClient, dict[str, str], Path]:
    root = tmp_path / "workspaces"
    root.mkdir()
    monkeypatch.setenv("VYOMA_BACKEND_API_TOKEN", "test-token-for-project-workspaces")
    monkeypatch.setenv("VYOMA_WORKSPACE_ROOT", str(root))
    client = TestClient(api)
    return client, {"Authorization": "Bearer test-token-for-project-workspaces"}, root


def test_create_project_creates_private_empty_workspace(tmp_path: Path, monkeypatch) -> None:
    client, headers, root = setup_client(tmp_path, monkeypatch)

    response = client.post("/v1/projects", headers=headers, json={"project_id": "demo-app"})

    assert response.status_code == 200, response.text
    assert response.json()["project_id"] == "demo-app"
    assert response.json()["status"] == "READY"
    project = root / "demo-app"
    assert project.is_dir()
    assert list(project.iterdir()) == []
    assert oct(project.stat().st_mode & 0o777) == "0o700"


def test_create_project_requires_authentication(tmp_path: Path, monkeypatch) -> None:
    client, _, _ = setup_client(tmp_path, monkeypatch)

    response = client.post("/v1/projects", json={"project_id": "demo-app"})

    assert response.status_code == 401


def test_create_project_rejects_invalid_ids(tmp_path: Path, monkeypatch) -> None:
    client, headers, root = setup_client(tmp_path, monkeypatch)

    for project_id in ("../outside", "/tmp/project", "has spaces", ""):
        response = client.post("/v1/projects", headers=headers, json={"project_id": project_id})
        assert response.status_code == 422

    assert list(root.iterdir()) == []


def test_create_project_rejects_duplicate_ids(tmp_path: Path, monkeypatch) -> None:
    client, headers, root = setup_client(tmp_path, monkeypatch)

    first = client.post("/v1/projects", headers=headers, json={"project_id": "demo-app"})
    second = client.post("/v1/projects", headers=headers, json={"project_id": "demo-app"})

    assert first.status_code == 200
    assert second.status_code == 409
    assert (root / "demo-app").is_dir()
