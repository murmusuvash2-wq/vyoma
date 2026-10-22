# Vyoma Architecture

## Product boundary

**Vyoma itself is intended to be an Android-native app builder.** The mobile app is the control surface; apps it generates are separate projects. The target native stack is Kotlin + Jetpack Compose + Material 3. The existing root-level Next.js dashboard is only a temporary UI prototype and must not be mistaken for the final product architecture.

No AI coding, persistent memory, GitHub authorization, isolated test runner, or APK build is considered implemented until code and execution evidence exist.

## Core workflow

1. **Capture intent** — app goal, platform, users, constraints, and acceptance criteria.
2. **Create a blueprint** — screens, navigation, data model, integrations, permissions, error states, and tests.
3. **Review and approve** — the user can edit the blueprint before code changes begin.
4. **Plan a scoped change** — identify files and expected behavior; asking a question must never mutate code.
5. **Preview and diff** — show proposed UI/code changes and their scope.
6. **Apply with recovery** — create a checkpoint and support undo/rollback.
7. **Verify** — run applicable tests/builds and report actual outputs and limitations.
8. **Build and release** — use an isolated cloud Gradle worker for Android APK/AAB outputs; provide access-controlled artifacts.

## Logical components

- **Android client (Kotlin + Compose + Material 3):** onboarding, projects, chat, blueprint approval, embedded preview, project memory, files/diffs, tests, build status, settings.
- **Secure application API:** authenticates users and validates project access, permissions, quotas, and requests.
- **Orchestrator:** coordinates intent classification, planning, tool calls, approvals, and progress events. Start with one orchestrator and explicit tools, not seven autonomous agents.
- **Project memory:** structured project facts and decisions with provenance/timestamps: requirements, approved design tokens, architecture decisions, rejected alternatives, task state, code summaries, and test/fix history.
- **Repository adapter:** least-privilege GitHub operations, branches/checkpoints, diffs, and commit history. GitHub remains the source of truth for code.
- **Execution workers:** isolated environments for generated-code tests and Gradle builds. Never execute untrusted generated code in the Android app or API request process.
- **Preview renderer:** clearly labels drafts; web previews and emulator/device-tested native behavior are distinct.
- **Artifact store:** access-controlled APK/AAB outputs and logs with expiry.
- **Observability and policy:** usage/cost tracking, redacted logs, permission checks, audit events, and honest failure reporting.

## Data ownership and trust

- Source code and committed assets: GitHub.
- Current task/test/build status: execution results tied to commit SHA, environment, command, and timestamp.
- Project decisions and approved requirements: structured project memory.
- Preview: draft until approved; never imply preview is saved source.
- Android build results: evidence from Gradle/build logs; a successful compile alone does not prove production readiness.

Memory is context, not unquestioned truth. If memory conflicts with the repository or a newer user instruction, follow the explicit newer instruction and surface consequential conflicts.

## Security boundaries

- Keep model provider keys, OAuth tokens, service credentials, and signing keys server-side or in dedicated secret storage; never put them in Android resources, client bundles, generated apps, public commits, prompts, or unredacted logs.
- Request minimum GitHub permissions and support disconnect/revocation.
- Enforce ownership and authorization on every API operation.
- Treat prompts, repository files, dependency scripts, and generated code as untrusted input.
- Run generated code in isolated, resource-limited workers with no ambient secrets.
- Require explicit approval for destructive operations, external publishing, permission changes, or costly builds.
- Use row-level security if a future Supabase schema is exposed to clients.

## Proposed stack (subject to validation)

- **Vyoma Android client:** Kotlin, Jetpack Compose, Material 3.
- **Android app builds:** Gradle in GitHub Actions or isolated cloud workers; downloadable APK/AAB artifacts.
- **Backend/orchestrator:** authenticated server-side API and model adapter; provider choice based on coding quality, latency, and cost.
- **Project data:** evaluate existing Supabase projects and their status before creating or activating any database. Do not create a duplicate by default.
- **Repository:** GitHub integration with narrowly scoped OAuth/App permissions.
- **Figma:** design and UX source only; implementation remains in GitHub.
- **Temporary reference:** root-level Next.js dashboard prototype is not the final product and should not dictate the production platform.

## Corrected implementation sequence

1. Preserve the current prototype but label it accurately; establish Android-native project structure and Android CI.
2. Implement the mobile app shell and navigation in Compose.
3. Define blueprint and project-memory types before connecting a database.
4. Build the workspace: chat, preview, files/diff, activity, blueprint approval.
5. Add persistence and authentication only after schema and authorization rules are reviewed.
6. Add GitHub integration and a safe branch/checkpoint workflow.
7. Add one real coding tool and a commit-scoped verification pipeline.
8. Add isolated cloud Gradle builds, APK artifact delivery, and emulator/device verification.
