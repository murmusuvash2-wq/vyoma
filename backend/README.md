# Vyoma backend — Phase B development foundation

This Python service is a development-only backend for the Android-native Vyoma app builder.

## Current implementation

- FastAPI coding-agent API with a shared development bearer-token gate.
- Server-managed project workspace creation and bounded source-only ZIP import into an empty project.
- OpenAI-compatible planner configured through server-side environment variables; it returns structured proposed file changes.
- Optional OpenHands SDK adapter using `FileEditorTool` and `TaskTrackerTool` in a disposable copy. The terminal/shell tool is not registered; planning does not write to the original project.
- Workspace listing/reading and source path validation; path traversal, symlinks, protected secrets/signing files, unsupported file types and oversized ZIP entries are rejected.
- Task status, proposed/original source contents and audit metadata stored in local SQLite. Audit events are opt-in via a configured JSONL path.
- Reviewable unified diffs; planning is read-only. Approval requires an explicit authenticated API action, rechecks stale contents, stages file replacements, and attempts rollback if a replacement fails.
- Multi-file change sets are supported by the backend's transactional apply path. The Android client exposes approval for the whole change set only after displaying all returned diffs.
- Android Coding Agent screen can create a task plan, refresh task status, and approve/reject a plan. It requires explicit consent before source contents are sent to the backend-configured AI provider.

## Local development

From this directory, install test/runtime requirements:

```bash
python -m pip install -r requirements-dev.txt
python -m pytest -q
```

Set these environment variables in the backend process:

- `VYOMA_BACKEND_API_TOKEN`: long random bearer token for development.
- `VYOMA_WORKSPACE_ROOT`: server-managed directory with one folder per project ID.
- `VYOMA_AI_ENDPOINT`: HTTPS OpenAI-compatible chat-completions endpoint for the simple planner.
- `VYOMA_AI_MODEL`: model name supported by that endpoint.
- `VYOMA_AI_API_KEY`: server-side provider key for the simple planner. Never commit it or put it in the Android app.
- `VYOMA_OPENHANDS_MODEL`: OpenHands SDK model identifier.
- `VYOMA_OPENHANDS_API_KEY`: server-side OpenHands LLM key. Never commit it or put it in the Android app.
- `VYOMA_OPENHANDS_BASE_URL` (optional): provider base URL accepted by the OpenHands SDK.
- `VYOMA_AUDIT_LOG_PATH` (optional): path to a protected metadata-only JSONL audit log.
- `VYOMA_TASK_DB_PATH` (optional): location for the local SQLite task database. Defaults to `backend/.data/coding_tasks.sqlite3`; protect this file because task records may include original and proposed source contents.

Start the API from this directory:

```bash
uvicorn app.coding_agent:api --host 127.0.0.1 --port 8000
```

The Android client requires HTTPS for the backend URL. For device testing, use a private HTTPS development endpoint with access controls. Do not expose the local development server or a shared bearer token publicly.

## Known limitations — not production-ready

- The bearer token is a shared development gate, not per-user authentication or project ownership authorization. Any holder of the token may access endpoints within the configured workspace root.
- SQLite task persistence is single-node development persistence; it is not multi-user tenancy, distributed locking, or a production task/audit database.
- The OpenHands adapter has mocked CI coverage but has not been live-validated against a real model/provider. A temporary directory is not a security sandbox.
- Multi-file apply and undo have preflight/stale checks and rollback attempts, but there is no durable task-wide checkpoint or guaranteed recovery if the underlying filesystem fails during rollback.
- No shell execution, Gradle build/test execution, isolated Agent Server/container, GitHub repository authorization, APK artifact delivery, quotas, or production user authentication is implemented.
- The Android Coding Agent screen is a one-task-at-a-time planning/review surface, not yet the persistent conversational Chat-to-App Builder. It does not dynamically update the approved blueprint, render a live generated app preview, or build/download an APK.
- Source import supports bounded UTF-8 text/source files only; binary assets, Git metadata, build outputs and signing/secret files are intentionally excluded.

Do not use sensitive/private projects or deploy publicly. Production requires per-user authentication, project authorization, isolated ephemeral workspaces, secret management, quotas, robust recovery/audit controls, real-provider validation, and isolated build workers before generated code is executed or APKs are delivered.
