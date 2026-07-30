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

    private var accumulatedText = ""
    private var recordingCounter = 0
    private var opCounter = 0L
    private var currentVideoPath = ""
    private var currentAudioPath = ""

    private val speechListener = object : SpeechListener {
        override fun onPartialResult(text: String) {
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
        isPlaying = true
        isMatching = true
        accumulatedText = ""
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

        val newWords = extractNewWords(text, accumulatedText)
        accumulatedText = text
        if (newWords.isBlank()) return

        val result = SpeechMatcher.match(newWords, script.words, currentWordIndex)
        isMatching = result.isMatching
        if (result.isMatching) {
            currentWordIndex = minOf(result.newWordIndex, script.wordCount())
            updateScrollOffset()
        }
    }

    private fun extractNewWords(newText: String, oldText: String): String {
        if (oldText.isBlank()) return newText

        val newWords = newText.lowercase().trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val oldWords = oldText.lowercase().trim().split("\\s+".toRegex()).filter { it.isNotBlank() }

        val firstDiff = newWords.indices.firstOrNull { i ->
            i >= oldWords.size || normalize(newWords[i]) != normalize(oldWords.getOrNull(i) ?: "")
        } ?: return ""

        return newWords.drop(firstDiff).joinToString(" ")
    }

    private fun normalize(word: String): String =
        word.lowercase().filter { it.isLetterOrDigit() }

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
