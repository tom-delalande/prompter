package co.delalande.prompter.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer as MLKitInternalRecognizer
import co.delalande.prompter.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale
import kotlin.math.sqrt

class MLKitSpeechRecognizer(context: Context) : SpeechRecognizer {

    companion object {
        private const val TAG = "MLKitSpeech"
        private const val SAMPLE_RATE = 16000
    }

    private val appContext = context.applicationContext
    private var recognizer: MLKitInternalRecognizer? = null
    private var listener: SpeechListener? = null
    private var recognitionJob: Job? = null
    private var audioCaptureJob: Job? = null
    private var isActive = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var audioRecord: AudioRecord? = null
    private var audioPipeWriteEnd: ParcelFileDescriptor? = null
    private var smoothedLevel = 0f
    private var lastExternalReported = false

    override fun setListener(listener: SpeechListener?) {
        this.listener = listener
    }

    override fun startListening() {
        if (isActive) return
        isActive = true
        Log.d(TAG) { "--- startListening ---" }

        recognizer = createRecognizer() ?: return
        val audioSource = startAudioCapture()

        if (audioSource == null) {
            recognizer = null
            isActive = false
            return
        }

        recognitionJob = scope.launch(Dispatchers.Default) {
            try {
                setupAndRecognize(recognizer!!, audioSource)
            } catch (e: Exception) {
                Log.e(TAG, throwable = e) { "Setup failed" }
                if (isActive) {
                    listener?.onError("Setup failed: ${e.message}")
                    isActive = false
                }
            }
        }
    }

    private fun createRecognizer(): MLKitInternalRecognizer? {
        return try {
            val options = SpeechRecognizerOptions.Builder().apply {
                locale = Locale.US
                preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC
            }.build()
            Log.d(TAG) { "Creating recognizer..." }
            SpeechRecognition.getClient(options).also {
                Log.d(TAG) { "Recognizer created" }
            }
        } catch (e: Exception) {
            Log.e(TAG, throwable = e) { "Failed to create recognizer" }
            listener?.onError("Recognizer init failed: ${e.message}")
            isActive = false
            null
        }
    }

    private suspend fun setupAndRecognize(rec: MLKitInternalRecognizer, audioSource: AudioSource) {
        Log.d(TAG) { "Checking model status..." }
        when (rec.checkStatus()) {
            FeatureStatus.AVAILABLE -> {
                Log.d(TAG) { "Model available" }
                startRecognitionFlow(rec, audioSource)
            }
            FeatureStatus.DOWNLOADABLE -> {
                Log.d(TAG) { "Downloading model..." }
                rec.download().collect { s ->
                    when (s) {
                        is DownloadStatus.DownloadCompleted -> {
                            Log.d(TAG) { "Download completed" }
                            if (isActive) startRecognitionFlow(rec, audioSource)
                        }
                        is DownloadStatus.DownloadFailed -> {
                            Log.e(TAG) { "Download failed: ${s.e.message}" }
                            listener?.onError("Model download failed: ${s.e.message}")
                            isActive = false
                        }
                        else -> {}
                    }
                }
            }
            FeatureStatus.DOWNLOADING -> {
                Log.d(TAG) { "Model downloading, waiting..." }
                delay(3000)
                if (!isActive) return
                if (rec.checkStatus() == FeatureStatus.AVAILABLE) {
                    startRecognitionFlow(rec, audioSource)
                } else {
                    listener?.onError("Model not ready. Try again later.")
                    isActive = false
                }
            }
            else -> {
                listener?.onError("Speech recognition unavailable on this device")
                isActive = false
            }
        }
    }

