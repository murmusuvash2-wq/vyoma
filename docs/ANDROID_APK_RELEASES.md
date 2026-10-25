# Vyoma Android APK and ZIP distribution

## What the release workflow produces

For each manual signed build or version tag, the workflow creates:
- `Vyoma-<version>-Android.zip`: distributable ZIP containing the signed APK, install/update instructions, build metadata and APK SHA-256 checksum.
- `SHA256SUMS-ZIP-<version>.txt`: SHA-256 checksum for the ZIP itself.
- `SHA256SUMS-<version>.txt`: SHA-256 checksum for the APK inside the bundle.
- A separately downloadable signed APK in the workflow artifact for convenience.

The `latest-test` channel also publishes `Vyoma-latest-test-Android.zip`, `Vyoma-latest-test.apk`, checksums and `latest-test.json`. It is a test channel, not a production release. GitHub Actions refreshes it when the `feature/approved-homepage-layout` branch receives a push.

## Configure stable signing before running a release

Open **Repository → Settings → Secrets and variables → Actions** and add all four repository secrets:

- `ANDROID_KEYSTORE_BASE64`: the same release keystore encoded as one-line Base64.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: alias inside the keystore.
- `ANDROID_KEY_PASSWORD`: password for that alias.

Never commit the keystore, Base64 value, or passwords. Keep a secure offline backup of the original keystore. Use this exact same key for every APK that should update an existing installation.

**The workflow intentionally fails if any signing secret is missing. It does not fall back to a debug-signed APK.** This avoids publishing an APK that cannot reliably update the prior release.

If you have a `.jks` file, create the Base64 value locally and paste the single-line output directly into the GitHub secret field. Do not upload the keystore to this repository or send the secret through chat. Do not regenerate the keystore for each release.

## Build a signed APK + ZIP manually

1. Open **Actions → Android APK → Run workflow**.
2. Enter a unique version name such as `0.1.5`.
3. Run the workflow and wait for all steps to succeed.
4. Download the artifact named `vyoma-android-<version>-signed-bundle`.
5. Verify the ZIP checksum before extracting or installing it.

Manual workflow artifacts expire after 14 days. Use a version tag for a persistent GitHub Release.

## Publish a versioned release

After reviewing the source and confirming the four signing secrets are configured, create and push a version tag such as `v0.1.5`. The workflow builds, verifies the signature and package ID, creates the ZIP bundle and attaches the bundle/checksums to the GitHub Release.

Only tag versions intended for distribution. Android `versionCode` is generated from the workflow run number; `versionName` comes from the tag or manual input. Never reuse an old version code for a new installable release.

## Install and update on Android

1. Download the ZIP to the phone and extract it.
2. Open the included APK.
3. If Android asks, allow the file manager/browser to install unknown apps, then return to the installer.
4. Confirm installation in Android's system dialog.

Android may require user confirmation for every installation/update; this workflow does not silently install updates.

For an in-place update, the package ID must remain `com.vyoma.app` and the APK must use the same compatible signing certificate as the installed app. If Android reports a signature conflict or refuses the update, **do not uninstall the existing app first** because local data may be lost. Check that the stable release keystore has not changed.

## What this does not promise

- The ZIP is a distribution package, not a source-project ZIP.
- A successful build and valid signature do not prove every app feature works on a physical device.
- Automatic silent updates are not possible through this APK workflow; Android installation requires user/system approval.
- Public production release still requires device testing and confirmation that the signing key matches any previously distributed stable APK.
