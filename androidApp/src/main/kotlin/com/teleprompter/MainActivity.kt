package com.teleprompter

import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.teleprompter.audio.AndroidAudioRecorder
import com.teleprompter.data.PlatformStorage
import com.teleprompter.recording.AndroidCameraRecorder
import com.teleprompter.speech.MLKitSpeechRecognizer
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: filesDir

        setContent {
            App(
                platformStorage = PlatformStorage(this),
                speechRecognizerFactory = { MLKitSpeechRecognizer(this@MainActivity) },
                cameraRecorderFactory = { AndroidCameraRecorder(this) },
                audioRecorderFactory = { AndroidAudioRecorder(outputDir) },
                onDownloadFile = { filePath -> downloadVideo(filePath) }
            )
        }
    }

    private fun downloadVideo(filePath: String) {
        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val destFile = File(downloadsDir, sourceFile.name)
        try {
            sourceFile.copyTo(destFile, overwrite = true)
            Toast.makeText(this, "Saved to Downloads/${sourceFile.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
