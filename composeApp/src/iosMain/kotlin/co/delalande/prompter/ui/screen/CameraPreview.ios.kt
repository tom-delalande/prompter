package co.delalande.prompter.ui.screen

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import co.delalande.prompter.recording.CameraRecorder
import co.delalande.prompter.recording.IOSCameraRecorder
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(
    cameraRecorder: CameraRecorder,
    modifier: Modifier
) {
    val iosRecorder = cameraRecorder as? IOSCameraRecorder

    LaunchedEffect(Unit) {
        iosRecorder?.setUp()
    }

    UIKitView(
        factory = {
            iosRecorder?.previewView ?: UIView()
        },
        modifier = modifier,
        properties = UIKitInteropProperties()
    )
}
