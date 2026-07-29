package com.teleprompter

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import com.teleprompter.data.PlatformStorage
import com.teleprompter.recording.AndroidCameraRecorder
import com.teleprompter.speech.AndroidSpeechRecognizer
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            App(
                platformStorage = PlatformStorage(this),
                speechRecognizerFactory = { AndroidSpeechRecognizer(this) },
                cameraRecorderFactory = { AndroidCameraRecorder(this) },
                onShareFile = { filePath -> shareVideo(filePath) }
            )
        }
    }

    private fun shareVideo(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share Recording"))
    }
}
