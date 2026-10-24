# Vyoma backend — Phase B development foundation

This Python service is a development-only backend for the Android-native Vyoma app builder.

## Current implementation

- Bounded source-file listing/reading and reviewable file-change proposals.
- Provider-backed planner uses the server-configured OpenAI-compatible chat-completions endpoint and returns proposed diffs; it does not execute shell commands.
- Optional OpenHands SDK engine runs with FileEditorTool and TaskTrackerTool only, in a disposable copy of the project; no terminal/shell tool is registered. It returns reviewable diffs and never edits the original project during planning.
- Android Coding Agent screen calls the API exposed as `app.coding_agent:api`.
- Bearer-token-gated endpoints create a plan, fetch task status, approve a one-file plan, and reject a plan.
- Coding task state and reviewable proposal contents are persisted in a local SQLite database, allowing task refresh/review after backend restart. The database directory/file are restricted to owner access where the OS permits.
- Project directories are selected by a validated project ID under `VYOMA_WORKSPACE_ROOT`; clients cannot submit arbitrary absolute paths.
- Planning does not mutate files. Approval rechecks the original file content. Multi-file plans are rejected at apply time until transactional checkpoints exist.
- Audit events are metadata-only and are written only when `VYOMA_AUDIT_LOG_PATH` is configured.
- Tests mock the provider and do not need a live API key.

## Local development

From this directory, install test/runtime requirements:

```bash
python -m pip install -r requirements-dev.txt
python -m pytest -q
```

Set these environment variables in the backend process:

- `VYOMA_BACKEND_API_TOKEN`: long random bearer token for development.
- `VYOMA_WORKSPACE_ROOT`: server-managed directory with one folder per project ID.
- `VYOMA_AI_ENDPOINT`: HTTPS OpenAI-compatible chat-completions endpoint.
- `VYOMA_AI_MODEL`: model name supported by the endpoint.
- `VYOMA_AI_API_KEY`: server-side provider key for the simple planner. Never commit it or put it in the Android app.
- `VYOMA_OPENHANDS_MODEL`: OpenHands SDK model identifier, e.g. a LiteLLM-supported model.
- `VYOMA_OPENHANDS_API_KEY`: server-side OpenHands LLM key. Never commit it or put it in the Android app.
- `VYOMA_OPENHANDS_BASE_URL` (optional): provider base URL accepted by the OpenHands SDK.
- `VYOMA_AUDIT_LOG_PATH` (optional for local development): path to a protected JSONL metadata audit log.
- `VYOMA_TASK_DB_PATH` (optional): location for the local SQLite task database. Defaults to `backend/.data/coding_tasks.sqlite3`; keep this file private because it stores proposed/original source content.

Start the API from this directory:

```bash
uvicorn app.coding_agent:api --host 127.0.0.1 --port 8000
```

The Android client currently requires HTTPS for the backend URL. For device testing, use a private HTTPS development endpoint with access controls; do not expose the local server or a shared bearer token publicly.

## Known limitations — not production-ready

- The bearer token is a shared development gate, not per-user authentication or project ownership authorization.
- Task state is process-local and is lost on restart; task lookup is not durable.
- Audit logging is optional and not a tamper-resistant durable audit service.
- No quotas, cancellation, transactional multi-file changes, checkpoints/undo, or isolated build worker. The SDK adapter uses a temporary copy but is not a security sandbox; use only trusted development projects until it is moved to a dedicated ephemeral container/Agent Server.
- No OpenHands SDK runtime/autonomous tool-using loop, GitHub repository integration, Gradle build execution, or APK artifact delivery.
- Do not use sensitive/private projects or deploy publicly. Production needs per-user authentication, project authorization, durable task/audit storage, quotas, isolated ephemeral workspaces, secret management, and recovery controls.
