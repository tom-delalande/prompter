package com.teleprompter.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.teleprompter.audio.AudioRecorder
import com.teleprompter.audio.AudioRecorderListener
import com.teleprompter.audio.AudioVideoMuxer
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
    val audioRecorder: AudioRecorder,
    private val audioVideoMuxer: AudioVideoMuxer
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
    var isMerging by mutableStateOf(false)

    private var accumulatedText = ""
    private var recordingCounter = 0
    private var opCounter = 0L
    private var currentVideoPath = ""
    private var currentAudioPath = ""
    private var cameraFinished = false
    private var audioFinished = false
    private val speechListener = object : SpeechListener {
        override fun onPartialResult(text: String) {
            recognizedText = text
            if (!isPlaying) {
                println("DEBUG: onPartialResult ignored — isPlaying=false, text=\"$text\"")
                return
            }
            if (text.isBlank()) {
                println("DEBUG: onPartialResult ignored — blank text")
                return
            }
            println("DEBUG: onPartialResult -> processSpeech(\"$text\")")
            processSpeech(text)
        }

        override fun onError(error: String) {
            recordingError = error
        }

        override fun onVoiceActivityChanged(isActive: Boolean) {}

        override fun onAudioRecorded(path: String) {
            currentAudioPath = path
            audioFinished = true
            tryMerge()
        }
    }

    private val recordingListener = object : RecordingListener {
        override fun onRecordingStarted() {
            isRecording = true
        }

        override fun onRecordingFinished(filePath: String) {
            currentVideoPath = filePath
            cameraFinished = true
            tryMerge()
        }

        override fun onError(error: String) {
            recordingError = error
            isRecording = false
        }
    }

    private val audioListener = object : AudioRecorderListener {
        override fun onRecordingStarted() {}

        override fun onRecordingFinished(filePath: String) {
            currentAudioPath = filePath
            audioFinished = true
            tryMerge()
        }

        override fun onError(error: String) {
            recordingError = "Audio: $error"
            isRecording = false
        }
    }

    init {
        speechRecognizer.setListener(speechListener)
        cameraRecorder.setListener(recordingListener)
        audioRecorder.setListener(audioListener)
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
        currentAudioPath = "teleprompter_${recordingCounter}_$opCounter.wav"
        cameraFinished = false
        audioFinished = false
        recordingError = null
        cameraRecorder.startRecording(currentVideoPath)
        // DIAGNOSTIC: skip audioRecorder to test mic contention
        // audioRecorder.startRecording(currentAudioPath)
    }

    private fun stopRecording() {
        cameraRecorder.stopRecording()
        audioRecorder.stopRecording()
        speechRecognizer.stopListening()
    }

    private fun tryMerge() {
        if (!cameraFinished || !audioFinished) return
        isMerging = true
        val outputPath = currentVideoPath.replace(".mp4", "_with_audio.mp4")
        try {
            audioVideoMuxer.merge(currentAudioPath, currentVideoPath, outputPath)
            lastRecordedFile = outputPath
        } catch (e: Exception) {
            recordingError = "Merge failed: ${e.message}"
            lastRecordedFile = currentVideoPath
        }
        isMerging = false
        isRecording = false
        isPlaying = false
    }

    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        cameraRecorder.switchCamera()
    }

    private fun processSpeech(text: String) {
        if (currentWordIndex >= script.wordCount()) {
            println("DEBUG: processSpeech skipped — at end (index=$currentWordIndex)")
            return
        }

        val newWords = extractNewWords(text, accumulatedText)
        println("DEBUG: processSpeech input=\"$text\" accumulated=\"$accumulatedText\" newWords=\"$newWords\" currentIndex=$currentWordIndex")
        accumulatedText = text
        if (newWords.isBlank()) {
            println("DEBUG: processSpeech — no new words to process")
            return
        }

        val result = SpeechMatcher.match(newWords, script.words, currentWordIndex)
        println("DEBUG: match result — isMatching=${result.isMatching} newWordIndex=${result.newWordIndex}")

        isMatching = result.isMatching
        if (result.isMatching) {
            currentWordIndex = minOf(result.newWordIndex, script.wordCount())
            println("DEBUG: advanced to index $currentWordIndex")
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

    fun destroy() {
        speechRecognizer.destroy()
        cameraRecorder.destroy()
        audioRecorder.destroy()
    }
}
