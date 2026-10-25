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



def test_audit_trail_records_proposed_rejected_and_applied_changes(tmp_path: Path, monkeypatch) -> None:
    token = setup_env(tmp_path, monkeypatch)
    headers = {"Authorization": f"Bearer {token}"}
    task_id = client.post("/v1/tasks", headers=headers).json()["task_id"]

    rejected_proposal = client.post(
        f"/v1/tasks/{task_id}/proposals",
        headers=headers,
        json={"path": "src/Rejected.kt", "proposed_content": "package src\\n"},
    )
    assert rejected_proposal.status_code == 200
    rejected_id = rejected_proposal.json()["proposal_id"]
    rejected = client.post(
        f"/v1/tasks/{task_id}/proposals/{rejected_id}/decision",
        headers=headers,
        json={"approved": False},
    )
    assert rejected.status_code == 200

    approved_proposal = client.post(
        f"/v1/tasks/{task_id}/proposals",
        headers=headers,
        json={"path": "src/Accepted.kt", "proposed_content": "package src\\n"},
    )
    assert approved_proposal.status_code == 200
    approved_id = approved_proposal.json()["proposal_id"]
    applied = client.post(
        f"/v1/tasks/{task_id}/proposals/{approved_id}/decision",
        headers=headers,
        json={"approved": True},
    )
    assert applied.status_code == 200

    audit = client.get(f"/v1/tasks/{task_id}/audit", headers=headers)
    assert audit.status_code == 200
    events = audit.json()["events"]
    event_types = [event["event_type"] for event in events]
    assert event_types == [
        "task_created",
        "proposal_created",
        "proposal_rejected",
        "proposal_created",
        "proposal_applied",
    ]
    assert all("proposed_content" not in event for event in events)
    assert events[1]["proposal_id"] == rejected_id
    assert events[-1]["proposal_id"] == approved_id


def test_audit_endpoint_rejects_unknown_task(tmp_path: Path, monkeypatch) -> None:
    token = setup_env(tmp_path, monkeypatch)
    response = client.get(
        "/v1/tasks/00000000-0000-0000-0000-000000000000/audit",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 404



def test_applied_proposal_can_be_undone_and_audited(tmp_path: Path, monkeypatch):
    token = setup_env(tmp_path, monkeypatch)
    headers = {"Authorization": f"Bearer {token}"}
    task_id = client.post("/v1/tasks", headers=headers).json()["task_id"]
    workspace = tmp_path / "workspaces" / task_id
    target = workspace / "src" / "Main.kt"
    target.parent.mkdir(parents=True)
    target.write_text("original\\n", encoding="utf-8")

    proposal = client.post(
        f"/v1/tasks/{task_id}/proposals",
        headers=headers,
        json={"path": "src/Main.kt", "proposed_content": "updated\\n"},
    )
    assert proposal.status_code == 200, proposal.text
    proposal_id = proposal.json()["proposal_id"]

    applied = client.post(
        f"/v1/tasks/{task_id}/proposals/{proposal_id}/decision",
        headers=headers,
        json={"approved": True},
    )
    assert applied.status_code == 200, applied.text
    assert target.read_text(encoding="utf-8") == "updated\\n"

    undone = client.post(
        f"/v1/tasks/{task_id}/proposals/{proposal_id}/undo",
        headers=headers,
    )
    assert undone.status_code == 200, undone.text
    assert undone.json()["status"] == "undone"
    assert target.read_text(encoding="utf-8") == "original\\n"

    duplicate = client.post(
        f"/v1/tasks/{task_id}/proposals/{proposal_id}/undo",
        headers=headers,
    )
    assert duplicate.status_code == 409

    audit = client.get(f"/v1/tasks/{task_id}/audit", headers=headers)
    assert [event["event_type"] for event in audit.json()["events"]][-2:] == [
        "proposal_applied",
        "proposal_undone",
    ]


def test_undo_refuses_to_overwrite_newer_file_content(tmp_path: Path, monkeypatch):
    token = setup_env(tmp_path, monkeypatch)
    headers = {"Authorization": f"Bearer {token}"}
    task_id = client.post("/v1/tasks", headers=headers).json()["task_id"]
    workspace = tmp_path / "workspaces" / task_id
    target = workspace / "Main.kt"
    target.write_text("original\\n", encoding="utf-8")

    proposal = client.post(
        f"/v1/tasks/{task_id}/proposals",
        headers=headers,
        json={"path": "Main.kt", "proposed_content": "approved version\\n"},
    ).json()
    proposal_id = proposal["proposal_id"]
    applied = client.post(
        f"/v1/tasks/{task_id}/proposals/{proposal_id}/decision",
        headers=headers,
        json={"approved": True},
    )
    assert applied.status_code == 200
    target.write_text("newer user edit\\n", encoding="utf-8")

    undone = client.post(
        f"/v1/tasks/{task_id}/proposals/{proposal_id}/undo",
        headers=headers,
    )
    assert undone.status_code == 409
    assert target.read_text(encoding="utf-8") == "newer user edit\\n"
