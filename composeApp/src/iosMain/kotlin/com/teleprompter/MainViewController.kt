package com.teleprompter

import androidx.compose.ui.window.ComposeUIViewController
import com.teleprompter.billing.IOSBillingManager
import com.teleprompter.data.PlatformStorage
import com.teleprompter.recording.IOSCameraRecorder
import com.teleprompter.speech.IOSSpeechRecognizer
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        App(
            platformStorage = PlatformStorage(),
            speechRecognizerFactory = { IOSSpeechRecognizer() },
            cameraRecorderFactory = { IOSCameraRecorder() },
            billingManagerFactory = { IOSBillingManager(PlatformStorage()) },
            onSaveToPhotos = { /* iOS — save to photo library */ }
        )
    }
}
