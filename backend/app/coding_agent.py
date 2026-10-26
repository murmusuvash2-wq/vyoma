"""Provider-backed coding planner that only returns reviewable file-change proposals.

No shell execution or file mutation happens in this module. Applying a proposal must
be performed separately through agent_workspace.apply_approved_change after user approval.
"""
from __future__ import annotations

import base64
import binascii
import json
import io
import re
import shutil
import tempfile
import zipfile
from pathlib import PurePosixPath
import urllib.error
import urllib.request
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterator

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
import sqlite3
import threading
import uuid
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from app.openhands_runtime import OpenHandsRuntimeError, run_openhands_plan
from app.agent_workspace import apply_approved_change, apply_approved_changes, restore_applied_change
from app.audit import write_audit_event

api = FastAPI(title="Vyoma Coding Agent API", version="0.1.0")
_PROJECT_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")
_api_tasks_lock = threading.Lock()
_api_tasks: dict[str, dict[str, Any]] = {}

@contextmanager
def _task_db() -> Iterator[sqlite3.Connection]:
    db_path = Path(os.environ.get(
        "VYOMA_TASK_DB_PATH",
        str(Path(__file__).resolve().parents[1] / ".data" / "coding_tasks.sqlite3"),
    )).expanduser()
    db_path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    try:
        os.chmod(db_path.parent, 0o700)
    except OSError:
        pass
    connection = sqlite3.connect(db_path, timeout=10)
    connection.row_factory = sqlite3.Row
    try:
        connection.execute("CREATE TABLE IF NOT EXISTS coding_tasks (task_id TEXT PRIMARY KEY, project_id TEXT NOT NULL, status TEXT NOT NULL, payload_json TEXT NOT NULL, updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)")
        try:
            os.chmod(db_path, 0o600)
        except OSError:
            pass
        yield connection
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def _task_payload(task: dict[str, Any]) -> dict[str, Any]:
    plan: CodingPlan = task["plan"]
    return {
        "summary": plan.summary,
        "rationale": list(plan.rationale),
        "changes": [
            {
                "relative_path": change.relative_path,
                "original_content": change.original_content,
                "proposed_content": change.proposed_content,
                "unified_diff": change.unified_diff,
                "original_exists": change.original_exists,
            }
            for change in plan.changes
        ],
        "error": task.get("error"),
    }


def _persist_task(task: dict[str, Any]) -> None:
    payload = json.dumps(_task_payload(task), ensure_ascii=False)
    with _task_db() as connection:
        connection.execute(
            "INSERT INTO coding_tasks(task_id, project_id, status, payload_json, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT(task_id) DO UPDATE SET project_id=excluded.project_id, status=excluded.status, payload_json=excluded.payload_json, updated_at=CURRENT_TIMESTAMP",
            (task["task_id"], task["project_id"], task["status"], payload),
        )


def _get_task(task_id: str) -> dict[str, Any] | None:
    with _api_tasks_lock:
        cached = _api_tasks.get(task_id)
    if cached is not None:
        return cached
    with _task_db() as connection:
        row = connection.execute("SELECT task_id, project_id, status, payload_json FROM coding_tasks WHERE task_id=?", (task_id,)).fetchone()
    if row is None:
        return None
    try:
        payload = json.loads(row["payload_json"])
        workspace = _resolve_project_workspace(row["project_id"])
        changes = tuple(
            FileChangeProposal(
                relative_path=item["relative_path"],
                original_content=item.get("original_content"),
                proposed_content=item["proposed_content"],
                unified_diff=item["unified_diff"],
                original_exists=bool(item["original_exists"]),
            )
            for item in payload["changes"]
        )
        plan = CodingPlan(summary=payload["summary"], changes=changes, rationale=tuple(payload["rationale"]))
        task = {"task_id": row["task_id"], "project_id": row["project_id"], "workspace": workspace, "plan": plan, "status": row["status"], "error": payload.get("error")}
    except (KeyError, TypeError, ValueError, HTTPException, OSError, json.JSONDecodeError):
        return None
    with _api_tasks_lock:
        _api_tasks[task_id] = task
    return task



