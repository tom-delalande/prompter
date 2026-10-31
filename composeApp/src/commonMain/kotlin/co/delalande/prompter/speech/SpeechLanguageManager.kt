package co.delalande.prompter.speech

import kotlinx.coroutines.flow.Flow

const val DEFAULT_SPEECH_LANGUAGE_TAG = "en-US"

data class SpeechLanguage(
    val localeTag: String,
    val displayName: String,
)

enum class SpeechLanguageStatus {
    Installed,
    Downloadable,
    Downloading,
    Unavailable,
}

data class SpeechLanguageAvailability(
    val language: SpeechLanguage,
    val status: SpeechLanguageStatus,
)

sealed interface SpeechModelDownload {
    data class Started(val totalBytes: Long) : SpeechModelDownload
    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : SpeechModelDownload
    data object Completed : SpeechModelDownload
    data class Failed(val message: String) : SpeechModelDownload
}

interface SpeechLanguageManager {
    val canManageDownloads: Boolean
    val canDeleteDownloads: Boolean
    suspend fun getLanguages(): List<SpeechLanguageAvailability>
    fun download(localeTag: String): Flow<SpeechModelDownload>

    /**
     * Returns null when the model was deleted, or a user-facing explanation when
     * the platform does not expose model deletion to third-party applications.
     */
    suspend fun delete(localeTag: String): String?
}

val SupportedSpeechLanguages = listOf(
    SpeechLanguage("en-US", "English"),
    SpeechLanguage("fr-FR", "French"),
    SpeechLanguage("de-DE", "German"),
    SpeechLanguage("it-IT", "Italian"),
    SpeechLanguage("es-ES", "Spanish"),
    SpeechLanguage("pt-BR", "Portuguese"),
    SpeechLanguage("hi-IN", "Hindi"),
    SpeechLanguage("ja-JP", "Japanese"),
    SpeechLanguage("ko-KR", "Korean"),
    SpeechLanguage("pl-PL", "Polish"),
    SpeechLanguage("ru-RU", "Russian"),
    SpeechLanguage("tr-TR", "Turkish"),
    SpeechLanguage("vi-VN", "Vietnamese"),
    SpeechLanguage("cmn-Hans-CN", "Simplified Chinese"),
    SpeechLanguage("cmn-Hant-TW", "Traditional Chinese"),
)

fun speechLanguageLabel(localeTag: String?): String {
    val effectiveTag = localeTag ?: DEFAULT_SPEECH_LANGUAGE_TAG
    return SupportedSpeechLanguages.firstOrNull { it.localeTag == effectiveTag }?.displayName
        ?: effectiveTag
}
