from pathlib import Path

from fastapi.testclient import TestClient

from app.api import app

client = TestClient(app)


def setup_env(tmp_path: Path, monkeypatch) -> str:
    token = "test-token-" + ("x" * 40)
    monkeypatch.setenv("VYOMA_BACKEND_TOKEN", token)
    monkeypatch.setenv("VYOMA_WORKSPACE_ROOT", str(tmp_path / "workspaces"))
    monkeypatch.setenv("VYOMA_TASK_DB", str(tmp_path / "tasks.sqlite3"))
    return token


def test_health_does_not_require_auth() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_task_endpoints_require_bearer_token(tmp_path: Path, monkeypatch) -> None:
    setup_env(tmp_path, monkeypatch)
    response = client.post("/v1/tasks")
    assert response.status_code == 401


def test_task_workspace_proposal_rejection_does_not_mutate(tmp_path: Path, monkeypatch) -> None:
    token = setup_env(tmp_path, monkeypatch)
    headers = {"Authorization": f"Bearer {token}"}
    created = client.post("/v1/tasks", headers=headers)
    assert created.status_code == 200
    task_id = created.json()["task_id"]

    proposal = client.post(
        f"/v1/tasks/{task_id}/proposals",
        headers=headers,
        json={"path": "src/Main.kt", "proposed_content": "fun main() = Unit\\n"},
    )
    assert proposal.status_code == 200
    proposal_id = proposal.json()["proposal_id"]
    target = tmp_path / "workspaces" / task_id / "src" / "Main.kt"
    assert not target.exists()

    rejected = client.post(
        f"/v1/tasks/{task_id}/proposals/{proposal_id}/decision",
        headers=headers,
        json={"approved": False},
    )
    assert rejected.status_code == 200
    assert rejected.json()["status"] == "rejected"
    assert not target.exists()


def test_approved_proposal_applies_and_duplicate_decision_conflicts(tmp_path: Path, monkeypatch) -> None:
    token = setup_env(tmp_path, monkeypatch)
    headers = {"Authorization": f"Bearer {token}"}
    task_id = client.post("/v1/tasks", headers=headers).json()["task_id"]
    proposal = client.post(
        f"/v1/tasks/{task_id}/proposals",
        headers=headers,
        json={"path": "src/Main.kt", "proposed_content": "fun main() = Unit\\n"},
    ).json()
    url = f"/v1/tasks/{task_id}/proposals/{proposal['proposal_id']}/decision"
    applied = client.post(url, headers=headers, json={"approved": True})
    assert applied.status_code == 200
    assert applied.json()["status"] == "applied"
    target = tmp_path / "workspaces" / task_id / "src" / "Main.kt"
    assert target.read_text(encoding="utf-8") == "fun main() = Unit\\n"
    duplicate = client.post(url, headers=headers, json={"approved": True})
    assert duplicate.status_code == 409


def test_unknown_task_is_not_created_from_client_path(tmp_path: Path, monkeypatch) -> None:
    token = setup_env(tmp_path, monkeypatch)
    headers = {"Authorization": f"Bearer {token}"}
    response = client.get("/v1/tasks/not-a-uuid/files", headers=headers)
    assert response.status_code == 404
