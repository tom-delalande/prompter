# Teleprompter — Agent Guide

## Project Overview

Kotlin Compose Multiplatform teleprompter app for Android & iOS. Records video + audio while scrolling a script. Uses **Voice Activity Detection (VAD)** for on-device speech-driven auto-scroll (no API keys, no network).

## Tech Stack

| Component | Version |
|---|---|
| Kotlin | 2.3.20 |
| Compose Multiplatform | 1.11.0 |
| AGP | 9.3.0 |
| Gradle | 9.5.1 (use `gradlew`, not system Gradle) |
| Min SDK | 26 (Android 8.0) |
| Target / Compile SDK | 35 |

### Key Libraries
- **UI**: Compose Multiplatform (Runtime, Foundation, Material3, UI)
- **Serialization**: `kotlinx.serialization.json` 1.8.1
- **Camera (Android)**: CameraX 1.5.0 (camera2, lifecycle, video, view)
- **Lifecycle (Android)**: `lifecycle-runtime-compose` 2.9.0
- **Activity (Android)**: `activity-compose` 1.10.1

## Project Structure

```
teleprompter/
├── composeApp/                          # KMP shared library
│   └── src/
│       ├── commonMain/kotlin/co/delalande/prompter/
│       │   ├── App.kt                   # Root composable, screen navigation
│       │   ├── data/
│       │   │   ├── PlatformStorage.kt   # expect file I/O interface
│       │   │   └── ScriptRepository.kt  # JSON-backed script persistence
│       │   ├── model/Script.kt          # Script data class
│       │   ├── recording/CameraRecorder.kt  # expect camera interface
│       │   ├── speech/
│       │   │   ├── SpeechRecognizer.kt  # SpeechRecognizer + SpeechListener interfaces
│       │   │   └── SpeechMatcher.kt     # Word-by-word matching logic
│       │   ├── ui/
│       │   │   ├── screen/
│       │   │   │   ├── ScriptListScreen.kt    # Script list with swipe-to-delete
│       │   │   │   ├── ScriptEditorScreen.kt  # Editor with auto-save
│       │   │   │   └── TeleprompterScreen.kt  # Camera + text overlay + controls
│       │   │   └── theme/Theme.kt      # Dark theme only
│       │   └── viewmodel/TeleprompterViewModel.kt  # All recording/scroll logic
│       │
│       ├── androidMain/kotlin/co/delalande/prompter/
│       │   ├── data/PlatformStorage.android.kt   # Android file I/O
│       │   ├── recording/
│       │   │   ├── CameraPreview.android.kt      # CameraX preview + permissions
│       │   │   └── CameraRecorder.android.kt     # CameraX video+audio recording
│       │   ├── speech/
│       │   │   ├── AudioInput.kt               # AudioDevice helpers (external mic routing, RMS→level)
│       │   │   └── MLKitSpeechRecognizer.kt    # ML Kit voice recognition (sole Android recognizer)
│       │   └── ui/screen/PlatformActual.kt     # PlatformCompositionContext
│       │
│       └── iosMain/kotlin/co/delalande/prompter/
│           ├── data/PlatformStorage.ios.kt   # iOS file I/O
│           ├── recording/CameraRecorder.ios.kt  # AVFoundation recorder
│           ├── speech/SpeechRecognizer.ios.kt   # SFSpeechRecognizer
│           ├── ui/screen/CameraPreview.ios.kt   # UIKitView camera preview
│           └── MainViewController.kt            # iOS entry point
│
├── androidApp/                          # Pure Android shell
│   └── src/main/
│       ├── AndroidManifest.xml          # Portrait+sensor, camera+audio+internet perms
│       └── kotlin/co/delalande/prompter/
│           └── MainActivity.kt          # Creates Android implementations, handles share
│
├── iosApp/                              # iOS shell (Xcode project)
│   └── iosApp/Info.plist                # Orientation + camera/mic/speech usage strings
│
├── gradle/libs.versions.toml            # Version catalog
└── build.gradle.kts                     # Root build
```

## Architecture & Conventions

### Pattern: expect/actual with factory functions
- Common interfaces defined in `commonMain` (`SpeechRecognizer`, `CameraRecorder`, `PlatformStorage`, etc.)
- `Android*` and `IOS*` implementations in platform source sets
- Platform instances created via factory lambdas in `App.kt` (passed from `MainActivity.kt` / `MainViewController.kt`)
- `App.kt` receives `speechRecognizerFactory: () -> SpeechRecognizer`, `cameraRecorderFactory: () -> CameraRecorder`

### Navigation
- Enum-based `Screen` (ScriptList, ScriptEditor, Teleprompter) in `App.kt`
- `TeleprompterViewModel` created fresh each time user enters teleprompter screen

### TeleprompterViewModel
- Holds all mutable state as Compose `mutableStateOf` properties (for reactivity)
- Manages: speech recognizer, camera recorder
- Two parallel recording paths (Android only):
  1. **MLKitSpeechRecognizer**: AudioRecord → speech recognition → advances `currentWordIndex`
  2. **AndroidCameraRecorder**: CameraX record video + audio (`.withAudioEnabled()` produces a single MP4)
- On camera finish: `onRecordingFinished(filePath)` sets `lastRecordedFile` directly (no merge needed)
- `onVoiceActivityChanged`, `onAudioLevelChanged`, and `onExternalMicrophoneChanged` have default empty implementations in `SpeechListener` so iOS doesn't need to implement them

### Speech Recognition
- **Android**: `MLKitSpeechRecognizer` — on-device speech recognition via `com.google.mlkit:genai-speech-recognition`. Advances the script by matching partial transcription against the script words.
- **iOS**: `SFSpeechRecognizer` via `SpeechRecognizer.ios.kt`
- `SpeechMatcher.kt` holds the word-matching logic used by both platforms.

