# Vyoma

**Your Ideas. Your Apps.**

Vyoma is intended to be an **Android-native app builder**. The Vyoma mobile app is the builder; it helps users plan, design, generate, review, test, and build other apps. The target native app stack is Kotlin + Jetpack Compose + Material 3.

## Product direction (corrected)

- **Vyoma itself:** Android app, designed for mobile-first use.
- **Generated Android apps:** Kotlin + Jetpack Compose where native Android is selected.
- **Live preview:** shown inside Vyoma's workspace; a preview is not proof that a generated app works on a real Android device.
- **Builds:** run Gradle in a cloud/CI worker so users do not need to compile large projects on their phone. APK artifacts must be access-controlled.
- **GitHub:** source-code history and reviewable changes.
- **Figma:** UI/UX design only; implementation and source code stay in GitHub.

## Current repository status

The existing root-level Next.js dashboard is a **temporary UI prototype**, not the intended product platform. It is retained for reference while the Android-native direction is established; it must not be described as the final Vyoma app. The prototype's project creation is local UI state only.

Not implemented yet: a complete Android app, AI coding agent, persistent project memory, real GitHub authorization, isolated code execution, automated app testing, or cloud APK generation. Do not treat roadmap items as working features.

## Intended Android stack

- Kotlin
- Jetpack Compose
- Material 3
- Gradle / Android Gradle Plugin
- Cloud build and test jobs (for example, GitHub Actions or an isolated worker)
- Secure server-side orchestration for model/API access; no provider secrets in the Android client or repository

## Product principles

1. Blueprint before code.
2. Keep confirmed requirements and approved design separate from raw chat history.
3. Show scoped changes, diffs, and a recovery path.
4. Asking a question must not mutate project files.
5. Treat tests as evidence tied to a commit and environment.
6. Never expose API keys, OAuth tokens, signing keys, or service credentials in client code, APKs, public commits, or logs.
7. Keep cloud builds separate from the phone UI; never claim an APK is production-ready just because compilation succeeded.

## Development note

The repository currently contains a web prototype and its CI checks. That CI validates the prototype only; it does **not** validate an Android APK. Android implementation and Android-specific CI must be added and verified separately.
