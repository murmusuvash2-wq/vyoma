# Vyoma AI Agent and MCP Architecture

Status: architecture baseline for the hybrid approach. This document describes the planned system; it does not claim the backend, MCP tools, or build worker are implemented.

## Decision

Start with one main coding agent, supported by planner and reviewer modules. Use explicit tools rather than several independently autonomous agents. Add specialist agents only when the single-agent workflow has measurable limits.

## System boundary

```text
Vyoma Android app (Kotlin + Compose)
  └─ authenticated HTTPS API
       └─ Vyoma backend / orchestrator
            ├─ Provider adapter → configured AI model
            ├─ Planner module → structured blueprint and task plan
            ├─ Main coding agent → proposes bounded file changes
            ├─ Reviewer module → reviews scope, diff, risks and evidence
            ├─ Tool gateway / MCP client
            │    ├─ GitHub tools / GitHub MCP server
            │    └─ restricted workspace filesystem tools
            └─ Build-job queue
                 └─ isolated Android worker → Gradle / tests / APK artifact
```

The Android app must never contain provider API keys, GitHub app secrets, privileged access tokens, or signing keys. The backend authenticates the user and authorizes each project action. The build worker receives only the source snapshot and minimum credentials needed for a job.

## Responsibilities

- **Planner module:** turns the user's idea into a versioned blueprint and bounded implementation plan. It cannot write files.
- **Main coding agent:** reads the approved task context, proposes changes, and requests tool calls. It cannot bypass approval policy.
- **Reviewer module:** checks whether the proposed diff matches the task, flags risky permissions/dependencies/secrets, and reports test evidence. It does not silently rewrite code.
- **Tool gateway / MCP client:** validates tool schemas, project scope, allowed paths, permissions, timeouts, and audit records before executing a tool.
- **GitHub integration:** reads repository state and creates a dedicated task branch/PR. Use least-privilege, revocable authorization; never ask users to paste a personal access token into chat.
- **Filesystem tools:** operate only inside a task-specific workspace; reject path traversal and writes outside the workspace.
- **Android build worker:** runs builds and tests in an isolated, disposable environment with resource limits, cancellation, log redaction, and expiring artifacts.

## First vertical slice

1. Authenticate a user and create/select a Vyoma project.
2. Send an idea to the planner and return a structured blueprint.
3. Require explicit user approval before implementation.
4. Create a task record and an isolated branch/workspace.
5. Let the coding agent propose a small patch.
6. Show a file diff and reviewer findings to the user.
7. Require approval before applying/committing the patch.
8. Run the configured checks/build in the isolated worker.
9. Store status with commit SHA, environment, timestamp, logs, and artifact expiry.

The first slice should stop at a reviewable patch and verifiable build result. Automatic publishing is out of scope.

## Tool contract (initial proposal)

Every tool must have a typed input/output schema and be authorized per project. Example operations:

- `project.read_summary(project_id)`
- `repository.read_file(project_id, path, ref)`
- `workspace.apply_patch(task_id, patch)`
- `workspace.diff(task_id)`
- `github.create_task_branch(project_id, task_id)`
- `checks.run_unit_tests(task_id)`
- `android.run_gradle_build(task_id, variant)`
- `build.get_status(build_id)`
- `build.get_artifact_metadata(build_id)`

These names are design examples, not currently available APIs. Do not expose a generic shell tool to the model. Prefer narrow, auditable operations with strict path and argument validation.

## Security and approval policy

- Keep model/provider secrets server-side and load them from a secret manager or deployment environment.
- Do not commit secrets, signing material, raw authorization headers, or sensitive prompts/logs.
- Validate every model-proposed tool call as untrusted input.
- Use user-scoped project authorization on every request; never trust a client-supplied project ID by itself.
- Treat repository content and tool output as untrusted data, not system instructions.
- Ask for explicit approval before applying changes, creating externally visible PRs, adding dependencies/permissions, or releasing artifacts.
- Run builds with no unnecessary network access, limited CPU/memory/time, and disposable credentials.
- Keep an append-only audit trail of who requested, approved, and executed each action.
- Report checks as passed, failed, or not run; attach evidence to the exact commit SHA.
- Expire generated APKs and restrict downloads to authorized project members.

## Suggested repository layout when implementation begins

```text
backend/
  src/
    api/             # authenticated HTTP endpoints
    orchestration/   # planner, coding loop, reviewer policy
    providers/       # model-provider adapter interface
    tools/           # typed tool registry and permission checks
    github/          # GitHub integration
    jobs/            # build-job API and queue contracts
worker/
  android-build/     # isolated Gradle worker image/job runner
docs/
  AI_AGENT_ARCHITECTURE.md
```

Do not create the backend or worker until runtime, hosting, authentication, and deployment choices are made. Keep provider adapters replaceable so model choice is configuration, not a rewrite.

## Acceptance criteria for the architecture milestone

- [ ] No provider or GitHub secret is stored in Android resources, BuildConfig, source control, or generated APKs.
- [ ] Planner, coding, reviewer, and tool-execution responsibilities have separate interfaces.
- [ ] File writes are limited to an authorized task workspace.
- [ ] Mutating actions require an approval record and are auditable.
- [ ] Build/test results are linked to commit SHA and environment.
- [ ] Build jobs are isolated, cancellable, quota-limited, and produce expiring artifacts.
- [ ] A failed or skipped check is never presented as passed.
