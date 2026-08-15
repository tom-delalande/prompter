package co.delalande.prompter

import androidx.compose.ui.window.ComposeUIViewController
import co.delalande.prompter.billing.IOSBillingManager
import co.delalande.prompter.data.PlatformStorage
import co.delalande.prompter.log.initLogging
import co.delalande.prompter.recording.IOSCameraRecorder
import co.delalande.prompter.speech.IOSSpeechRecognizer
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val versionName =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "1.0"
    val buildInfo = "v$versionName · ${BuildInfo.GIT_COMMIT}"
    return ComposeUIViewController {
        App(
            platformStorage = PlatformStorage(),
            isDebug = false,
            speechRecognizerFactory = { IOSSpeechRecognizer() },
            cameraRecorderFactory = { IOSCameraRecorder() },
            billingManagerFactory = { IOSBillingManager(PlatformStorage()) },
            buildInfo = buildInfo,
            onSaveToPhotos = { _, _ -> /* iOS — save to photo library */ },
            onOpenVideo = { _ -> /* iOS — open video */ }
        )
    }
}
