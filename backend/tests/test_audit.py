import json
from pathlib import Path

from app.audit import write_audit_event


def test_audit_event_is_metadata_only_and_redacts_sensitive_fields(tmp_path: Path, monkeypatch):
    log_path = tmp_path / "audit.jsonl"
    monkeypatch.setenv("VYOMA_AUDIT_LOG_PATH", str(log_path))
    write_audit_event(
        "plan_created",
        task_id="task-123",
        project_id="sample",
        status="WAITING_FOR_APPROVAL",
        paths=["Main.kt"],
        details={"model": "test-model", "api_key": "do-not-log", "diff": "private source"},
    )
    event = json.loads(log_path.read_text(encoding="utf-8"))
    assert event["action"] == "plan_created"
    assert event["paths"] == ["Main.kt"]
    assert event["details"]["model"] == "test-model"
    assert event["details"]["api_key"] == "[REDACTED]"
    assert event["details"]["diff"] == "[REDACTED]"
    assert "do-not-log" not in log_path.read_text(encoding="utf-8")
    assert "private source" not in log_path.read_text(encoding="utf-8")


def test_audit_is_disabled_without_explicit_path(monkeypatch, tmp_path: Path):
    monkeypatch.delenv("VYOMA_AUDIT_LOG_PATH", raising=False)
    write_audit_event("no-op", task_id="task")
