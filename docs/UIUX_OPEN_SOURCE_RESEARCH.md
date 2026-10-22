# Vyoma UI/UX Open-Source Research

Research snapshot: 2026-10-22. This is a candidate list, not approval to import code. Check the exact commit, licence files, assets, and transitive dependencies again before implementation.

## Product context

Vyoma is an Android-native app builder, built with Kotlin + Jetpack Compose + Material 3. We are completing Vyoma itself first. Only after its own AI Chat → approved Blueprint → Preview → reviewable changes → cloud build workflow is working will we prioritize using Vyoma to create other apps.

We will not copy any reference application wholesale. Use these repositories to learn patterns and, only when useful, adapt small components into Vyoma's own design system.

## Highest-value Android UI/UX candidates

| Priority | Repository | Licence reported by repository | What to study/adapt | What not to assume |
|---|---|---|---|---|
| 1 | [android/compose-samples](https://github.com/android/compose-samples) | Apache-2.0 | Jetchat for chat input, keyboard/IME, UI state, animations and tests; Jetnews for navigation patterns; Jetsnack for custom layouts and design tokens; Reply for adaptive navigation and larger screens. | These are independent sample apps, not a ready-made AI app-builder UI. Select small patterns, not whole screens or apps. |
| 2 | [Trendyol/komposto](https://github.com/Trendyol/komposto) | Apache-2.0 | Reusable Compose components, design tokens, RTL and accessibility patterns. Evaluate component quality and compatibility before adding a dependency. | It has a relatively small ecosystem footprint; do not add the entire library if a few components or patterns are enough. |
| 3 | [android/nowinandroid](https://github.com/android/nowinandroid) | Apache-2.0 | Production-oriented Compose structure, state handling, navigation, modularity, accessibility and testing conventions. | This is a reference app/architecture, not an AI coding workspace to transplant. |
| 4 | [Sanuu7/AndroidHarness](https://github.com/Sanuu7/AndroidHarness) | MIT reported; re-verify at integration commit | Inspect workspace interaction, file/project flows, GitHub/MCP integration concepts, preview and build-status UX. | Do not assume every feature has a stable public API or that its phone-side execution engine fits Vyoma's cloud-first architecture. |
| 5 | [BankkRoll/sidekickcn](https://github.com/BankkRoll/sidekickcn) | MIT | Web-only reference for modular AI assistant layouts, sliding sidebar/full-panel variants, mobile responsiveness and server-side credential handling. | React/Next.js components cannot be dropped into the Kotlin app. Use only interaction ideas unless Vyoma later needs a separate web console. |

Useful official references:
- Compose samples and their Apache-2.0 notice: https://github.com/android/compose-samples
- Jetchat specifics: https://github.com/android/compose-samples/tree/main/Jetchat
- Komposto component system and Apache-2.0 notice: https://github.com/Trendyol/komposto
- Now in Android: https://github.com/android/nowinandroid
- AndroidHarness: https://github.com/Sanuu7/AndroidHarness
- Sidekick/cn: https://github.com/BankkRoll/sidekickcn

## Proposed Vyoma UI/UX work

### 1. Design foundation
- Keep Vyoma's own brand, navy + mint direction, typography and spacing consistent.
- Define design tokens before adding components from other libraries.
- Use reusable Compose components rather than one-off styling on every screen.
- Verify contrast, touch target sizes, TalkBack labels, keyboard focus, edge-to-edge and system-bar handling.
- Support loading, empty, error, offline, retry and success states—not only the happy path.

### 2. Main mobile navigation
- Keep the hamburger menu hidden until tapped, to preserve workspace space.
- Drawer should have a clear Vyoma header, grouped destinations, strong selected state, predictable back/outside-tap dismissal, and accessible labels.
- Use Compose/Material 3 navigation patterns as a reference; keep Vyoma's own layout and visual identity.

### 3. AI Chat
- Clear message hierarchy, streaming/working state, cancel/retry, model/provider status where relevant, and visible tool activity.
- Make the message composer behave correctly with the software keyboard and small screens.
- Distinguish a question from an action: asking a question or inspecting a file must not mutate the project.
- Show approval requests before sensitive GitHub or file-changing actions.

### 4. Blueprint and change review
- Show screens, navigation, data, integrations, permissions and acceptance checks in scannable sections.
- Keep Draft, Needs review, Approved and Out-of-date states visually distinct.
- Before applying a change, show its scope and affected files.
- Provide diff, checkpoint and undo/recovery where possible.

### 5. Preview and build
- Preview is a first-class workspace destination, not just a placeholder card.
- Label the preview type explicitly: prototype/web preview, running supported preview, or native build/device verification.
- Provide refresh, loading, failure, retry and logs.
- Never imply that a WebView prototype proves Kotlin/Compose code compiles or works on a device.
- Cloud build status should show queued/running/succeeded/failed, commit or project version, logs, artifact expiry and download action.

### 6. Testing before calling a UI change complete
- Add Compose UI tests for drawer open/close, navigation, keyboard handling, blueprint approval and Preview loading/error states.
- Check small phone screens and large font sizes.
- Include TalkBack/accessibility checks and screenshot/visual regression where practical.
- Test on an actual Android device before saying a flow is complete.

## Licence and provenance gate

Before importing any code, icons, fonts, images or other assets:

1. Record repository URL and exact commit/tag.
2. Read the top-level licence and any file-level notices.
3. Inspect licences for transitive dependencies and bundled assets separately.
4. Identify exactly which files or concepts will be reused and what modifications are made.
5. Preserve copyright and licence notices and add attribution where required.
6. Add a row to the project’s third-party notices/inventory.
7. Reject the reuse if licence terms or provenance are unclear; use the idea as general design inspiration instead of copying source code.

MIT and Apache-2.0 are permissive licences, but their conditions still apply. A repository licence is not a blanket licence for unrelated dependencies or third-party assets.

## First implementation recommendation

1. Audit current Vyoma Compose screens and the open navigation-drawer PR before redesigning existing work.
2. Use official Compose samples as the primary interaction/accessibility reference.
3. Evaluate Komposto only for components that Vyoma genuinely needs.
4. Use AndroidHarness to study the coding-workspace workflow, not as an assumed drop-in cloud backend.
5. Implement the Vyoma-specific Chat, Blueprint, Preview, Files/Diff and Build UI in our own component system.
6. Run Android CI and UI checks; keep the work in a reviewable branch/PR.

## Status

- [x] Candidate repositories and likely UI/UX roles documented.
- [x] Non-wholesale reuse policy documented in the roadmap.
- [ ] Exact versions, individual files/assets and dependency licences to be re-checked at each integration.
- [ ] UI/UX audit of current Compose screens and drawer PR.
- [ ] Implement design tokens and missing workspace states.
