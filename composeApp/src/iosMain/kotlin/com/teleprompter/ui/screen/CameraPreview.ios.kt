package com.teleprompter.ui.screen

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.teleprompter.recording.CameraRecorder
import com.teleprompter.recording.IOSCameraRecorder
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
