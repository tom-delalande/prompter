package co.delalande.prompter

import androidx.compose.ui.window.ComposeUIViewController
import co.delalande.prompter.billing.IOSBillingManager
import co.delalande.prompter.data.PlatformStorage
import co.delalande.prompter.log.Log
import co.delalande.prompter.model.SaveResult
import co.delalande.prompter.recording.IOSCameraRecorder
import co.delalande.prompter.speech.IOSSpeechRecognizer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Photos.*
import platform.UIKit.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val TAG = "iOSSave"

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
            onSaveToPhotos = { filePath, onResult -> saveVideoToPhotoLibrary(filePath, onResult) },
            onOpenVideo = { openInPhotos() }
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun saveVideoToPhotoLibrary(filePath: String, onResult: (SaveResult) -> Unit) {
    val url = NSURL.fileURLWithPath(filePath)

    fun performSave() {
        PHPhotoLibrary.sharedPhotoLibrary().performChanges(
            changeBlock = { PHAssetCreationRequest.creationRequestForAssetFromVideoAtFileURL(url) }
        ) { success, error ->
            runOnMain {
                if (success) {
                    Log.i(TAG) { "Saved video to Photos: $filePath" }
                    onResult(SaveResult(uri = url.path))
                } else {
                    Log.e(TAG) { "Failed to save video: ${error?.localizedDescription}" }
                    onResult(SaveResult(error = error?.localizedDescription ?: "Unknown error"))
                }
            }
        }
    }

    when (PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelAddOnly)) {
        PHAuthorizationStatusAuthorized, PHAuthorizationStatusLimited -> performSave()
        PHAuthorizationStatusNotDetermined -> {
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
                runOnMain {
                    if (status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited) {
                        performSave()
                    } else {
                        Log.w(TAG) { "Photo library permission denied" }
                        onResult(SaveResult(error = "Photo library access denied"))
                    }
                }
            }
        }
        else -> {
            Log.w(TAG) { "Photo library access denied" }
            onResult(SaveResult(error = "Photo library access denied"))
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun openInPhotos() {
    val url = NSURL.URLWithString("photos-redirect://") ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
}

@OptIn(ExperimentalForeignApi::class)
private fun runOnMain(block: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) { block() }
}
