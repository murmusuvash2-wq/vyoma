# Vyoma Roadmap

Status reflects repository evidence, not intended design. A screen or documentation item is not a working backend feature.

## M0 — Foundation and verification
- [x] Public repository and initial responsive dashboard prototype
- [x] Product principles and initial stack documented
- [ ] CI runs lint and production build on push/pull request
- [ ] Resolve all CI errors and pin dependencies with a committed lockfile
- [ ] Review mobile navigation, keyboard access, and empty/error states

**Exit gate:** CI is green on the default branch and the README reports accurate status.

## M1 — Project workspace and blueprint
- [ ] Project workspace route and navigation
- [ ] Blueprint data types: platform, goals, screens, flows, data, integrations, permissions, edge cases, acceptance tests
- [ ] Blueprint draft/review/approved states
- [ ] User can edit and explicitly approve a blueprint
- [ ] Empty, loading, failure, and retry states

**Exit gate:** a user can create and approve a blueprint without any code being generated.

## M2 — Durable project memory
- [ ] Define versioned memory records and provenance
- [ ] Separate confirmed requirements, decisions, rejected alternatives, design tokens, task state, code summaries, and verification history
- [ ] Add conflict handling and user correction
- [ ] Add authentication/authorization and persistence after schema review
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
- [ ] Commit only after applicable validation and user approval policy
- [ ] Track commit SHA for every result

**Exit gate:** one small, reproducible code task can be completed with a reviewable diff and recovery path.

## M5 — Test and review pipeline
- [ ] Unit and type checks
- [ ] Integration/API tests
- [ ] UI/navigation/E2E tests for critical flows
- [ ] Accessibility and visual regression checks where applicable
- [ ] Dependency/security scans and secret detection
- [ ] Report pass/fail/skipped with logs, environment, timestamp, and commit SHA

**Exit gate:** status reports never say “passed” for tests that did not run.

## M6 — Build and release
- [ ] Web build and preview deployment
- [ ] Isolated worker, quotas, cancellation, and artifact expiry
- [ ] Android Kotlin/Compose starter template
- [ ] Cloud Gradle build and downloadable APK artifact
- [ ] Signing-key handling outside source code; release signing requires explicit setup
- [ ] Build failure diagnostics and reproducibility metadata

**Exit gate:** a clean Android starter project builds in the cloud and the APK can be downloaded securely.

## M7 — Product hardening
- [ ] Usage/cost limits and abuse controls
- [ ] Audit trail and redacted observability
- [ ] Backup/recovery and data export/deletion
- [ ] Real-device testing and beta feedback
- [ ] Document supported project types and known limitations

## Explicit non-goals for the first milestone

- Seven independent autonomous agents
- Automatic production publishing without approval
- Claiming production readiness based only on a successful compile
- Putting provider credentials into browser code or generated apps
- Connecting a database before its schema and access policies are reviewed
