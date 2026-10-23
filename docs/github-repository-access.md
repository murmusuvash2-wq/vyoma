# Vyoma GitHub repository access

## Implemented in this branch

- OAuth connect flow with random state and PKCE S256.
- Callback state validation, server-side code exchange, and GitHub identity verification.
- Encrypted HttpOnly cookie session; the access token is never returned to the client.
- Connection status and disconnect endpoints.
- Repository listing and text-file read/update endpoints through GitHub's API.
- Workspace UI for GitHub connect/status/disconnect, repository selection, file read/edit, commit message, explicit save confirmation, and working-branch creation/selection.
- File updates require the current Git blob SHA and a commit message. Conflicts require re-reading the file rather than blindly retrying.
- Same-origin check for mutating endpoints and input/path validation.

## Required configuration before it can run

Set these in the deployed server's secret/environment settings only:

- `GITHUB_CLIENT_ID`
- `GITHUB_CLIENT_SECRET`
- `GITHUB_REDIRECT_BASE_URL` (canonical HTTPS origin, no trailing slash)
- `GITHUB_SESSION_SECRET` (cryptographically random, at least 32 characters)

Register `<GITHUB_REDIRECT_BASE_URL>/api/github/callback` as the OAuth callback URL. Never put the client secret, session secret, or access token in Android code, public repository files, or `NEXT_PUBLIC_` variables.

## API routes

- `GET /api/github/connect` — start authorization.
- `GET /api/github/callback` — complete authorization.
- `GET /api/github/status` — return connected state and account login, never the token.
- `POST /api/github/disconnect` — clear the local session.
- `GET /api/github/repos?page=1` — list repositories visible to the authorized account.
- `GET /api/github/file?repo=owner%2Frepo&path=path%2Ffile&ref=branch` — read a text file up to 1 MB.
- `PUT /api/github/file` — update an existing text file. Body: `{ repo, path, content, sha, message, branch? }`.

## Audit findings and limitations

- The Android APK is not wired to these routes; the connector UI currently lives in the temporary Next.js web prototype.
- No OAuth credentials or server deployment have been configured or verified; the endpoints are code only until configured and tested end-to-end.
- The connected Supabase projects visible during audit were both `INACTIVE` and are not assumed to be Vyoma infrastructure. This change does not activate or alter them.
- This starter flow requests OAuth `repo read:user`; GitHub's `repo` scope is broad and includes read/write access across repositories the user can access. For production, prefer a GitHub App with selected repository access and only Contents read/write plus Metadata read-only permissions.
- Vyoma does not yet have its own user authentication/ownership layer. Do not expose this as a multi-user production service until Vyoma identity, per-user authorization, rate limiting, and audit logging are added.
- AI/model integration is intentionally not included. Before AI writes source code, add task-scoped branches/checkpoints, a diff/review screen, automated tests, and explicit user approval before commit/push. The AI must not silently mutate the default branch.

## Validation still required

Run `npm run lint` and `npm run build`, then test connect, callback/state rejection, repository listing, file read, successful write, stale-SHA conflict, disconnect, and expired/missing session. A passing compile alone does not prove OAuth works in production.