### Compiler Flags
- `-Xexpect-actual-classes` added in `composeApp/build.gradle.kts` (suppresses Beta warning for expect/actual classes)

### Platform-Specific Notes
- **`NSObject`**: import from `platform.darwin.NSObject` (not `platform.NSObject` or `kotlin.native`)
- **`AVCaptureVideoPreviewLayer`**: use named parameter `sessionWithNoConnection` to disambiguate constructors
- **`AVAudioEngine`**: in `platform.AVFAudio.*` on iOS
- **`UIKitView`**: use `androidx.compose.ui.viewinterop.UIKitView` (not the deprecated `androidx.compose.ui.interop`)

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

# iOS (via Xcode/AppCode)
# Open iosApp/iosApp.xcodeproj, select simulator, run

# Check both targets at once
./gradlew :composeApp:compileKotlinIosArm64 :androidApp:assembleDebug
```

## Known Issues & Gotchas

1. **AudioRecord conflicts**: `MLKitSpeechRecognizer`'s `AudioRecord` + CameraX `withAudioEnabled()` both use the microphone simultaneously. Works on Android 10+ (shared mic mode).

2. **No coroutines library dependency**: `kotlinx.coroutines` is a transitive dependency from Compose Multiplatform but not declared in `libs.versions.toml`. To use coroutines explicitly in commonMain, add `implementation(kotlinx.coroutines.core)` to dependencies.

## Debugging on the Physical Android Device (preferred workflow)

Debug against the attached physical device (`adb devices`, serial `53181JEBF20037`) rather than reasoning in the abstract.

### Logging infrastructure

Use `co.delalande.prompter.log.Log` (a Napier-backed facade) in **commonMain and all source sets** — never raw `println` or `android.util.Log`:
- `Log.d/i` (lazy `{ "..." }` lambdas) for verbose state, `Log.w/e` for warnings/errors.
- `Log.verboseEnabled` (persisted via `DebugRepository` → `files/debug.json`) gates d/i/v at runtime in debug builds.
- Release builds strip all d/i/v call sites via R8 `assumenosideeffects` in `androidApp/proguard-rules.pro`; w/e remain.
- In logcat, tags appear as `MLKitSpeech`, `App`, `TeleprompterVM`, `Settings`, `DebugRepo`. Grep logcat with the relevant tag.

### The built-in debug panel (use this instead of injecting temporary code)

A debug panel lives at the bottom of **Settings** and is visible only on debug builds (`BuildConfig.DEBUG`). It exposes:
- **Verbose logging** toggle (flips `Log.verboseEnabled`, persisted).
- **Auto-advance words** toggle — simulates speech without a mic by auto-scrolling (`seekTo` every 150ms) in the teleprompter; shows a "DEBUG AUTO-SCROLL" pill.
- **Inject 1000+ word test script** (creates `id="debug-large"` in `scripts.json`) and **Remove debug scripts**.

So the old "add a `LaunchedEffect` to App.kt/TeleprompterScreen.kt" harness is now a Settings toggle — no source edits needed. Key state transitions are already logged from `TeleprompterViewModel` (`startPlayback`, `startRecording`, `seekTo`, speech advances).

When you need to observe app behavior but can't tap the screen (locked device, or you can't see the screen):

1. Ensure `debug.json` has `{"autoAdvanceWords": true}` and `scripts.json` contains `debug-large`, either via the debug panel (if the screen is unlocked) or directly via `run-as` file writes.
2. Build + install + launch, then read logs:
   ```bash
   ./gradlew :androidApp:assembleDebug
   adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
   adb logcat -c
   adb shell am force-stop co.delalande.prompter; adb shell am start -n co.delalande.prompter/.MainActivity
   sleep 10 && adb logcat -d 2>/dev/null | grep "TeleprompterVM"
   ```
3. `adb shell pidof co.delalande.prompter` confirms the app is running.

### Locked-device facts learned the hard way

- A PIN-locked device **cannot** be unlocked via adb (not rooted). `wm dismiss-keyguard`, `input keyevent 82`, swipe gestures, and `svc power stayon true` do **not** bypass a secure PIN. You'll need the user to unlock; or verify non-visual logic headlessly (see below).
- **Logcat works even under the keyguard**, and recomposition triggered by state writes still happens — so window/layout/state logic IS verifiable while locked. Use the harness + logcat for that.
- **Compose animations DO NOT run behind the keyguard / while the screen sleeps** (no vsync frames → `Animatable.animateTo` freezes at its last value while `target` keeps changing). This is a **test artifact**, not a bug. To verify animation logic you must either reason by inspection or have the user unlock and run visible mode, then confirm `anim == target` in the logs.
- `adb shell run-as co.delalande.prompter cat files/scripts.json` works on debuggable builds — use it to inspect/edit app-private storage (e.g. to delete a `debug-large` script you injected). Push back via `adb shell "run-as co.delalande.prompter sh -c 'cat > files/scripts.json'" < localfile`.

### Cleanup after device debugging (mandatory)

Restore device data before handing back:
- Remove injected scripts (`debug-large`) from the device's `files/scripts.json` via `run-as` — never `pm clear` (wipes the user's real scripts). You can also use the debug panel's **Remove debug scripts** button.
- If you injected anything manually (not via the debug panel), delete it before handing back. No source cleanup needed for the built-in debug panel — it's permanent and stripped from release builds.

### Reading the screen without image input

If you can't view screenshots, use the UI hierarchy:
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
