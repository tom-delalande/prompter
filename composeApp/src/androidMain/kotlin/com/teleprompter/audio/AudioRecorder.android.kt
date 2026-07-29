package com.teleprompter.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

private const val SAMPLE_RATE = 44100

class AndroidAudioRecorder(private val outputDir: File) : AudioRecorder {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var outputFile: File? = null
    private var listener: AudioRecorderListener? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun setListener(listener: AudioRecorderListener?) {
        this.listener = listener
    }

    override fun startRecording(outputPath: String) {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            4096
        )
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            listener?.onError("Failed to get audio buffer size")
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
        outputFile = File(outputDir, outputPath)
        record.startRecording()
        isRecording = true
        listener?.onRecordingStarted()
        executor.submit { writeAudioLoop(bufferSize) }
    }

    private fun writeAudioLoop(bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        val file = outputFile ?: return
        try {
            val chunks = mutableListOf<ByteArray>()
            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (bytesRead > 0) {
                    chunks.add(buffer.copyOf(bytesRead))
                }
            }
            writeWavFile(file, chunks)
            listener?.onRecordingFinished(file.absolutePath)
        } catch (e: Exception) {
            listener?.onError("Audio recording error: ${e.message}")
        }
    }

    private fun writeWavFile(file: File, chunks: List<ByteArray>) {
        val totalDataSize = chunks.sumOf { it.size }
        RandomAccessFile(file, "rw").use { raf ->
            raf.write("RIFF".toByteArray())
            raf.write(intToLE(totalDataSize + 36))
            raf.write("WAVE".toByteArray())
            raf.write("fmt ".toByteArray())
            raf.write(intToLE(16))
            raf.write(shortToLE(1))
            raf.write(shortToLE(1))
            raf.write(intToLE(SAMPLE_RATE))
            raf.write(intToLE(SAMPLE_RATE * 2))
            raf.write(shortToLE(2))
            raf.write(shortToLE(16))
            raf.write("data".toByteArray())
            raf.write(intToLE(totalDataSize))
            chunks.forEach { raf.write(it) }
        }
    }

    private fun intToLE(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    private fun shortToLE(v: Short) = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array()

    override fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun destroy() {
        stopRecording()
        executor.shutdown()
    }
}

class AndroidAudioVideoMuxer : AudioVideoMuxer {
    override fun merge(audioWavPath: String, videoPath: String, outputPath: String) {
        val videoExtractor = MediaExtractor().apply { setDataSource(videoPath) }
        val videoTrackInfo = videoExtractor.let {
            it.selectTrack(0)
            TrackInfo(it.getTrackFormat(0), it)
        }

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val videoTrack = muxer.addTrack(videoTrackInfo.format)

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            },
            null, null, MediaCodec.CONFIGURE_FLAG_ENCODE
        )
        encoder.start()

        val wavData = RandomAccessFile(audioWavPath, "r").use { raf ->
            val data = ByteArray((raf.length() - 44).toInt())
            raf.seek(44); raf.readFully(data); data
        }

        val inputBuffers = encoder.inputBuffers
        val outputBuffers = encoder.outputBuffers
        val bufferInfo = MediaCodec.BufferInfo()

        var pcmOffset = 0
        var audioTrack = -1
        var muxerStarted = false
        var eosSent = false
        var eosReceived = false

        while (!eosReceived) {
            if (!eosSent) {
                if (pcmOffset < wavData.size) {
                    val idx = encoder.dequeueInputBuffer(10000L)
                    if (idx >= 0) {
                        val buf = inputBuffers[idx]!!
                        buf.clear()
                        val remaining = wavData.size - pcmOffset
                        val chunkSize = minOf(buf.capacity(), remaining)
                        val pts = pcmOffset / 2L * 1_000_000L / SAMPLE_RATE
                        buf.put(wavData, pcmOffset, chunkSize)
                        encoder.queueInputBuffer(idx, 0, chunkSize, pts, 0)
                        pcmOffset += chunkSize
                    }
                } else {
                    val idx = encoder.dequeueInputBuffer(10000L)
                    if (idx >= 0) {
                        encoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eosSent = true
                    }
                }
            }

            val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10000L)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    audioTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outIdx >= 0 -> {
                    if (muxerStarted) {
                        val buf = outputBuffers[outIdx]!!
                        buf.position(bufferInfo.offset)
                        buf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(audioTrack, buf, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        eosReceived = true
                    }
                }
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (eosSent) Thread.sleep(5)
                }
            }
        }

        encoder.stop()
        encoder.release()

        // Write video frames
        val videoBuf = ByteBuffer.allocate(1024 * 1024)
        while (true) {
            videoBuf.clear()
            val sampleSize = videoTrackInfo.extractor.readSampleData(videoBuf, 0)
            if (sampleSize < 0) break
            videoBuf.rewind(); videoBuf.limit(sampleSize)
            bufferInfo.set(0, sampleSize, videoTrackInfo.extractor.sampleTime, 0)
            muxer.writeSampleData(videoTrack, videoBuf, bufferInfo)
            videoTrackInfo.extractor.advance()
        }

        videoTrackInfo.extractor.release()
        muxer.stop()
        muxer.release()
        File(audioWavPath).delete()
    }

    private data class TrackInfo(val format: MediaFormat, val extractor: MediaExtractor)
}

actual fun createAudioVideoMuxer(): AudioVideoMuxer = AndroidAudioVideoMuxer()
