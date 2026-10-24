# Phase B — Coding-agent foundation

## Implemented in this increment
- Restricted task-workspace file listing and UTF-8 source reading.
- Provider-backed planner sends bounded project context to an HTTPS OpenAI-compatible chat-completions endpoint and expects structured JSON.
- Model output becomes a reviewable file-change proposal and unified diff; planning itself never mutates source files.
- Development FastAPI endpoints provide plan creation, task lookup, and explicit approval.
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

## Latest increment: reject a plan safely\n- Added an authenticated reject endpoint for plans awaiting approval.\n- Rejection records an audit event and changes task status to REJECTED without applying any file changes.\n- Added a native Reject plan action and a test that verifies workspace contents remain unchanged.\n\n## Acceptance status
- [x] Workspace safety tools and reviewable diffs
- [x] Provider-backed planning with response/path validation
- [x] Authenticated development API for plan/status/approval
- [x] Unit tests and backend CI passing on the current branch
- [ ] OpenHands SDK runtime/autonomous loop
- [ ] Per-user authentication and durable task storage
- [x] Initial Android Coding Agent screen wired to plan, explicit single-file approval, and plan rejection
- [ ] Android task persistence, refresh/status recovery, and richer diff review UI
- [ ] Transactional edits, rollback and audit history
- [ ] Isolated build worker and APK delivery

This is progress toward Phase B, not a complete coding agent or production-ready backend.
