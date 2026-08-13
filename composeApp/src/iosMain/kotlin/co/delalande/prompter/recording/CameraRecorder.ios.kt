package co.delalande.prompter.recording

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.Foundation.*
import platform.darwin.NSObject
import platform.UIKit.*

@OptIn(ExperimentalForeignApi::class)
class IOSCameraRecorder : CameraRecorder {

    private var captureSession: AVCaptureSession? = null
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var movieOutput: AVCaptureMovieFileOutput? = null
    private var listener: RecordingListener? = null
    private var currentOutputPath: String? = null
    private var delegateObj: RecordingDelegate? = null

    val previewView = UIView()

    override fun setListener(listener: RecordingListener?) {
        this.listener = listener
    }

    fun setUp() {
        val session = AVCaptureSession()
        session.sessionPreset = AVCaptureSessionPresetHigh
        captureSession = session

        val layer = AVCaptureVideoPreviewLayer(sessionWithNoConnection = session)
        layer.setFrame(previewView.bounds)
        previewLayer = layer
        previewView.layer.addSublayer(layer)

        delegateObj = RecordingDelegate()

        configureCamera()
        configureAudio()
        configureOutput()

        session.startRunning()
    }

    private fun configureCamera() {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return
        captureSession?.addInput(input)
    }

    private fun configureAudio() {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio) ?: return
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return
        captureSession?.addInput(input)
    }

    private fun configureOutput() {
        val output = AVCaptureMovieFileOutput()
        captureSession?.addOutput(output)
        movieOutput = output
    }

    override fun startRecording(outputPath: String) {
        val documentsDir = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: return

        val filePath = "$documentsDir/$outputPath"
        currentOutputPath = filePath
        val outputURL = NSURL.fileURLWithPath(filePath)

        val delegate = delegateObj ?: return
        delegate.listener = listener
        movieOutput?.startRecordingToOutputFileURL(outputURL, delegate)
    }

    override fun stopRecording() {
        movieOutput?.stopRecording()
    }

    override fun switchCamera() {
        captureSession?.beginConfiguration()
        captureSession?.inputs?.forEach { input ->
            (input as? AVCaptureInput)?.let { captureSession?.removeInput(it) }
        }
        captureSession?.commitConfiguration()
        configureCamera()
    }

    override fun destroy() {
        stopRecording()
        captureSession?.stopRunning()
        captureSession = null
        previewLayer?.removeFromSuperlayer()
        previewLayer = null
        delegateObj = null
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
            listener?.onError("Recording error: ${error.localizedDescription}")
        } else {
            val path = didFinishRecordingToOutputFileAtURL.path ?: return
            listener?.onRecordingFinished(path)
        }
    }
}
