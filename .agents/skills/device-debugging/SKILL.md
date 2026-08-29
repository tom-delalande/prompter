---
name: device-debugging
description: Debug the teleprompter Kotlin/Compose Multiplatform Android app on a physical device via adb and logcat. Use whenever there is a UI or runtime bug to diagnose on the attached physical Android device (serial 53181JEBF20037), especially when the device is PIN-locked or you cannot see or tap the screen. Covers the self-driving harness pattern (injecting debug auto-navigation code), reading logcat for println output, verifying state via uiautomator hierarchy, inspecting app-private storage with run-as, and mandatory cleanup of debug code. Reach for this whenever debugging teleprompter app behavior instead of reasoning about the code in the abstract.
---

# Debugging the Teleprompter on a Physical Android Device

Debug against the attached physical device rather than reasoning in the abstract. `println(...)` in Kotlin lands in logcat as `System.out` — this is the primary signal channel.

## Prerequisites

- Physical device attached via adb: `adb devices` (known good serial: `53181JEBF20037`; check `adb devices` first since serials change).
- The app is debuggable: build with `:androidApp:assembleDebug`.

## The self-driving harness pattern (use this by default for UI/runtime bugs)

When you need to observe app behavior but can't tap the screen (PIN-locked device, or you can't see the screen), make the app **drive itself** with temporary debug-only code in `commonMain`:

1. In `App.kt`: add a `LaunchedEffect(Unit)` that loads/creates a large script (e.g. `id="debug-large"`, 1000+ words) and auto-navigates to `Screen.Teleprompter`.
2. In `TeleprompterScreen.kt`: add a `LaunchedEffect(Unit)` that auto-advances state to simulate speech without a mic, e.g.:
   ```kotlin
   while(true) { delay(120); viewModel.seekTo(viewModel.currentWordIndex + 1) }
   ```
3. Log key state transitions with a unique tag:
   ```kotlin
   println("MYTAG: idx=… start=… target=… anim=…")
   ```

## Build, install, launch, and read logs

```bash
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb logcat -c
adb shell am force-stop co.delalande.prompter.debug; adb shell am start -n co.delalande.prompter.debug/.MainActivity
sleep 10 && adb logcat -d 2>/dev/null | grep "MYTAG"
```

- `adb shell pidof co.delalande.prompter.debug` confirms the app is running.

## Locked-device facts (learned the hard way)

- A PIN-locked device **cannot** be unlocked via adb (not rooted). `wm dismiss-keyguard`, `input keyevent 82`, swipe gestures, and `svc power stayon true` do **not** bypass a secure PIN. You'll need the user to unlock; or verify non-visual logic headlessly.
- **Logcat works even under the keyguard**, and recomposition triggered by state writes still happens — so window/layout/state logic IS verifiable while locked. Use the harness + logcat for that.
- **Compose animations DO NOT run behind the keyguard / while the screen sleeps** (no vsync frames → `Animatable.animateTo` freezes at its last value while `target` keeps changing). This is a **test artifact, not a bug**. To verify animation logic you must either reason by inspection or have the user unlock and run visible mode, then confirm `anim == target` in the logs.

## Reading the screen without image input

If you can't view screenshots, use the UI hierarchy:

```bash
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb shell cat /sdcard/ui.xml | tr '>' '>\n' | grep -oE '(text|content-desc)="[^"]+"' | sort -u
```

On the lock screen this shows the keyguard widgets — a signal the device is locked and visual/interaction testing needs the user.

## Inspecting app-private storage

`adb shell run-as co.delalande.prompter.debug cat files/scripts.json` works on debuggable builds — use it to inspect/edit app-private storage (e.g. to delete a `debug-large` script you injected). Push back via:

```bash
adb shell "run-as co.delalande.prompter.debug sh -c 'cat > files/scripts.json'" < localfile
```

## Cleanup after device debugging (mandatory)

Always remove the temporary harness and fix device data before handing back:

- Delete the debug `LaunchedEffect`s, `println` logging, and any auto-navigate code.
- Remove injected scripts (`debug-large`) from the device's `files/scripts.json` via `run-as` — never `pm clear` (wipes the user's real scripts).
- Rebuild + reinstall the clean APK, re-run both compilation targets, and verify no debug tags remain:
  ```bash
  grep -rn "WINDOWDBG\|TEMP DEBUG" composeApp/
  ```

## Verify both compilation targets after changes

```bash
./gradlew :androidApp:assembleDebug                 # Android
./gradlew :composeApp:compileKotlinIosArm64          # iOS device
./gradlew :composeApp:compileKotlinIosSimulatorArm64 # iOS simulator
# Both at once:
./gradlew :composeApp:compileKotlinIosArm64 :androidApp:assembleDebug
```
