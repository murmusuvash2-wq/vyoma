from pathlib import Path

import pytest

from app.openhands_runtime import OpenHandsRuntimeError, run_openhands_plan


class FakeConversation:
    def __init__(self, workspace: str):
        self.workspace = Path(workspace)

    def send_message(self, message: str):
        self.message = message

    def run(self):
        (self.workspace / "Main.kt").write_text(
            'fun main() = println("planned")\n', encoding="utf-8"
        )


def fake_factory(**kwargs):
    return FakeConversation(kwargs["workspace"])


def test_openhands_returns_proposal_without_mutating_original(tmp_path: Path):
    original = tmp_path / "Main.kt"
    original.write_text("fun main() = Unit\n", encoding="utf-8")
    summary, proposals = run_openhands_plan(
        tmp_path,
        "Change the greeting",
        model="test-model",
        api_key="test-only-key",
        sdk_factory=fake_factory,
    )
    assert "temporary copy" in summary
    assert len(proposals) == 1
    assert proposals[0].relative_path == "Main.kt"
    assert "planned" in proposals[0].unified_diff
    assert original.read_text(encoding="utf-8") == "fun main() = Unit\n"


def test_openhands_requires_backend_credentials(tmp_path: Path, monkeypatch):
    monkeypatch.delenv("VYOMA_OPENHANDS_MODEL", raising=False)
    monkeypatch.delenv("VYOMA_OPENHANDS_API_KEY", raising=False)
    with pytest.raises(OpenHandsRuntimeError, match="Configure"):
        run_openhands_plan(tmp_path, "Change the greeting")


def test_openhands_rejects_empty_goal(tmp_path: Path):
    with pytest.raises(OpenHandsRuntimeError, match="Describe"):
        run_openhands_plan(
            tmp_path, "  ", model="test-model", api_key="test-only-key",
            sdk_factory=fake_factory,
        )


def test_openhands_sdk_dependencies_and_tool_imports():
    from openhands.sdk import Agent, Conversation, LLM, Tool
    from openhands.tools.file_editor import FileEditorTool
    from openhands.tools.task_tracker import TaskTrackerTool

    assert all(item is not None for item in (
        Agent, Conversation, LLM, Tool, FileEditorTool, TaskTrackerTool
    ))
