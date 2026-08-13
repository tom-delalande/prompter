package co.delalande.prompter.speech

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

private const val SAMPLE_RATE = 16000

class WhisperSpeechRecognizer(
    private val apiKey: String
) : SpeechRecognizer {

    private var listener: SpeechListener? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private val executor = Executors.newSingleThreadExecutor()
    private val chunkDurationMs = 1500L
    private var buffer: ByteArrayOutputStream = ByteArrayOutputStream()

    override fun setListener(listener: SpeechListener?) {
        this.listener = listener
    }

    override fun startListening() {
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
        record.startRecording()
        isListening = true
        executor.submit { captureLoop(bufferSize) }
    }

    private fun captureLoop(bufferSize: Int) {
        val buf = ByteArray(bufferSize)
        val bytesPerChunk = (SAMPLE_RATE * 2 * chunkDurationMs / 1000).toInt()
        var chunkBuf = ByteArrayOutputStream()

        while (isListening) {
            val bytesRead = audioRecord?.read(buf, 0, buf.size) ?: -1
            if (bytesRead > 0) {
                buffer.write(buf, 0, bytesRead)
                chunkBuf.write(buf, 0, bytesRead)
                if (chunkBuf.size() >= bytesPerChunk) {
                    val chunk = chunkBuf.toByteArray()
                    chunkBuf = ByteArrayOutputStream()
                    transcribeChunk(chunk)
                }
            }
        }
    }

    private fun transcribeChunk(pcmData: ByteArray) {
        try {
            val wavData = pcmToWav(pcmData)
            val response = uploadToWhisper(wavData)
            if (response.isNotBlank()) {
                listener?.onPartialResult(response.trim())
            }
        } catch (e: Exception) {
            // silently ignore transcription errors
        }
    }

    private fun pcmToWav(pcmData: ByteArray): ByteArray {
        val totalSize = 44 + pcmData.size
        val wav = ByteArrayOutputStream()
        val le = { v: Int -> ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array() }
        val sLe = { v: Short -> ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array() }
        wav.write("RIFF".toByteArray())
        wav.write(le(totalSize - 8))
        wav.write("WAVE".toByteArray())
        wav.write("fmt ".toByteArray())
        wav.write(le(16))
        wav.write(sLe(1))
        wav.write(sLe(1))
        wav.write(le(SAMPLE_RATE))
        wav.write(le(SAMPLE_RATE * 2))
        wav.write(sLe(2))
        wav.write(sLe(16))
        wav.write("data".toByteArray())
        wav.write(le(pcmData.size))
        wav.write(pcmData)
        return wav.toByteArray()
    }

    private fun uploadToWhisper(wavData: ByteArray): String {
        val boundary = "---WhisperBoundary${System.currentTimeMillis()}"
        val url = URL("https://api.openai.com/v1/audio/transcriptions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        val body = ByteArrayOutputStream()
        val writer = DataOutputStream(body)

        writer.writeBytes("--$boundary\r\n")
        writer.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n")
        writer.writeBytes("Content-Type: audio/wav\r\n\r\n")
        writer.write(wavData)
        writer.writeBytes("\r\n")

        writer.writeBytes("--$boundary\r\n")
        writer.writeBytes("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
        writer.writeBytes("whisper-1\r\n")

        writer.writeBytes("--$boundary\r\n")
        writer.writeBytes("Content-Disposition: form-data; name=\"language\"\r\n\r\n")
        writer.writeBytes("en\r\n")

        writer.writeBytes("--$boundary\r\n")
        writer.writeBytes("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n")
        writer.writeBytes("text\r\n")

        writer.writeBytes("--$boundary--\r\n")
        writer.flush()

        conn.connect()
        val outputStream: OutputStream = conn.outputStream
        outputStream.write(body.toByteArray())
        outputStream.flush()

        val responseCode = conn.responseCode
        if (responseCode != 200) {
            val errorStream = conn.errorStream?.bufferedReader()?.readText() ?: ""
            return ""
        }

        return conn.inputStream.bufferedReader().readText()
    }

    override fun stopListening() {
        isListening = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun destroy() {
        stopListening()
        executor.shutdown()
    }

    fun getRecordedAudio(): ByteArray = buffer.toByteArray()
}
