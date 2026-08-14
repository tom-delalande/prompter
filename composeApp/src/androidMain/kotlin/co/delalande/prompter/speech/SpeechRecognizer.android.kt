package co.delalande.prompter.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidSpeechRecognizer
import android.os.Handler
import android.os.Looper

class AndroidSpeechRecognizer(private val context: Context) : SpeechRecognizer {

    private var recognizer: AndroidSpeechRecognizer? = null
    private var listener: SpeechListener? = null
    private var isActive = false
    private val handler = Handler(Looper.getMainLooper())

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
    }

    private val androidListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            restart()
        }

        override fun onError(error: Int) {
            if (!isActive) return
            when (error) {
                AndroidSpeechRecognizer.ERROR_NO_MATCH,
                AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    restart()
                }
                AndroidSpeechRecognizer.ERROR_CLIENT -> {
                    listener?.onError("Speech service unavailable on this device")
                }
                else -> {
                    listener?.onError("Error: $error")
                    restart()
                }
            }
        }

        override fun onResults(results: Bundle?) {
            if (!isActive) return
            val texts = results?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
            texts?.firstOrNull()?.let { listener?.onPartialResult(it) }
            restart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isActive) return
            val texts = partialResults?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
            texts?.firstOrNull()?.let { listener?.onPartialResult(it) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun restart() {
        if (!isActive) return
        handler.postDelayed({
            if (isActive) {
                recognizer?.destroy()
                recognizer = AndroidSpeechRecognizer.createSpeechRecognizer(context)
                recognizer?.setRecognitionListener(androidListener)
                recognizer?.startListening(intent)
            }
        }, 200L)
    }

    override fun setListener(listener: SpeechListener?) {
        this.listener = listener
    }

    override fun startListening() {
        if (isActive) return
        isActive = true
        recognizer = AndroidSpeechRecognizer.createSpeechRecognizer(context)
        if (recognizer == null) {
            listener?.onError("Speech recognition not available on this device")
            isActive = false
            return
        }
        recognizer?.setRecognitionListener(androidListener)
        recognizer?.startListening(intent)
    }

    override fun stopListening() {
        isActive = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }

    override fun destroy() {
        stopListening()
        handler.removeCallbacksAndMessages(null)
    }
}
