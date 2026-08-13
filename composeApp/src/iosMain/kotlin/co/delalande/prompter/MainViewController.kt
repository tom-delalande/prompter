package co.delalande.prompter

import androidx.compose.ui.window.ComposeUIViewController
import co.delalande.prompter.billing.IOSBillingManager
import co.delalande.prompter.data.PlatformStorage
import co.delalande.prompter.recording.IOSCameraRecorder
import co.delalande.prompter.speech.IOSSpeechRecognizer
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        App(
            platformStorage = PlatformStorage(),
            speechRecognizerFactory = { IOSSpeechRecognizer() },
            cameraRecorderFactory = { IOSCameraRecorder() },
            billingManagerFactory = { IOSBillingManager(PlatformStorage()) },
            onSaveToPhotos = { _, _ -> /* iOS — save to photo library */ },
            onOpenVideo = { _ -> /* iOS — open video */ }
        )
    }
}
