package co.delalande.prompter.recording

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import co.delalande.prompter.model.VideoFrameRate
import co.delalande.prompter.model.VideoResolution
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AndroidCameraRecorder(private val context: Context) : CameraRecorder {

    val previewView = PreviewView(context)

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var isFrontCamera = true
    private var listener: RecordingListener? = null
    private var activeRecording: Recording? = null
    private var currentOutputPath: String? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var videoResolution: VideoResolution = VideoResolution.FHD_1080
    private var videoFrameRate: VideoFrameRate = VideoFrameRate.FPS_30

    override fun configure(resolution: VideoResolution, frameRate: VideoFrameRate) {
        videoResolution = resolution
        videoFrameRate = frameRate
        if (lifecycleOwner != null) {
            bindCamera()
        }
    }

    override fun setListener(listener: RecordingListener?) {
        this.listener = listener
    }

    fun setUp(lifecycleOwner: LifecycleOwner) {
        this.lifecycleOwner = lifecycleOwner
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun getDisplayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display.rotation
        } else {
            @Suppress("DEPRECATION")
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return

        provider.unbindAll()

        val cameraSelector = if (isFrontCamera)
            CameraSelector.DEFAULT_FRONT_CAMERA
        else
            CameraSelector.DEFAULT_BACK_CAMERA

        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val rotation = getDisplayRotation()

        val quality = when (videoResolution) {
            VideoResolution.HD_720 -> Quality.HD
            VideoResolution.FHD_1080 -> Quality.FHD
            VideoResolution.UHD_4K -> Quality.UHD
        }
        val fps = when (videoFrameRate) {
            VideoFrameRate.FPS_24 -> 24
            VideoFrameRate.FPS_30 -> 30
            VideoFrameRate.FPS_60 -> 60
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(quality))
            .build()
        recorder.setVideoEncodingFrameRate(fps)

        videoCapture = VideoCapture.withOutput(recorder)
        videoCapture?.targetRotation = rotation

        try {
            camera = provider.bindToLifecycle(
                owner, cameraSelector, preview, videoCapture
            )
        } catch (e: Exception) {
            listener?.onError("Camera bind failed: ${e.message}")
        }
    }

    override fun startRecording(filePath: String) {
        if (activeRecording != null) {
            listener?.onError("A recording is already in progress")
            return
        }

        val capture = videoCapture ?: run {
            listener?.onError("VideoCapture not initialized")
            return
        }

        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
        val file = File(outputDir, filePath)
        currentOutputPath = file.absolutePath

        val outputOptions = FileOutputOptions.Builder(file).build()

        activeRecording = capture.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()
                .start(executor) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        mainHandler.post { listener?.onRecordingStarted() }
                    }
                    is VideoRecordEvent.Finalize -> {
                        val path = currentOutputPath ?: file.absolutePath
                        mainHandler.post {
                            if (recordEvent.hasError()) {
                                listener?.onError("Recording error: ${recordEvent.error}")
                            } else {
                                listener?.onRecordingFinished(path)
                            }
                        }
                    }
                    else -> {}
                }
            }
    }

    override fun stopRecording() {
        activeRecording?.close()
        activeRecording = null
    }

    override fun destroy() {
        activeRecording?.close()
        activeRecording = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        lifecycleOwner = null
        listener = null
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdown()
    }
}
