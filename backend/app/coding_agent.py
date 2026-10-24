"""Provider-backed coding planner that only returns reviewable file-change proposals.

No shell execution or file mutation happens in this module. Applying a proposal must
be performed separately through agent_workspace.apply_approved_change after user approval.
"""
from __future__ import annotations

import json
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from app.agent_workspace import FileChangeProposal, WorkspaceError, list_files, propose_file_change

MAX_CONTEXT_BYTES = 40_000
MAX_RESPONSE_BYTES = 512 * 1024
MAX_PROPOSED_FILES = 8


class CodingAgentError(RuntimeError):
    """A safe, user-displayable planning error."""


@dataclass(frozen=True)
class CodingPlan:
    summary: str
    changes: tuple[FileChangeProposal, ...]
    rationale: tuple[str, ...]


def _read_response(response: Any) -> bytes:
    data = response.read(MAX_RESPONSE_BYTES + 1)
    if len(data) > MAX_RESPONSE_BYTES:
        raise CodingAgentError("AI response exceeded the size limit. Narrow the task and retry.")
    return data


def _extract_json(content: str) -> dict[str, Any]:
    text = content.strip()
    if text.startswith("```"):
        lines = text.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        text = "\n".join(lines).strip()
    try:
        value = json.loads(text)
    except json.JSONDecodeError as exc:
        raise CodingAgentError("The model did not return valid JSON. Retry with a smaller task.") from exc
    if not isinstance(value, dict):
        raise CodingAgentError("The model response must be a JSON object.")
    return value


