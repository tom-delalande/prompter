# Teleprompter — Agent Guide

## Project Overview

Kotlin Compose Multiplatform teleprompter app for Android & iOS. Records video + audio while scrolling a script. Uses on-device speech recognition for speech-driven auto-scroll (no API keys, no network).

## Tech Stack

| Component | Version |
|---|---|
| Kotlin | 2.3.20 |
| Compose Multiplatform | 1.12.0-rc01 |
| AGP | 9.3.0 |
| Gradle | 9.5.1 (use `gradlew`, not system Gradle) |
| Min SDK | 26 (Android 8.0) |
| Compile SDK / target SDK | 37 / 36 |

### Key Libraries
- **UI**: Compose Multiplatform (Runtime, Foundation, Material3, UI)
- **Serialization**: `kotlinx.serialization.json` 1.8.1
- **Camera (Android)**: CameraX 1.5.0 (camera2, lifecycle, video, view)
- **Lifecycle (Android)**: `lifecycle-runtime-compose` 2.9.0
- **Activity (Android)**: `activity-compose` 1.10.1

## Project Structure

```
composeApp/                            # KMP shared library
├── commonMain/kotlin/co/delalande/prompter/
│   ├── App.kt                         # Root composable, enum-based screen navigation
│   ├── billing/BillingManager.kt      # In-app tip products (expect)
│   ├── data/                          # PlatformStorage (expect), ScriptRepository, SettingsRepository, DebugRepository, JsonStorage
│   ├── log/Log.kt                     # Napier-backed facade; d/i/v gated by Log.verboseEnabled
│   ├── model/                         # Script, Settings, DebugSettings, SaveResult
│   ├── recording/CameraRecorder.kt    # expect camera interface
│   ├── speech/                        # SpeechRecognizer + SpeechListener, SpeechMatcher, AudioLevel
│   ├── ui/                            # SystemBackHandler (expect), TextDisplay, TimeFormat
│   │   ├── screen/                    # ScriptList, ScriptEditor, Settings, Teleprompter screens
│   │   └── theme/Theme.kt             # Dark theme only
│   └── viewmodel/TeleprompterViewModel.kt  # All recording/scroll logic
├── androidMain/.../                   # Android actuals: CameraPreview/Recorder (CameraX), MLKitSpeechRecognizer, AudioInput, AndroidBillingManager, PlatformStorage, SystemBackHandler, LogInitializer, PlatformActual
└── iosMain/.../                       # iOS actuals: CameraRecorder (AVFoundation), SpeechRecognizer (SFSpeechRecognizer), CameraPreview (UIKitView), IOSBillingManager, PlatformStorage, SystemBackHandler, LogInitializer, MainViewController

androidApp/                            # Android shell: MainActivity, AndroidManifest
iosApp/                                # iOS shell: Xcode project (iosApp/Prompter.xcodeproj), SwiftUI entry, Info.plist, Assets
```

## Architecture & Conventions

### Pattern: expect/actual with factory functions
- Common interfaces defined in `commonMain` (`SpeechRecognizer`, `CameraRecorder`, `PlatformStorage`, `BillingManager`); `Android*` / `IOS*` implementations in platform source sets.
- `App.kt` receives factory lambdas (`speechRecognizerFactory`, `cameraRecorderFactory`, `billingManagerFactory`, `platformStorage`) wired up in `MainActivity.kt` / `MainViewController.kt`.

### Navigation
- Enum-based `Screen` (ScriptList, ScriptEditor, Teleprompter, Settings) in `App.kt`.
- `TeleprompterViewModel` is created fresh each time the teleprompter screen is entered.

### TeleprompterViewModel
- Holds all mutable state as Compose `mutableStateOf` properties (for reactivity).
- Two parallel Android recording paths:
  1. **MLKitSpeechRecognizer**: AudioRecord → speech recognition → advances `currentWordIndex`
  2. **AndroidCameraRecorder**: CameraX record video + audio (`.withAudioEnabled()` produces a single MP4)
- On camera finish: `onRecordingFinished(filePath)` sets `lastRecordedFile` directly (no merge needed).
- `onVoiceActivityChanged`, `onAudioLevelChanged`, `onExternalMicrophoneChanged` have default no-op implementations in `SpeechListener`, so iOS doesn't need to implement them.

### Speech Recognition
- **Android**: `MLKitSpeechRecognizer` — on-device via `com.google.mlkit:genai-speech-recognition`. Advances the script by matching partial transcription against the script words.
- **iOS**: `SFSpeechRecognizer` via `SpeechRecognizer.ios.kt`.
- `SpeechMatcher.kt` holds the word-matching logic used by both platforms.

### Compiler Flags
- `-Xexpect-actual-classes` in `composeApp/build.gradle.kts` (suppresses Beta warning for expect/actual classes).

### Platform-Specific Notes
- **`NSObject`**: import from `platform.darwin.NSObject` (not `platform.NSObject` or `kotlin.native`).
- **`AVCaptureVideoPreviewLayer`**: use named parameter `sessionWithNoConnection` to disambiguate constructors.
- **`AVAudioEngine`**: in `platform.AVFAudio.*` on iOS.
- **`UIKitView`**: use `androidx.compose.ui.viewinterop.UIKitView` (not the deprecated `androidx.compose.ui.interop`).

## UI Conventions

- **Dark theme only** (`darkColorScheme` in `Theme.kt`)
- **Portrait by default**, but supports `sensor` orientation (AndroidManifest) and landscape on iOS
- **Camera preview**: fills full screen with subtle gradient vignette for text readability
- **Teleprompter text**: overlaid on camera, single `Text` with `buildAnnotatedString` for word coloring:
  - Current word: white, bold, 38sp, highlighted background
  - Past matched words: gray, 28sp
  - Past mismatched words: dim red, 28sp
  - Upcoming words: light gray (`#CCCCCC`), 28sp