class PlanTaskRequest(BaseModel):
    goal: str = Field(min_length=1, max_length=4000)
    model: str | None = Field(default=None, max_length=200)
    engine: str = Field(default="planner", pattern="^(planner|openhands)$")


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
    if body.engine == "openhands":
        model = (body.model or os.environ.get("VYOMA_OPENHANDS_MODEL", "")).strip()
        try:
            summary, changes = run_openhands_plan(workspace, body.goal, model=model or None)
            plan = CodingPlan(summary=summary, changes=changes, rationale=tuple(
                "Prepared by OpenHands in a disposable copy; review before applying."
                for _ in changes
            ))
        except OpenHandsRuntimeError as exc:
            write_audit_event(
                "openhands_plan_failed", project_id=project_id, status="FAILED",
                details={"error_type": type(exc).__name__},
            )
            raise HTTPException(status_code=502, detail=str(exc)) from None
    else:
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
    _persist_task(task)
    write_audit_event(
        "plan_created", task_id=task_id, project_id=project_id,
        status=task["status"],
        paths=[change.relative_path for change in plan.changes],
        details={"change_count": len(plan.changes), "model": model, "engine": body.engine},
    )
    return _task_json(task)


@api.get("/v1/tasks/{task_id}")
def api_get_task(task_id: str, _: None = Depends(_require_api_token)) -> dict[str, Any]:
    task = _get_task(task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="Task was not found.")
    return _task_json(task)


@api.post("/v1/tasks/{task_id}/approve")
def api_approve_task(task_id: str, _: None = Depends(_require_api_token)) -> dict[str, Any]:
    task = _get_task(task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="Task was not found.")
    with _api_tasks_lock:
        if task["status"] != "WAITING_FOR_APPROVAL":
            raise HTTPException(status_code=409, detail="Task is not waiting for approval.")
        plan: CodingPlan = task["plan"]
        try:
            apply_approved_changes(task["workspace"], plan.changes, approved=True)
        except (WorkspaceError, OSError):
            task["status"] = "FAILED"
            task["error"] = "Workspace changed or the approved change could not be applied safely. Re-plan."
            _persist_task(task)
            write_audit_event(
                "approval_apply_failed", task_id=task_id, project_id=task["project_id"],
                status="FAILED",
                paths=[change.relative_path for change in plan.changes],
            )
            raise HTTPException(status_code=409, detail=task["error"]) from None
        task["status"] = "SUCCEEDED"
        _persist_task(task)
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
    # Load persisted state first, just like task refresh and approval. Do this before
    # acquiring the task lock because _get_task also uses that lock.
    task = _get_task(task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="Task was not found.")
    with _api_tasks_lock:
        if task["status"] != "WAITING_FOR_APPROVAL":
            raise HTTPException(status_code=409, detail="Task is not waiting for approval.")
        task["status"] = "REJECTED"
        _persist_task(task)
        write_audit_event(
            "plan_rejected", task_id=task_id, project_id=task["project_id"],
            status="REJECTED",
            paths=[change.relative_path for change in task["plan"].changes],
        )
        return _task_json(task)


@api.post("/v1/tasks/{task_id}/undo")
def api_undo_task(task_id: str, _: None = Depends(_require_api_token)) -> dict[str, Any]:
    """Undo a successfully applied single-file task, only if the file is unchanged."""
    task = _get_task(task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="Task was not found.")
    with _api_tasks_lock:
        if task["status"] != "SUCCEEDED":
            raise HTTPException(status_code=409, detail="Only a successful, not-yet-undone task can be undone.")
        plan: CodingPlan = task["plan"]
        if len(plan.changes) != 1:
            raise HTTPException(status_code=409, detail="Undo currently supports one-file tasks only.")
        change = plan.changes[0]
        try:
            restore_applied_change(
                task["workspace"],
                change.relative_path,
                expected_current_content=change.proposed_content,
                original_content=change.original_content,
                original_exists=change.original_exists,
                approved=True,
            )
        except (WorkspaceError, OSError):
            write_audit_event(
                "undo_refused", task_id=task_id, project_id=task["project_id"],
                status="CONFLICT", paths=[change.relative_path],
            )
            raise HTTPException(
                status_code=409,
                detail="Undo was refused because the file changed after the approved edit. Review the file and create a new task.",
            ) from None
        task["status"] = "UNDONE"
        _persist_task(task)
        write_audit_event(
            "task_undone", task_id=task_id, project_id=task["project_id"],
            status="UNDONE", paths=[change.relative_path],
        )
        return _task_json(task)


class CreateProjectRequest(BaseModel):
    project_id: str = Field(min_length=1, max_length=64, pattern=r"^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")


