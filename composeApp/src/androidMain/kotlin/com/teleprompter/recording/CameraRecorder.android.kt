package com.teleprompter.recording

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
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
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        return windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
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
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()

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
                println("STOPFLOW: CameraX recordEvent=${recordEvent::class.simpleName}")
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        println("STOPFLOW: CameraX Start event")
                        mainHandler.post { listener?.onRecordingStarted() }
                    }
                    is VideoRecordEvent.Finalize -> {
                        val path = currentOutputPath ?: file.absolutePath
                        println("STOPFLOW: CameraX Finalize event hasError=${recordEvent.hasError()} error=${recordEvent.error} outputPath=$path")
                        val f = File(path)
                        println("STOPFLOW: CameraX output file exists=${f.exists()} length=${f.length()}")
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
        println("STOPFLOW: AndroidCameraRecorder.stopRecording activeRecording=$activeRecording")
        activeRecording?.close()
        activeRecording = null
        println("STOPFLOW: AndroidCameraRecorder.stopRecording done")
    }

    override fun switchCamera() {
        isFrontCamera = !isFrontCamera
        bindCamera()
    }

    override fun destroy() {
        activeRecording?.close()
        cameraProvider?.unbindAll()
        cameraProvider = null
        executor.shutdown()
    }
}
