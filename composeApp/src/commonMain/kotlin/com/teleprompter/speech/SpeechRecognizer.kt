package com.teleprompter.speech

interface SpeechListener {
    fun onPartialResult(text: String)
    fun onError(error: String)
}

interface SpeechRecognizer {
    fun setListener(listener: SpeechListener?)
    fun startListening()
    fun stopListening()
    fun destroy()
}
