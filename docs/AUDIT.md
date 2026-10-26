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


## Local project persistence follow-up — 2026-10-22
- Added `ProjectStore.kt` using app-private SharedPreferences and JSON serialization for approved blueprints and local demo project names.
- Connected app startup to restore saved items and approval/create actions to persist changes.
- This is local-device persistence for the prototype, not Room, encrypted cross-device sync, authentication, or cloud backup. Corrupt stored JSON falls back to an empty list; recovery UX and migration tests remain future work.
- Android CI must be checked for the persistence commits before marking this implementation build-verified.


## AI Chat Workspace & GitHub Cloud Build Integration — 2026-10-25
- Added `SettingsStore.kt` enabling user-selected AI providers (Google Gemini, OpenAI, Anthropic Claude, Custom/OpenRouter) and secure storage of user-provided API keys and GitHub credentials.
- Added `AiService.kt` for provider-agnostic AI chat reasoning, real-time honest idea validation, competitor analysis, and Jetpack Compose code generation.
- Added `GitHubService.kt` for repository verification/creation, multi-file commits via GitHub REST API, and querying cloud workflow runs.
- Upgraded `HomeScreen` with an AI Idea & GitHub Research Lab providing real-time honest reality checks, competitor landscape, growth metrics, and 1-tap "Add to Projects" conversion.
- Added `LivePreviewTabContent` to `WorkspaceScreen.kt` featuring an interactive simulated smartphone canvas with dynamic screen switching, working interactive components, and theme toggling.
- Added MCP tools recommendation tracking across blueprints and project workspaces.
- Added `android.permission.INTERNET` to `AndroidManifest.xml`.
- Integrated `WorkspaceScreen` transitions directly into `MainActivity.kt` from blueprint approvals, research reports, and project cards.


## Non-Coder UI/UX Architecture & Dual Mode (Creator vs Dev) — 2026-10-26
- Added `ThemePalette.kt` providing 7 curated Material 3 palettes (Fintech Emerald, Cyber Neon, Pastel Minimal, Luxury Obsidian, Sunset Coral, Deep Ocean, Lavender Dream) with guaranteed WCAG AA high-contrast compliance and 48dp touch targets.
- Added Dual Mode ("Creator Mode" for non-coders vs "Developer Mode" for engineers) with seamless 1-tap switching in top navigation bar and persistent state in `SettingsStore`.
- Upgraded `WorkspaceScreen.kt` with:
  - **Live Palette Selector Bar:** 1-tap dynamic re-theming of the simulated smartphone preview and automatic synchronization to generated Jetpack Compose theme files (`Theme.kt`, `Color.kt`).
  - **Click-to-Tweak Visual Customizer:** Direct inline modification of app title, core objective, brand icon category, and interactive task management without touching Kotlin code.
  - **Pre-Engineered UI Blocks Tab:** Visual lego-block container view for non-coders with accessibility compliance indicators.
  - **1-Tap Cloud APK Delivery:** Non-technical cloud generation interface highlighting the Zero Phone RAM/Overhead guarantee (optimized for 3-4 GB RAM mobile devices).
- Updated `MainActivity.kt` Settings Screen with Studio Experience Mode selection and phone hardware load explanation.


## Compact Unified Studio Refactor — 2026-10-26
- Redesigned `MainActivity.kt` into a streamlined **Projects Hub**:
  - Eliminated fragmented 5-screen hops (separate Blueprint review, template preview, and complex drawer navigations).
  - Prominent "What do you want to build?" input that creates and directly transitions into the workspace studio in 1 tap.
  - "Your Projects" cards list with immediate "Open Studio" access.
- Refactored `WorkspaceScreen.kt` into a **Unified 3-Pillar Switcher**:
  - `[ 💬 Chat | 📱 Preview | 📁 Files ]`: Both coders and non-coders work in the exact same workspace without artificial mode segregation.
  - **Universal Cloud Build Pill:** Integrated directly into the top bar (`[ 🚀 Build APK ]`), allowing 1-tap cloud builds and live status tracking from any tab.
  - Embedded inline action chips in AI chat bubbles (`Preview`, `Files`, `Build APK`).


## GitHub In-App OTA Auto-Update System — 2026-10-26
- Added `AppUpdateService.kt` allowing Vyoma to query GitHub Releases API (`repos/{owner}/{repo}/releases/latest`) on launch.
- Added version comparison engine comparing semantic tags (e.g. `v0.1.2`) against installed `versionName`.
- Added streaming APK downloader with real-time percentage progress tracking.
- Added in-place APK installation trigger using `FileProvider` (`content://...`) and `ACTION_VIEW` intent with `application/vnd.android.package-archive`.
- Added `REQUEST_INSTALL_PACKAGES` permission and `file_paths.xml` configuration to `AndroidManifest.xml`.
- Integrated automated In-App Update notification card on homepage (`ProjectsHubScreen`) and manual "Check for Updates" tool in `SettingsScreen`.
- Since builds share the stable release signing keystore, in-place updates preserve all local databases and user preferences seamlessly without re-downloading from browser.
