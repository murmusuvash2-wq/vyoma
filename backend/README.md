# Vyoma Backend (prototype)

This is the starting point for Vyoma's API/orchestration service.

## Run locally

Python 3.11+ is recommended.

```bash
python -m venv .venv
# Linux/macOS
source .venv/bin/activate
# Windows PowerShell
# .venv\\Scripts\\Activate.ps1
pip install -r requirements-dev.txt
uvicorn app.main:app --reload
```

Then open `/health` or `/docs` on the local server.

## Implemented

- `GET /health`: liveness check.
- `GET /v1/capabilities`: integration status.
- `POST /v1/ai/plan`: first OpenAI planning call using a user-supplied key in `X-Vyoma-AI-Key`.
- Provider key is request-scoped; this backend does not write it to a database or environment variable.
- Configurable default model via `VYOMA_DEFAULT_AI_MODEL`; default is `gpt-4o-mini`.

Example request (replace the placeholder locally; never commit a real key):

```bash
curl -X POST http://127.0.0.1:8000/v1/ai/plan \\
  -H 'Content-Type: application/json' \\
  -H 'X-Vyoma-AI-Key: YOUR_OPENAI_API_KEY' \\
  -d '{"idea":"A simple offline-first habit tracker","app_name":"Habit Garden","extra_requirements":["Daily reminders"]}'
```

## Security and prototype limits

- Do not commit API keys, OAuth tokens, MCP secrets, or signing keys.
- Do not put provider keys in Android resources, BuildConfig, generated APKs, or logs.
- The Android client must use HTTPS when calling a deployed backend. Client-side keys can be extracted from a compromised device; this request-header approach is a prototype BYOK flow, not a secure secret vault.
- This endpoint has no user authentication, project authorization, rate limiting, or quota controls. Keep it local/private until those controls are implemented.
- The provider request has a fixed OpenAI API URL; callers cannot supply a URL, avoiding user-controlled outbound destinations.
- Provider response bodies are not returned in errors.
- This prototype currently supports OpenAI only. It does not yet support arbitrary providers, GitHub OAuth, MCP connections, code execution, or Android build execution.
- Do not run builds or arbitrary commands inside this API process. Use a separate isolated worker for Android builds later.

## Deployment

A Docker-based Hugging Face Space may be useful for a prototype, but free/idle runtime behavior and resource limits must be checked against current platform terms. Do not expose this unauthenticated endpoint publicly.
