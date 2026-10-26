import base64
import io
import json
import zipfile
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

    # Simulate a backend process restart: the in-memory cache is empty, so the
    # task must be reconstructed from the persistent task database.
    coding_agent_module._api_tasks.clear()
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



def test_api_rejects_plan_without_mutating_workspace(tmp_path: Path, monkeypatch):
    root = tmp_path / "workspaces"
    project = root / "sample"
    project.mkdir(parents=True)
    target = project / "Main.kt"
    target.write_text("fun main() = Unit\\n", encoding="utf-8")
    monkeypatch.setenv("VYOMA_BACKEND_API_TOKEN", "test-token")
    monkeypatch.setenv("VYOMA_WORKSPACE_ROOT", str(root))
    monkeypatch.setenv("VYOMA_AI_ENDPOINT", "https://provider.example/v1/chat/completions")
    monkeypatch.setenv("VYOMA_AI_MODEL", "test-model")
    monkeypatch.setenv("VYOMA_AI_API_KEY", "not-a-real-key")
    model_payload = {"summary": "Update greeting", "changes": [{
        "path": "Main.kt", "content": 'fun main() = println("hello")\\n',
        "rationale": "Add a greeting",
    }]}
    monkeypatch.setattr(
        "urllib.request.urlopen",
        lambda request, timeout: FakeResponse(provider_envelope(json.dumps(model_payload))),
    )
    client = TestClient(coding_agent_module.api)
    headers = {"Authorization": "Bearer test-token"}
    planned = client.post(
        "/v1/projects/sample/tasks/plan",
        json={"goal": "add greeting"},
        headers=headers,
    )
    assert planned.status_code == 200, planned.text
    task_id = planned.json()["task_id"]
    rejected = client.post(f"/v1/tasks/{task_id}/reject", headers=headers)
    assert rejected.status_code == 200, rejected.text
    assert rejected.json()["status"] == "REJECTED"
    assert target.read_text(encoding="utf-8") == "fun main() = Unit\\n"
    duplicate = client.post(f"/v1/tasks/{task_id}/reject", headers=headers)
    assert duplicate.status_code == 409


def test_api_applies_multi_file_plan_after_single_explicit_approval(tmp_path: Path, monkeypatch):
    root = tmp_path / "workspaces"
    project = root / "sample"
    project.mkdir(parents=True)
    first = project / "Main.kt"
    second = project / "Settings.kt"
    first.write_text("fun main() = Unit\\n", encoding="utf-8")
    second.write_text("object Settings\\n", encoding="utf-8")

    monkeypatch.setenv("VYOMA_BACKEND_API_TOKEN", "test-token")
    monkeypatch.setenv("VYOMA_WORKSPACE_ROOT", str(root))
    monkeypatch.setenv("VYOMA_AI_ENDPOINT", "https://provider.example/v1/chat/completions")
    monkeypatch.setenv("VYOMA_AI_MODEL", "test-model")
    monkeypatch.setenv("VYOMA_AI_API_KEY", "fake-test-key")
    coding_agent_module._api_tasks.clear()

    model_payload = {"summary": "Change two files", "changes": [
        {"path": "Main.kt", "content": "fun main() = println(1)\\n", "rationale": "Update main"},
        {"path": "Settings.kt", "content": "object Settings { val enabled = true }\\n", "rationale": "Add setting"},
    ]}
    envelope = provider_envelope(json.dumps(model_payload))
    monkeypatch.setattr(
        "urllib.request.urlopen",
        lambda request, timeout: FakeResponse(envelope),
    )
    client = TestClient(coding_agent_module.api)
    headers = {"Authorization": "Bearer test-token"}
    planned = client.post(
        "/v1/projects/sample/tasks/plan",
        json={"goal": "change both files"},
        headers=headers,
    )
    assert planned.status_code == 200, planned.text
    task = planned.json()
    assert len(task["changes"]) == 2
    # Planning must remain read-only until the user approves the complete diff.
    assert first.read_text(encoding="utf-8") == "fun main() = Unit\\n"
    assert second.read_text(encoding="utf-8") == "object Settings\\n"

    approved = client.post(f"/v1/tasks/{task['task_id']}/approve", headers=headers)
    assert approved.status_code == 200, approved.text
    assert approved.json()["status"] == "SUCCEEDED"
    assert first.read_text(encoding="utf-8") == "fun main() = println(1)\\n"
    assert second.read_text(encoding="utf-8") == "object Settings { val enabled = true }\\n"