- **Controls**: bottom bar with exit (✕), circular record button (red, white dot/stop icon), share (↗) or spacer
- **Camera switch**: top-right (🔄)
- **Status**: "Recording" / word count shown above record button
- **Script list**: cards with content preview (120 chars), word count; swipe-left to delete with confirmation dialog
- **Script editor**: auto-save with 400ms debounce, saves on back navigation (no save button)

## Build & Run

```bash
# Android
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug

# iOS — open iosApp/Prompter.xcodeproj in Xcode, pick a simulator or signed device, run.
# The "Build ComposeApp Framework" run-script phase invokes gradle embedAndSignAppleFrameworkForXcode first.

# Verify both targets at once
./gradlew :composeApp:compileKotlinIosArm64 :androidApp:assembleDebug
```

## Known Issues & Gotchas

1. **AudioRecord conflicts**: `MLKitSpeechRecognizer`'s `AudioRecord` + CameraX `withAudioEnabled()` both use the microphone simultaneously. Works on Android 10+ (shared mic mode).
2. **Compose/Xcode SDK coupling**: Compose Multiplatform ≥ 1.12.0-alpha02 is required to build with Xcode 16 SDKs — 1.11.x references iOS-26-only symbols (`UIViewLayoutRegion`, `UIUtilities`) that Xcode < 26 cannot link. Keep Compose at ≥ 1.12 unless building on Xcode 26+.
3. **Compose 1.12 plist sanity check**: iOS `Info.plist` must contain `CADisableMinimumFrameDurationOnPhone = true` or the app aborts at launch.

## Debugging on the Physical Android Device (preferred workflow)

Debug against the attached physical device (`adb devices`, serial `53181JEBF20037`) rather than reasoning in the abstract.

### Logging

Use `co.delalande.prompter.log.Log` (Napier-backed facade) in **commonMain and all source sets** — never `println` or `android.util.Log`:
- `Log.d/i` (lazy `{ "..." }` lambdas) for verbose state, `Log.w/e` for warnings/errors.
- `Log.verboseEnabled` (persisted via `DebugRepository` → `files/debug.json`) gates d/i/v at runtime in debug builds.
- Release builds strip d/i/v call sites via R8 `assumenosideeffects` (`androidApp/proguard-rules.pro`); w/e remain.
- logcat tags: `MLKitSpeech`, `App`, `TeleprompterVM`, `Settings`, `DebugRepo`.

### Built-in debug panel (no source edits needed)

A debug panel at the bottom of **Settings** (debug builds only, `BuildConfig.DEBUG`) provides:
- **Verbose logging** toggle (flips `Log.verboseEnabled`, persisted).
- **Auto-advance words** toggle — simulates speech without a mic by auto-scrolling (`seekTo` every 150ms) in the teleprompter; shows a "DEBUG AUTO-SCROLL" pill.
- **Inject 1000+ word test script** (creates `id="debug-large"` in `scripts.json`) and **Remove debug scripts**.

Key state transitions are already logged from `TeleprompterViewModel` (`startPlayback`, `startRecording`, `seekTo`, speech advances).

To observe behavior without tapping the screen (locked device):

1. Ensure `debug.json` has `{"autoAdvanceWords": true}` and `scripts.json` contains `debug-large`, either via the debug panel or directly via `run-as` file writes.
2. Build + install + launch, then read logs:
   ```bash
   ./gradlew :androidApp:assembleDebug
   adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
   adb logcat -c
   adb shell am force-stop co.delalande.prompter; adb shell am start -n co.delalande.prompter/.MainActivity
   sleep 10 && adb logcat -d 2>/dev/null | grep "TeleprompterVM"
   ```
3. `adb shell pidof co.delalande.prompter` confirms the app is running.

### Locked-device facts

- A PIN-locked device **cannot** be unlocked via adb (not rooted). `wm dismiss-keyguard`, `input keyevent 82`, swipe gestures, and `svc power stayon true` do **not** bypass a secure PIN. Get the user to unlock, or verify non-visual logic headlessly.
- **Logcat works even under the keyguard**, and recomposition triggered by state writes still happens — window/layout/state logic IS verifiable while locked.
- **Compose animations do NOT run behind the keyguard / while the screen sleeps** (no vsync frames → `Animatable.animateTo` freezes at its last value while `target` keeps changing). This is a **test artifact, not a bug** — verify by inspection or with the user watching visible mode.
- `adb shell run-as co.delalande.prompter cat files/scripts.json` inspects/edits app-private storage on debuggable builds; write back via `adb shell "run-as co.delalande.prompter sh -c 'cat > files/scripts.json'" < localfile`.

### Cleanup after device debugging (mandatory)

- Remove injected scripts (`debug-large`) from the device's `files/scripts.json` via `run-as` — never `pm clear` (wipes the user's real scripts). The debug panel's **Remove debug scripts** button also works.
- If you injected anything manually (not via the debug panel), delete it before handing back. The debug panel itself is permanent and stripped from release builds.

### Reading the screen without image input

```bash
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb shell cat /sdcard/ui.xml | tr '>' '>\n' | grep -oE '(text|content-desc)="[^"]+"' | sort -u
```

On the lock screen this shows the keyguard widgets — a signal the device is locked and visual/interaction testing needs the user.

## When Adding New Features

1. Add interface in `commonMain` if cross-platform
2. Add `expect` declaration if using platform APIs
3. Add `actual` in `androidMain` + `iosMain`
4. Add factory parameter to `App.kt`
5. Wire in `MainActivity.kt` / `MainViewController.kt`
6. Run both compilation targets before committing
