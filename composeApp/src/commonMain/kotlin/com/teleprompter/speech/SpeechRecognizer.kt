package com.teleprompter.speech

interface SpeechListener {
    fun onPartialResult(text: String)
    fun onError(error: String)
    fun onVoiceActivityChanged(isActive: Boolean) = Unit
    fun onAudioRecorded(path: String) = Unit
}

interface SpeechRecognizer {
    fun setListener(listener: SpeechListener?)
    fun startListening()
    fun stopListening()
    fun destroy()
    fun setAudioOutputPath(path: String) = Unit
}
