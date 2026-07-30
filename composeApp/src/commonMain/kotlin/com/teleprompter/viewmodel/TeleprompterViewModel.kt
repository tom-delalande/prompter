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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var mergeDone = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _mergeCompletedFile = MutableStateFlow<String?>(null)
    val mergeCompletedFile = _mergeCompletedFile.asStateFlow()

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
            println("STOPFLOW: speechListener.onError: $error")
            recordingError = error
        }

        override fun onVoiceActivityChanged(isActive: Boolean) {}

        override fun onAudioRecorded(path: String) {
            println("STOPFLOW: speechListener.onAudioRecorded path=$path")
            currentAudioPath = path
            audioFinished = true
            tryMerge()
        }
    }

    private val recordingListener = object : RecordingListener {
        override fun onRecordingStarted() {
            println("STOPFLOW: recordingListener.onRecordingStarted")
            isRecording = true
        }

        override fun onRecordingFinished(filePath: String) {
            println("STOPFLOW: recordingListener.onRecordingFinished filePath=$filePath")
            currentVideoPath = filePath
            cameraFinished = true
            tryMerge()
        }

        override fun onError(error: String) {
            println("STOPFLOW: recordingListener.onError: $error")
            recordingError = error
            cameraFinished = true
            tryMerge()
        }
    }

    private val audioListener = object : AudioRecorderListener {
        override fun onRecordingStarted() {
            println("STOPFLOW: audioListener.onRecordingStarted")
        }

        override fun onRecordingFinished(filePath: String) {
            println("STOPFLOW: audioListener.onRecordingFinished filePath=$filePath")
            currentAudioPath = filePath
            audioFinished = true
            tryMerge()
        }

        override fun onError(error: String) {
            println("STOPFLOW: audioListener.onError: $error")
            recordingError = "Audio: $error"
            audioFinished = true
            tryMerge()
        }
    }

    init {
        println("STOPFLOW: ViewModel init")
        speechRecognizer.setListener(speechListener)
        cameraRecorder.setListener(recordingListener)
        audioRecorder.setListener(audioListener)
    }

    fun startPlayback() {
        println("STOPFLOW: startPlayback currentAudioPath=$currentAudioPath")
        isPlaying = true
        isMatching = true
        accumulatedText = ""
        speechRecognizer.setAudioOutputPath(currentAudioPath)
        speechRecognizer.startListening()
    }

    fun toggleRecording() {
        println("STOPFLOW: toggleRecording isRecording=$isRecording")
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
        mergeDone = false
        recordingError = null
        println("STOPFLOW: startRecording video=$currentVideoPath audio=$currentAudioPath mergeDone reset to false")
        _mergeCompletedFile.value = null
        cameraRecorder.startRecording(currentVideoPath)
        audioRecorder.startRecording(currentAudioPath)
    }

    private fun stopRecording() {
        println("STOPFLOW: stopRecording called")
        println("STOPFLOW: stopRecording state before: cameraFinished=$cameraFinished audioFinished=$audioFinished mergeDone=$mergeDone currentVideoPath=$currentVideoPath currentAudioPath=$currentAudioPath")
        isPlaying = false
        cameraRecorder.stopRecording()
        println("STOPFLOW: cameraRecorder.stopRecording done")
        audioRecorder.stopRecording()
        println("STOPFLOW: audioRecorder.stopRecording done")
        speechRecognizer.stopListening()
        println("STOPFLOW: speechRecognizer.stopListening done")
        audioFinished = true
        println("STOPFLOW: set audioFinished=true, calling tryMerge()")
        tryMerge()
        println("STOPFLOW: stopRecording finished")
    }

    private fun tryMerge() {
        println("STOPFLOW: tryMerge cameraFinished=$cameraFinished audioFinished=$audioFinished mergeDone=$mergeDone")
        if (!cameraFinished || !audioFinished || mergeDone) return
        mergeDone = true
        isMerging = true
        println("STOPFLOW: tryMerge PROCEEDING, launching coroutine")
        scope.launch {
            val outputPath = currentVideoPath.replace(".mp4", "_with_audio.mp4")
            println("STOPFLOW: coroutine running, starting merge on Default")
            try {
                withContext(Dispatchers.Default) {
                    audioVideoMuxer.merge(currentAudioPath, currentVideoPath, outputPath)
                }
                println("STOPFLOW: merge succeeded, outputPath=$outputPath")
                _mergeCompletedFile.value = outputPath
            } catch (e: Exception) {
                println("STOPFLOW: merge failed: ${e.message}")
                recordingError = "Merge failed: ${e.message}"
                _mergeCompletedFile.value = currentVideoPath
            }
            isMerging = false
        }
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

    fun clearLastRecording() {
        lastRecordedFile = null
    }

    fun destroy() {
        scope.cancel()
        speechRecognizer.destroy()
        cameraRecorder.destroy()
        audioRecorder.destroy()
    }
}
