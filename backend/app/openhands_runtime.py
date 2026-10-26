"""OpenHands SDK adapter that produces reviewable diffs in a disposable copy.

This adapter intentionally exposes no shell/terminal tool. The OpenHands SDK can edit
files only inside a temporary copy of the selected project. The original project is
never mounted writable and all changes are returned as proposals for human review.
"""
from __future__ import annotations

import os
import shutil
import tempfile
from pathlib import Path
from typing import Any

from app.agent_workspace import (
    FileChangeProposal,
    WorkspaceError,
    list_files,
    propose_file_change,
)

MAX_WORKSPACE_FILES = 100
MAX_WORKSPACE_BYTES = 2 * 1024 * 1024
MAX_GOAL_CHARS = 8_000


class OpenHandsRuntimeError(RuntimeError):
    """Safe, user-displayable error from the OpenHands adapter."""


def run_openhands_plan(
    workspace_root: str | Path,
    user_goal: str,
    *,
    model: str | None = None,
    api_key: str | None = None,
    base_url: str | None = None,
    max_iterations: int = 12,
    sdk_factory: Any | None = None,
) -> tuple[str, tuple[FileChangeProposal, ...]]:
    """Run OpenHands in a disposable source copy and return proposals, never apply them.

    Provider configuration is server-side only. Terminal/shell tools are intentionally
    omitted. This is a development adapter; production should use OpenHands Agent Server
    with ephemeral container workspaces and network/resource policies.
    """
    if not user_goal.strip():
        raise OpenHandsRuntimeError("Describe the coding task first.")
    if len(user_goal) > MAX_GOAL_CHARS:
        raise OpenHandsRuntimeError(f"Task description exceeds {MAX_GOAL_CHARS} characters.")

    resolved_model = (model or os.getenv("VYOMA_OPENHANDS_MODEL", "")).strip()
    resolved_key = (api_key or os.getenv("VYOMA_OPENHANDS_API_KEY", "")).strip()
    resolved_base_url = (base_url or os.getenv("VYOMA_OPENHANDS_BASE_URL", "")).strip() or None
    if not resolved_model or not resolved_key:
        raise OpenHandsRuntimeError(
            "Configure VYOMA_OPENHANDS_MODEL and VYOMA_OPENHANDS_API_KEY on the backend."
        )

    root = Path(workspace_root).expanduser().resolve(strict=True)
    if not root.is_dir():
        raise OpenHandsRuntimeError("Project workspace does not exist.")

    paths = list_files(root, limit=MAX_WORKSPACE_FILES)
    total_bytes = 0
    with tempfile.TemporaryDirectory(prefix="vyoma-oh-plan-") as temp_dir:
        sandbox = Path(temp_dir) / "project"
        sandbox.mkdir(mode=0o700)
        for relative in paths:
            source = root / relative
            try:
                content = source.read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError, WorkspaceError):
                continue
            encoded_size = len(content.encode("utf-8"))
            if total_bytes + encoded_size > MAX_WORKSPACE_BYTES:
                continue
            destination = sandbox / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(content, encoding="utf-8", newline="")
            total_bytes += encoded_size

        before: dict[str, str] = {}
        for relative in list_files(sandbox, limit=MAX_WORKSPACE_FILES):
            before[relative] = (sandbox / relative).read_text(encoding="utf-8")

        try:
            if sdk_factory is None:
                from openhands.sdk import Agent, Conversation, LLM, Tool
                from openhands.tools.file_editor import FileEditorTool
                from openhands.tools.task_tracker import TaskTrackerTool

                def sdk_factory(model: str, api_key: str, base_url: str | None, workspace: str):
                    llm_kwargs = {"model": model, "api_key": api_key}
                    if base_url:
                        llm_kwargs["base_url"] = base_url
                    llm = LLM(**llm_kwargs)
                    # Deliberately no TerminalTool: the model cannot run shell commands.
                    agent = Agent(
                        llm=llm,
                        tools=[
                            Tool(name=FileEditorTool.name),
                            Tool(name=TaskTrackerTool.name),
                        ],
                    )
                    return Conversation(
                        agent=agent,
                        workspace=workspace,
                        max_iteration_per_run=max(1, min(max_iterations, 20)),
                    )

            conversation = sdk_factory(
                model=resolved_model,
                api_key=resolved_key,
                base_url=resolved_base_url,
                workspace=str(sandbox),
            )
            conversation.send_message(
                "Task: " + user_goal.strip() +
                "\n\nWork only within this disposable project copy. Do not ask for secrets. "
                "Do not change credentials, signing files, CI workflows, or generated build outputs. "
                "Make the smallest coherent source change. Do not claim tests passed; no shell or "
                "test runner is available. When finished, summarize the intended changes."
            )
            conversation.run()
        except OpenHandsRuntimeError:
            raise
        except Exception as exc:
            # Avoid including exception reprs that could contain request/configuration data.
            raise OpenHandsRuntimeError(
                f"OpenHands task failed ({type(exc).__name__}). Check backend logs and provider configuration."
            ) from None

        after: dict[str, str] = {}
        for relative in list_files(sandbox, limit=MAX_WORKSPACE_FILES):
            try:
                after[relative] = (sandbox / relative).read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError):
                continue

        changed_paths = sorted(
            path for path in set(before) | set(after)
            if before.get(path) != after.get(path)
        )
        proposals: list[FileChangeProposal] = []
        for relative in changed_paths:
            # Re-run path/type/protected-file validation against the original workspace.
            content = after.get(relative)
            if content is None:
                # Deletions require a separate explicit delete workflow; never apply them here.
                continue
            try:
                proposal = propose_file_change(root, relative, content)
            except (WorkspaceError, OSError):
                continue
            if proposal.unified_diff:
                proposals.append(proposal)

        if not proposals:
            raise OpenHandsRuntimeError(
                "OpenHands returned no reviewable file changes. Refine the task and retry."
            )

        summary = (
            f"OpenHands prepared {len(proposals)} reviewable file change(s) in a temporary copy. "
            "The original project was not modified; no tests or builds were run."
        )
        return summary, tuple(proposals)
