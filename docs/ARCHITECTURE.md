# Vyoma Architecture

## Product boundary

Vyoma is a project-first app builder. The web workspace is the control plane; generated web apps and native Android apps are separate outputs. The current repository is only the dashboard foundation. No AI coding, persistence, GitHub authorization, test runner, or APK build is considered implemented until code and evidence exist.

## Core workflow

1. **Capture intent** — collect the app goal, platform, users, constraints, and acceptance criteria.
2. **Create a blueprint** — screens, navigation, data model, integrations, permissions, error states, and tests.
3. **Review and approve** — the user can edit the blueprint before code changes begin.
4. **Plan a scoped change** — identify files and expected behavior; asking a question must never mutate code.
5. **Preview and diff** — show the proposed UI/code change and its scope.
6. **Apply with recovery** — save a checkpoint and support undo/rollback.
7. **Verify** — run applicable tests and builds; report actual command output and limitations.
8. **Release** — publish web output or build an Android APK/AAB in a separate cloud pipeline.

## Logical components

- **Web workspace (Next.js/React):** dashboard, project workspace, chat, preview, files/diffs, tests, build status, settings.
- **Application API:** authenticated server-side endpoints; validates user, project, permissions, quotas, and request shape.
- **Orchestrator:** coordinates intent classification, planning, tool calls, approvals, and progress events. Begin with one orchestrator and explicit tools, not seven autonomous agents.
- **Project memory:** structured project facts and decisions with provenance and timestamps. Store requirements, approved design tokens, architecture decisions, rejected alternatives, task state, code summaries, and test/fix history separately from raw chat.
- **Repository adapter:** least-privilege GitHub operations, branch/checkpoint handling, diffs, and commit history. GitHub remains the source of truth for source code.
- **Execution workers:** isolated environments for tests and builds. Do not execute untrusted generated code in the web request process.
- **Artifact store:** short-lived or access-controlled build outputs and logs.
- **Observability and policy:** usage/cost tracking, redacted logs, permission checks, audit events, and failure reporting.

## Data ownership and trust

- Source code and committed assets: GitHub.
- Current task/test/build status: execution results with timestamps and commit SHA.
- Project decisions and approved requirements: structured project memory.
- Preview: explicitly labelled as draft until approved; never imply it is the saved source.
- Test results: evidence tied to a commit SHA, environment, command, and timestamp.

Memory is context, not unquestioned truth. If memory conflicts with the current repository or a newer user instruction, surface the conflict and ask or follow the explicit newer instruction.

## Security boundaries

- Keep provider keys, OAuth tokens, and service credentials on the server; never put them in client bundles, generated apps, public commits, prompts, or unredacted logs.
- Request minimum GitHub permissions and allow disconnect/revocation.
- Enforce project ownership and authorization on every API operation.
- Treat user prompts, repository files, dependency scripts, and generated code as untrusted input.
- Run generated code in isolated, resource-limited workers with no ambient secrets.
- Require explicit approval for destructive operations, external publishing, permission changes, or costly builds.
- Use database row-level security if a future Supabase schema is exposed to clients.

## Proposed stack (subject to validation)

- Web workspace: Next.js App Router, React, TypeScript.
- Styling: CSS initially; extract shared design tokens/components as UI stabilizes.
- Persistence: evaluate the existing Supabase projects and their status before creating or activating any database. Do not create a duplicate project by default.
- AI: server-side model adapter and orchestrator; select provider after measuring coding quality, latency, and cost.
- Repository: GitHub integration with narrowly scoped OAuth/App permissions.
- Web deployment: evaluate Vercel when project access is confirmed.
- Android output: Kotlin + Jetpack Compose + Material 3; build through Gradle in GitHub Actions or isolated cloud workers, independently of the web workspace.

## First implementation sequence

1. Make the foundation build and lint reliably in CI.
2. Establish blueprint and project-memory types/schema before connecting a database.
3. Build the project workspace shell: chat, preview, files/diff, activity.
4. Implement blueprint approval and scoped change-plan states using mock/local data first.
5. Add persistence and authentication only after the data model and authorization rules are reviewed.
6. Add GitHub integration and a safe branch/checkpoint workflow.
7. Add one real coding tool and a commit-scoped verification pipeline.
8. Add isolated cloud builds; Android APK generation is a separate milestone.
