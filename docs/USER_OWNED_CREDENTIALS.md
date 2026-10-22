# Vyoma User-Owned Credentials Architecture

Status: design baseline. This document does not claim live provider, GitHub OAuth, MCP, or build integrations are implemented.

## Goal

Vyoma should let each user bring credentials for supported AI providers, their own GitHub account, and optionally their own MCP servers. Vyoma should not pay the user's model-inference bill by default. Hosting, orchestration, and Android builds may still have separate costs.

## Credential classes

| Credential | Owner | Intended use | Handling rule |
| --- | --- | --- | --- |
| AI provider API key | User | Model inference | Prefer direct provider requests from the client when supported and safe. If a backend-mediated request is necessary, transmit only over HTTPS, use only for the current authorized operation, redact it from logs, and do not persist it by default. |
| GitHub authorization | User | Access to selected repositories | Prefer GitHub App/OAuth authorization with least-privilege repository permissions, revocation, and short-lived tokens where available. Never ask users to paste tokens into chat. |
| Custom MCP server auth | User/server owner | Call tools exposed by that server | Treat server URL, tool schemas, and output as untrusted. Require explicit consent, scoped credentials, and per-tool authorization. |
| Vyoma hosting credentials | Vyoma operator | Run backend and workers | Store only in deployment secret configuration; never in Android client, generated apps, public repository, or logs. |
| APK signing material | App owner/release operator | Sign release builds | Keep outside source control and ordinary build logs. Release signing requires explicit setup and approval. |

## Recommended request flow

1. The user selects a provider and reviews the required permissions.
2. The client validates that a key is present without logging or displaying the full value.
3. If direct client-to-provider calls are supported, send requests directly to the provider over HTTPS. Explain that a client-side API key can be extracted from a compromised device; BYOK is not equivalent to perfect secrecy.
4. If the agent requires server-side orchestration, pass the credential only for the authorized request through a protected transport. Avoid storing it in project memory, task history, analytics, crash reports, or logs.
5. The backend validates user identity, project ownership, task scope, budget/usage limits, and tool permissions independently of the AI key.
6. GitHub access is authorized separately from AI access. An AI key must never grant repository access, and a GitHub token must never grant AI-provider access.
7. Every mutating operation is proposed as a diff and requires an explicit approval record before applying or committing changes.
8. Disconnect/revoke controls must exist for provider, GitHub, and MCP integrations.

## Important constraints

- A BYOK model reduces Vyoma's model-inference spend; it does not remove backend hosting, storage, networking, build-worker, or support costs.
- A Hugging Face Space can host a prototype API, but it is not automatically a secure credential vault or a reliable long-running Android build worker.
- Never expose a generic shell to the model. Use narrow typed tools with project scope, path restrictions, timeouts, quotas, and audit events.
- Do not execute arbitrary code or build jobs in the API process. Queue them for an isolated, disposable worker.
- Treat repository content, MCP tool output, and model-generated tool arguments as untrusted input.
- Do not claim a provider, GitHub, MCP server, build, or test is connected until it has actually been configured and verified.

## First implementation scope

- FastAPI service with health/status endpoints only.
- No API key storage or logging.
- A documented provider adapter contract before enabling inference.
- GitHub authorization and MCP server support remain disabled until their permission model and secure connection flow are implemented.
- Build jobs remain out of process and are not part of the initial API skeleton.
