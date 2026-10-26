# Android APK builds and in-app updates

## How Vyoma updates itself
Vyoma includes an in-app update checker. It checks the latest test-build metadata on launch/background schedule and offers **Settings → Vyoma app updates → Check updates**. If a newer build exists, the app downloads the APK internally, verifies its SHA-256 checksum, and opens Android's installer. You do not need to manually find or download an APK file in a browser.

**Android security limitation:** Android does not allow an ordinary app to silently replace itself. You must confirm installation in the system installer. Fully automatic installs require a managed/device-owner setup or a distribution channel such as Google Play; Play Store distribution is not configured in this repository.

## Test channel
Pushes to either `feature/approved-homepage-layout` or `feature/phase-b-coding-agent-foundation` build a signed APK and refresh the mutable `latest-test` GitHub Release metadata. The installed app checks for a higher Android version code and offers that build.

This is a test channel: a Phase B development commit can contain incomplete features. Do not treat it as a stable production release.

## Build and signing requirements
The workflow requires these GitHub Actions repository secrets:
- `ANDROID_KEYSTORE_BASE64`: base64-encoded Java keystore file.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: key alias.
- `ANDROID_KEY_PASSWORD`: key password.

Use the same keystore for every update. Android accepts an in-place update only when the application ID remains `com.vyoma.app` and the signing certificate matches the installed app. If the installed app was signed with a different debug key, it cannot be updated in place by a differently signed APK; uninstalling would remove app-local data, so back up anything important first.

Keep a secure backup of the keystore. Never commit the keystore or passwords to Git.

## Versioned releases
Push a version tag such as `v0.1.5` for a versioned GitHub Release. Tags should only be used for versions intended to publish. The workflow verifies the APK signature and includes a SHA-256 checksum.

The workflow uses the GitHub Actions run number as Android version code. Version code must always increase for an update to be offered.
