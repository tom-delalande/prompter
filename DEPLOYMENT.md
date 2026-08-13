# Deploying Teleprompter to the App Store & Google Play

This guide walks through everything you need to do to ship **Prompter** (project name: Teleprompter) to both stores, starting from zero configuration. It's written against the current state of this repo, so it calls out the specific code changes and console setup steps that are missing.

## Current state of the project

| Item               | Android                                                                                                                    | iOS                                                         |
| ------------------ | -------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| App identifiers    | `co.delalande.prompter` (set)                                                                                              | Not set — no Xcode project exists yet                       |
| Versioning         | `versionCode 1`, `versionName "1.0"` (set)                                                                                 | `CFBundleShortVersionString 1.0`, `CFBundleVersion 1` (set) |
| Release signing    | **Missing** (falls back to debug keystore)                                                                                 | **Missing** (no team / certificates)                        |
| Xcode project      | n/a                                                                                                                        | **Missing from repo entirely**                              |
| App icons          | Present (mipmaps)                                                                                                          | Present (`AppIcon.appiconset`, incl. 1024px)                |
| Store screenshots  | Missing                                                                                                                    | Missing                                                     |
| Store metadata     | Partial (`fastlane/metadata/android`)                                                                                      | Missing                                                     |
| Privacy policy URL | **Missing (required)**                                                                                                     | **Missing (required)**                                      |
| In-app purchases   | Products `co.delalande.prompter.tip10` / `co.delalande.prompter.tip25` referenced in code, **not created in Play Console** | Same product IDs, **not created in App Store Connect**      |

> Two hard deadlines to keep in mind (today is 2026-07-31):
>
> - **App Store**: since **Apr 28, 2026**, builds must be made with **Xcode 26+** (iOS 26 SDK).

---

## 0. Things you need regardless of platform

- **Developer accounts**:
  - Google Play Console: $25 one-time.
  - Apple Developer Program: $99/year.
- **Privacy policy** hosted at a public URL (GitHub Pages, a simple site, etc.). Both stores require it because the app records video/audio, uses the mic, and does speech recognition.
- **Screenshots** (phone only, this is a phone app):
  - Play: minimum 2, recommended 8 (phone). Also a 512×512 icon and a 1024×500 feature graphic.
  - App Store: 6.7″ and 6.5″ screenshots at minimum.
- **Decide the display name** you'll ship as. The app currently brands itself **"Prompter"** (`strings.xml`, iOS `CFBundleDisplayName`).

---

## 1. Google Play (Android)

### 1.1 Account & first app

1. Go to [play.google.com/console](https://play.google.com/console) and sign up (pay the $25 fee, verify identity/bank details — takes a few days to review).
2. **Create app** → name it **Prompter**, language English (US), and enter your application ID: **`co.delalande.prompter`** (it must match `androidApp/build.gradle.kts` exactly).
3. You'll be guided through the "App content" checklist: data safety, ads, content rating, target audience, etc.

### 1.2 Code changes needed before you can publish

**b) Add release signing.** Without this, `assembleRelease`/`bundleRelease` is signed with the debug keystore and Play will reject it. Generate a keystore:

