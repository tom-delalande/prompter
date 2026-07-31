package com.teleprompter.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.teleprompter.model.Script
import com.teleprompter.recording.CameraRecorder
import com.teleprompter.recording.RecordingListener
import com.teleprompter.speech.SpeechListener
import com.teleprompter.speech.SpeechMatcher
import com.teleprompter.speech.SpeechRecognizer

class TeleprompterViewModel(
    val script: Script,
    private val speechRecognizer: SpeechRecognizer,
    val cameraRecorder: CameraRecorder,
) {
    var currentWordIndex by mutableStateOf(0)
    var isRecording by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var isMatching by mutableStateOf(true)
    var recognizedText by mutableStateOf("")
    var scrollOffset by mutableStateOf(0f)
    var isFrontCamera by mutableStateOf(true)
    var recordingError by mutableStateOf<String?>(null)
    var lastRecordedFile by mutableStateOf<String?>(null)
    var isProcessing by mutableStateOf(false)

    private val pendingWords = ArrayDeque<String>()
    private var lastPartialTokens: List<String> = emptyList()
    private val maxPendingWords = 40
    private var recordingCounter = 0
    private var opCounter = 0L
    private var currentVideoPath = ""
    private var currentAudioPath = ""

    private val speechListener = object : SpeechListener {
        override fun onPartialResult(text: String) {
            println("SKIPDBG| VM onPartial isPlaying=$isPlaying text=\"$text\"")
            recognizedText = text
            if (!isPlaying) return
            if (text.isBlank()) return
            processSpeech(text)
        }

        override fun onError(error: String) {
            recordingError = error
        }

        override fun onVoiceActivityChanged(isActive: Boolean) {}
        override fun onAudioRecorded(path: String) {}
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
            recordingError = error
        }
    }

    init {
        speechRecognizer.setListener(speechListener)
        cameraRecorder.setListener(recordingListener)
    }

    fun startPlayback() {
        println("SKIPDBG| VM startPlayback cur=$currentWordIndex words=${script.wordCount()} lastRecorded=${lastRecordedFile != null}")
        isPlaying = true
        isMatching = true
        pendingWords.clear()
        lastPartialTokens = emptyList()
        speechRecognizer.setAudioOutputPath(currentAudioPath)
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
        currentAudioPath = "audio_${recordingCounter}_$opCounter.wav"
        println("STOPFLOW: startRecording video=$currentVideoPath audio=$currentAudioPath")
        recordingError = null
        lastRecordedFile = null
        cameraRecorder.startRecording(currentVideoPath)
    }

    private fun stopRecording() {
        println("STOPFLOW: stopRecording called")
        isProcessing = true
        isPlaying = false
        cameraRecorder.stopRecording()
        speechRecognizer.stopListening()
        println("STOPFLOW: stopRecording finished")
    }

    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        cameraRecorder.switchCamera()
    }

    private fun processSpeech(text: String) {
        if (currentWordIndex >= script.wordCount()) return

        val tokens = SpeechMatcher.tokenize(text)
        if (tokens.isEmpty()) {
            println("SKIPDBG| VM process tokens=[] text=\"$text\"")
            return
        }

        var prefix = 0
        while (prefix < lastPartialTokens.size &&
            prefix < tokens.size &&
            lastPartialTokens[prefix] == tokens[prefix]
        ) {
            prefix++
        }
        val newTokens = tokens.drop(prefix)
        lastPartialTokens = tokens
        println(
            "SKIPDBG| VM process cur=$currentWordIndex last=${lastPartialTokens.size} " +
                "prefix=$prefix new=${newTokens.size} newTokens=$newTokens"
        )

        if (newTokens.isEmpty()) return

        pendingWords.addAll(newTokens)
        while (pendingWords.size > maxPendingWords) {
            pendingWords.removeFirst()
        }
        println(
            "SKIPDBG| VM pending=${pendingWords.size} pendingWords=${pendingWords.take(10)} " +
                (if (pendingWords.size > 10) "..." else "")
        )

        val result = SpeechMatcher.match(
            pendingWords.toList(),
            script.words,
            currentWordIndex
        )
        isMatching = result.isMatching
        if (result.isMatching && result.newWordIndex > currentWordIndex) {
            currentWordIndex = minOf(result.newWordIndex, script.wordCount())
            updateScrollOffset()
            val consumed = result.consumedWords.coerceAtMost(pendingWords.size)
            repeat(consumed) { pendingWords.removeFirst() }
            println(
                "SKIPDBG| VM MATCH ok idx=$currentWordIndex conf=${result.confidence} " +
                    "consumed=$consumed pending=${pendingWords.size}"
            )
        } else {
            println(
                "SKIPDBG| VM MATCH no isMatching=${result.isMatching} " +
                    "newIdx=${result.newWordIndex} conf=${result.confidence} cur=$currentWordIndex"
            )
        }
    }

    private fun updateScrollOffset() {
        if (script.wordCount() == 0) return
        scrollOffset = currentWordIndex.toFloat() / script.wordCount().toFloat()
    }

    fun seekTo(index: Int) {
        currentWordIndex = index.coerceIn(0, script.wordCount())
        updateScrollOffset()
    }

    fun clearLastRecording() {
        lastRecordedFile = null
    }

    fun destroy() {
        speechRecognizer.destroy()
        cameraRecorder.destroy()
    }
}
