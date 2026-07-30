package com.teleprompter

import androidx.compose.ui.window.ComposeUIViewController
import com.teleprompter.data.PlatformStorage
import com.teleprompter.audio.IOSAudioRecorder
import com.teleprompter.recording.IOSCameraRecorder
import com.teleprompter.speech.IOSSpeechRecognizer
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        App(
            platformStorage = PlatformStorage(),
            speechRecognizerFactory = { IOSSpeechRecognizer() },
            cameraRecorderFactory = { IOSCameraRecorder() },
            audioRecorderFactory = { IOSAudioRecorder() },
            onSaveToPhotos = { /* iOS — save to photo library */ },
            onShare = { filePath -> shareOnIOS(filePath) },
            onDeleteFile = { filePath -> /* iOS — delete file */ }
        )
    }
}

private fun shareOnIOS(filePath: String) {
    // iOS sharing would be implemented using UIActivityViewController
    // This requires the current UIViewController context
}
