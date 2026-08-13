package co.delalande.prompter.speech

interface SpeechListener {
    fun onPartialResult(text: String)
    fun onError(error: String)
    fun onVoiceActivityChanged(isActive: Boolean) = Unit
    fun onAudioRecorded(path: String) = Unit
    fun onAudioLevelChanged(level: Float) = Unit
    fun onExternalMicrophoneChanged(isExternal: Boolean) = Unit
}

interface SpeechRecognizer {
    fun setListener(listener: SpeechListener?)
    fun startListening()
    fun stopListening()
    fun destroy()
    fun setAudioOutputPath(path: String) = Unit
}