```bash
keytool -genkey -v \
  -keystore release.keystore \
  -alias teleprompter \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep the keystore out of the repo (add `release.keystore` to `.gitignore`) and store its passwords somewhere safe — you'll need them for every future release, and if you lose them you can't update the app.

Then wire it into `androidApp/build.gradle.kts` (use `keystore.properties` or env vars rather than hardcoding passwords):

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = "teleprompter"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

**c) Play App Signing:** when you upload your first AAB, opt into **Play App Signing** (recommended). Google manages the signing key and gives you the upload key. Your local keystore signs the upload; Google re-signs for distribution.

### 1.3 Build the release bundle

```bash
./gradlew :androidApp:bundleRelease
```

Output: `androidApp/build/outputs/bundle/release/androidApp-release.aab`. Upload this in **Play Console → Release → Production → Create release**.

### 1.4 Store listing (Play Console → Store presence)

- **Main listing**: title, short description (≤80 chars), full description (draft in `fastlane/metadata/android/en-US/`), 512×512 icon, feature graphic (1024×500), phone screenshots.
- **Contact details**: required email.
- **Privacy policy URL**: required — link to your hosted privacy policy.

### 1.5 Compliance forms (Play Console → App content)

- **App access**: "All functionality is available without login" → no.
- **Ads**: No.
- **Data safety**: disclose that you collect/process **audio/video recordings and microphone input** on-device; if you declare "collected/shared", you must link to the data-safety declarations in your privacy policy. The app is fully offline, so make that clear.
- **Content rating**: complete the questionnaire (generic "no objectionable content" answers; the app has camera/mic features but no mature content).
- **Target audience**: select children/teens/adults appropriately; if you select age groups including children, you may need a COPPA/child-safety declaration.
- **Government ID**: basic financial + identity verification is part of account setup.

### 1.6 In-app purchases (tips)

The code queries two consumable products (`BillingManager.kt:12-13`): **`co.delalande.prompter.tip10`** and **`co.delalande.prompter.tip25`**. In **Play Console → Monetize → Products → In-app products**, create both:

- Product ID must match exactly.
- Type: **Consumable** (the app treats them as one-time tips).
- Price, title, description.
- `AndroidBillingManager` connects lazily on the script list screen (`ScriptListScreen.kt:46-47`), so the products just need to be active and the app's billing status "ready" for the purchase button to work.

> Note: Google Play policy requires all digital goods to use Play Billing (they do). If you later gate any _feature_ behind a purchase instead of it being a donation-style tip, the tip semantics need to match the listing.

### 1.7 Testing → Production

Google now requires a **closed test** for new personal developer accounts before production:

1. Upload the AAB to an **Internal testing** track first, add yourself as a tester, install via the opt-in link, and smoke-test (record + voice scroll + tips).
2. Promote the same build to a **Closed testing** track and run the **closed-testing requirement**: at least **12 testers** opted in for **14 consecutive days** before you're allowed to release to production.
3. Once granted production access: **Production → Create release** → roll out (a staged rollout of e.g. 20% is wise for a first release).

---

## 2. App Store (iOS)

### 2.0 Important: there is no Xcode project in the repo

The repo contains only the iOS _sources_ (`iosApp/iosApp/` — `iOSApp.swift`, `ContentView.swift`, `Info.plist`, `Assets.xcassets`). There is **no `.xcodeproj`**, and `embedAndSignAppleFrameworkForXcode` is not wired up, so nothing can be archived for the App Store yet. You must create the Xcode project that:

1. **Builds the Kotlin framework**: add a **Run Script** build phase (placed _before_ Compile Sources) with:

   ```sh
   cd "$SRCROOT/.."
   ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
   ```

   (`$SRCROOT` here points into `iosApp/`, so the `..` resolves to the repo root.)

2. **Links `ComposeApp.framework`**: set Framework Search Paths to
   `$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`
   and add `ComposeApp.framework` to **Frameworks, Libraries, and Embedded Content**.
3. **Includes the existing files**: `iOSApp.swift`, `ContentView.swift`, `Info.plist`, `Assets.xcassets`.
4. Sets `PRODUCT_BUNDLE_IDENTIFIER` (e.g. `co.delalande.prompter` — must be globally unique and match what you register with Apple) and your development team for signing.

If you don't want to hand-assemble the project, [XcodeGen](https://github.com/yonaskolb/XcodeGen) or a `project.yml` committed to the repo is a cleaner route. Check the official guide: [Build a KMP app with the embedAndSignAppleFrameworkForXcode task](https://kotlinlang.org/docs/multiplatform-integrate-in-existing-app.html).

> Also fix `Info.plist` while you're in there: it references `UILaunchStoryboardName = LaunchScreen`, but no `LaunchScreen.storyboard` exists in the repo. Replace it with the empty `UILaunchScreen` dictionary (recommended) or add a real launch storyboard, or the build/archive will fail.
>
> You'll also need to bump the version for store submission — `CFBundleShortVersionString 1.0` / `CFBundleVersion 1` is fine to start, but it must be unique per uploaded build.

### 2.1 Account & App ID

1. Join the **Apple Developer Program** at [developer.apple.com](https://developer.apple.com).
2. In **Certificates, Identifiers & Profiles → Identifiers**, register an **App ID** (explicit bundle ID, e.g. `co.delalande.prompter`) and enable **Capabilities**: _Sign in with Apple_ is **not** required, but this app uses no special capabilities beyond what the Info.plist permissions cover (camera, mic, speech recognition usage strings are already present).

### 2.2 Create the Xcode project & set signing

Following 2.0, create the project, set:

- `PRODUCT_BUNDLE_IDENTIFIER` = your registered App ID.
- **Signing** = your developer team, **Automatically manage signing** (easiest for a solo dev).
- Minimum deployment target — pick something like iOS 15–16 (the Kotlin framework supports it; iOS 26 SDK requirement only affects the build SDK, not the min version).

Build for a simulator, then a device, and confirm recording + voice-driven scroll work before going further.

### 2.3 App Store Connect setup

1. Go to [appstoreconnect.apple.com](https://appstoreconnect.apple.com) → **My Apps → + → New App**.
2. Platform **iOS**, name **Prompter**, language, and the exact bundle ID from 2.1/2.2.
3. Fill in the **App Information** page: privacy policy URL, category, age rating, and the **App Privacy** questionnaire (same disclosures as Android — mic/audio/video captured on-device, not shared).

### 2.4 In-app purchases (tips)

Same two product IDs, **`co.delalande.prompter.tip10`** and **`co.delalande.prompter.tip25`**, in **App Store Connect → My Apps → App → Monetization → In-App Purchases → Consumable**:

- Reference name + product ID must match the code exactly.
- Set **price tiers** (App Store Connect uses price points, not raw amounts).
- Complete the **Paid Apps Agreement / banking & tax** in App Store Connect — required before any paid IAP can go live.

`IOSBillingManager` (`composeApp/src/iosMain/kotlin/co/delalande/prompter/billing/IOSBillingManager.kt`) mirrors the Android flow, so once the products exist and the agreement is signed, tips work as-is.

### 2.5 Archive & upload

1. In Xcode: set the scheme to **Any iOS Device (arm64)**, then **Product → Archive**.
2. **Distribute App → App Store Connect → Upload** (this runs `embedAndSignAppleFrameworkForXcode`, builds the Kotlin framework, and uploads). Xcode 26 required.
3. The build appears in **App Store Connect → TestFlight**.

### 2.6 TestFlight → Submit for review

1. In **TestFlight**, enable the build for **Internal Testing** and install it on your own devices. Speech recognition (`SFSpeechRecognizer`) works best on real hardware — test the auto-scroll and recording there.
2. For broader testing, set up **External Testing** (Apple reviews external builds).
3. When ready: **App Store Connect → App → Add for Review**. Fill the "What's New" notes, attach your screenshots (6.7″ + 6.5″), and submit. First review typically takes 1–3 days.

---

## 3. Keeping both stores in sync (release checklist)

Every release you'll touch:

- **Android** (`androidApp/build.gradle.kts`): bump `versionCode` (increment) and `versionName` (semver). Rebuild `bundleRelease`, upload to Play.
- **iOS** (`Info.plist` or Xcode project): bump `CFBundleVersion` (must be unique per upload) and `CFBundleShortVersionString`. Re-archive, upload, submit.
- Keep **product IDs, privacy policy, and feature parity** aligned between the two.
- Optional: set up Fastlane (the `fastlane/metadata/android` folder is a head start) once both stores are live, to automate metadata sync and uploads.
