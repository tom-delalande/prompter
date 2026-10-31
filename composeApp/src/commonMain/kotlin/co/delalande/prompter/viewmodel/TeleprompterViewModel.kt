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
import kotlin.time.Clock

class TeleprompterViewModel(
    val script: Script,
    private val speechRecognizer: SpeechRecognizer,
    val cameraRecorder: CameraRecorder,
    startWordIndex: Int = 0,
) {
    var currentWordIndex by mutableStateOf(0)
    var isRecording by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var recordingError by mutableStateOf<String?>(null)
    var lastRecordedFile by mutableStateOf<String?>(null)
    var isProcessing by mutableStateOf(false)
    var audioLevel by mutableStateOf(0f)
    var isExternalMic by mutableStateOf(false)

    private val pendingWords = ArrayDeque<String>()
    private var lastPartialTokens: List<String> = emptyList()
    private val maxPendingWords = 40
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
            isRecording = false
            isPlaying = false
            isProcessing = false
        }
    }

    init {
        Log.i(TAG) { "Teleprompter started for script '${script.title}' (${script.wordCount()} words, start=$startWordIndex)" }
        speechRecognizer.setListener(speechListener)
        cameraRecorder.setListener(recordingListener)
        if (startWordIndex > 0) {
            seekTo(startWordIndex)
        }
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
        val sanitizedTitle = script.title.replace(Regex("[^a-zA-Z0-9\\s-_]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .ifEmpty { "teleprompter" }
        val timestamp = formatTimestampForFileName(Clock.System.now().toEpochMilliseconds())
        currentVideoPath = "${sanitizedTitle}_${timestamp}.mp4"
        recordingError = null
        lastRecordedFile = null
        Log.i(TAG) { "startRecording -> $currentVideoPath" }
        cameraRecorder.startRecording(currentVideoPath)
    }

    private fun formatTimestampForFileName(epochMs: Long): String {
        val totalSeconds = epochMs / 1000
        val seconds = (totalSeconds % 60).toInt()
        val totalMinutes = totalSeconds / 60
        val minutes = (totalMinutes % 60).toInt()
        val totalHours = totalMinutes / 60
        val hours = (totalHours % 24).toInt()
        val totalDays = (totalHours / 24).toInt()

        var year = 1970
        var remainingDays = totalDays
        while (remainingDays >= if (isLeapYear(year)) 366 else 365) {
            remainingDays -= if (isLeapYear(year)) 366 else 365
            year++
        }

        val monthDays = intArrayOf(
            31, if (isLeapYear(year)) 29 else 28, 31, 30, 31, 30,
            31, 31, 30, 31, 30, 31
        )
        var month = 1
        for (days in monthDays) {
            if (remainingDays < days) break
            remainingDays -= days
            month++
        }

        val day = remainingDays + 1
        return "${year.pad4()}-${month.pad2()}-${day.pad2()}_${hours.pad2()}-${minutes.pad2()}-${seconds.pad2()}"
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private fun Int.pad2(): String = if (this < 10) "0$this" else "$this"
    private fun Int.pad4(): String = when {
        this < 10 -> "000$this"
        this < 100 -> "00$this"
        this < 1000 -> "0$this"
        else -> "$this"
    }

    private fun stopRecording() {
        isProcessing = true
        isPlaying = false
        Log.i(TAG) { "stopRecording (word $currentWordIndex of ${script.wordCount()})" }
        cameraRecorder.stopRecording()
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
                val consumed = result.consumedWords.coerceAtMost(subsetOfPendingWords.size)
                repeat(i + consumed) { pendingWords.removeFirst() }
                return
            }
        }
    }

    fun seekTo(index: Int) {
        currentWordIndex = index.coerceIn(0, script.wordCount())
        Log.v(TAG) { "seekTo $currentWordIndex" }
        pendingWords.clear()
        lastPartialTokens = emptyList()
    }

    fun resetAfterSave() {
        lastRecordedFile = null
        isProcessing = false
        Log.i(TAG) { "resetAfterSave" }
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