def test_api_openhands_engine_returns_reviewable_plan_without_mutation(tmp_path: Path, monkeypatch):
    root = tmp_path / "workspaces"
    project = root / "sample"
    project.mkdir(parents=True)
    target = project / "Main.kt"
    target.write_text("fun main() = Unit\\n", encoding="utf-8")
    monkeypatch.setenv("VYOMA_BACKEND_API_TOKEN", "test-token")
    monkeypatch.setenv("VYOMA_WORKSPACE_ROOT", str(root))
    monkeypatch.setenv("VYOMA_OPENHANDS_MODEL", "test-openhands-model")
    monkeypatch.setenv("VYOMA_OPENHANDS_API_KEY", "fake-test-key")
    coding_agent_module._api_tasks.clear()

    from app.agent_workspace import propose_file_change

    def fake_openhands(workspace, goal, *, model=None, api_key=None, base_url=None, max_iterations=12, sdk_factory=None):
        assert model == "test-openhands-model"
        proposal = propose_file_change(workspace, "Main.kt", 'fun main() = println("openhands")\\n')
        return "OpenHands test summary", (proposal,)

    monkeypatch.setattr(coding_agent_module, "run_openhands_plan", fake_openhands)
    client = TestClient(coding_agent_module.api)
    planned = client.post(
        "/v1/projects/sample/tasks/plan",
        json={"goal": "Add greeting", "engine": "openhands"},
        headers={"Authorization": "Bearer test-token"},
    )
    assert planned.status_code == 200, planned.text
    body = planned.json()
    assert body["status"] == "WAITING_FOR_APPROVAL"
    assert "openhands" in body["changes"][0]["diff"]
    assert target.read_text(encoding="utf-8") == "fun main() = Unit\\n"


def _zip_base64(entries: list[tuple[str, bytes]]) -> str:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in entries:
            archive.writestr(name, data)
    return base64.b64encode(buffer.getvalue()).decode("ascii")


def _prepare_import_project(tmp_path: Path, monkeypatch):
    root = tmp_path / "workspaces"
    project = root / "sample"
    project.mkdir(parents=True)
    monkeypatch.setenv("VYOMA_BACKEND_API_TOKEN", "test-token")
    monkeypatch.setenv("VYOMA_WORKSPACE_ROOT", str(root))
    return project


def test_zip_import_adds_utf8_source_files_to_empty_project(tmp_path: Path, monkeypatch):
    project = _prepare_import_project(tmp_path, monkeypatch)
    client = TestClient(coding_agent_module.api)
    response = client.post(
        "/v1/projects/sample/import-zip",
        json={"archive_base64": _zip_base64([
            ("app/src/MainActivity.kt", b"package sample\\n"),
            ("settings.gradle.kts", b'rootProject.name = "Sample"\\n'),
        ])},
        headers={"Authorization": "Bearer test-token"},
    )
    assert response.status_code == 200, response.text
    assert response.json()["imported_files"] == 2
    assert (project / "app/src/MainActivity.kt").read_text(encoding="utf-8") == "package sample\\n"
    assert (project / "settings.gradle.kts").is_file()


@pytest.mark.parametrize("unsafe_name", [
    "../outside.kt",
    "/absolute.kt",
    "app/../../outside.kt",
    "app\\..\\outside.kt",
    ".env",
    ".env/local-config.txt",
    "config/service-account.json",
    "certs/signing.pem",
    "app/upload-keystore.jks",
    "build/generated.kt",
    "assets/photo.png",
])
def test_zip_import_rejects_unsafe_or_unsupported_entries(tmp_path: Path, monkeypatch, unsafe_name: str):
    project = _prepare_import_project(tmp_path, monkeypatch)
    client = TestClient(coding_agent_module.api)
    response = client.post(
        "/v1/projects/sample/import-zip",
        json={"archive_base64": _zip_base64([(unsafe_name, b"unsafe content")])},
        headers={"Authorization": "Bearer test-token"},
    )
    assert response.status_code in {400, 413}
    assert list(project.iterdir()) == []


def test_zip_import_requires_authentication_and_empty_workspace(tmp_path: Path, monkeypatch):
    project = _prepare_import_project(tmp_path, monkeypatch)
    client = TestClient(coding_agent_module.api)
    payload = {"archive_base64": _zip_base64([("Main.kt", b"fun main() = Unit\\n")])}
    unauthorized = client.post("/v1/projects/sample/import-zip", json=payload)
    assert unauthorized.status_code == 401

    (project / "existing.kt").write_text("do not overwrite", encoding="utf-8")
    conflict = client.post(
        "/v1/projects/sample/import-zip",
        json=payload,
        headers={"Authorization": "Bearer test-token"},
    )
    assert conflict.status_code == 409
    assert (project / "existing.kt").read_text(encoding="utf-8") == "do not overwrite"


