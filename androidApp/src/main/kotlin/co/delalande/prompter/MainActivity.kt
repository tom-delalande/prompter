package co.delalande.prompter

import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.delalande.prompter.billing.AndroidBillingManager
import co.delalande.prompter.data.PlatformStorage
import co.delalande.prompter.recording.AndroidCameraRecorder
import co.delalande.prompter.speech.MLKitSpeechRecognizer
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            App(
                platformStorage = PlatformStorage(this),
                speechRecognizerFactory = { MLKitSpeechRecognizer(this@MainActivity) },
                cameraRecorderFactory = { AndroidCameraRecorder(this) },
                billingManagerFactory = {
                    AndroidBillingManager(this@MainActivity, PlatformStorage(this@MainActivity))
                },
                onSaveToPhotos = { filePath -> saveToPhotos(filePath) }
            )
        }
    }

    private fun saveToPhotos(filePath: String) {
        println("SAVE: saveToPhotos called with filePath=$filePath")
        val file = File(filePath)
        if (!file.exists()) {
            println("SAVE: file does NOT exist at $filePath")
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        println("SAVE: file exists, length=${file.length()}")

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Teleprompter")
        }

        println("SAVE: inserting into MediaStore with DISPLAY_NAME=${file.name}")
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            println("SAVE: got uri=$uri")
            try {
                val outputStream = contentResolver.openOutputStream(uri)
                println("SAVE: outputStream=$outputStream")
                outputStream?.use { output ->
                    file.inputStream().use { input ->
                        val bytesCopied = input.copyTo(output)
                        println("SAVE: copied $bytesCopied bytes")
                    }
                }
                Toast.makeText(this, "Saved to Photos", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                println("SAVE: exception: ${e.message}")
                contentResolver.delete(uri, null, null)
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            println("SAVE: insert returned null")
            Toast.makeText(this, "Failed to save to Photos", Toast.LENGTH_SHORT).show()
        }
    }


}
