# Phase B — Coding-agent foundation

## Implemented in this increment
- Restricted task-workspace file listing and UTF-8 source reading.
- Provider-backed planner sends bounded project context to an HTTPS OpenAI-compatible chat-completions endpoint and expects structured JSON.
- Model output becomes a reviewable file-change proposal and unified diff; planning itself never mutates source files.
- Development FastAPI endpoints provide plan creation, task lookup, explicit approval, and rejection.
- Task status and full reviewable proposals are stored in a local SQLite database so tasks can be recovered after backend restart; the database path is configurable with `VYOMA_TASK_DB_PATH`.
- API uses a configured server-side bearer token and validates project IDs under a configured workspace root; clients cannot supply arbitrary filesystem paths.
- Applying a proposal checks the original content for staleness. The API currently applies one-file proposals only because multi-file transactional writes/checkpoints are not implemented.
- Unit tests use a mocked provider; no real AI key is required for CI.

## Run locally
From the backend directory install requirements-dev.txt. Configure VYOMA_BACKEND_API_TOKEN, VYOMA_WORKSPACE_ROOT, VYOMA_AI_ENDPOINT, VYOMA_AI_MODEL, and VYOMA_AI_API_KEY as server-side environment variables. Never commit or pass the provider key to Android.

Run the development API on localhost with:
uvicorn app.coding_agent:api --host 127.0.0.1 --port 8000

Do not expose this service publicly.

## Architecture decision
OpenHands Software Agent SDK remains the candidate for the future main coding-agent runtime. Current provider planner is not the OpenHands SDK and is not an autonomous tool-using agent. No shell command execution is implemented.

## Security/production gates
1. Add per-user authentication and project ownership authorization.
2. Provision disposable isolated workspaces; never accept client-provided server filesystem paths.
3. Add durable task state, audit events, quotas, cancellation, and redacted observability.
4. Integrate OpenHands behind the narrow workspace tools; do not give the model unrestricted shell access.
5. Keep explicit human approval before file writes and external GitHub changes.
6. Implement transactional multi-file changes, checkpoints, rollback, and stale revision protection.
7. Run builds/tests in an isolated worker with CPU, memory, network, time and cost limits.
8. Connect Android task UI and diff review to the authenticated API.
9. Add cloud Gradle builds and APK artifact delivery only after worker isolation and security gates pass.

## Latest increment: reject a plan safely\n- Added an authenticated reject endpoint for plans awaiting approval.\n- Rejection records an audit event and changes task status to REJECTED without applying any file changes.\n- Added a native Reject plan action and a test that verifies workspace contents remain unchanged.\n\n## Source-code sharing consent

The Android Coding Agent screen now discloses that the task prompt and selected workspace source contents are sent to the AI provider configured on the backend. The user must opt in via a checkbox before creating a plan. This is a development safeguard, not a substitute for data minimization, provider review, per-user authorization, or production privacy controls.

## Acceptance status
- [x] Workspace safety tools and reviewable diffs
- [x] Provider-backed planning with response/path validation
- [x] Authenticated development API for plan/status/approval
- [x] Unit tests and backend CI passing on the current branch
- [ ] OpenHands SDK runtime/autonomous loop
- [ ] Per-user authentication and durable task storage
- [x] Initial Android Coding Agent screen wired to plan, explicit single-file approval, and plan rejection
- [x] Android task status refresh control calls the authenticated task lookup endpoint
- [x] Restore task state and proposals after backend restart using local SQLite (single-node development only; no multi-user tenancy or distributed locking)
- [ ] Android task persistence, refresh/status recovery, and richer diff review UI
- [ ] Transactional edits, rollback and audit history
- [ ] Isolated build worker and APK delivery

This is progress toward Phase B, not a complete coding agent or production-ready backend.


## OpenHands SDK integration (development-only)

The API accepts `engine: "openhands"` on the existing plan endpoint. The Android Coding Agent screen exposes a Use OpenHands SDK checkbox. The backend adapter builds a disposable copy containing only bounded, supported source/text files, starts an OpenHands SDK conversation with `FileEditorTool` and `TaskTrackerTool`, and intentionally does not register `TerminalTool`. It compares the copy with its initial state and returns validated proposals for the existing approval flow. The original project is not writable during planning.

Configure `VYOMA_OPENHANDS_MODEL` and `VYOMA_OPENHANDS_API_KEY` on the backend, with optional `VYOMA_OPENHANDS_BASE_URL`. OpenHands uses its own LLM configuration; this is separate from the simple planner's `VYOMA_AI_*` variables.

Important: a temporary directory is not a security sandbox. File-edit-only tools reduce execution risk, but do not use this adapter with sensitive or untrusted projects or expose the development backend publicly. Production should run OpenHands through Agent Server in an isolated ephemeral container with strict network, CPU, memory, time and cost controls. No build/test execution or APK delivery is enabled.

## OpenHands integration acceptance

- [x] Add pinned OpenHands SDK/tool dependencies.
- [x] Add a disposable-copy adapter without a terminal tool.
- [x] Add an API engine selector and Android UI opt-in.
- [x] Add mocked tests for the adapter and API route.
- [ ] Run the live SDK against a disposable sample project.
- [ ] Move execution to isolated OpenHands Agent Server/container before public use.


## Latest increment: task audit trail

- [x] Record task creation, proposal creation, user rejection, successful application, and workspace conflicts as append-only SQLite events.
- [x] Expose a bearer-authenticated task audit endpoint.
- [x] Keep audit events minimal: event type, task/proposal IDs, relative path, timestamp and bounded details; do not store proposed source content or API secrets in the audit event.
- [x] Add API tests for event ordering and unknown-task rejection.
- [ ] Add per-user identity/ownership, retention policy, tamper-resistant production audit storage, and concurrency/transaction guarantees before deployment.

This is a development audit trail, not a tamper-proof compliance log. The API still uses one shared development bearer token.