@api.post("/v1/projects")
def api_create_project(
    body: CreateProjectRequest,
    _: None = Depends(_require_api_token),
) -> dict[str, str]:
    """Create an empty server-managed workspace for a new coding project."""
    configured = os.environ.get("VYOMA_WORKSPACE_ROOT", "").strip()
    if not configured:
        raise HTTPException(status_code=503, detail="Workspace root is not configured.")
    root = Path(configured).expanduser().resolve()
    root.mkdir(parents=True, exist_ok=True)
    if not root.is_dir():
        raise HTTPException(status_code=503, detail="Workspace root is not a directory.")
    project = (root / body.project_id).resolve()
    try:
        project.relative_to(root)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid project ID.") from None
    try:
        project.mkdir(mode=0o700, parents=False, exist_ok=False)
    except FileExistsError:
        raise HTTPException(status_code=409, detail="A project with this ID already exists.") from None
    except OSError:
        raise HTTPException(status_code=500, detail="Could not create project workspace.") from None
    write_audit_event(
        "project_workspace_created",
        project_id=body.project_id,
        status="READY",
        details={"workspace_kind": "empty"},
    )
    return {
        "project_id": body.project_id,
        "status": "READY",
        "message": "Empty project workspace created. No source code or build files have been generated yet.",
    }


# ZIP import limits are intentionally conservative for the development backend.
MAX_IMPORT_ARCHIVE_BYTES = 10 * 1024 * 1024
MAX_IMPORT_FILES = 500
MAX_IMPORT_UNCOMPRESSED_BYTES = 25 * 1024 * 1024
MAX_IMPORT_FILE_BYTES = 2 * 1024 * 1024
_IMPORT_TEXT_SUFFIXES = {
    ".kt", ".kts", ".java", ".xml", ".gradle", ".properties", ".toml",
    ".json", ".yaml", ".yml", ".md", ".txt", ".py", ".ts", ".tsx",
    ".js", ".jsx", ".css", ".html", ".sql", ".sh", ".gitignore",
}
_PROTECTED_NAMES = {
    ".env", ".env.local", ".env.production", "id_rsa", "id_ed25519",
    "keystore", "upload-keystore.jks", "debug.keystore", "credentials.json",
    "secrets.json", "service-account.json",
}


class ImportProjectZipRequest(BaseModel):
    archive_base64: str = Field(min_length=1, max_length=14_000_000)


def _validate_import_member(info: zipfile.ZipInfo) -> tuple[str, bool]:
    raw = info.filename
    if not raw or "\x00" in raw or "\\" in raw:
        raise HTTPException(status_code=400, detail="ZIP contains an invalid path.")
    path = PurePosixPath(raw)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise HTTPException(status_code=400, detail="ZIP contains a path traversal entry.")
    if path.parts and ":" in path.parts[0]:
        raise HTTPException(status_code=400, detail="ZIP contains an absolute or drive-qualified path.")
    normalized = path.as_posix().rstrip("/")
    if not normalized:
        raise HTTPException(status_code=400, detail="ZIP contains an invalid path.")
    mode = (info.external_attr >> 16) & 0xFFFF
    if mode and (mode & 0o170000) == 0o120000:
        raise HTTPException(status_code=400, detail="ZIP symlink entries are not allowed.")
    if info.is_dir():
        return normalized, True
    name_parts = {part.lower() for part in path.parts}
    basename = path.name.lower()
    sensitive_suffixes = {".jks", ".keystore", ".pem", ".p12", ".pfx", ".key"}
    if (
        any(part == ".env" or part.startswith(".env.") for part in name_parts)
        or any(part in _PROTECTED_NAMES for part in name_parts)
        or basename.startswith(".env.")
        or path.suffix.lower() in sensitive_suffixes
    ):
        raise HTTPException(status_code=400, detail="ZIP contains a protected secrets or signing file.")
    if any(part in {"node_modules", ".git", ".gradle", "build", ".idea", ".venv"} for part in name_parts):
        raise HTTPException(status_code=400, detail="ZIP must not contain repository metadata or generated build output.")
    if path.suffix.lower() not in _IMPORT_TEXT_SUFFIXES and basename not in {".gitignore", "dockerfile"}:
        raise HTTPException(status_code=400, detail=f"Unsupported imported file type: {path.suffix or basename}. Only text/source files are supported in this first import flow.")
    if info.file_size > MAX_IMPORT_FILE_BYTES:
        raise HTTPException(status_code=413, detail="ZIP contains a file that exceeds the per-file size limit.")
    if info.file_size > 0 and info.compress_size == 0:
        raise HTTPException(status_code=400, detail="ZIP contains an invalid compressed-size declaration.")
    if info.compress_size > 0 and info.file_size / info.compress_size > 200:
        raise HTTPException(status_code=400, detail="ZIP compression ratio is too high.")
    return normalized, False


