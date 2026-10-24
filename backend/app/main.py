"""Authenticated, process-local API for coding-plan review.

Development foundation only. Requires a server-side bearer token and only accepts
project IDs resolved under a configured workspace root.
"""
from __future__ import annotations

import os
import re
import secrets
import threading
import uuid
from dataclasses import dataclass
from pathlib import Path

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from app.agent_workspace import WorkspaceError, apply_approved_change
from app.coding_agent import CodingAgentError, CodingPlan, plan_code_changes

app = FastAPI(title="Vyoma Coding Agent API", version="0.1.0")
_PROJECT_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")
_tasks_lock = threading.Lock()


@dataclass
class _Task:
    task_id: str
    project_id: str
    status: str
    summary: str
    changes: tuple
    rationale: tuple
    error: str | None = None


_tasks: dict[str, _Task] = {}


class PlanRequest(BaseModel):
    goal: str = Field(min_length=1, max_length=4000)
    model: str | None = Field(default=None, max_length=200)


class TaskResponse(BaseModel):
    task_id: str
    project_id: str
    status: str
    summary: str
    changes: list[dict]
    error: str | None = None


def _authorize(authorization: str | None = Header(default=None)) -> None:
    expected = os.environ.get("VYOMA_BACKEND_API_TOKEN", "").strip()
    if not expected:
        raise HTTPException(status_code=503, detail="Backend authentication is not configured.")
    scheme, _, supplied = (authorization or "").partition(" ")
    if scheme.lower() != "bearer" or not supplied or not secrets.compare_digest(supplied, expected):
        raise HTTPException(status_code=401, detail="Valid bearer authentication is required.")


def _project_root(project_id: str) -> Path:
    if not _PROJECT_ID.fullmatch(project_id):
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


def _provider_config(model_override: str | None) -> tuple[str, str, str]:
    endpoint = os.environ.get("VYOMA_AI_ENDPOINT", "").strip()
    model = (model_override or os.environ.get("VYOMA_AI_MODEL", "")).strip()
    api_key = os.environ.get("VYOMA_AI_API_KEY", "").strip()
    if not endpoint or not model or not api_key:
        raise HTTPException(status_code=503, detail="Server-side AI provider configuration is incomplete.")
    return endpoint, model, api_key


def _serialize(task: _Task) -> TaskResponse:
    return TaskResponse(
        task_id=task.task_id,
        project_id=task.project_id,
        status=task.status,
        summary=task.summary,
        error=task.error,
        changes=[
            {
                "path": proposal.relative_path,
                "diff": proposal.unified_diff,
                "rationale": task.rationale[index],
                "original_exists": proposal.original_exists,
            }
            for index, proposal in enumerate(task.changes)
        ],
    )


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "mode": "development-foundation"}


@app.post("/v1/projects/{project_id}/tasks/plan", response_model=TaskResponse)
def create_plan(project_id: str, body: PlanRequest, _: None = Depends(_authorize)) -> TaskResponse:
    workspace = _project_root(project_id)
    endpoint, model, api_key = _provider_config(body.model)
    try:
        plan: CodingPlan = plan_code_changes(
            workspace, body.goal, endpoint=endpoint, model=model, api_key=api_key
        )
    except CodingAgentError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from None
    task_id = str(uuid.uuid4())
    task = _Task(
        task_id=task_id,
        project_id=project_id,
        status="WAITING_FOR_APPROVAL" if plan.changes else "READY_FOR_REVIEW",
        summary=plan.summary,
        changes=plan.changes,
        rationale=plan.rationale,
    )
    with _tasks_lock:
        _tasks[task_id] = task
    return _serialize(task)


@app.get("/v1/tasks/{task_id}", response_model=TaskResponse)
def get_task(task_id: str, _: None = Depends(_authorize)) -> TaskResponse:
    with _tasks_lock:
        task = _tasks.get(task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="Task was not found.")
    return _serialize(task)


@app.post("/v1/tasks/{task_id}/approve", response_model=TaskResponse)
def approve_task(task_id: str, _: None = Depends(_authorize)) -> TaskResponse:
    with _tasks_lock:
        task = _tasks.get(task_id)
        if task is None:
            raise HTTPException(status_code=404, detail="Task was not found.")
        if task.status != "WAITING_FOR_APPROVAL":
            raise HTTPException(status_code=409, detail="Task is not waiting for approval.")
        # Refuse multi-file plans until applying them can be transactional.
        # Otherwise a later failure could leave a partially modified workspace.
        if len(task.changes) != 1:
            raise HTTPException(
                status_code=409,
                detail="This development API only applies one-file plans. Review and split the task.",
            )
        workspace = _project_root(task.project_id)
        try:
            apply_approved_change(workspace, task.changes[0], approved=True)
        except (WorkspaceError, OSError):
            task.status = "FAILED"
            task.error = "Could not apply approved changes safely. Re-plan the task."
            raise HTTPException(status_code=409, detail=task.error) from None
        task.status = "SUCCEEDED"
        return _serialize(task)
