# Repository Audit — 2026-10-22

## Scope and method
Reviewed the current public GitHub tree, README, Android Gradle files, both GitHub Actions workflows, roadmap, package manifest, and current Android entry screen. This is a static repository audit plus recorded CI evidence; it is not a manual device test or a full local dependency-analysis run.

## Product direction
- Vyoma itself is the Android-native app builder: Kotlin + Jetpack Compose + Material 3.
- GitHub remains source of truth for code; Figma is for UI/UX only.
- The root Next.js app is explicitly documented as a temporary visual reference/prototype, not the final product.
- Do not delete the web prototype yet: it is documented as intentionally retained for reference. Its CI is now scoped so Android-only commits do not run the web build.

## Current Android state
- Android project scaffold and Compose app entry exist.
- Home has an idea text field, six starter cards, and Home/Projects/Preview/Settings navigation.
- Splash and three onboarding pages are implemented in MainActivity.kt. Completion is stored in SharedPreferences.
- Project/template state is still in-memory in the main app; it is not a durable project database.
- Preview is illustrative, not a running generated app.
- AI provider calls, real blueprint generation/approval, Project Memory, GitHub OAuth, code-generation agent, cloud Gradle worker, and APK delivery workflow are not implemented.

## Confirmed CI evidence
- Android CI for commit 367c6581ce8c68db485f8454b9c0e0f9ec548342 completed successfully: https://github.com/murmusuvash2-wq/vyoma/actions/runs/39754569341
- General web CI for the same commit completed successfully: https://github.com/murmusuvash2-wq/vyoma/actions/runs/39754569191
- These are CI build checks, not device/UI tests.

## Cleanup performed
- Removed Android dependencies that were not referenced by the current source: lifecycle-runtime-ktx, ui-tooling-preview, and debug ui-tooling. Re-add them when a concrete implementation uses them (for example, lifecycle APIs or Compose @Preview tooling).
- Scoped the root Next.js CI workflow to web-prototype files and its own workflow. Android-only changes now trigger Android CI rather than unnecessarily running npm install/lint/Next.js build.

## Keep (not classified as unused)
- Android Gradle files and Android CI: required for native app builds.
- README, architecture and roadmap docs: establish platform decisions and staged delivery.
- Root Next.js files and package.json: kept only as the documented temporary UI prototype/reference. Do not add new production features there.
- material-icons-extended: currently needed by the icon imports in MainActivity.kt.
- Compose Material 3, Compose UI, activity-compose and Kotlin/Android plugins: used by current Android app.

## Not deleted
No source files were deleted. Static tree inspection alone cannot prove that the Next.js prototype is safe to delete, and project documentation explicitly says it is retained as a reference. No lockfile is present in the repository tree; do not claim dependency locking is complete. A full cleanup of source imports and build/test dependencies should be rechecked after splitting MainActivity.kt into feature files.

## Next steps
1. Re-run Android CI after dependency cleanup.
2. Split the ~30 KB MainActivity.kt into theme, onboarding, home, projects, preview, and settings files only when doing so keeps the app buildable.
3. Implement typed blueprint draft/review/approved state before any code generation.
4. Add persistence and tests before connecting AI/backend services.


## Follow-up refactor — 2026-10-22
- Extracted splash, first-launch routing, and onboarding screens into `EntryScreens.kt`; the activity file is now smaller (roughly 30 KB down to 23 KB).
- Removed imports made unused by the extraction.
- Android CI after dependency cleanup passed on commit `2be57e37c8c9c11a6a75f1205a26be83b46b05f5`: https://github.com/murmusuvash2-wq/vyoma/actions/runs/39755494824
- Android CI for the final screen-extraction commit `c53f2ba8bba9caf41ccf7e0759c79e803886c099` was running at audit update time: https://github.com/murmusuvash2-wq/vyoma/actions/runs/39755809138
- First-launch behavior is code-reviewed but not yet instrumented with automated UI tests or manually verified on a physical device. Do not describe that UI flow as device-tested.


## Blueprint flow implementation — 2026-10-22
- Added `BlueprintScreen.kt` with editable project name, goal, screen/navigation list, data, integrations, permissions, edge cases, and acceptance checks.
- Connected Home's “Plan idea” and starter-template “Use” actions to the Blueprint tab.
- Added explicit “Approve blueprint” action. Approved blueprints appear in Projects for the current app session.
- This is a local UI/model flow only: no AI generation, Room/database persistence, backend sync, or code generation. Approved state is lost on process restart.
- Android CI for the previous screen-extraction refactor passed: https://github.com/murmusuvash2-wq/vyoma/actions/runs/39755809138
- CI for the blueprint implementation commit(s) is running; final pass/fail must be confirmed from Actions before calling this implementation build-verified.
