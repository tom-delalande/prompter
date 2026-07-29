package com.teleprompter.speech

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.*
import platform.Foundation.*
import platform.darwin.NSObject
import platform.Speech.*

@OptIn(ExperimentalForeignApi::class)
class IOSSpeechRecognizer : SpeechRecognizer {

    private var recognizer: SFSpeechRecognizer? = null
    private var recognitionTask: SFSpeechRecognitionTask? = null
    private var audioEngine: AVAudioEngine? = null
    private var listener: SpeechListener? = null
    private var delegate: SpeechRecognitionDelegate? = null

    init {
        SFSpeechRecognizer.requestAuthorization { _ ->
            recognizer = SFSpeechRecognizer()
        }
    }

    override fun setListener(listener: SpeechListener?) {
        this.listener = listener
    }

    override fun startListening() {
        val engine = AVAudioEngine()
        audioEngine = engine

        val node = engine.inputNode
        val recordingFormat = node.outputFormatForBus(0u)

        val recognitionRequest = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = true
        }

        engine.prepare()
        engine.startAndReturnError(null)

        delegate = SpeechRecognitionDelegate(listener)

        recognitionTask = recognizer?.recognitionTaskWithRequest(
            recognitionRequest,
            delegate ?: return
        )
    }

    override fun stopListening() {
        recognitionTask?.cancel()
        recognitionTask = null
        audioEngine?.stop()
        audioEngine = null
        delegate = null
    }

    override fun destroy() {
        stopListening()
        recognizer = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private class SpeechRecognitionDelegate(
    private val speechListener: SpeechListener?
) : NSObject(), SFSpeechRecognitionTaskDelegateProtocol {

    override fun speechRecognitionTask(
        task: SFSpeechRecognitionTask,
        didHypothesizeTranscription: SFTranscription
    ) {
        speechListener?.onPartialResult(didHypothesizeTranscription.formattedString)
    }

    override fun speechRecognitionTask(
        task: SFSpeechRecognitionTask,
        didFinishRecognition: SFSpeechRecognitionResult
    ) {
        speechListener?.onPartialResult(didFinishRecognition.bestTranscription.formattedString)
    }

    override fun speechRecognitionTaskWasCancelled(task: SFSpeechRecognitionTask) {}

    override fun speechRecognitionTask(
        task: SFSpeechRecognitionTask,
        didFinishSuccessfully: Boolean
    ) {
        if (!didFinishSuccessfully) {
            speechListener?.onError("Recognition task failed")
        }
    }
}
