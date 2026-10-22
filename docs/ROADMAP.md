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
- [ ] Refine UI/UX using the documented open-source evaluation process; do not copy any reference app wholesale

**Exit gate:** a user can create and approve a blueprint in the Android app without code being generated.

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
- [ ] Establish Vyoma-specific design tokens, spacing/typography scale, component inventory, and UI states
- [ ] Hidden hamburger navigation drawer with polished open/close and selected states
- [ ] Chat UI with streaming, tool-call/status cards, attachments where useful, and reliable keyboard/IME behavior
- [ ] Blueprint review UI with clear approve/edit actions
- [ ] Preview screen with explicit draft/live/build status and refresh/error/retry states
- [ ] File tree, diff review, test/build logs, and activity history
- [ ] Before/after view and file diff
- [ ] Scope confirmation, approval, checkpoint, undo/rollback
- [ ] Asking a question or reviewing code causes no mutation
- [ ] Add accessibility checks, small-screen layout checks, and Compose UI tests

**Exit gate:** the core workspace is usable on a phone, states are clear, and an approved UI change does not silently modify unrelated screens.

## M4 — AI Chat, repository tools, and coding loop
- [ ] Audit existing backend and open PRs before adding duplicate services or data models
- [ ] Connect AI Chat to a secure server-side model adapter; keep provider keys out of the Android app
- [ ] One orchestrator with explicit, auditable tools
- [ ] Research and implement a supported GitHub/MCP connection; do not assume a desktop coding agent can be embedded as a mobile API
- [ ] Secure GitHub connect/disconnect and least-privilege permissions
- [ ] Create isolated branch/checkpoint per task
- [ ] Apply a small code change and present the diff
- [ ] Commit only after applicable validation and approval policy
- [ ] Track commit SHA for every result

**Exit gate:** one small, reproducible code task can be completed with a reviewable diff and recovery path.

## M5 — Preview, test, and review pipeline
- [ ] Implement a first-level draft preview renderer for supported previewable content
- [ ] Clearly distinguish web/prototype preview from a compiled native Android app
- [ ] Add supported project preview adapters incrementally; do not claim every generated app can run inside a WebView
- [ ] Unit and type checks
- [ ] Integration/API tests
- [ ] Android UI/navigation/E2E tests for critical flows
- [ ] Accessibility and visual regression checks where applicable
- [ ] Dependency/security scans and secret detection
- [ ] Report pass/fail/skipped with logs, environment, timestamp, and commit SHA

**Exit gate:** preview type and verification evidence are visible, and status reports never say “passed” for tests that did not run.

## M6 — Cloud build and release
- [ ] Isolated worker, quotas, cancellation, and artifact expiry
- [ ] Cloud Gradle build and downloadable APK artifact
- [ ] Signing-key handling outside source code; release signing requires explicit setup
- [ ] Build failure diagnostics and reproducibility metadata
- [ ] Emulator/device testing of critical flows
- [ ] Optional on-device build only after the cloud path is reliable and the device constraints are evaluated

**Exit gate:** a clean Android starter project builds in the cloud and the APK can be downloaded securely.

## M7 — Product hardening
- [ ] Usage/cost limits and abuse controls
- [ ] Audit trail and redacted observability
- [ ] Backup/recovery and data export/deletion
- [ ] Real-device beta testing and feedback
- [ ] Document supported project types and known limitations

**Exit gate:** Vyoma's own chat → blueprint → review → preview → cloud build flow is tested on a real phone before we prioritize using Vyoma to create other apps.

## Open-source reuse policy
- We will **not copy or fork a whole project by default**. Treat candidate repositories as references and component sources.
- Reuse only the smallest useful, clearly identified components or patterns; adapt them to Vyoma's Kotlin + Jetpack Compose architecture and product design.
- Before code or assets are reused, check the repository's current licence, file-level headers, third-party dependency licences, fonts/icons/images, and any additional asset terms.
- Preserve required copyright/licence notices and provide attribution where the licence requires it. Keep a record of source URL, version/commit, licence, reused paths, modifications, and notices in the repository.
- Prefer permissive licences such as Apache-2.0 or MIT, but still satisfy their conditions. Do not reuse code/assets when the licence is missing, unclear, incompatible, or the provenance cannot be established; ask for review instead.
- A repository's top-level licence does not automatically cover every dependency, bundled asset, screenshot, font, or model.
- Re-check licence and compatibility at the time of integration. Research shortlist is not approval to copy code.
- See [UI/UX open-source research](UIUX_OPEN_SOURCE_RESEARCH.md) for candidate projects and intended use.

## Explicit non-goals for the first milestone
- Seven independent autonomous agents
- Automatic production publishing without approval
- Claiming production readiness based only on a successful compile
- Putting provider credentials into Android client code or generated apps
- Connecting a database before its schema and access policies are reviewed
- Copying an entire third-party app into Vyoma as a shortcut
