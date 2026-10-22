# First BYOK AI planner milestone

## Implemented in this branch

- `POST /v1/ai/plan` calls the fixed OpenAI Chat Completions endpoint.
- Caller supplies their own key in `X-Vyoma-AI-Key`; the app does not persist it.
- Input is bounded and includes app idea, optional name, target platform, and extra requirements.
- Output contains provider, model, and planning text for a native Kotlin/Compose app.
- Tests mock the provider; they do not need a real key or incur model charges.

## Security limitation

Request-scoped handling prevents intentional storage but does not make a public unauthenticated proxy safe. Before public deployment or Android integration, add authentication, project authorization, rate limits/quotas, abuse controls, and request redaction in observability. Use HTTPS only. A key entered into a mobile client can be extracted from a compromised device.

## Not included

Android UI wiring, other providers, GitHub OAuth, MCP, code generation, build worker, APK delivery, and live verification with a real key.

## Verify locally

From `backend/` run:

```bash
pip install -r requirements-dev.txt
python -m compileall -q app tests
python -m pytest -q
```
