package co.delalande.prompter

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import co.delalande.prompter.billing.AndroidBillingManager
import co.delalande.prompter.data.PlatformStorage
import co.delalande.prompter.feedback.FEEDBACK_EMAIL_ADDRESS
import co.delalande.prompter.log.Log
import co.delalande.prompter.log.initLogging
import co.delalande.prompter.model.SaveResult
import co.delalande.prompter.recording.AndroidCameraRecorder
import co.delalande.prompter.speech.MLKitSpeechRecognizer
import co.delalande.prompter.speech.AndroidSpeechLanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var sharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initLogging()
        handleShareIntent(intent)

        setContent {
            App(
                platformStorage = PlatformStorage(this@MainActivity),
                isDebug = BuildConfig.DEBUG,
                speechRecognizerFactory = { localeTag ->
                    MLKitSpeechRecognizer(this@MainActivity, localeTag)
                },
                speechLanguageManagerFactory = { AndroidSpeechLanguageManager() },
                cameraRecorderFactory = { AndroidCameraRecorder(this@MainActivity) },
                billingManagerFactory = {
                    AndroidBillingManager(this@MainActivity)
                },
                buildInfo = "v${BuildConfig.VERSION_NAME} · ${BuildInfo.GIT_COMMIT}",
                sharedText = sharedText.value,
                onSaveToPhotos = { filePath, onDone ->
                    val callback = onDone
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO + NonCancellable) {
                            try {
                                SaveResult(uri = saveVideoToMediaStore(filePath).toString())
                            } catch (e: Exception) {
                                SaveResult(error = e.message ?: "Unknown error")
                            }
                        }
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                            callback(result)
                        }
                    }
                },
                onOpenVideo = { uriString -> openVideo(uriString) },
                onComposeEmail = { subject, isBugReport -> composeEmail(subject, isBugReport) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedText.value = intent.getStringExtra(Intent.EXTRA_TEXT)
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

    private fun composeEmail(subject: String, isBugReport: Boolean) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL_ADDRESS))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                if (isBugReport) {
                    putExtra(Intent.EXTRA_TEXT, buildEmailBody())
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open email app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildEmailBody(): String = buildString {
        appendLine("Prompter v${BuildConfig.VERSION_NAME} (${BuildInfo.GIT_COMMIT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine()
        appendLine("Describe the issue below:")
    }

    private fun saveVideoToMediaStore(filePath: String): Uri {
        val file = File(filePath)
        if (!file.isFile || file.length() == 0L) {
            throw IOException("Recording file is missing or empty: $filePath")
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
            val expectedBytes = file.length()
            val copiedBytes = contentResolver.openOutputStream(uri, "wt")?.use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Failed to open output stream")
            if (copiedBytes != expectedBytes) {
                throw IOException("Incomplete video copy: $copiedBytes of $expectedBytes bytes")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                if (contentResolver.update(uri, done, null, null) != 1) {
                    throw IOException("Failed to finish saving video")
                }
            }
            if (!file.delete()) Log.w(TAG) { "Saved video, but could not remove temporary file $filePath" }
            return uri
        } catch (e: Exception) {
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            throw e
        }
    }
}
