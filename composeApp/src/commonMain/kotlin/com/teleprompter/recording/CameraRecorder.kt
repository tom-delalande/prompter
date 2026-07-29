package com.teleprompter.recording

interface RecordingListener {
    fun onRecordingStarted()
    fun onRecordingFinished(filePath: String)
    fun onError(error: String)
}

interface CameraRecorder {
    fun setListener(listener: RecordingListener?)
    fun startRecording(filePath: String)
    fun stopRecording()
    fun switchCamera()
    fun destroy()
}
