package co.delalande.prompter.speech

import co.delalande.prompter.log.Log
import kotlinx.cinterop.*
import platform.AVFAudio.*
import platform.Foundation.*
import platform.darwin.NSObject
import platform.Speech.*
import kotlin.math.sqrt

private const val TAG = "IOSSpeech"

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IOSSpeechRecognizer(
    private val preferredLocaleTag: String? = null,
) : SpeechRecognizer {

    private var recognizer: SFSpeechRecognizer? = null
    private var recognitionTask: SFSpeechRecognitionTask? = null
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest? = null
    private var audioEngine: AVAudioEngine? = null
    private var listener: SpeechListener? = null
    private var delegate: SpeechRecognitionDelegate? = null
    private var smoothedLevel = 0f
    private var lastExternalReported = false
    private var isListening = false
    private var pendingPermissionRequest = false
    private var hasReportedAudioLevel = false

    init {
        SFSpeechRecognizer.requestAuthorization { status ->
            Log.i(TAG) { "Speech recognition authorization: $status" }
            recognizer = createRecognizer()
            if (isListening) {
                startRecognitionTask()
            }
        }
    }

    private fun createRecognizer(): SFSpeechRecognizer = preferredLocaleTag
        ?.let { SFSpeechRecognizer(locale = NSLocale(it)) }
        ?: SFSpeechRecognizer()

    override fun setListener(listener: SpeechListener?) {
        this.listener = listener
    }

    override fun startListening() {
        if (audioEngine != null) return
        val session = AVAudioSession.sharedInstance()
        when (session.recordPermission) {
            AVAudioSessionRecordPermissionGranted -> startAudioEngine()
            AVAudioSessionRecordPermissionUndetermined -> {
                if (pendingPermissionRequest) return
                pendingPermissionRequest = true
                session.requestRecordPermission { granted ->
                    pendingPermissionRequest = false
                    if (granted) {
                        Log.i(TAG) { "Microphone permission granted" }
                        startAudioEngine()
                    } else {
                        Log.w(TAG) { "Microphone permission denied; audio level unavailable" }
                    }
                }
            }
            else -> Log.w(TAG) { "Microphone permission denied; audio level unavailable" }
        }
    }

    private fun startAudioEngine() {
        if (audioEngine != null) return
        val session = AVAudioSession.sharedInstance()
        memScoped {
            val categoryError = alloc<ObjCObjectVar<NSError?>>()
            val categoryOptions =
                AVAudioSessionCategoryOptionMixWithOthers or AVAudioSessionCategoryOptionAllowBluetooth
            if (!session.setCategory(AVAudioSessionCategoryPlayAndRecord, categoryOptions, categoryError.ptr)) {
                Log.e(TAG) { "Failed to set audio session category: ${categoryError.value?.localizedDescription}" }
            }
            val activeError = alloc<ObjCObjectVar<NSError?>>()
            if (!session.setActive(true, activeError.ptr)) {
                Log.e(TAG) { "Failed to activate audio session: ${activeError.value?.localizedDescription}" }
            }
        }

        val engine = AVAudioEngine()
        audioEngine = engine

        val node = engine.inputNode
        val hardwareFormat = node.outputFormatForBus(0u)
        val recordingFormat = if (hardwareFormat.sampleRate > 0.0 && hardwareFormat.channelCount > 0u) {
            hardwareFormat
        } else {
            Log.w(TAG) {
                "Invalid input format (${hardwareFormat.sampleRate}Hz, ${hardwareFormat.channelCount}ch), falling back to 48kHz mono"
            }
            AVAudioFormat(48000.0, 1u)
        }
        Log.i(TAG) {
            "Audio engine tap format: ${recordingFormat.sampleRate}Hz, ${recordingFormat.channelCount}ch"
        }

        val request = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = true
        }
        recognitionRequest = request

        node.installTapOnBus(0u, 1024u, recordingFormat) { buffer, _ ->
            buffer?.let {
                request.appendAudioPCMBuffer(it)
                reportAudioLevel(it)
            }
        }

        engine.prepare()
        memScoped {
            val startError = alloc<ObjCObjectVar<NSError?>>()
            if (!engine.startAndReturnError(startError.ptr)) {
                Log.e(TAG) { "Audio engine failed to start: ${startError.value?.localizedDescription}" }
            }
        }

        delegate = SpeechRecognitionDelegate(listener)
        isListening = true
        startRecognitionTask()
        reportExternalMic()
    }

    private fun startRecognitionTask() {
        if (recognitionTask != null) return
        val request = recognitionRequest ?: return
        val rec = recognizer ?: return
        val d = delegate ?: return
        recognitionTask = rec.recognitionTaskWithRequest(request, d)
    }

    private fun reportAudioLevel(buffer: AVAudioPCMBuffer) {
        val frameCount = buffer.frameLength
        if (frameCount == 0u || buffer.format.channelCount == 0u) return
        val channels = buffer.floatChannelData ?: return
        val channel = channels[0] ?: return

        var sum = 0.0
        for (i in 0 until frameCount.toInt()) {
            val v = channel[i].toDouble()
            sum += v * v
        }
        val rms = sqrt(sum / frameCount.toInt())
        val level = levelFromRms(rms, maxAmplitude = 1.0)
        if (!hasReportedAudioLevel && level > 0.01f) {
            hasReportedAudioLevel = true
            Log.i(TAG) { "First audio level received (rms=$rms, level=$level)" }
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

    override fun destroy() {
        recognitionTask?.cancel()
        recognitionTask = null
        recognitionRequest = null
        audioEngine?.inputNode?.removeTapOnBus(0u)
        audioEngine?.stop()
        audioEngine = null
        delegate = null
        isListening = false
        pendingPermissionRequest = false
        hasReportedAudioLevel = false
        smoothedLevel = 0f
        listener?.onAudioLevelChanged(0f)
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
            Log.e(TAG) { "Speech recognition task failed" }
            speechListener?.onError("Recognition task failed")
        }
    }
}
