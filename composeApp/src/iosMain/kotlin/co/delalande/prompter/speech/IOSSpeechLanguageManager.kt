package co.delalande.prompter.speech

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** iOS downloads and evicts speech assets itself and exposes no app-level model manager. */
class IOSSpeechLanguageManager : SpeechLanguageManager {
    override val canManageDownloads: Boolean = false
    override val canDeleteDownloads: Boolean = false

    override suspend fun getLanguages(): List<SpeechLanguageAvailability> =
        SupportedSpeechLanguages.map { SpeechLanguageAvailability(it, SpeechLanguageStatus.Installed) }

    override fun download(localeTag: String): Flow<SpeechModelDownload> =
        flowOf(SpeechModelDownload.Failed("Speech language downloads are managed by iOS."))

    override suspend fun delete(localeTag: String): String =
        "Speech language downloads are managed automatically by iOS."
}
