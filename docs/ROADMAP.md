# Vyoma Roadmap

Status reflects repository evidence, not intended design. A screen or documentation item is not a working backend feature.

## M0 — Correct platform direction and verify foundations
- [x] Public repository and initial dashboard UI prototype
- [x] Product principles documented
- [x] Root-level web prototype explicitly labelled temporary, not the final product
- [x] Create the Android-native project foundation (Kotlin + Jetpack Compose + Material 3)
- [x] Add Android-specific CI: Gradle debug APK build
- [ ] Resolve and pin prototype dependencies with a committed lockfile
- [ ] Review Android navigation, accessibility, and empty/error states; add tests

**Exit gate:** Android project structure is in GitHub and Android CI validates it. Existing Next.js CI only validates the temporary prototype.

## M1 — Native mobile shell and blueprint
- [x] Compose app shell and mobile navigation (Home, Projects, Preview, Settings tabs)
- [x] Home dashboard, projects list, and local demo draft flow
- [x] Initial editable blueprint fields: goal, screens, data, integrations, permissions, edge cases, acceptance checks (local prototype model)
- [x] Blueprint draft/review/approved UI states
- [x] User can edit and explicitly approve a blueprint
- [x] Persist approved blueprints and local demo projects across app restarts using app-private SharedPreferences JSON (prototype-scale local storage; not Room or cloud sync)
- [ ] Loading, failure, retry, and empty states

**Exit gate:** a user can create and approve a blueprint in the Android app without code being generated.

## M1.5 — AI provider connection (Phase A)
- [x] Native AI connection settings in the Android app
- [x] Configurable HTTPS OpenAI-compatible chat-completions endpoint and model
- [x] BYOK API key encrypted at rest with Android Keystore AES-GCM
- [x] Save-and-test flow and visible provider error handling
- [ ] Validate with a real provider key on a physical Android device
- [ ] Add authenticated backend proxy, quotas, and server-side provider secret management before public production
- [ ] Add streaming and persistent chat history

**Exit gate:** a real provider request succeeds on a device and invalid credentials fail safely. Source implementation alone does not satisfy this gate.

## M4.1 — Coding-agent foundation (Phase B, first increment)
- [x] Add restricted task-workspace file listing and UTF-8 source reading
- [x] Generate reviewable file-change proposals without mutating the workspace
- [x] Require explicit approval before applying a proposal
- [x] Reject path traversal and symlink paths; bound file/list sizes and skip build output
- [x] Detect stale proposals before applying changes
- [x] Add backend unit tests and a dedicated Python CI workflow
- [x] Add explicit plan rejection path that leaves workspace unchanged
- [x] Integrate OpenHands Software Agent SDK in a disposable-copy, file-edit-only development adapter (live runtime validation still pending)
- [x] Add development bearer-token task API and local SQLite task persistence; [ ] add per-user authorization, quotas and production audit database
- [x] Connect Android Coding Agent UI to the backend with an OpenHands engine option and explicit approval/rejection
- [x] Add basic SQLite audit events for task creation and proposal decisions (development-only, shared-token auth)
- [ ] Add a task-level undo/restore endpoint and UI with conflict detection after an approved change
- [ ] Add task-wide durable checkpoints and isolated build-worker execution

**Exit gate:** a coding-agent task proposes a scoped diff, waits for approval, applies safely, and returns verifiable checks. This increment provides the workspace safety boundary only; it is not yet an autonomous agent.

## M2 — Workspace and durable project memory
- [ ] Project workspace with chat, preview, files/diff, tests, and activity
- [ ] Define versioned memory records and provenance
- [ ] Separate requirements, decisions, rejected alternatives, design tokens, task state, code summaries, and verification history
- [ ] Add conflict handling and user correction
- [x] Add Room project/blueprint entities, projectId foreign-key relationship, DAO, and repository foundation (UI wiring and legacy SharedPreferences migration still pending)
- [ ] Migrate existing local prototype data into Room with schema/version migration tests
- [ ] Add authentication/authorization and remote persistence only after schema and access-policy review
- [ ] Add export/delete controls for project data

**Exit gate:** project context survives reloads and can be reviewed/corrected by its owner.

## M3 — Design and controlled edits
- [ ] Design tokens and reusable component inventory
- [ ] Draft preview clearly separated from saved/approved source
- [ ] Before/after view and file diff
- [ ] Scope confirmation, approval, checkpoint, undo/rollback
- [ ] Asking a question or reviewing code causes no mutation

**Exit gate:** an approved UI change does not silently modify unrelated screens.

## M4 — Repository and coding loop
- [ ] Secure GitHub connect/disconnect and least-privilege permissions
- [ ] Create isolated branch/checkpoint per task
- [ ] One orchestrator with explicit, auditable tools
- [ ] Apply a small code change and present the diff
- [ ] Commit only after applicable validation and approval policy
- [ ] Track commit SHA for every result

**Exit gate:** one small, reproducible code task can be completed with a reviewable diff and recovery path.

## M5 — Test and review pipeline
- [ ] Unit and type checks
- [ ] Integration/API tests
- [ ] Android UI/navigation/E2E tests for critical flows
- [ ] Accessibility and visual regression checks where applicable
- [ ] Dependency/security scans and secret detection
- [ ] Report pass/fail/skipped with logs, environment, timestamp, and commit SHA

**Exit gate:** status reports never say “passed” for tests that did not run.

## M6 — Cloud build and release
- [ ] Isolated worker, quotas, cancellation, and artifact expiry
- [ ] Cloud Gradle build and downloadable APK artifact
- [ ] Signing-key handling outside source code; release signing requires explicit setup
- [ ] Build failure diagnostics and reproducibility metadata
- [ ] Emulator/device testing of critical flows

**Exit gate:** a clean Android starter project builds in the cloud and the APK can be downloaded securely.

## M7 — Product hardening
- [ ] Usage/cost limits and abuse controls
- [ ] Audit trail and redacted observability
- [ ] Backup/recovery and data export/deletion
- [ ] Real-device beta testing and feedback
- [ ] Document supported project types and known limitations

## Explicit non-goals for the first milestone

- Seven independent autonomous agents
- Automatic production publishing without approval
- Claiming production readiness based only on a successful compile
- Putting provider credentials into Android client code or generated apps
- Connecting a database before its schema and access policies are reviewed
