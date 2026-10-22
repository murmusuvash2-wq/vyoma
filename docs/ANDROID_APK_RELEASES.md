# Android APK builds and GitHub Releases

## Build a test APK
1. Open **Actions → Android APK → Run workflow**.
2. Choose a version name and run it.
3. Download the APK from the workflow run's Artifacts section. Manual-run artifacts expire after 14 days.

## Publish a versioned Release
Push a version tag such as `v0.1.1`. The workflow builds an APK and attaches it to a GitHub Release. Tags should be used only for versions you intend to publish.

## Important: stable signing is required for in-place updates
Android permits an APK to update an installed app only when the application ID remains the same and the signing certificate is compatible. Vyoma's application ID is `com.vyoma.app`.

Before publishing APKs intended to upgrade earlier installs, configure these **Repository Actions secrets**:
- `ANDROID_KEYSTORE_BASE64`: base64-encoded Java keystore file.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: key alias.
- `ANDROID_KEY_PASSWORD`: key password.

Keep a secure backup of the keystore. Never commit the keystore or passwords to Git. Use the same keystore for every release.

If these secrets are absent, the workflow creates a debug-signed APK for testing and labels it clearly. GitHub-hosted runners generate their own debug signing key, so debug APKs from different runs are **not guaranteed to update each other**. Do not distribute those as upgradeable production releases.

The workflow run number is used as the Android version code; the version name comes from the tag (or manual workflow input). The workflow also provides a SHA-256 checksum.
