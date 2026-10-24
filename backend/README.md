# Vyoma backend — Phase B foundation

This Python service is a development foundation for the Android-native Vyoma app builder.

## Implemented
- Bounded source-file listing and reading plus reviewable file-change proposals.
- Provider-backed planner that returns diffs only; it does not execute shell commands.
- Authenticated FastAPI endpoints to create a plan, fetch task status, and explicitly approve a plan.
- Project paths are selected by a validated project ID beneath the server-configured workspace root; clients cannot submit filesystem paths.
- Approval checks the workspace snapshot before applying a proposal.
- The development API only applies one-file plans until transactional multi-file apply/checkpointing is implemented.
- Unit tests use a mocked provider and require no live API key.

## Local development
Install requirements-dev.txt, then set these environment variables on the backend process:
- VYOMA_BACKEND_API_TOKEN: a long random bearer token for local development.
- VYOMA_WORKSPACE_ROOT: server-owned directory containing one folder per project ID.
- VYOMA_AI_ENDPOINT: HTTPS OpenAI-compatible chat-completions endpoint.
- VYOMA_AI_MODEL: model name.
- VYOMA_AI_API_KEY: server-side provider key. Never commit or send this to Android.

Run uvicorn app.coding_agent:api --host 127.0.0.1 --port 8000 from this directory. Keep it bound to localhost until proper identity, project authorization, persistent task storage, rate limits, audit logging and isolated workspaces are implemented.

## Not production-ready
- Bearer token is a development gate, not per-user authentication.
- Task state is in memory and is lost on restart.
- No transactional multi-file writes, audit database, quotas or durable task state.
- No OpenHands SDK runtime, shell/build worker, GitHub integration, APK artifacts, or Android UI integration yet.
- Do not expose publicly without per-user authentication, project ownership checks, audit trail, quotas, isolated ephemeral workspaces, and rollback.