def test_api_multi_file_undo_restores_complete_change_set(tmp_path: Path, monkeypatch):
    root = tmp_path / "workspaces"
    project = root / "sample"
    project.mkdir(parents=True)
    first = project / "Main.kt"
    second = project / "Settings.kt"
    first.write_text("main original\\n", encoding="utf-8")
    second.write_text("settings original\\n", encoding="utf-8")
    monkeypatch.setenv("VYOMA_BACKEND_API_TOKEN", "test-token")
    monkeypatch.setenv("VYOMA_WORKSPACE_ROOT", str(root))
    monkeypatch.setenv("VYOMA_TASK_DB_PATH", str(tmp_path / "tasks.sqlite3"))
    monkeypatch.setenv("VYOMA_AI_ENDPOINT", "https://provider.example/v1/chat/completions")
    monkeypatch.setenv("VYOMA_AI_MODEL", "test-model")
    monkeypatch.setenv("VYOMA_AI_API_KEY", "fake-test-key")
    coding_agent_module._api_tasks.clear()
    payload = {"summary": "Update both files", "changes": [
        {"path": "Main.kt", "content": "main updated\\n", "rationale": "Update main"},
        {"path": "Settings.kt", "content": "settings updated\\n", "rationale": "Update settings"},
    ]}
    monkeypatch.setattr("urllib.request.urlopen", lambda request, timeout: FakeResponse(provider_envelope(json.dumps(payload))))
    client = TestClient(coding_agent_module.api)
    headers = {"Authorization": "Bearer test-token"}
    planned = client.post("/v1/projects/sample/tasks/plan", json={"goal": "update both files"}, headers=headers)
    assert planned.status_code == 200, planned.text
    task_id = planned.json()["task_id"]
    approved = client.post(f"/v1/tasks/{task_id}/approve", headers=headers)
    assert approved.status_code == 200, approved.text
    assert first.read_text(encoding="utf-8") == "main updated\\n"
    assert second.read_text(encoding="utf-8") == "settings updated\\n"
    undone = client.post(f"/v1/tasks/{task_id}/undo", headers=headers)
    assert undone.status_code == 200, undone.text
    assert undone.json()["status"] == "UNDONE"
    assert first.read_text(encoding="utf-8") == "main original\\n"
    assert second.read_text(encoding="utf-8") == "settings original\\n"


def test_planner_sends_valid_design_image_as_vision_content(tmp_path: Path, monkeypatch):
    payload = {"summary": "Implement screenshot layout", "changes": []}
    captured = {}

    def fake_urlopen(request, timeout):
        captured["body"] = json.loads(request.data.decode("utf-8"))
        return FakeResponse(provider_envelope(json.dumps(payload)))

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)
    png = bytes.fromhex("89504e470d0a1a0a") + b"test-image-bytes"
    data_url = "data:image/png;base64," + base64.b64encode(png).decode("ascii")
    plan_code_changes(
        tmp_path, "Build this screen in Jetpack Compose",
        endpoint="https://provider.example/v1/chat/completions",
        model="vision-model", api_key="test-key", design_image_data_url=data_url,
    )
    content = captured["body"]["messages"][1]["content"]
    assert isinstance(content, list)
    assert content[1]["type"] == "image_url"
    assert content[1]["image_url"]["url"] == data_url
    assert "Jetpack Compose" in content[0]["text"]


def test_planner_rejects_invalid_design_image_before_provider_call(tmp_path: Path, monkeypatch):
    called = False

    def fake_urlopen(request, timeout):
        nonlocal called
        called = True
        return FakeResponse(provider_envelope('{"summary":"ok","changes":[]}'))

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)
    with pytest.raises(CodingAgentError, match="PNG, JPEG, or WebP"):
        plan_code_changes(
            tmp_path, "Build screen", endpoint="https://provider.example/v1/chat/completions",
            model="vision-model", api_key="test-key", design_image_data_url="data:image/gif;base64,R0lGODlh",
        )
    assert called is False


def test_planner_rejects_design_image_over_two_megabytes(tmp_path: Path):
    png = bytes.fromhex("89504e470d0a1a0a") + b"x" * (2 * 1024 * 1024)
    data_url = "data:image/png;base64," + base64.b64encode(png).decode("ascii")
    with pytest.raises(CodingAgentError, match="2 MB"):
        plan_code_changes(
            tmp_path, "Build screen", endpoint="https://provider.example/v1/chat/completions",
            model="vision-model", api_key="test-key", design_image_data_url=data_url,
        )


def test_connector_catalog_is_authenticated_and_contains_allowlisted_providers(monkeypatch):
    monkeypatch.setenv("VYOMA_BACKEND_API_TOKEN", "catalog-test-token")
    client = TestClient(coding_agent_module.api)
    denied = client.get("/v1/connectors/catalog")
    assert denied.status_code == 401
    response = client.get(
        "/v1/connectors/catalog",
        headers={"Authorization": "Bearer catalog-test-token"},
    )
    assert response.status_code == 200
    connectors = {item["connector_id"]: item for item in response.json()["connectors"]}
    assert {"figma", "canva", "supabase", "github"} <= connectors.keys()
    assert connectors["supabase"]["initial_access"] == "read-only initially"
    assert all("token" not in key.lower() and "secret" not in key.lower() for item in connectors.values() for key in item)
