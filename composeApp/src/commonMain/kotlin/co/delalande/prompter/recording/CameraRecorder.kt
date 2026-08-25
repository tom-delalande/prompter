package co.delalande.prompter.recording

import co.delalande.prompter.model.VideoFrameRate
import co.delalande.prompter.model.VideoResolution

interface RecordingListener {
    fun onRecordingStarted()
    fun onRecordingFinished(filePath: String)
    fun onError(error: String)
}

interface CameraRecorder {
    fun configure(resolution: VideoResolution, frameRate: VideoFrameRate)
    fun setListener(listener: RecordingListener?)
    fun startRecording(filePath: String)
    fun stopRecording()
    fun switchCamera()
    fun destroy()
}
