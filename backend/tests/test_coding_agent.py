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
