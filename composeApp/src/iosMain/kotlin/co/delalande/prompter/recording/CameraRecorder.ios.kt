package co.delalande.prompter.recording

import co.delalande.prompter.log.Log
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectMake
import platform.Foundation.*
import platform.darwin.NSObject
import platform.UIKit.*

private const val TAG = "IOSCamera"

@OptIn(ExperimentalForeignApi::class)
class IOSCameraRecorder : CameraRecorder {

    private var captureSession: AVCaptureSession? = null
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var movieOutput: AVCaptureMovieFileOutput? = null
    private var listener: RecordingListener? = null
    private var currentOutputPath: String? = null
    private var delegateObj: RecordingDelegate? = null
    private var isFrontCamera = true

    private val previewViewImpl = PreviewView()
    val previewView: UIView get() = previewViewImpl

    override fun setListener(listener: RecordingListener?) {
        this.listener = listener
    }

    fun setUp() {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        when (status) {
            AVAuthorizationStatusAuthorized -> startSession()
            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    if (granted) {
                        startSession()
                    } else {
                        Log.w(TAG) { "Camera permission denied" }
                    }
                }
            }
            else -> Log.w(TAG) { "Camera permission not granted; preview unavailable" }
        }
    }

    private fun startSession() {
        if (captureSession != null) return
        val session = AVCaptureSession()
        session.sessionPreset = AVCaptureSessionPresetHigh
        captureSession = session

        val layer = AVCaptureVideoPreviewLayer(session = session)
        layer.videoGravity = AVLayerVideoGravityResizeAspectFill
        previewLayer = layer
        previewViewImpl.previewLayer = layer
        previewViewImpl.layer.addSublayer(layer)
        previewViewImpl.setNeedsLayout()

        delegateObj = RecordingDelegate()

        val audioSession = AVAudioSession.sharedInstance()
        audioSession.setCategory(AVAudioSessionCategoryRecord, 0uL, null)
        audioSession.setActive(true, null)

        configureCamera()
        configureAudio()
        configureOutput()

        session.startRunning()
        Log.i(TAG) { "Capture session started" }
    }

    private fun configureCamera() {
        val position = if (isFrontCamera) AVCaptureDevicePositionFront else AVCaptureDevicePositionBack
        val device = AVCaptureDevice.defaultDeviceWithDeviceType(
            AVCaptureDeviceTypeBuiltInWideAngleCamera,
            AVMediaTypeVideo,
            position
        ) ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return
        captureSession?.addInput(input)
    }

    private fun configureAudio() {
        val session = AVAudioSession.sharedInstance()
        when (session.recordPermission) {
            AVAudioSessionRecordPermissionGranted -> addAudioInput()
            AVAudioSessionRecordPermissionUndetermined -> {
                session.requestRecordPermission { granted ->
                    if (granted) {
                        Log.i(TAG) { "Mic permission granted; adding audio input" }
                        addAudioInput()
                    } else {
                        Log.w(TAG) { "Mic permission denied; recording without audio" }
                    }
                }
            }
            else -> Log.w(TAG) { "Mic permission denied; recording without audio" }
        }
    }

    private fun addAudioInput() {
        val session = captureSession ?: return
        val hasAudio = session.inputs.any { input ->
            (input as? AVCaptureDeviceInput)?.device?.hasMediaType(AVMediaTypeAudio) == true
        }
        if (hasAudio) return

        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio) ?: return
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
        if (input == null) {
            Log.e(TAG) { "Failed to create audio input (microphone unavailable)" }
            return
        }
        session.beginConfiguration()
        session.addInput(input)
        session.commitConfiguration()
        Log.i(TAG) { "Audio input added to capture session" }
    }

    private fun configureOutput() {
        val output = AVCaptureMovieFileOutput()
        captureSession?.addOutput(output)
        movieOutput = output
    }

    override fun startRecording(filePath: String) {
        val documentsDir = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: return

        val fullPath = "$documentsDir/$filePath"
        currentOutputPath = fullPath
        val outputURL = NSURL.fileURLWithPath(fullPath)

        val delegate = delegateObj ?: return
        delegate.listener = listener
        movieOutput?.startRecordingToOutputFileURL(outputURL, delegate)
    }

    override fun stopRecording() {
        movieOutput?.stopRecording()
    }

    override fun switchCamera() {
        isFrontCamera = !isFrontCamera
        val session = captureSession ?: return
        session.beginConfiguration()
        session.inputs.forEach { input ->
            val deviceInput = input as? AVCaptureDeviceInput ?: return@forEach
            if (deviceInput.device.hasMediaType(AVMediaTypeVideo)) {
                session.removeInput(deviceInput)
            }
        }
        configureCamera()
        session.commitConfiguration()
        Log.i(TAG) { "Switched to ${if (isFrontCamera) "front" else "back"} camera" }
    }

    override fun destroy() {
        stopRecording()
        captureSession?.stopRunning()
        captureSession = null
        previewLayer?.removeFromSuperlayer()
        previewLayer = null
        previewViewImpl.previewLayer = null
        delegateObj = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PreviewView : UIView(CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var previewLayer: AVCaptureVideoPreviewLayer? = null

    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer?.setFrame(bounds)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class RecordingDelegate : NSObject(), AVCaptureFileOutputRecordingDelegateProtocol {
    var listener: RecordingListener? = null

    override fun captureOutput(
        output: AVCaptureFileOutput,
        didStartRecordingToOutputFileAtURL: NSURL,
        fromConnections: List<*>
    ) {
        listener?.onRecordingStarted()
    }

    override fun captureOutput(
        output: AVCaptureFileOutput,
        didFinishRecordingToOutputFileAtURL: NSURL,
        fromConnections: List<*>,
        error: NSError?
    ) {
        if (error != null) {
            Log.e(TAG) { "Recording finished with error: ${error.localizedDescription}" }
            listener?.onError("Recording error: ${error.localizedDescription}")
        } else {
            val path = didFinishRecordingToOutputFileAtURL.path ?: return
            listener?.onRecordingFinished(path)
        }
    }
}