def plan_code_changes(
    workspace_root: str | Path,
    user_goal: str,
    *,
    endpoint: str,
    model: str,
    api_key: str,
    timeout_seconds: int = 60,
) -> CodingPlan:
    """Ask an OpenAI-compatible endpoint for a bounded plan and validated file diffs.

    The endpoint/model/key must be supplied by a trusted backend caller. Never pass
    a client-controlled workspace path or expose provider keys to Android clients.
    """
    if not user_goal.strip():
        raise CodingAgentError("Describe what you want the coding agent to change.")
    if not model.strip() or not api_key.strip():
        raise CodingAgentError("AI model and provider credentials must be configured on the backend.")
    from urllib.parse import urlparse

    parsed = urlparse(endpoint.strip())
    if parsed.scheme != "https" or not parsed.netloc:
        raise CodingAgentError("The AI provider endpoint must use HTTPS.")

    root = Path(workspace_root).expanduser().resolve(strict=True)
    file_context: list[dict[str, str]] = []
    used = 0
    for relative_path in list_files(root, limit=100):
        try:
            from app.agent_workspace import read_file
            content = read_file(root, relative_path)
        except WorkspaceError:
            continue
        remaining = MAX_CONTEXT_BYTES - used
        if remaining <= 0:
            break
        clipped = content[:remaining]
        file_context.append({"path": relative_path, "content": clipped})
        used += len(clipped.encode("utf-8"))
        if used >= MAX_CONTEXT_BYTES:
            break

    system_prompt = (
        "You are Vyoma, an Android-native app coding planner. Return ONLY a JSON object "
        "with keys summary (string) and changes (array). Each change must have path "
        "(normalized relative path), content (complete UTF-8 file content), and rationale "
        "(string). Do not return markdown. Do not request secrets. Do not edit build outputs, "
        "credentials, signing keys, or workflow permissions. Make the smallest relevant change. "
        "You cannot execute commands. Your output will be reviewed before any file is changed."
    )
    user_payload = json.dumps(
        {"goal": user_goal.strip(), "workspace_files": file_context},
        ensure_ascii=False,
    )
    request_body = json.dumps({
        "model": model.strip(),
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_payload},
        ],
        "temperature": 0.1,
    }).encode("utf-8")
    request = urllib.request.Request(
        endpoint.strip(),
        data=request_body,
        headers={
            "Authorization": f"Bearer {api_key.strip()}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=max(5, min(timeout_seconds, 120))) as response:
            raw = _read_response(response)
    except urllib.error.HTTPError as exc:
        # Do not echo request headers or credentials into error text/logs.
        if exc.code in (401, 403):
            raise CodingAgentError("AI provider rejected the backend credentials (401/403).") from None
        if exc.code == 429:
            raise CodingAgentError("AI provider rate limit or billing quota reached (429).") from None
        raise CodingAgentError(f"AI provider request failed with HTTP {exc.code}.") from None
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise CodingAgentError("Could not reach the AI provider. Check backend networking and retry.") from None

    try:
        envelope = json.loads(raw.decode("utf-8"))
        content = envelope["choices"][0]["message"]["content"]
        if not isinstance(content, str):
            raise TypeError("content is not text")
    except (UnicodeDecodeError, json.JSONDecodeError, KeyError, IndexError, TypeError):
        raise CodingAgentError("AI provider returned an unsupported chat-completions response.") from None

    payload = _extract_json(content)
    summary = payload.get("summary")
    changes = payload.get("changes")
    if not isinstance(summary, str) or not summary.strip() or not isinstance(changes, list):
        raise CodingAgentError("Model output must include a non-empty summary and a changes array.")
    if len(changes) > MAX_PROPOSED_FILES:
        raise CodingAgentError(f"The model proposed too many files (maximum {MAX_PROPOSED_FILES}).")

    proposals: list[FileChangeProposal] = []
    rationales: list[str] = []
    seen_paths: set[str] = set()
    for item in changes:
        if not isinstance(item, dict):
            raise CodingAgentError("Each proposed change must be a JSON object.")
        path = item.get("path")
        content = item.get("content")
        rationale = item.get("rationale")
        if not all(isinstance(value, str) for value in (path, content, rationale)):
            raise CodingAgentError("Each proposed change needs path, content, and rationale text.")
        if path in seen_paths:
            raise CodingAgentError("The model proposed the same path more than once.")
        seen_paths.add(path)
        try:
            proposal = propose_file_change(root, path, content)
        except (WorkspaceError, OSError) as exc:
            raise CodingAgentError(f"Rejected proposed path or content for {path!r}: {exc}") from None
        proposals.append(proposal)
        rationales.append(rationale[:1000])

    return CodingPlan(summary=summary.strip()[:2000], changes=tuple(proposals), rationale=tuple(rationales))



# --- Development API: authenticated plan/review/apply lifecycle -----------------
# This deliberately uses in-memory task state and is not production-ready.
import os
import re
import secrets
import threading
import uuid
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from app.agent_workspace import apply_approved_change
from app.audit import write_audit_event

api = FastAPI(title="Vyoma Coding Agent API", version="0.1.0")
_PROJECT_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")
_api_tasks_lock = threading.Lock()
_api_tasks: dict[str, dict[str, Any]] = {}


class PlanTaskRequest(BaseModel):
    goal: str = Field(min_length=1, max_length=4000)
    model: str | None = Field(default=None, max_length=200)


def _require_api_token(authorization: str | None = Header(default=None)) -> None:
    expected = os.environ.get("VYOMA_BACKEND_API_TOKEN", "").strip()
    if not expected:
        raise HTTPException(status_code=503, detail="Backend authentication is not configured.")
    scheme, _, supplied = (authorization or "").partition(" ")
    if scheme.lower() != "bearer" or not supplied or not secrets.compare_digest(supplied, expected):
        raise HTTPException(status_code=401, detail="Valid bearer authentication is required.")


def _resolve_project_workspace(project_id: str) -> Path:
    if not _PROJECT_ID_PATTERN.fullmatch(project_id):
        raise HTTPException(status_code=400, detail="Invalid project ID.")
    configured = os.environ.get("VYOMA_WORKSPACE_ROOT", "").strip()
    if not configured:
        raise HTTPException(status_code=503, detail="Workspace root is not configured.")
    root = Path(configured).expanduser().resolve()
    project = (root / project_id).resolve()
    try:
        project.relative_to(root)
    except ValueError:
        raise HTTPException(status_code=400, detail="Project path is outside the workspace root.") from None
    if not project.is_dir():
        raise HTTPException(status_code=404, detail="Project workspace was not found.")
    return project


def _provider_settings(model_override: str | None) -> tuple[str, str, str]:
    endpoint = os.environ.get("VYOMA_AI_ENDPOINT", "").strip()
    model = (model_override or os.environ.get("VYOMA_AI_MODEL", "")).strip()
    key = os.environ.get("VYOMA_AI_API_KEY", "").strip()
    if not endpoint or not model or not key:
        raise HTTPException(status_code=503, detail="Server-side AI provider configuration is incomplete.")
    return endpoint, model, key


def _task_json(task: dict[str, Any]) -> dict[str, Any]:
    plan: CodingPlan = task["plan"]
    return {
        "task_id": task["task_id"],
        "project_id": task["project_id"],
        "status": task["status"],
        "summary": plan.summary,
        "changes": [
            {
                "path": proposal.relative_path,
                "diff": proposal.unified_diff,
                "rationale": plan.rationale[index],
                "original_exists": proposal.original_exists,
            }
            for index, proposal in enumerate(plan.changes)
        ],
        "error": task.get("error"),
    }


@api.get("/health")
def api_health() -> dict[str, str]:
    return {"status": "ok", "mode": "development-foundation"}


@api.post("/v1/projects/{project_id}/tasks/plan")
def api_create_plan(
    project_id: str,
    body: PlanTaskRequest,
    _: None = Depends(_require_api_token),
) -> dict[str, Any]:
    workspace = _resolve_project_workspace(project_id)
    endpoint, model, key = _provider_settings(body.model)
    try:
        plan = plan_code_changes(workspace, body.goal, endpoint=endpoint, model=model, api_key=key)
    except CodingAgentError as exc:
        write_audit_event(
            "plan_failed", project_id=project_id, status="FAILED",
            details={"error_type": type(exc).__name__},
        )
        raise HTTPException(status_code=502, detail=str(exc)) from None
    task_id = str(uuid.uuid4())
    task = {
        "task_id": task_id,
        "project_id": project_id,
        "workspace": workspace,
        "plan": plan,
        "status": "WAITING_FOR_APPROVAL" if plan.changes else "READY_FOR_REVIEW",
    }
    with _api_tasks_lock:
        _api_tasks[task_id] = task
    write_audit_event(
        "plan_created", task_id=task_id, project_id=project_id,
        status=task["status"],
        paths=[change.relative_path for change in plan.changes],
        details={"change_count": len(plan.changes), "model": model},
    )
    return _task_json(task)


@api.get("/v1/tasks/{task_id}")
def api_get_task(task_id: str, _: None = Depends(_require_api_token)) -> dict[str, Any]:
    with _api_tasks_lock:
        task = _api_tasks.get(task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="Task was not found.")
    return _task_json(task)


@api.post("/v1/tasks/{task_id}/approve")
def api_approve_task(task_id: str, _: None = Depends(_require_api_token)) -> dict[str, Any]:
    with _api_tasks_lock:
        task = _api_tasks.get(task_id)
        if task is None:
            raise HTTPException(status_code=404, detail="Task was not found.")
        if task["status"] != "WAITING_FOR_APPROVAL":
            raise HTTPException(status_code=409, detail="Task is not waiting for approval.")
        plan: CodingPlan = task["plan"]
        # Until transactional multi-file apply/checkpointing exists, refuse it.
        if len(plan.changes) != 1:
            raise HTTPException(
                status_code=409,
                detail="This development API only applies one-file plans. Review and split the task.",
            )
        try:
            apply_approved_change(task["workspace"], plan.changes[0], approved=True)
        except (WorkspaceError, OSError):
            task["status"] = "FAILED"
            task["error"] = "Workspace changed or the approved change could not be applied safely. Re-plan."
            write_audit_event(
                "approval_apply_failed", task_id=task_id, project_id=task["project_id"],
                status="FAILED",
                paths=[change.relative_path for change in plan.changes],
            )
            raise HTTPException(status_code=409, detail=task["error"]) from None
        task["status"] = "SUCCEEDED"
        write_audit_event(
            "approval_applied", task_id=task_id, project_id=task["project_id"],
            status="SUCCEEDED",
            paths=[change.relative_path for change in plan.changes],
            details={"change_count": len(plan.changes)},
        )
        return _task_json(task)


@api.post("/v1/tasks/{task_id}/reject")
def api_reject_task(task_id: str, _: None = Depends(_require_api_token)) -> dict[str, Any]:
    """Reject a pending plan without mutating the workspace."""
    with _api_tasks_lock:
        task = _api_tasks.get(task_id)
        if task is None:
            raise HTTPException(status_code=404, detail="Task was not found.")
        if task["status"] != "WAITING_FOR_APPROVAL":
            raise HTTPException(status_code=409, detail="Task is not waiting for approval.")
        task["status"] = "REJECTED"
        write_audit_event(
            "plan_rejected", task_id=task_id, project_id=task["project_id"],
            status="REJECTED",
            paths=[change.relative_path for change in task["plan"].changes],
        )
        return _task_json(task)
