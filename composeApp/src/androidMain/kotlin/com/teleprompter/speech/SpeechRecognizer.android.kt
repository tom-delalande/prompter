package com.teleprompter.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidSpeechRecognizer

class AndroidSpeechRecognizer(private val context: Context) : SpeechRecognizer {

    private val recognizer: AndroidSpeechRecognizer =
        AndroidSpeechRecognizer.createSpeechRecognizer(context)

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
    }

    private var listener: SpeechListener? = null

    private val androidListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val msg = when (error) {
                AndroidSpeechRecognizer.ERROR_NETWORK -> "Network error"
                AndroidSpeechRecognizer.ERROR_AUDIO -> "Audio error"
                AndroidSpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                AndroidSpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Error: $error"
            }
            listener?.onError(msg)
        }

        override fun onResults(results: Bundle?) {
            val texts = results?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
            texts?.firstOrNull()?.let { listener?.onPartialResult(it) }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val texts = partialResults?.getStringArrayList(AndroidSpeechRecognizer.RESULTS_RECOGNITION)
            texts?.firstOrNull()?.let { listener?.onPartialResult(it) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun setListener(listener: SpeechListener?) {
        this.listener = listener
    }

    override fun startListening() {
        recognizer.setRecognitionListener(androidListener)
        recognizer.startListening(intent)
    }

    override fun stopListening() {
        recognizer.stopListening()
    }

    override fun destroy() {
        recognizer.destroy()
    }
}