@api.post("/v1/projects/{project_id}/import-zip")
def api_import_project_zip(
    project_id: str,
    body: ImportProjectZipRequest,
    _: None = Depends(_require_api_token),
) -> dict[str, Any]:
    """Import a bounded source-only ZIP into an existing empty server-managed project."""
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,63}", project_id):
        raise HTTPException(status_code=400, detail="Invalid project ID.")
    configured = os.environ.get("VYOMA_WORKSPACE_ROOT", "").strip()
    if not configured:
        raise HTTPException(status_code=503, detail="Workspace root is not configured.")
    root = Path(configured).expanduser().resolve()
    project = (root / project_id).resolve()
    try:
        project.relative_to(root)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid project ID.") from None
    if not project.is_dir():
        raise HTTPException(status_code=404, detail="Project workspace was not found.")
    if any(project.iterdir()):
        raise HTTPException(status_code=409, detail="Import is only allowed into an empty project workspace.")

    try:
        archive_bytes = base64.b64decode(body.archive_base64, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(status_code=400, detail="Archive must be valid base64-encoded ZIP data.") from None
    if not archive_bytes or len(archive_bytes) > MAX_IMPORT_ARCHIVE_BYTES:
        raise HTTPException(status_code=413, detail="ZIP archive is empty or exceeds the 10 MB upload limit.")

    staged_files = 0
    total_uncompressed = 0
    seen: set[str] = set()
    with tempfile.TemporaryDirectory(prefix="vyoma-import-", dir=str(root)) as temp_dir:
        stage = Path(temp_dir) / "source"
        stage.mkdir(mode=0o700)
        try:
            with zipfile.ZipFile(io.BytesIO(archive_bytes)) as archive:
                entries = archive.infolist()
                if not entries:
                    raise HTTPException(status_code=400, detail="ZIP archive contains no files.")
                for info in entries:
                    relative, is_directory = _validate_import_member(info)
                    if relative in seen:
                        raise HTTPException(status_code=400, detail="ZIP contains duplicate paths.")
                    seen.add(relative)
                    if is_directory:
                        continue
                    staged_files += 1
                    total_uncompressed += info.file_size
                    if staged_files > MAX_IMPORT_FILES:
                        raise HTTPException(status_code=413, detail="ZIP contains too many files.")
                    if total_uncompressed > MAX_IMPORT_UNCOMPRESSED_BYTES:
                        raise HTTPException(status_code=413, detail="ZIP expands beyond the 25 MB total limit.")
                    destination = stage.joinpath(*PurePosixPath(relative).parts)
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    # Stream with an actual byte limit rather than trusting ZIP metadata alone.
                    written = 0
                    with archive.open(info, "r") as source, destination.open("xb") as target:
                        while True:
                            chunk = source.read(64 * 1024)
                            if not chunk:
                                break
                            written += len(chunk)
                            if written > MAX_IMPORT_FILE_BYTES:
                                raise HTTPException(status_code=413, detail="ZIP contains a file that exceeds the per-file size limit.")
                            target.write(chunk)
                    try:
                        destination.read_text(encoding="utf-8")
                    except UnicodeDecodeError:
                        raise HTTPException(status_code=400, detail="Only UTF-8 text/source files are accepted in this import flow.") from None
                    if written != info.file_size:
                        raise HTTPException(status_code=400, detail="ZIP entry size did not match its contents.")
        except zipfile.BadZipFile:
            raise HTTPException(status_code=400, detail="Upload is not a valid ZIP archive.") from None

        if staged_files == 0:
            raise HTTPException(status_code=400, detail="ZIP archive contains no supported source files.")
        # Replace the empty workspace directory in one rename; restore it if promotion fails.
        if any(project.iterdir()):
            raise HTTPException(status_code=409, detail="Project workspace changed during import.")
        backup = Path(temp_dir) / "empty-project-backup"
        project.rename(backup)
        try:
            stage.rename(project)
        except OSError:
            backup.rename(project)
            raise HTTPException(status_code=500, detail="Could not finish importing the project. The empty workspace was restored.") from None
        shutil.rmtree(backup, ignore_errors=True)

    write_audit_event(
        "project_source_imported",
        project_id=project_id,
        status="READY",
        details={"file_count": staged_files, "uncompressed_bytes": total_uncompressed, "format": "source-only-zip"},
    )
    return {
        "project_id": project_id,
        "status": "READY",
        "imported_files": staged_files,
        "uncompressed_bytes": total_uncompressed,
        "message": "Source files imported. Binary assets, signing files, secrets, and generated build output are not supported.",
    }
