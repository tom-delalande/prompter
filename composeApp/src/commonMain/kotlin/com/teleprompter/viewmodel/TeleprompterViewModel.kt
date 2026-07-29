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
    val cameraRecorder: CameraRecorder
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

    private val speechListener = object : SpeechListener {
        override fun onPartialResult(text: String) {
            recognizedText = text
            if (isPlaying && text.isNotBlank()) {
                processSpeech(text)
            }
        }

        override fun onError(error: String) {
            recordingError = error
        }
    }

    private val recordingListener = object : RecordingListener {
        override fun onRecordingStarted() {
            isRecording = true
        }

        override fun onRecordingFinished(filePath: String) {
            isRecording = false
            isPlaying = false
            lastRecordedFile = filePath
        }

        override fun onError(error: String) {
            recordingError = error
            isRecording = false
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
        val fileName = "teleprompter_$recordingCounter.mp4"
        recordingError = null
        cameraRecorder.startRecording(fileName)
    }

    private fun stopRecording() {
        cameraRecorder.stopRecording()
        speechRecognizer.stopListening()
    }

    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        cameraRecorder.switchCamera()
    }

    private fun processSpeech(text: String) {
        if (currentWordIndex >= script.wordCount()) return

        accumulatedText = text
        val result = SpeechMatcher.match(text, script.words, currentWordIndex)

        isMatching = result.isMatching
        if (result.isMatching) {
            currentWordIndex = minOf(result.newWordIndex, script.wordCount())
            updateScrollOffset()
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

    fun destroy() {
        speechRecognizer.destroy()
        cameraRecorder.destroy()
    }
}
