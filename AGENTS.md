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
│       ├── commonMain/kotlin/com/teleprompter/
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
│       ├── androidMain/kotlin/com/teleprompter/
│       │   ├── data/PlatformStorage.android.kt   # Android file I/O
│       │   ├── recording/
│       │   │   ├── CameraPreview.android.kt      # CameraX preview + permissions
│       │   │   └── CameraRecorder.android.kt     # CameraX video+audio recording
│       │   ├── speech/
│       │   │   ├── MLKitSpeechRecognizer.kt      # PRIMARY: ML Kit voice recognition
│       │   │   ├── VoiceActivityRecognizer.kt    # RMS-based VAD fallback
│       │   │   └── WhisperSpeechRecognizer.kt    # OpenAI Whisper API (unused)
│       │   └── ui/screen/PlatformActual.kt       # PlatformCompositionContext
│       │
│       └── iosMain/kotlin/com/teleprompter/
│           ├── data/PlatformStorage.ios.kt   # iOS file I/O
│           ├── recording/CameraRecorder.ios.kt  # AVFoundation recorder
│           ├── speech/SpeechRecognizer.ios.kt   # SFSpeechRecognizer
│           ├── ui/screen/CameraPreview.ios.kt   # UIKitView camera preview
│           └── MainViewController.kt            # iOS entry point
│
├── androidApp/                          # Pure Android shell
│   └── src/main/
│       ├── AndroidManifest.xml          # Portrait+sensor, camera+audio+internet perms
│       └── kotlin/com/teleprompter/
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
  1. **VoiceActivityRecognizer** (or ML Kit): AudioRecord → voice detection → advances `currentWordIndex`
  2. **AndroidCameraRecorder**: CameraX record video + audio (`.withAudioEnabled()` produces a single MP4)
- On camera finish: `onRecordingFinished(filePath)` sets `lastRecordedFile` directly (no merge needed)
- `onVoiceActivityChanged` and `onAudioRecorded` have default empty implementations in `SpeechListener` so iOS doesn't need to implement them

### Speech Recognition
- **Android (PRIMARY)**: `VoiceActivityRecognizer` — entirely on-device VAD via RMS energy threshold. No speech-to-text, just voice/silence detection. Advances script at fixed pace while speaking.
- **Android (fallback)**: `AndroidSpeechRecognizer` — Google's cloud SpeechRecognizer API. Known to fail on Android 12+ with `ERROR_CLIENT` (bug #228221293, default recognizer routes through broken "Android System Intelligence").
- **iOS**: `SFSpeechRecognizer` via `SpeechRecognizer.ios.kt`
- `SpeechMatcher.kt` has word-matching logic but is NOT actively used on Android (only iOS). Kept for potential future use.

### Camera/Recording Flow (Android)
1. User presses Record → `startPlayback()` starts VAD, then `toggleRecording()` starts camera recorder with audio
2. CameraX captures **video + audio** (`.withAudioEnabled()` produces a single MP4)
3. `VoiceActivityRecognizer` records raw PCM at 16000 Hz → detects voice activity
4. After stop: `cameraRecorder.stopRecording()` → `onRecordingFinished(filePath)` sets `lastRecordedFile` directly
5. Final file: single MP4 with synchronized audio+video from CameraX

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

# Check Android compilation
./gradlew :androidApp:assembleDebug

# Check iOS compilation
./gradlew :composeApp:compileKotlinIosArm64
./gradlew :composeApp:compileKotlinIosSimulatorArm64

# Check both at once
./gradlew :composeApp:compileKotlinIosArm64 :androidApp:assembleDebug
```

## Known Issues & Gotchas

1. **AudioRecord conflicts**: `VoiceActivityRecognizer` + CameraX `withAudioEnabled()` both use `AudioRecord` simultaneously. Works on Android 10+ (shared mic mode). If VAD is unreliable, combine into a single AudioRecord that provides both voice activity detection and audio file saving.

2. **Android SpeechRecognizer ERROR_CLIENT on Android 12+**: The default recognizer routes through "Android System Intelligence" which is broken for third-party apps. Fix: go to Settings → Apps → Default apps → Digital assistant app → Voice input → select "Speech Services by Google". Or use `VoiceActivityRecognizer`.

3. **No coroutines library dependency**: `kotlinx.coroutines` is a transitive dependency from Compose Multiplatform but not declared in `libs.versions.toml`. To use coroutines explicitly in commonMain, add `implementation(kotlinx.coroutines.core)` to dependencies.

## When Adding New Features

1. Add interface in `commonMain` if cross-platform
2. Add `expect` declaration if using platform APIs
3. Add `actual` in `androidMain` + `iosMain`
4. Add factory parameter to `App.kt` 
5. Wire in `MainActivity.kt` / `MainViewController.kt`
6. Run both compilation targets before committing
