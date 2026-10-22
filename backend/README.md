# Vyoma Backend (prototype)

This is the starting point for Vyoma's API/orchestration service. It is deliberately small and currently exposes health/status endpoints only.

## Run locally

Python 3.11+ is recommended.

```bash
python -m venv .venv
# Linux/macOS
source .venv/bin/activate
# Windows PowerShell
# .venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload
```

Then open `/health` or `/docs` on the local server.

## Current scope

- FastAPI application skeleton
- Health endpoint
- Capability/status endpoint that clearly marks integrations as not configured
- No credential collection, persistence, or logging
- No AI inference, GitHub OAuth, MCP connection, code execution, or Android build execution yet

## Security baseline

- Never commit real API keys, OAuth tokens, GitHub tokens, MCP secrets, or signing keys.
- Do not place provider keys in Android resources, BuildConfig, generated APKs, or logs.
- Add authentication and project-level authorization before exposing non-health endpoints publicly.
- Do not run builds or arbitrary commands inside this API process.
- Use a separate isolated worker for Android builds when that milestone begins.
- Keep this service behind HTTPS when deployed.

## Deployment

The API can be prototyped on Hugging Face Spaces with a suitable Docker Space configuration, but free/idle runtime behavior and resource limits must be checked against current platform terms. A Space is not the Android build worker.
