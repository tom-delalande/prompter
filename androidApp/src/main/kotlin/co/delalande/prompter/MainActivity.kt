package co.delalande.prompter

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import co.delalande.prompter.billing.AndroidBillingManager
import co.delalande.prompter.data.PlatformStorage
import co.delalande.prompter.log.initLogging
import co.delalande.prompter.model.SaveResult
import co.delalande.prompter.recording.AndroidCameraRecorder
import co.delalande.prompter.speech.MLKitSpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initLogging()

        setContent {
            App(
                platformStorage = PlatformStorage(this@MainActivity),
                isDebug = BuildConfig.DEBUG,
                speechRecognizerFactory = { MLKitSpeechRecognizer(this@MainActivity) },
                cameraRecorderFactory = { AndroidCameraRecorder(this@MainActivity) },
                billingManagerFactory = {
                    AndroidBillingManager(this@MainActivity)
                },
                buildInfo = "v${BuildConfig.VERSION_NAME} · ${BuildInfo.GIT_COMMIT}",
                onSaveToPhotos = { filePath, onDone ->
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            try {
                                SaveResult(uri = saveVideoToMediaStore(filePath).toString())
                            } catch (e: Exception) {
                                SaveResult(error = e.message ?: "Unknown error")
                            }
                        }
                        onDone(result)
                    }
                },
                onOpenVideo = { uriString -> openVideo(uriString) }
            )
        }
    }

    private fun openVideo(uriString: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uriString), "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open video", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveVideoToMediaStore(filePath: String): Uri {
        val file = File(filePath)
        if (!file.exists()) {
            throw IOException("File not found")
        }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Teleprompter")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Failed to create MediaStore entry")

        try {
            contentResolver.openFileDescriptor(uri, "wt")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { fos ->
                    file.inputStream().use { fis ->
                        val inChannel = fis.channel
                        val outChannel = fos.channel
                        val size = inChannel.size()
                        var position = 0L
                        val chunk = 8L shl 20
                        try {
                            while (position < size) {
                                position += inChannel.transferTo(position, chunk, outChannel)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Partial save: wrote $position of $size bytes", e)
                        }
                    }
                }
            } ?: throw IOException("Failed to open output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(uri, done, null, null)
            }
            return uri
        } catch (e: Exception) {
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            throw e
        } finally {
            file.delete()
        }
    }
}
