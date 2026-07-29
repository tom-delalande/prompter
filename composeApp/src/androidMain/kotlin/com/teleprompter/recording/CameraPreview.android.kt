package com.teleprompter.ui.screen

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.teleprompter.recording.AndroidCameraRecorder
import com.teleprompter.recording.CameraRecorder

@Composable
actual fun CameraPreview(
    cameraRecorder: CameraRecorder,
    modifier: Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val androidRecorder = cameraRecorder as? AndroidCameraRecorder
    if (androidRecorder == null) return

    LaunchedEffect(lifecycleOwner) {
        androidRecorder.setUp(lifecycleOwner)
    }

    AndroidView(
        factory = { androidRecorder.previewView },
        modifier = modifier
    )
}
