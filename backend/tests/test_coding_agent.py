import json
from pathlib import Path
from urllib.error import HTTPError

import pytest

from app.coding_agent import CodingAgentError, plan_code_changes


class FakeResponse:
    def __init__(self, payload: dict):
        self.payload = json.dumps(payload).encode()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def read(self, size=-1):
        return self.payload[:size]


def provider_envelope(content: str) -> dict:
    return {"choices": [{"message": {"content": content}}]}


def test_planner_returns_proposals_without_mutating_workspace(tmp_path: Path, monkeypatch):
    target = tmp_path / "Main.kt"
    target.write_text("fun main() = Unit\n", encoding="utf-8")
    model_payload = {"summary": "Update greeting", "changes": [{
        "path": "Main.kt",
        "content": 'fun main() = println("hello")\n',
        "rationale": "Show a greeting",
    }]}
    monkeypatch.setattr(
        "urllib.request.urlopen",
        lambda request, timeout: FakeResponse(provider_envelope(json.dumps(model_payload))),
    )
    plan = plan_code_changes(
        tmp_path, "Add a greeting", endpoint="https://provider.example/v1/chat/completions",
        model="test-model", api_key="not-a-real-key",
    )
    assert plan.summary == "Update greeting"
    assert len(plan.changes) == 1
    assert "println" in plan.changes[0].unified_diff
    assert target.read_text(encoding="utf-8") == "fun main() = Unit\n"


@pytest.mark.parametrize("path", ["../outside.txt", "/tmp/outside.txt"])
def test_planner_rejects_unsafe_model_paths(tmp_path: Path, monkeypatch, path: str):
    payload = {"summary": "unsafe", "changes": [{
        "path": path, "content": "secret", "rationale": "bad",
    }]}
    monkeypatch.setattr(
        "urllib.request.urlopen",
        lambda request, timeout: FakeResponse(provider_envelope(json.dumps(payload))),
    )
    with pytest.raises(CodingAgentError, match="Rejected proposed path"):
        plan_code_changes(
            tmp_path, "change", endpoint="https://provider.example/v1/chat/completions",
            model="test-model", api_key="not-a-real-key",
        )


def test_planner_rejects_non_https_endpoint(tmp_path: Path):
    with pytest.raises(CodingAgentError, match="HTTPS"):
        plan_code_changes(
            tmp_path, "change", endpoint="http://provider.example/chat",
            model="test-model", api_key="x",
        )


def test_provider_credentials_error_is_redacted(tmp_path: Path, monkeypatch):
    def fail(request, timeout):
        raise HTTPError(request.full_url, 401, "unauthorized", {}, None)
    monkeypatch.setattr("urllib.request.urlopen", fail)
    with pytest.raises(CodingAgentError, match="rejected the backend credentials"):
        plan_code_changes(
            tmp_path, "change", endpoint="https://provider.example/v1/chat/completions",
            model="test-model", api_key="secret-should-not-appear",
        )


# API lifecycle tests: fake provider response, no live key or network required.
from fastapi.testclient import TestClient
from app import coding_agent as coding_agent_module


def test_api_requires_bearer_token(tmp_path: Path, monkeypatch):
    root = tmp_path / "workspaces"
    (root / "sample").mkdir(parents=True)
    monkeypatch.setenv("VYOMA_BACKEND_API_TOKEN", "test-token")
    monkeypatch.setenv("VYOMA_WORKSPACE_ROOT", str(root))
    client = TestClient(coding_agent_module.api)
    response = client.post("/v1/projects/sample/tasks/plan", json={"goal": "add greeting"})
    assert response.status_code == 401


def test_api_plan_does_not_write_until_approval(tmp_path: Path, monkeypatch):
    root = tmp_path / "workspaces"
    project = root / "sample"
    project.mkdir(parents=True)
    target = project / "Main.kt"
    target.write_text("fun main() = Unit\\n", encoding="utf-8")
    monkeypatch.setenv("VYOMA_BACKEND_API_TOKEN", "test-token")
    monkeypatch.setenv("VYOMA_WORKSPACE_ROOT", str(root))
    monkeypatch.setenv("VYOMA_AI_ENDPOINT", "https://provider.example/v1/chat/completions")
    monkeypatch.setenv("VYOMA_AI_MODEL", "test-model")
    monkeypatch.setenv("VYOMA_AI_API_KEY", "fake-test-key")
    coding_agent_module._api_tasks.clear()
    model_payload = {"summary": "Add greeting", "changes": [{
        "path": "Main.kt",
        "content": 'fun main() = println("hello")\\n',
        "rationale": "Show a greeting",
    }]}
    envelope = provider_envelope(json.dumps(model_payload))

    monkeypatch.setattr(
        "urllib.request.urlopen",
        lambda request, timeout: FakeResponse(envelope),
    )
    client = TestClient(coding_agent_module.api)
    headers = {"Authorization": "Bearer test-token"}
    planned = client.post(
        "/v1/projects/sample/tasks/plan",
        json={"goal": "add greeting"},
        headers=headers,
    )
    assert planned.status_code == 200, planned.text
    body = planned.json()
    assert body["status"] == "WAITING_FOR_APPROVAL"
    assert "println" in body["changes"][0]["diff"]
    assert target.read_text(encoding="utf-8") == "fun main() = Unit\\n"

    fetched = client.get(f"/v1/tasks/{body['task_id']}", headers=headers)
    assert fetched.status_code == 200
    assert fetched.json()["task_id"] == body["task_id"]

    approved = client.post(f"/v1/tasks/{body['task_id']}/approve", headers=headers)
    assert approved.status_code == 200, approved.text
    assert approved.json()["status"] == "SUCCEEDED"
    assert 'println("hello")' in target.read_text(encoding="utf-8")


def test_api_rejects_unknown_project_workspace(tmp_path: Path, monkeypatch):
    root = tmp_path / "workspaces"
    root.mkdir()
    monkeypatch.setenv("VYOMA_BACKEND_API_TOKEN", "test-token")
    monkeypatch.setenv("VYOMA_WORKSPACE_ROOT", str(root))
    client = TestClient(coding_agent_module.api)
    response = client.post(
        "/v1/projects/not-found/tasks/plan",
        json={"goal": "change code"},
        headers={"Authorization": "Bearer test-token"},
    )
    assert response.status_code == 404
