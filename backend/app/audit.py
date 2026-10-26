"""Append-only, redacted audit events for the Vyoma development coding API."""
from __future__ import annotations

import json
import os
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

_lock = threading.Lock()
_SECRET_KEYS = {"authorization", "api_key", "token", "secret", "content", "diff", "original_content", "proposed_content"}


def _redact(value: Any) -> Any:
    if isinstance(value, dict):
        return {
            str(key): ("[REDACTED]" if str(key).lower() in _SECRET_KEYS else _redact(item))
            for key, item in value.items()
        }
    if isinstance(value, list):
        return [_redact(item) for item in value]
    if isinstance(value, str):
        return value[:1000]
    if value is None or isinstance(value, (bool, int, float)):
        return value
    return str(value)[:1000]


def write_audit_event(action: str, *, task_id: str | None = None,
                      project_id: str | None = None, status: str | None = None,
                      paths: list[str] | None = None, details: dict[str, Any] | None = None) -> None:
    """Write metadata-only JSONL. Never pass provider keys or source contents as details."""
    configured = os.environ.get("VYOMA_AUDIT_LOG_PATH", "").strip()
    if not configured:
        # Audit persistence is opt-in for local development; production must configure a
        # durable, access-controlled log sink before exposing any API.
        return
    destination = Path(configured).expanduser()
    event = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "action": action[:80],
        "task_id": task_id,
        "project_id": project_id,
        "status": status,
        "paths": [str(path)[:500] for path in (paths or [])][:20],
        "details": _redact(details or {}),
    }
    line = json.dumps(event, ensure_ascii=False, separators=(",", ":")) + "\n"
    destination.parent.mkdir(parents=True, exist_ok=True)
    with _lock:
        with destination.open("a", encoding="utf-8") as stream:
            stream.write(line)
            stream.flush()
            os.fsync(stream.fileno())
