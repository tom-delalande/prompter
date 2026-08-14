package co.delalande.prompter.speech

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

private const val SAMPLE_RATE = 16000
private const val RMS_THRESHOLD = 30.0
private const val MIN_SPEAKING_MS = 200L
private const val MIN_SILENCE_MS = 400L

class VoiceActivityRecognizer : SpeechRecognizer {

    private var listener: SpeechListener? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private val executor = Executors.newSingleThreadExecutor()

    private var isSpeaking = false
    private var voiceStartTime = 0L
    private var silenceStartTime = 0L
    private var buffer = ByteArrayOutputStream()
    private var smoothedLevel = 0f
    private var lastExternalReported = false

    override fun setListener(listener: SpeechListener?) {
        this.listener = listener
    }

    override fun startListening() {
        if (isListening) return
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            4096
        )
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            listener?.onError("Audio buffer error")
            return
        }
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            listener?.onError("AudioRecord failed to initialize")
            return
        }
        audioRecord = record
        buffer = ByteArrayOutputStream()
        smoothedLevel = 0f
        record.startRecording()
        isListening = true
        isSpeaking = false
        executor.submit { captureLoop(bufferSize) }
    }

    private fun captureLoop(bufferSize: Int) {
        val buf = ShortArray(bufferSize / 2)
        val byteBuf = ByteArray(bufferSize)

        while (isListening) {
            val bytesRead = audioRecord?.read(byteBuf, 0, byteBuf.size) ?: -1
            if (bytesRead <= 0) continue

            buffer.write(byteBuf, 0, bytesRead)

            val sampleCount = bytesRead / 2
            var sumSquares = 0.0
            for (i in 0 until sampleCount) {
                val sample = (byteBuf[i * 2].toInt() and 0xFF) or (byteBuf[i * 2 + 1].toInt() shl 8)
                sumSquares += (sample.toDouble() * sample.toDouble())
            }
            val rms = sqrt(sumSquares / sampleCount)

            val level = AudioInput.levelFromRms(rms)
            smoothedLevel = smoothedLevel * 0.6f + level * 0.4f
            listener?.onAudioLevelChanged(smoothedLevel)

            val external = AudioInput.isExternalDevice(audioRecord?.routedDevice)
            if (external != lastExternalReported) {
                lastExternalReported = external
                listener?.onExternalMicrophoneChanged(external)
            }

            val now = System.currentTimeMillis()
            val currentlyActive = rms > RMS_THRESHOLD

            if (currentlyActive) {
                silenceStartTime = 0L
                if (!isSpeaking) {
                    if (voiceStartTime == 0L) voiceStartTime = now
                    if (now - voiceStartTime >= MIN_SPEAKING_MS) {
                        isSpeaking = true
                        voiceStartTime = 0L
                        silenceStartTime = 0L
                        listener?.onVoiceActivityChanged(true)
                    }
                }
            } else {
                voiceStartTime = 0L
                if (isSpeaking) {
                    if (silenceStartTime == 0L) silenceStartTime = now
                    if (now - silenceStartTime >= MIN_SILENCE_MS) {
                        isSpeaking = false
                        silenceStartTime = 0L
                        listener?.onVoiceActivityChanged(false)
                    }
                }
            }
        }
    }

    override fun stopListening() {
        isListening = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        if (isSpeaking) {
            isSpeaking = false
            listener?.onVoiceActivityChanged(false)
        }
    }

    override fun destroy() {
        stopListening()
        executor.shutdown()
    }

    fun getRecordedAudio(): ByteArray = buffer.toByteArray()
}
