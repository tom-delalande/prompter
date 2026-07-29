package com.teleprompter.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer as MLKitInternalRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale

class MLKitSpeechRecognizer(context: Context) : SpeechRecognizer {

    companion object {
        private const val TAG = "MLKitSpeech"
        private const val SAMPLE_RATE = 16000
    }

    private val appContext = context.applicationContext
    private val outputDir = run {
        val dirs = android.os.Environment.DIRECTORY_MOVIES
        context.getExternalFilesDir(dirs) ?: context.filesDir
    }
    private var recognizer: MLKitInternalRecognizer? = null
    private var listener: SpeechListener? = null
    private var recognitionJob: Job? = null
    private var audioCaptureJob: Job? = null
    private var isActive = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var audioRecord: AudioRecord? = null
    private var audioPipeWriteEnd: ParcelFileDescriptor? = null
    private var audioOutputPath: String? = null
    private var totalAudioBytes: Long = 0

    override fun setListener(listener: SpeechListener?) {
        this.listener = listener
    }

    override fun setAudioOutputPath(path: String) {
        audioOutputPath = path
    }

    override fun startListening() {
        if (isActive) return
        isActive = true
        totalAudioBytes = 0
        Log.d(TAG, "--- startListening ---")

        val rec = createRecognizer() ?: return
        val audioSource = audioOutputPath?.let { startAudioCapture(it) }
            ?: run {
                Log.d(TAG, "No audio output path, using fromMic()")
                AudioSource.fromMic()
            }

        if (audioSource == null) {
            listener?.onError("Failed to start audio capture")
            isActive = false
            return
        }

        recognitionJob = scope.launch(Dispatchers.Default) {
            try {
                setupAndRecognize(rec, audioSource)
            } catch (e: Exception) {
                Log.e(TAG, "Setup failed", e)
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
            Log.d(TAG, "Creating recognizer...")
            SpeechRecognition.getClient(options).also {
                Log.d(TAG, "Recognizer created")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create recognizer", e)
            listener?.onError("Recognizer init failed: ${e.message}")
            isActive = false
            null
        }
    }

    private suspend fun setupAndRecognize(rec: MLKitInternalRecognizer, audioSource: AudioSource) {
        Log.d(TAG, "Checking model status...")
        when (rec.checkStatus()) {
            FeatureStatus.AVAILABLE -> {
                Log.d(TAG, "Model available")
                startRecognitionFlow(rec, audioSource)
            }
            FeatureStatus.DOWNLOADABLE -> {
                Log.d(TAG, "Downloading model...")
                rec.download().collect { s ->
                    when (s) {
                        is DownloadStatus.DownloadCompleted -> {
                            Log.d(TAG, "Download completed")
                            if (isActive) startRecognitionFlow(rec, audioSource)
                        }
                        is DownloadStatus.DownloadFailed -> {
                            Log.e(TAG, "Download failed: ${s.e.message}")
                            listener?.onError("Model download failed: ${s.e.message}")
                            isActive = false
                        }
                        else -> {}
                    }
                }
            }
            FeatureStatus.DOWNLOADING -> {
                Log.d(TAG, "Model downloading, waiting...")
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

    private fun startAudioCapture(path: String): AudioSource? {
        val fullPath = File(outputDir, path).absolutePath
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
            Log.e(TAG, "AudioRecord init failed (state=${record.state})")
            return null
        }

        val pipe = ParcelFileDescriptor.createPipe()
        audioPipeWriteEnd = pipe[1]
        val pfdRead = pipe[0]
        audioRecord = record

        writeWavHeader(fullPath)

        try {
            record.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord start failed", e)
            try { record.release() } catch (_: Exception) {}
            try { pipe[0].close() } catch (_: Exception) {}
            try { pipe[1].close() } catch (_: Exception) {}
            audioRecord = null
            return null
        }

        Log.d(TAG, "AudioRecord started at ${SAMPLE_RATE}Hz")

        audioCaptureJob = scope.launch(Dispatchers.Default) {
            val buffer = ByteArray(bufferSize)
            val fos = FileOutputStream(fullPath, true)
            val pipeOut = ParcelFileDescriptor.AutoCloseOutputStream(audioPipeWriteEnd!!)

            try {
                while (isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val bytesRead = record.read(buffer, 0, bufferSize)
                    if (bytesRead > 0) {
                        pipeOut.write(buffer, 0, bytesRead)
                        fos.write(buffer, 0, bytesRead)
                        totalAudioBytes += bytesRead
                    } else if (bytesRead < 0) {
                        Log.e(TAG, "AudioRecord read error: $bytesRead")
                        break
                    }
                }
            } catch (e: IOException) {
                if (isActive) Log.e(TAG, "Audio capture IO error", e)
            } finally {
                try { fos.close() } catch (_: Exception) {}
                try { pipeOut.close() } catch (_: Exception) {}
                stopAudioCapture()
                updateWavHeader(fullPath)
                Log.d(TAG, "Audio capture ended, ${totalAudioBytes} bytes written to $fullPath")
                if (totalAudioBytes > 0) {
                    scope.launch {
                        listener?.onAudioRecorded(fullPath)
                    }
                }
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

    private fun writeWavHeader(path: String) {
        RandomAccessFile(path, "rw").use { raf ->
            raf.write("RIFF".toByteArray())
            writeLeInt(raf, 0)
            raf.write("WAVE".toByteArray())
            raf.write("fmt ".toByteArray())
            writeLeInt(raf, 16)
            writeLeShort(raf, 1)
            writeLeShort(raf, 1)
            writeLeInt(raf, SAMPLE_RATE)
            writeLeInt(raf, SAMPLE_RATE * 2)
            writeLeShort(raf, 2)
            writeLeShort(raf, 16)
            raf.write("data".toByteArray())
            writeLeInt(raf, 0)
        }
    }

    private fun updateWavHeader(path: String) {
        if (totalAudioBytes <= 0) return
        try {
            RandomAccessFile(path, "rw").use { raf ->
                raf.seek(4)
                writeLeInt(raf, (36 + totalAudioBytes).toInt())
                raf.seek(40)
                writeLeInt(raf, totalAudioBytes.toInt())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update WAV header", e)
        }
    }

    private fun writeLeInt(raf: RandomAccessFile, value: Int) {
        raf.write((value shr 0) and 0xFF)
        raf.write((value shr 8) and 0xFF)
        raf.write((value shr 16) and 0xFF)
        raf.write((value shr 24) and 0xFF)
    }

    private fun writeLeShort(raf: RandomAccessFile, value: Int) {
        raf.write((value shr 0) and 0xFF)
        raf.write((value shr 8) and 0xFF)
    }

    private suspend fun startRecognitionFlow(rec: MLKitInternalRecognizer, source: AudioSource) {
        val request = SpeechRecognizerRequest.Builder().apply {
            audioSource = source
        }.build()

        Log.d(TAG, "Starting recognition flow...")
        try {
            rec.startRecognition(request)
                .flowOn(Dispatchers.Default)
                .collect { response ->
                    if (!isActive) return@collect
                    when (response) {
                        is SpeechRecognizerResponse.PartialTextResponse -> {
                            Log.d(TAG, "PARTIAL: \"${response.text}\"")
                            listener?.onPartialResult(response.text)
                        }
                        is SpeechRecognizerResponse.FinalTextResponse -> {
                            Log.d(TAG, "FINAL: \"${response.text}\"")
                            listener?.onPartialResult(response.text)
                        }
                        is SpeechRecognizerResponse.ErrorResponse -> {
                            Log.e(TAG, "ERROR: ${response.e.message}")
                            if (isActive) listener?.onError(response.e.message ?: "Recognition error")
                        }
                        is SpeechRecognizerResponse.CompletedResponse -> {
                            Log.d(TAG, "COMPLETED")
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Recognition flow threw", e)
            if (isActive) listener?.onError(e.message ?: "Recognition error")
        }
    }

    override fun stopListening() {
        Log.d(TAG, "stopListening called")
        isActive = false
        recognitionJob?.cancel()
        recognitionJob = null
        audioCaptureJob?.cancel()
        audioCaptureJob = null
        stopAudioCapture()
        scope.launch {
            try {
                recognizer?.stopRecognition()
                Log.d(TAG, "stopRecognition completed")
            } catch (e: Exception) {
                Log.e(TAG, "stopRecognition threw", e)
            }
        }
    }

    override fun destroy() {
        Log.d(TAG, "destroy called")
        stopListening()
        try { recognizer?.close() } catch (_: Exception) {}
        recognizer = null
        scope.cancel()
    }
}
