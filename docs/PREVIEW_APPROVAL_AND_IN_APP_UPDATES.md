# Preview approval and generated-app updates

## Product requirement
Vyoma must separate preview from release. The user can inspect the generated app first; only when they explicitly approve the preview does the **Build update** action become available. Generated apps should expose an in-app update control so users can check for, verify, and install a newer approved release.

## User flow
1. **Generate a draft** — coding agent works on a task-scoped workspace and records a source revision.
2. **Build preview** — an isolated build worker creates a preview artifact tied to that exact revision. The UI reports actual build/test status; no success state may be faked.
3. **Review preview** — user can inspect the preview, build/test report, change summary and revision identifier.
4. **Approve or reject** — explicit approval records the revision. Reject returns to editing; it must not publish or install anything.
5. **Build update** — only an approved immutable revision can be queued for release build. Display queued/building/succeeded/failed and preserve logs/artifact metadata.
6. **Publish release** — publish only after signing and compatibility checks. Keep the prior known-good version available for rollback.
7. **In-app update control** — the generated app's Settings/About screen has “Check for updates”, current version, available version, release notes, download progress and retry/error state.
8. **Verify and install** — verify signed release metadata, package identity, versionCode and SHA-256 before handing off to the OS installer or Play update API.

## Distribution rules
- **Google Play:** use Play's supported in-app update flow for Play-distributed apps; the app cannot bypass Play policy or OS/user controls.
- **Direct APK:** download only from the configured trusted release origin, verify expected package/version/hash and APK signature, then launch Android's package installer. Android/user confirmation may be required; do not promise silent installation.
- An update must use the same applicationId/package name and compatible signing identity as the installed app. A signing-key change is not an ordinary update and must fail safely.
- Never put signing keys, provider API keys or release credentials inside the generated app or model prompt.
- A failed update must leave the currently installed version usable. Keep release history and a prior approved rollback target.

## Required data per release
- projectId and immutable source revision/commit
- monotonically increasing versionCode and user-visible versionName
- package/application ID and signing-certificate fingerprint
- build and test statuses with logs and timestamps
- artifact URL, SHA-256, artifact size, release notes
- approving user/action and approval timestamp
- previous known-good release identifier

## Acceptance tests
- Previewing alone never publishes, installs or changes the installed app.
- “Build update” is unavailable until explicit approval for the current revision.
- If source changes after preview, approval is invalidated and a new preview is required.
- Failed build or tests cannot be labelled ready to release.
- Wrong package ID, lower/equal versionCode, hash mismatch, or signature mismatch is rejected.
- Network failure, download cancellation and installer cancellation leave the installed app intact.
- An approved release can be checked from inside the generated app and the OS-mediated update flow is launched.
- A previous approved release can be selected as the rollback target and rebuilt with a valid increasing versionCode.

## Implementation boundary
This is a product specification, not yet a working update pipeline. Current Vyoma's UpdateSettingsCard checks for updates to Vyoma itself; it is not yet a generated-project release/update service. The current Preview screen is an illustrative template preview, not a running generated APK. The new flow must not be represented as implemented until backend release APIs, immutable builds, artifact verification, generated-app update UI, and device tests exist.
