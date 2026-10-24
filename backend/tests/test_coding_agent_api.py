from pathlib import Path

from fastapi.testclient import TestClient

from app import coding_agent
from app.agent_workspace import propose_file_change

client = TestClient(coding_agent.api)


def configure(monkeypatch, tmp_path: Path) -> tuple[str, Path]:
    token = "test-token-" + ("x" * 48)
    root = tmp_path / "projects"
    project = root / "sample"
    project.mkdir(parents=True)
    (project / "Main.kt").write_text("fun main() = Unit\n", encoding="utf-8")
    monkeypatch.setenv("VYOMA_BACKEND_API_TOKEN", token)
    monkeypatch.setenv("VYOMA_WORKSPACE_ROOT", str(root))
    monkeypatch.setenv("VYOMA_TASK_DB_PATH", str(tmp_path / "coding_tasks.sqlite3"))
    monkeypatch.setenv("VYOMA_AI_ENDPOINT", "https://provider.example/v1/chat/completions")
    monkeypatch.setenv("VYOMA_AI_MODEL", "test-model")
    monkeypatch.setenv("VYOMA_AI_API_KEY", "not-a-real-key")
    monkeypatch.delenv("VYOMA_AUDIT_LOG_PATH", raising=False)
    return token, project


def test_actual_android_api_requires_auth(monkeypatch, tmp_path: Path):
    configure(monkeypatch, tmp_path)
    response = client.post("/v1/projects/sample/tasks/plan", json={"goal": "Update greeting"})
    assert response.status_code == 401


def test_plan_then_approve_applies_single_file_only(monkeypatch, tmp_path: Path):
    token, project = configure(monkeypatch, tmp_path)
    proposal = propose_file_change(project, "Main.kt", 'fun main() = println("hello")\n')
    plan = coding_agent.CodingPlan(
        summary="Update greeting",
        changes=(proposal,),
        rationale=("Show a greeting",),
    )
    monkeypatch.setattr(coding_agent, "plan_code_changes", lambda *args, **kwargs: plan)
    headers = {"Authorization": f"Bearer {token}"}

    created = client.post(
        "/v1/projects/sample/tasks/plan",
        headers=headers,
        json={"goal": "Update greeting"},
    )
    assert created.status_code == 200
    body = created.json()
    assert body["status"] == "WAITING_FOR_APPROVAL"
    assert body["changes"][0]["path"] == "Main.kt"
    assert (project / "Main.kt").read_text(encoding="utf-8") == "fun main() = Unit\n"

    task_id = body["task_id"]
    fetched = client.get(f"/v1/tasks/{task_id}", headers=headers)
    assert fetched.status_code == 200
    assert fetched.json()["task_id"] == task_id

    approved = client.post(f"/v1/tasks/{task_id}/approve", headers=headers)
    assert approved.status_code == 200
    assert approved.json()["status"] == "SUCCEEDED"
    assert (project / "Main.kt").read_text(encoding="utf-8") == 'fun main() = println("hello")\n'

    duplicate = client.post(f"/v1/tasks/{task_id}/approve", headers=headers)
    assert duplicate.status_code == 409


def test_reject_does_not_mutate_workspace(monkeypatch, tmp_path: Path):
    token, project = configure(monkeypatch, tmp_path)
    proposal = propose_file_change(project, "Main.kt", "fun main() = error(\"no\")\n")
    monkeypatch.setattr(
        coding_agent,
        "plan_code_changes",
        lambda *args, **kwargs: coding_agent.CodingPlan("Rejected candidate", (proposal,), ("test",)),
    )
    headers = {"Authorization": f"Bearer {token}"}
    created = client.post(
        "/v1/projects/sample/tasks/plan",
        headers=headers,
        json={"goal": "Make a risky change"},
    )
    task_id = created.json()["task_id"]
    rejected = client.post(f"/v1/tasks/{task_id}/reject", headers=headers)
    assert rejected.status_code == 200
    assert rejected.json()["status"] == "REJECTED"
    assert (project / "Main.kt").read_text(encoding="utf-8") == "fun main() = Unit\n"


def test_multi_file_plan_is_applied_after_single_approval(monkeypatch, tmp_path: Path):
    token, project = configure(monkeypatch, tmp_path)
    first = propose_file_change(project, "Main.kt", 'fun main() = println("first")\\n')
    second = propose_file_change(project, "New.kt", "fun newFile() = Unit\\n")
    monkeypatch.setattr(
        coding_agent,
        "plan_code_changes",
        lambda *args, **kwargs: coding_agent.CodingPlan(
            "Two file change", (first, second), ("first", "second")
        ),
    )
    headers = {"Authorization": f"Bearer {token}"}
    created = client.post(
        "/v1/projects/sample/tasks/plan",
        headers=headers,
        json={"goal": "Change two files"},
    )
    assert created.status_code == 200
    task_id = created.json()["task_id"]
    approved = client.post(f"/v1/tasks/{task_id}/approve", headers=headers)
    assert approved.status_code == 200
    assert approved.json()["status"] == "SUCCEEDED"
    assert (project / "Main.kt").read_text(encoding="utf-8") == 'fun main() = println("first")\\n'
    assert (project / "New.kt").read_text(encoding="utf-8") == "fun newFile() = Unit\\n"


def test_model_output_cannot_escape_project_root(monkeypatch, tmp_path: Path):
    token, project = configure(monkeypatch, tmp_path)
    outside = tmp_path / "outside.txt"
    outside.write_text("keep", encoding="utf-8")
    bad = propose_file_change(project, "Main.kt", "fun main() = Unit\n")
    escaped = bad.__class__(
        relative_path="../outside.txt",
        original_content="keep",
        proposed_content="pwned",
        unified_diff="",
        original_exists=True,
    )
    monkeypatch.setattr(
        coding_agent,
        "plan_code_changes",
        lambda *args, **kwargs: coding_agent.CodingPlan("Bad path", (escaped,), ("test",)),
    )
    headers = {"Authorization": f"Bearer {token}"}
    created = client.post(
        "/v1/projects/sample/tasks/plan",
        headers=headers,
        json={"goal": "Try unsafe path"},
    )
    assert created.status_code == 200
    task_id = created.json()["task_id"]
    approved = client.post(f"/v1/tasks/{task_id}/approve", headers=headers)
    assert approved.status_code == 409
    assert outside.read_text(encoding="utf-8") == "keep"


def test_reject_restores_task_from_sqlite_after_cache_loss(monkeypatch, tmp_path: Path):
    token, project = configure(monkeypatch, tmp_path)
    proposal = propose_file_change(project, "Main.kt", 'fun main() = println("should not apply")\n')
    plan = coding_agent.CodingPlan(
        summary="Persisted rejection candidate",
        changes=(proposal,),
        rationale=("Verify persisted task recovery",),
    )
    monkeypatch.setattr(coding_agent, "plan_code_changes", lambda *args, **kwargs: plan)
    headers = {"Authorization": f"Bearer {token}"}
    created = client.post(
        "/v1/projects/sample/tasks/plan",
        headers=headers,
        json={"goal": "Prepare a change then reject it"},
    )
    assert created.status_code == 200
    task_id = created.json()["task_id"]

    # Simulate a backend process restart: the SQLite row remains but the in-memory cache is gone.
    coding_agent._api_tasks.pop(task_id, None)
    rejected = client.post(f"/v1/tasks/{task_id}/reject", headers=headers)

    assert rejected.status_code == 200
    assert rejected.json()["status"] == "REJECTED"
    assert (project / "Main.kt").read_text(encoding="utf-8") == "fun main() = Unit\n"
