package co.delalande.prompter.speech

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import platform.AVFAudio.*
import platform.Foundation.*
import platform.darwin.NSObject
import platform.Speech.*
import kotlin.math.ln
import kotlin.math.sqrt

@OptIn(ExperimentalForeignApi::class)
class IOSSpeechRecognizer : SpeechRecognizer {

    private var recognizer: SFSpeechRecognizer? = null
    private var recognitionTask: SFSpeechRecognitionTask? = null
    private var audioEngine: AVAudioEngine? = null
    private var listener: SpeechListener? = null
    private var delegate: SpeechRecognitionDelegate? = null
    private var smoothedLevel = 0f
    private var lastExternalReported = false

    init {
        SFSpeechRecognizer.requestAuthorization { _ ->
            recognizer = SFSpeechRecognizer()
        }
    }

    override fun setListener(listener: SpeechListener?) {
        this.listener = listener
    }

    override fun startListening() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryRecord, 0uL, null)
        session.setActive(true, null)

        val engine = AVAudioEngine()
        audioEngine = engine

        val node = engine.inputNode
        val recordingFormat = node.outputFormatForBus(0u)

        val recognitionRequest = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = true
        }

        node.installTapOnBus(0u, 1024u, recordingFormat) { buffer, _ ->
            buffer?.let {
                recognitionRequest.appendAudioPCMBuffer(it)
                reportAudioLevel(it)
            }
        }

        engine.prepare()
        engine.startAndReturnError(null)

        delegate = SpeechRecognitionDelegate(listener)

        recognitionTask = recognizer?.recognitionTaskWithRequest(
            recognitionRequest,
            delegate ?: return
        )

        reportExternalMic()
    }

    private fun reportAudioLevel(buffer: AVAudioPCMBuffer) {
        val frameCount = buffer.frameLength
        if (frameCount == 0u) return
        val channels = buffer.floatChannelData ?: return
        val channel = channels[0] ?: return

        var sum = 0.0
        for (i in 0 until frameCount.toInt()) {
            val v = channel[i].toDouble()
            sum += v * v
        }
        val rms = sqrt(sum / frameCount.toInt())
        val level = if (rms <= 0.0) 0f else {
            val db = 20.0 * ln(rms) / ln(10.0)
            ((db + 45.0) / 45.0).coerceIn(0.0, 1.0).toFloat()
        }
        smoothedLevel = smoothedLevel * 0.6f + level * 0.4f
        listener?.onAudioLevelChanged(smoothedLevel)
    }

    private fun reportExternalMic() {
        val session = AVAudioSession.sharedInstance()
        val inputs = session.currentRoute.inputs
        val externalPort = inputs
            .mapNotNull { it as? AVAudioSessionPortDescription }
            .firstOrNull { isExternalPortType(it.portType ?: "") }
        val isExternal = externalPort != null
        if (isExternal != lastExternalReported) {
            lastExternalReported = isExternal
            listener?.onExternalMicrophoneChanged(isExternal)
        }
        if (externalPort != null) {
            session.setPreferredInput(externalPort, null)
        }
    }

    private fun isExternalPortType(portType: String): Boolean = when (portType) {
        AVAudioSessionPortUSBAudio,
        AVAudioSessionPortBluetoothHFP,
        AVAudioSessionPortBluetoothA2DP,
        AVAudioSessionPortBluetoothLE,
        AVAudioSessionPortLineIn,
        AVAudioSessionPortHeadsetMic,
        -> true
        else -> false
    }

    override fun stopListening() {
        recognitionTask?.cancel()
        recognitionTask = null
        audioEngine?.inputNode?.removeTapOnBus(0u)
        audioEngine?.stop()
        audioEngine = null
        delegate = null
        smoothedLevel = 0f
        listener?.onAudioLevelChanged(0f)
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
