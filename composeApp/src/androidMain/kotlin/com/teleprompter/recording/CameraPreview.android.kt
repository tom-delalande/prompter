package com.teleprompter.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    var permissionsGranted by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        permissionsGranted = granted.values.all { it }
    }

    LaunchedEffect(Unit) {
        launcher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    LaunchedEffect(lifecycleOwner, permissionsGranted) {
        if (permissionsGranted) {
            androidRecorder.setUp(lifecycleOwner)
        }
    }

    AndroidView(
        factory = { androidRecorder.previewView },
        modifier = modifier
    )
}