    private fun startAudioCapture(): AudioSource? {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG) { "AudioRecord init failed (state=${record.state})" }
            return null
        }

        AudioInput.routeToExternalMic(appContext, record)

        val pipe = ParcelFileDescriptor.createPipe()
        audioPipeWriteEnd = pipe[1]
        val pfdRead = pipe[0]
        audioRecord = record

        try {
            record.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, throwable = e) { "AudioRecord start failed" }
            try { record.release() } catch (_: Exception) {}
            try { pipe[0].close() } catch (_: Exception) {}
            try { pipe[1].close() } catch (_: Exception) {}
            audioRecord = null
            return null
        }

        reportExternalMic(record)
        smoothedLevel = 0f

        Log.d(TAG) { "AudioRecord started at ${SAMPLE_RATE}Hz" }

        audioCaptureJob = scope.launch(Dispatchers.Default) {
            val buffer = ByteArray(bufferSize)
            val pipeOut = ParcelFileDescriptor.AutoCloseOutputStream(audioPipeWriteEnd!!)

            try {
                while (isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val bytesRead = record.read(buffer, 0, bufferSize)
                    if (bytesRead > 0) {
                        reportExternalMic(record)
                        reportAudioLevel(buffer, bytesRead)
                        pipeOut.write(buffer, 0, bytesRead)
                    } else if (bytesRead < 0) {
                        Log.e(TAG) { "AudioRecord read error: $bytesRead" }
                        break
                    }
                }
            } catch (e: IOException) {
                if (isActive) Log.e(TAG, throwable = e) { "Audio capture IO error" }
            } finally {
                try { pipeOut.close() } catch (_: Exception) {}
                stopAudioCapture()
            }
        }

        return AudioSource.fromPfd(pfdRead)
    }

    private fun stopAudioCapture() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        try { audioPipeWriteEnd?.close() } catch (_: Exception) {}
        audioPipeWriteEnd = null
    }

    private fun reportExternalMic(record: AudioRecord) {
        val external = AudioInput.isExternalDevice(record.routedDevice)
        if (external != lastExternalReported) {
            lastExternalReported = external
            listener?.onExternalMicrophoneChanged(external)
        }
    }

    private fun reportAudioLevel(buffer: ByteArray, bytesRead: Int) {
        val sampleCount = bytesRead / 2
        if (sampleCount <= 0) return
        var sumSquares = 0.0
        for (i in 0 until sampleCount) {
            val sample = (buffer[i * 2].toInt() and 0xFF) or (buffer[i * 2 + 1].toInt() shl 8)
            sumSquares += sample.toDouble() * sample.toDouble()
        }
        val rms = sqrt(sumSquares / sampleCount)
        val level = AudioInput.levelFromRms(rms)
        smoothedLevel = smoothedLevel * 0.6f + level * 0.4f
        listener?.onAudioLevelChanged(smoothedLevel)
    }

    private suspend fun startRecognitionFlow(rec: MLKitInternalRecognizer, source: AudioSource) {
        val request = SpeechRecognizerRequest.Builder().apply {
            audioSource = source
        }.build()

        Log.d(TAG) { "Starting recognition flow..." }
        try {
            rec.startRecognition(request)
                .flowOn(Dispatchers.Default)
                .collect { response ->
                    if (!isActive) return@collect
                    when (response) {
                        is SpeechRecognizerResponse.PartialTextResponse -> {
                            Log.d(TAG) { "PARTIAL: \"${response.text}\"" }
                            listener?.onPartialResult(response.text)
                        }
                        is SpeechRecognizerResponse.FinalTextResponse -> {
                            Log.d(TAG) { "FINAL: \"${response.text}\"" }
                            listener?.onPartialResult(response.text)
                        }
                        is SpeechRecognizerResponse.ErrorResponse -> {
                            Log.e(TAG) { "ERROR: ${response.e.message}" }
                            if (isActive) listener?.onError(response.e.message ?: "Recognition error")
                        }
                        is SpeechRecognizerResponse.CompletedResponse -> {
                            Log.d(TAG) { "COMPLETED" }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, throwable = e) { "Recognition flow threw" }
            if (isActive) listener?.onError(e.message ?: "Recognition error")
        }
    }

    override fun destroy() {
        Log.d(TAG) { "destroy called" }
        isActive = false
        listener = null
        recognitionJob?.cancel()
        recognitionJob = null
        audioCaptureJob?.cancel()
        audioCaptureJob = null
        scope.launch(Dispatchers.IO) {
            stopAudioCapture()
            try { recognizer?.stopRecognition() } catch (_: Exception) {}
            try { recognizer?.close() } catch (_: Exception) {}
            recognizer = null
            scope.cancel()
        }
    }
}
