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

## Build & run — Android

```bash
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug
```

## Build & run — iOS

The iOS app is a thin SwiftUI shell (`iosApp/iosApp/`) over the shared Compose code. Building always compiles the Kotlin framework first via a run-script phase that invokes `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`. Open **`iosApp/Prompter.xcodeproj`** in Xcode.

### Simulator

1. Open `iosApp/Prompter.xcodeproj` in Xcode.
2. Pick any iPhone simulator in the scheme dropdown and press **Run** (⌘R).
3. The first build is slow — it compiles the whole Kotlin/Native framework.

> Only arm64 simulators are supported (Apple Silicon). Simulators have no camera or reliable microphone, so record/speech features should be tested on a real device.

### External device

1. Open `iosApp/Prompter.xcodeproj` in Xcode.
2. **Signing & Capabilities** → check **Automatically manage signing** and select your team. A free Apple ID works (certificates expire every 7 days); an Apple Developer Program membership removes that limit.
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

## Further reading

- `AGENTS.md` — architecture, conventions, and debugging on the physical Android device.
- `DEPLOYMENT.md` — App Store & Google Play submission checklist.
