package com.teleprompter.audio

class IOSAudioRecorder : AudioRecorder {
    private var listener: AudioRecorderListener? = null

    override fun setListener(listener: AudioRecorderListener?) {
        this.listener = listener
    }

    override fun startRecording(outputPath: String) {
        listener?.onError("Audio recording not implemented on iOS")
    }

    override fun stopRecording() {}

    override fun destroy() {}
}

actual fun createAudioVideoMuxer(): AudioVideoMuxer = object : AudioVideoMuxer {
    override fun merge(audioFilePath: String, videoFilePath: String, outputFilePath: String) {
        throw UnsupportedOperationException("Audio muxing not implemented on iOS")
    }
}
