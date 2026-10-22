# Vyoma Android UI/UX Audit

Audit date: 2026-10-22  
Scope: Android Compose source on `main`, open navigation-drawer PR #10, and current roadmap. This is a source/flow audit, not a hands-on device usability test. No screenshot capture or manual phone test was performed during this audit.

## Executive summary

**Overall: promising visual foundation, not yet a complete app-builder workspace.**

The app uses a consistent navy/mint/cream palette, rounded cards, Compose Material 3 components, and a closed-by-default `ModalNavigationDrawer`. The Home → Blueprint → Projects flow is understandable for a prototype. The largest UX gap is not just styling: important screens imply capabilities that are not yet connected, and the Preview is not yet a real generated-project preview.

PR #10 improves the drawer hierarchy and visual treatment and its Android CI run #39801393150 completed successfully. It remains an open PR, so its design is not on `main` yet. CI ran `assembleDebug`; this is compile evidence, not proof of usability or accessibility.

## Findings by priority

### P0 — Fix before calling the builder workflow functional

#### 1. Home copy overstates current AI capability
- Evidence: Home card says “AI planning · no code needed”; Plan idea opens the local Blueprint screen.
- Impact: users may reasonably expect a real AI response, but the current flow only passes local text into a form.
- Recommendation: until the backend is connected, label it as a prototype/planning draft. Once connected, show clear states: sending, AI working, clarification needed, draft ready, failed/retry.

#### 2. Preview is a template/demo preview, not generated app preview
- Evidence: Home template action sets a `StarterTemplate`; the Preview screen receives the selected template. There is no generated project renderer/build artifact path connected on `main`.
- Impact: the word “Preview” can imply the user's own app is running.
- Recommendation: explicitly label current state “Template preview” or “Preview prototype”. The eventual workspace must distinguish (a) mock/prototype preview, (b) running supported web preview, and (c) native Android build/device-tested output. Add empty/loading/error/retry states.

#### 3. Approved blueprint and project persistence are split/local
- Evidence: `main` saves local projects and approved blueprints via `ProjectStore`; Room-backed project workspace is in open PR #9, not on `main`.
- Impact: duplicated/local-only state can confuse the user, and a visible project is not yet a remote/synced workspace.
- Recommendation: resolve PR #9 merge conflict, review its persistence and workspace code, migrate legacy data with tests, and clearly state local-only vs synced. Do not claim cloud persistence yet.

#### 4. No complete Chat → tool action → diff → preview → build loop
- Evidence: main source has no connected AI Chat endpoint, GitHub authorization, generated-file execution, or cloud build status workflow.
- Impact: the app currently helps collect an idea and plan a draft; it does not yet build another app.
- Recommendation: design workspace states now, but only display successful actions when backed by real results. Connect the backend incrementally and show explicit approval before code/file changes.

### P1 — High-value usability improvements

#### 5. Home idea input needs stronger text-input UX
- Evidence: Home uses `BasicTextField` with a fixed 94dp container and no explicit keyboard options/IME action or visible labelled field semantics in the current implementation.
- Impact: on long ideas or smaller screens, text entry and submission may be awkward; accessibility and keyboard behavior need verification.
- Recommendation: use a labelled Material 3 text field or give the custom field a clear label/semantics, set appropriate keyboard options and IME action, support multiline growth/scrolling, and verify keyboard insets on a real device.

#### 6. Navigation needs complete destination and state coverage
- Evidence: main uses tab integers and a drawer; Preview and Settings are top-level items. Blueprint review and any project workspace are not represented as a clear destination in the current drawer. The open PR #10 improves grouping/selected states but still targets only the existing Home/Projects/Preview/Settings destinations.
- Impact: as Chat, Blueprint, Files, Build and project-specific workspace grow, navigation may become ambiguous.
- Recommendation: define a typed destination model rather than growing integer tabs; distinguish global destinations from project-scoped tabs. Keep the drawer hidden until the hamburger is tapped. Add clear selected states, back behavior, and state restoration.

#### 7. Loading/error/empty states are incomplete
- Evidence: roadmap still lists loading/failure/retry/empty states as pending; preview, settings and project flow need explicit states.
- Impact: network failures, empty projects, missing previews and build failures can look like broken UI.
- Recommendation: define a reusable state pattern (loading, empty, recoverable error + retry, permission required, success) and use it consistently.

#### 8. No Compose UI test suite evidenced
- Evidence: current CI workflow builds the APK with `assembleDebug`; no Compose UI tests were found in the audited paths.
- Impact: build success does not validate drawer interaction, keyboard behavior, blueprint approval, accessibility or preview states.
- Recommendation: add Compose UI tests for drawer open/close and navigation, idea entry/submit, blueprint edit/approve, project persistence, and Preview loading/error states. Add accessibility checks and manual TalkBack verification. Official guidance: https://developer.android.com/develop/ui/compose/testing and https://developer.android.com/develop/ui/compose/accessibility/testing.

### P2 — Polish after core states are real

#### 9. Design system is mostly constants, not a reusable token/component layer
- Evidence: colors are shared constants, but the app has no documented spacing/type/shape/component inventory.
- Recommendation: create a small Vyoma design system (color, typography, spacing, shapes, button/card/text-field styles) before adding lots of screens. Use Compose samples as a reference, not wholesale copied UI.

#### 10. Long content and compact screens need validation
- Evidence: Home and several screens use scrollable columns, but there is no documented test matrix for small devices, large font sizes, landscape or accessibility.
- Recommendation: test at least a compact phone, default phone, large font size and keyboard-visible state. Verify touch targets, contrast, TalkBack labels, focus order and system bars.

## Drawer PR #10 review

**Positive**
- Drawer remains closed by default and opens from the hamburger.
- Branded navy header, clearer section headings, selected mint state and footer create a more intentional hierarchy.
- Navigation items close the drawer after selection.
- Android CI run #39801393150 passed `assembleDebug`.

**Still to verify/improve**
- Test actual open/close, outside-tap dismissal, Android back behavior and scroll/height on short screens.
- Add Compose UI tests and accessibility checks; CI compilation alone is not sufficient.
- Once PR #9's project workspace is resolved, add a project-scoped Workspace entry without making the global drawer confusing.
- Consider using Material 3's built-in navigation semantics and verify text scaling/contrast.

## Recommended execution order

1. Resolve and review PR #9's merge conflict; avoid merging a stale or conflicted workspace branch.
2. Merge PR #10 only after checking its current mergeability and ensuring it does not overwrite the workspace integration.
3. Add typed navigation and Compose UI test foundations.
4. Improve Home input and explicitly label prototype/local-only behavior.
5. Implement the workspace Preview empty/loading/error states, then connect a real supported preview renderer.
6. Connect AI Chat and GitHub tools through the secure backend; expose tool progress and reviewable diffs.
7. Implement cloud build status/logs/artifact download.
8. Run Android CI plus UI/accessibility tests and a real-device pass before calling the flow complete.

## Audit limits

- Source review only; no live phone interaction or screenshot-based visual inspection was performed.
- Build success was checked from GitHub Actions for PR #10. No claim is made that every screen was tested on-device.
- UI states and features described as missing are based on the current `main` source reviewed on 2026-10-22; re-audit after PRs merge.
