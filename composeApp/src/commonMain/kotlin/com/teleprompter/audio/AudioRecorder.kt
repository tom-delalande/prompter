package com.teleprompter.audio

interface AudioRecorderListener {
    fun onRecordingStarted()
    fun onRecordingFinished(filePath: String)
    fun onError(error: String)
}

interface AudioRecorder {
    fun setListener(listener: AudioRecorderListener?)
    fun startRecording(outputPath: String)
    fun stopRecording()
    fun destroy()
}

expect fun createAudioVideoMuxer(): AudioVideoMuxer

interface AudioVideoMuxer {
    fun merge(audioFilePath: String, videoFilePath: String, outputFilePath: String)
}
