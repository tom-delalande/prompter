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
│       │   ├── audio/AudioRecorder.kt   # AudioRecorder + AudioVideoMuxer interfaces
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
│       │   ├── audio/AudioRecorder.android.kt    # AudioRecord → WAV + MediaMuxer merge
│       │   ├── data/PlatformStorage.android.kt   # Android file I/O
│       │   ├── recording/
│       │   │   ├── CameraPreview.android.kt      # CameraX preview + permissions
│       │   │   └── CameraRecorder.android.kt     # CameraX video recording
│       │   ├── speech/
│       │   │   ├── SpeechRecognizer.android.kt   # Android SpeechRecognizer (fallback)
│       │   │   ├── VoiceActivityRecognizer.kt    # PRIMARY: RMS-based VAD
│       │   │   └── WhisperSpeechRecognizer.kt    # OpenAI Whisper API (unused)
│       │   └── ui/screen/PlatformActual.kt       # PlatformCompositionContext
│       │
│       └── iosMain/kotlin/com/teleprompter/
│           ├── audio/AudioRecorder.ios.kt    # AudioRecorder stub
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
- Common interfaces defined in `commonMain` (`SpeechRecognizer`, `CameraRecorder`, `AudioRecorder`, `PlatformStorage`, etc.)
- `Android*` and `IOS*` implementations in platform source sets
- Platform instances created via factory lambdas in `App.kt` (passed from `MainActivity.kt` / `MainViewController.kt`)
- `App.kt` receives `speechRecognizerFactory: () -> SpeechRecognizer`, `cameraRecorderFactory: () -> CameraRecorder`, `audioRecorderFactory: () -> AudioRecorder`

### Navigation
- Enum-based `Screen` (ScriptList, ScriptEditor, Teleprompter) in `App.kt`
- `TeleprompterViewModel` created fresh each time user enters teleprompter screen

### TeleprompterViewModel
- Holds all mutable state as Compose `mutableStateOf` properties (for reactivity)
- Manages: speech recognizer, camera recorder, audio recorder, audio video muxer
- Three parallel recording paths (Android only):
  1. **VoiceActivityRecognizer**: AudioRecord → RMS energy → `onVoiceActivityChanged(true/false)` → advances `currentWordIndex` at 400ms/word via coroutine
  2. **AndroidCameraRecorder**: CameraX record video (NO audio from camera)
  3. **AndroidAudioRecorder**: Separate AudioRecord → WAV file (for video muxing)
- On stop: camera + audio listeners each set their `finished` flag → when both done, `tryMerge()` runs `AudioVideoMuxer.merge()` → produces `_with_audio.mp4`
- `onVoiceActivityChanged` is added with a default empty implementation in `SpeechListener` so iOS doesn't need to implement it

### Speech Recognition
- **Android (PRIMARY)**: `VoiceActivityRecognizer` — entirely on-device VAD via RMS energy threshold. No speech-to-text, just voice/silence detection. Advances script at fixed pace while speaking.
- **Android (fallback)**: `AndroidSpeechRecognizer` — Google's cloud SpeechRecognizer API. Known to fail on Android 12+ with `ERROR_CLIENT` (bug #228221293, default recognizer routes through broken "Android System Intelligence").
- **iOS**: `SFSpeechRecognizer` via `SpeechRecognizer.ios.kt`
- `SpeechMatcher.kt` has word-matching logic but is NOT actively used on Android (only iOS). Kept for potential future use.

### Camera/Recording Flow (Android)
1. User presses Record → `startPlayback()` starts VAD, then `toggleRecording()` starts camera + audio recorders
2. CameraX captures **video only** (no audio via `.withAudioEnabled()`)
3. `AndroidAudioRecorder` records raw PCM at 44100 Hz → writes WAV file
4. `VoiceActivityRecognizer` records raw PCM at 16000 Hz → detects voice activity
5. After stop: `AndroidAudioVideoMuxer.merge()` encodes WAV → AAC (MediaCodec) → muxes with video (MediaMuxer)
6. Final file: `*_with_audio.mp4`

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
./gradlew :composeApp:compileKotlinAndroid

# Check iOS compilation
./gradlew :composeApp:compileKotlinIosArm64
./gradlew :composeApp:compileKotlinIosSimulatorArm64

# Check both at once
./gradlew :composeApp:compileKotlinIosArm64 :androidApp:assembleDebug
```

## Known Issues & Gotchas

1. **AudioRecord conflicts**: `VoiceActivityRecognizer` + `AndroidAudioRecorder` both use `AudioRecord` simultaneously. Works on Android 10+ (shared mic mode). If VAD is unreliable, combine into a single AudioRecord that provides both voice activity detection and audio file saving.

2. **Android SpeechRecognizer ERROR_CLIENT on Android 12+**: The default recognizer routes through "Android System Intelligence" which is broken for third-party apps. Fix: go to Settings → Apps → Default apps → Digital assistant app → Voice input → select "Speech Services by Google". Or use `VoiceActivityRecognizer`.

3. **AudioVideoMuxer**: The AAC encoder + MediaMuxer merging is fragile. If `merge()` fails, the `_with_audio.mp4` won't be created but the original video file is returned as fallback.

4. **`MediaCodec.inputBuffers` / `outputBuffers`**: These are deprecated in newer Android but still work. The new API uses `getInputBuffer(int)` / `getOutputBuffer(int)`.

5. **No coroutines library dependency**: `kotlinx.coroutines` is a transitive dependency from Compose Multiplatform but not declared in `libs.versions.toml`. To use coroutines explicitly in commonMain, add `implementation(kotlinx.coroutines.core)` to dependencies.

6. **iOS audio recording + muxing**: Not implemented (stub returns error). `MainViewController.kt` passes `IOSAudioRecorder()` which reports "not implemented".

## When Adding New Features

1. Add interface in `commonMain` if cross-platform
2. Add `expect` declaration if using platform APIs
3. Add `actual` in `androidMain` + `iosMain`
4. Add factory parameter to `App.kt` 
5. Wire in `MainActivity.kt` / `MainViewController.kt`
6. Run both compilation targets before committing
