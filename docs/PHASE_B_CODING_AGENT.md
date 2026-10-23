# Phase B — Coding agent foundation

## Goal

Move Vyoma from AI connection into a controlled coding workflow. Vyoma remains an Android-native Kotlin/Jetpack Compose app; agent orchestration belongs in the backend, not in the Android APK.

## First increment implemented

- A scoped workspace module can list and read supported source/text files.
- The agent can prepare a file-change proposal and unified diff without changing the workspace.
- Applying a proposal requires an explicit approval flag and checks that the source file has not changed since review.
- Paths are constrained to a task workspace; path traversal and symlink paths are rejected.
- Common generated/build folders are excluded from listing; file sizes and list counts are bounded.
- Unit tests cover non-mutation before approval, approved changes, stale proposals, path traversal, symlink escape, and build-folder exclusion.

## Architecture decision

OpenHands Software Agent SDK remains the selected candidate for the main coding-agent runtime. The workspace module is an intentionally narrow tool boundary that can be adapted to SDK tool calls; it is not itself the OpenHands SDK and does not yet run an autonomous agent. Planner/reviewer responsibilities remain separate modules, not independent autonomous agents.

## Security gates before a live agent

1. Authenticate users and authorize every project/task.
2. Provision a disposable workspace per task; never use an arbitrary server path from a client request.
3. Add audit events for proposed, approved, applied, rejected, and reverted changes.
4. Integrate OpenHands behind the typed workspace tools; do not give the model a generic shell.
5. Require user review of diffs before mutation and before external GitHub writes.
6. Run tests/builds in an isolated worker with time, CPU, memory, network and cost limits.
7. Keep provider credentials request-scoped or in a secret manager; redact logs.
8. Report every check as pass/fail/not-run and tie results to a source revision.

## Acceptance status

- [x] Initial restricted workspace tool contract and implementation
- [x] Explicit approval gate for file mutation
- [x] Stale-proposal conflict detection
- [x] Unit tests for core workspace safety behavior
- [ ] Run backend test suite in CI
- [ ] Integrate OpenHands SDK agent loop
- [ ] Add authenticated task API and durable task state
- [ ] Connect the Android workspace UI to the backend
- [ ] Add diff/review/undo UI and approval records
- [ ] Isolated Android build worker and APK artifact delivery

This is the first Phase B increment, not a completed coding agent.
