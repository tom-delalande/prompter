package co.delalande.prompter.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.delalande.prompter.log.Log
import co.delalande.prompter.model.Script
import co.delalande.prompter.recording.CameraRecorder
import co.delalande.prompter.recording.RecordingListener
import co.delalande.prompter.speech.SpeechListener
import co.delalande.prompter.speech.SpeechMatcher
import co.delalande.prompter.speech.SpeechRecognizer

class TeleprompterViewModel(
    val script: Script,
    private val speechRecognizer: SpeechRecognizer,
    val cameraRecorder: CameraRecorder,
) {
    var currentWordIndex by mutableStateOf(0)
    var isRecording by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var scrollOffset by mutableStateOf(0f)
    var isFrontCamera by mutableStateOf(true)
    var recordingError by mutableStateOf<String?>(null)
    var lastRecordedFile by mutableStateOf<String?>(null)
    var isProcessing by mutableStateOf(false)
    var audioLevel by mutableStateOf(0f)
    var isExternalMic by mutableStateOf(false)

    private val pendingWords = ArrayDeque<String>()
    private var lastPartialTokens: List<String> = emptyList()
    private val maxPendingWords = 40
    private var recordingCounter = 0
    private var opCounter = 0L
    private var currentVideoPath = ""

    private val speechListener = object : SpeechListener {
        override fun onPartialResult(text: String) {
            if (!isPlaying) return
            if (text.isBlank()) return
            processSpeech(text)
        }

        override fun onError(error: String) {
            Log.e(TAG) { "Speech error: $error" }
            recordingError = error
        }

        override fun onVoiceActivityChanged(isActive: Boolean) {
            if (!isActive) {
                pendingWords.clear()
            }
        }

        override fun onAudioLevelChanged(level: Float) {
            audioLevel = level
        }

        override fun onExternalMicrophoneChanged(isExternal: Boolean) {
            isExternalMic = isExternal
        }
    }

    private val recordingListener = object : RecordingListener {
        override fun onRecordingStarted() {
            isRecording = true
        }

        override fun onRecordingFinished(filePath: String) {
            lastRecordedFile = filePath
            isRecording = false
            isPlaying = false
            isProcessing = false
        }

        override fun onError(error: String) {
            Log.e(TAG) { "Recording error: $error" }
            recordingError = error
            isProcessing = false
        }
    }

    init {
        Log.i(TAG) { "Teleprompter started for script '${script.title}' (${script.wordCount()} words)" }
        speechRecognizer.setListener(speechListener)
        cameraRecorder.setListener(recordingListener)
    }

    fun startMonitoring() {
        speechRecognizer.startListening()
    }

    fun startPlayback() {
        isPlaying = true
        pendingWords.clear()
        lastPartialTokens = emptyList()
        Log.i(TAG) { "startPlayback (word $currentWordIndex of ${script.wordCount()})" }
        speechRecognizer.startListening()
    }

    fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        recordingCounter++
        opCounter++
        currentVideoPath = "teleprompter_${recordingCounter}_$opCounter.mp4"
        recordingError = null
        lastRecordedFile = null
        Log.i(TAG) { "startRecording -> $currentVideoPath" }
        cameraRecorder.startRecording(currentVideoPath)
    }

    private fun stopRecording() {
        isProcessing = true
        isPlaying = false
        Log.i(TAG) { "stopRecording (word $currentWordIndex of ${script.wordCount()})" }
        cameraRecorder.stopRecording()
    }

    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        cameraRecorder.switchCamera()
    }

    private fun processSpeech(text: String) {
        if (currentWordIndex >= script.wordCount()) return

        val tokens = SpeechMatcher.tokenize(text)
        if (tokens.isEmpty()) return

        var prefix = 0
        while (prefix < lastPartialTokens.size &&
            prefix < tokens.size &&
            lastPartialTokens[prefix] == tokens[prefix]
        ) {
            prefix++
        }
        val newTokens = tokens.drop(prefix)
        lastPartialTokens = tokens

        if (newTokens.isEmpty()) return

        pendingWords.addAll(newTokens)
        while (pendingWords.size > maxPendingWords) {
            pendingWords.removeFirst()
        }

        for (i in 0 until pendingWords.size) {
            val subsetOfPendingWords = pendingWords.subList(i, pendingWords.size)
            val result = SpeechMatcher.match(
                subsetOfPendingWords.toList(),
                script.words,
                currentWordIndex
            )
            if (result.isMatching && result.newWordIndex > currentWordIndex) {
                currentWordIndex = minOf(result.newWordIndex, script.wordCount())
                Log.v(TAG) { "Speech advanced to word $currentWordIndex" }
                updateScrollOffset()
                val consumed = result.consumedWords.coerceAtMost(subsetOfPendingWords.size)
                repeat(i) { pendingWords.removeFirst() }
                repeat(consumed) { pendingWords.removeFirst() }
                return
            }
        }
    }

    private fun updateScrollOffset() {
        if (script.wordCount() == 0) return
        scrollOffset = currentWordIndex.toFloat() / script.wordCount().toFloat()
    }

    fun seekTo(index: Int) {
        currentWordIndex = index.coerceIn(0, script.wordCount())
        Log.v(TAG) { "seekTo $currentWordIndex" }
        updateScrollOffset()
        pendingWords.clear()
        lastPartialTokens = emptyList()
    }

    fun destroy() {
        Log.i(TAG) { "destroy (word $currentWordIndex of ${script.wordCount()})" }
        speechRecognizer.destroy()
        cameraRecorder.destroy()
    }

    companion object {
        private const val TAG = "TeleprompterVM"
    }
}
