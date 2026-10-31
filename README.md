# Teleprompter (Prompter)

A Kotlin [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) teleprompter for **Android & iOS**. It records video + audio while scrolling a script, and auto-scrolls the text as you read using **on-device speech recognition** — no API keys, no network, no account.

## Features

- Speech-driven auto-scroll (ML Kit on Android, `SFSpeechRecognizer` on iOS)
- Video + audio recording with the camera preview as the prompter background
- Script list with swipe-to-delete, editor with auto-save, dark theme
- Optional in-app tip products

## Tech stack

| | |
|---|---|
| Kotlin / Compose Multiplatform | 2.3.20 / 1.12.0-rc01 |
| Android | min SDK 26, compile SDK 37, CameraX 1.5, ML Kit speech recognition |
| iOS | iOS 15.0+, AVFoundation, `SFSpeechRecognizer` |

## Prerequisites

- JDK 17+ and the Android SDK (for the Gradle/Kotlin parts)
- **Xcode 16.1+** — Compose is pinned to ≥ 1.12, so it links against both Xcode 16 and 26 SDKs (1.11.x requires Xcode 26; see AGENTS.md)
- An Apple ID (free personal team) or Apple Developer account to run on a device

## Local configuration

The root `local.properties` file is ignored by git. Copy the relevant entries from
`config.sample.properties` into it, keeping any `sdk.dir` entry created by Android Studio:

- `feedback.email` pre-fills the recipient for feedback and bug reports. If unset, the app opens an email draft without a recipient.
- `apple.team.id` is your 10-character Apple Developer Team ID. After changing it, run `python3 scripts/sync_local_config.py` before opening or building the Xcode project. This writes an ignored `iosApp/LocalSigning.xcconfig` file.
- `device.serial` records your preferred physical Android device for local debugging. Check `adb devices` before using it.

Do not commit either `local.properties` or `iosApp/LocalSigning.xcconfig`.

## Build & run — Android

```bash
./gradlew :androidApp:assembleDebug -PversionCode=1 -PversionName=1.0
./gradlew :androidApp:installDebug -PversionCode=1 -PversionName=1.0
```

## Build & run — iOS

The iOS app is a thin SwiftUI shell (`iosApp/iosApp/`) over the shared Compose code. Building always compiles the Kotlin framework first via a run-script phase that invokes `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`. Run `python3 scripts/sync_local_config.py`, then open **`iosApp/Prompter.xcodeproj`** in Xcode.

### Simulator

1. Open `iosApp/Prompter.xcodeproj` in Xcode.
2. Pick any iPhone simulator in the scheme dropdown and press **Run** (⌘R).
3. The first build is slow — it compiles the whole Kotlin/Native framework.

> Only arm64 simulators are supported (Apple Silicon). Simulators have no camera or reliable microphone, so record/speech features should be tested on a real device.

### External device

1. Open `iosApp/Prompter.xcodeproj` in Xcode.
2. Set `apple.team.id` in `local.properties` and run `python3 scripts/sync_local_config.py`, or choose your team in **Signing & Capabilities**. A free Apple ID works (certificates expire every 7 days); an Apple Developer Program membership removes that limit.
3. Plug the iPhone in, unlock it, and tap **Trust This Computer** on the phone.
4. Select the device in the scheme dropdown and press **Run** (⌘R).
5. On the phone: **Settings → General → VPN & Device Management → trust your developer certificate**, then open the app and approve the trust prompt.
6. Grant **camera**, **microphone**, and **speech recognition** permissions when prompted.
7. Test the full flow: create a script → open the teleprompter → record → read aloud — the text should auto-scroll.

### iOS troubleshooting

| Symptom | Fix |
|---|---|
| "Xcode requires a development team" | Pick your team in Signing & Capabilities (step 2). |
| App crashes on launch on the device | Developer certificate not trusted yet (step 5). |
| App aborts at launch in the simulator | `Info.plist` must contain `CADisableMinimumFrameDurationOnPhone = true` (already set). |
| Link error `Undefined symbols: _OBJC_CLASS_$_UIViewLayoutRegion` | Compose must be ≥ 1.12.0-alpha02 (already pinned). Xcode 26 would also work. |

## Deploying to Google Play

Releases are automated with [Fastlane](https://fastlane.tools/). One command bumps the version, builds the release AAB, uploads it to the Play Console internal track, and tags.

Versions are derived from **git tags** — no version files, no committed version bumps.

### One-time setup

1. Install Fastlane:
   ```bash
   gem install fastlane
   # or: brew install fastlane
   ```

2. Create a **Google Play service account**:
   - Go to [Google Cloud Console](https://console.cloud.google.com/) → create a project (or use an existing one).
   - Enable the **Google Play Developer API**.
   - Go to **IAM & Admin → Service Accounts** → create one → download the JSON key.
   - In [Play Console](https://play.google.com/console) → **Setup → API access** → link the service account with **Release manager** permissions.

3. Place the JSON key at `fastlane/play-store-key.json` (gitignored).

4. Make sure the release keystore exists at `androidApp/keystore/release.keystore` and `androidApp/keystore.properties` is configured (see `DEPLOYMENT.md` for keystore generation).

### Releasing

```bash
# First release (no tags yet) — specify the starting version
fastlane android release version:10

# Subsequent releases — auto-increments from last tag
fastlane android release
```

This will:
1. Read the last git tag (e.g. `10`), increment to `11`
2. Generate release notes from git commits since that tag
3. Build the release AAB with version passed as Gradle properties (no file edits)
4. Upload to the **internal testing** track on Play Console
5. Create and push the new git tag (`11`)

After uploading, go to **Play Console → Release → Production** to promote the build.

### Build only (no upload)

```bash
fastlane android build
```

## Further reading

- `LICENSE` — MIT license for this repository.
- `AGENTS.md` — architecture, conventions, and debugging on the physical Android device.
- `DEPLOYMENT.md` — App Store & Google Play submission checklist.
