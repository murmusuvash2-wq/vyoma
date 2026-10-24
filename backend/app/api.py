"""Authenticated local-development API for task-scoped coding workspaces.

This API intentionally does not run shell commands or call an LLM. Do not expose it publicly
until the workspace root is a disposable isolated worker and production identity/authorization
are configured.
"""
from __future__ import annotations

import hmac
import os
import secrets
import sqlite3
import uuid
from contextlib import closing
from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, status
from pydantic import BaseModel, Field

from app.agent_workspace import (
    FileChangeProposal,
    WorkspaceError,
    apply_approved_change,
    list_files,
    propose_file_change,
    read_file,
)

app = FastAPI(title="Vyoma Coding Agent API", version="0.1.0")
MAX_TASKS_PER_REQUEST = 100


def _token() -> str:
    token = os.environ.get("VYOMA_BACKEND_TOKEN", "")
    if len(token) < 32:
        raise RuntimeError("VYOMA_BACKEND_TOKEN must be configured with at least 32 characters.")
    return token


def require_token(authorization: Annotated[str | None, Header()] = None) -> None:
    expected = _token()
    scheme, _, supplied = (authorization or "").partition(" ")
    if scheme.lower() != "bearer" or not supplied or not hmac.compare_digest(supplied, expected):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Valid bearer token required.")


Auth = Annotated[None, Depends(require_token)]


def _root() -> Path:
    raw = os.environ.get("VYOMA_WORKSPACE_ROOT", "").strip()
    if not raw:
        raise RuntimeError("VYOMA_WORKSPACE_ROOT must point to the server-managed workspace directory.")
    root = Path(raw).expanduser().resolve()
    root.mkdir(parents=True, exist_ok=True)
    if not root.is_dir():
        raise RuntimeError("Configured workspace root is not a directory.")
    return root


def _workspace(task_id: str) -> Path:
    try:
        parsed = uuid.UUID(task_id)
    except (ValueError, AttributeError) as exc:
        raise HTTPException(status_code=404, detail="Task workspace not found.") from exc
    root = _root()
    workspace = (root / str(parsed)).resolve()
    if workspace.parent != root or not workspace.is_dir():
        raise HTTPException(status_code=404, detail="Task workspace not found.")
    return workspace


def _db_path() -> Path:
    raw = os.environ.get("VYOMA_TASK_DB", "").strip()
    if not raw:
        raise RuntimeError("VYOMA_TASK_DB must be configured to a server-managed database path.")
    path = Path(raw).expanduser().resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    with closing(sqlite3.connect(path)) as db:
        db.execute("""CREATE TABLE IF NOT EXISTS proposals (
            proposal_id TEXT PRIMARY KEY,
            task_id TEXT NOT NULL,
            relative_path TEXT NOT NULL,
            original_content TEXT,
            proposed_content TEXT NOT NULL,
            unified_diff TEXT NOT NULL,
            original_exists INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'pending',
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )""")
        db.commit()
    return path


def _save_proposal(task_id: str, proposal: FileChangeProposal) -> str:
    proposal_id = str(uuid.uuid4())
    with closing(sqlite3.connect(_db_path())) as db:
        db.execute(
            """INSERT INTO proposals
            (proposal_id, task_id, relative_path, original_content, proposed_content,
             unified_diff, original_exists, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')""",
            (proposal_id, task_id, proposal.relative_path, proposal.original_content,
             proposal.proposed_content, proposal.unified_diff, int(proposal.original_exists)),
        )
        db.commit()
    return proposal_id


class CreateTaskResponse(BaseModel):
    task_id: str
    status: str
    note: str


class ProposalRequest(BaseModel):
    path: str = Field(min_length=1, max_length=500)
    proposed_content: str = Field(max_length=512 * 1024)


class ProposalResponse(BaseModel):
    proposal_id: str
    task_id: str
    path: str
    unified_diff: str
    status: str = "pending_review"


class ApprovalRequest(BaseModel):
    approved: bool


@app.get("/health")
def health() -> dict[str, str]:
    """Non-sensitive liveness probe; does not reveal configuration or secrets."""
    return {"status": "ok"}


@app.post("/v1/tasks", response_model=CreateTaskResponse, dependencies=[Depends(require_token)])
def create_task() -> CreateTaskResponse:
    task_id = str(uuid.uuid4())
    workspace = _root() / task_id
    workspace.mkdir(mode=0o700)
    return CreateTaskResponse(
        task_id=task_id,
        status="ready",
        note="Empty task workspace created. No agent execution or shell access is enabled.",
    )


@app.get("/v1/tasks/{task_id}/files", dependencies=[Depends(require_token)])
def get_files(task_id: str, limit: int = 100) -> dict[str, object]:
    try:
        return {"task_id": task_id, "files": list_files(_workspace(task_id), limit=limit)}
    except WorkspaceError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/v1/tasks/{task_id}/files/{path:path}", dependencies=[Depends(require_token)])
def get_file(task_id: str, path: str) -> dict[str, str]:
    try:
        return {"task_id": task_id, "path": path, "content": read_file(_workspace(task_id), path)}
    except WorkspaceError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/v1/tasks/{task_id}/proposals", response_model=ProposalResponse, dependencies=[Depends(require_token)])
def create_proposal(task_id: str, request: ProposalRequest) -> ProposalResponse:
    try:
        proposal = propose_file_change(_workspace(task_id), request.path, request.proposed_content)
        proposal_id = _save_proposal(task_id, proposal)
        return ProposalResponse(
            proposal_id=proposal_id, task_id=task_id, path=proposal.relative_path,
            unified_diff=proposal.unified_diff,
        )
    except WorkspaceError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/v1/tasks/{task_id}/proposals/{proposal_id}/decision", dependencies=[Depends(require_token)])
def decide_proposal(task_id: str, proposal_id: str, request: ApprovalRequest) -> dict[str, str]:
    db_path = _db_path()
    with closing(sqlite3.connect(db_path)) as db:
        row = db.execute(
            """SELECT relative_path, original_content, proposed_content, unified_diff,
                      original_exists, status
               FROM proposals WHERE proposal_id = ? AND task_id = ?""",
            (proposal_id, task_id),
        ).fetchone()
        if row is None:
            raise HTTPException(status_code=404, detail="Proposal not found.")
        relative_path, original, proposed, diff, original_exists, current_status = row
        if current_status != "pending":
            raise HTTPException(status_code=409, detail="Proposal has already been decided.")
        if not request.approved:
            db.execute("UPDATE proposals SET status='rejected' WHERE proposal_id=?", (proposal_id,))
            db.commit()
            return {"proposal_id": proposal_id, "status": "rejected"}
        proposal = FileChangeProposal(relative_path, original, proposed, diff, bool(original_exists))
        try:
            apply_approved_change(_workspace(task_id), proposal, approved=True)
        except WorkspaceError as exc:
            db.execute("UPDATE proposals SET status='conflict' WHERE proposal_id=?", (proposal_id,))
            db.commit()
            raise HTTPException(status_code=409, detail=str(exc)) from exc
        db.execute("UPDATE proposals SET status='applied' WHERE proposal_id=?", (proposal_id,))
        db.commit()
    return {"proposal_id": proposal_id, "status": "applied"}
