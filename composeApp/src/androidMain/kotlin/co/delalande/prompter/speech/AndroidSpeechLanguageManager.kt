package co.delalande.prompter.speech

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Locale

class AndroidSpeechLanguageManager : SpeechLanguageManager {
    override val canManageDownloads: Boolean = true
    override val canDeleteDownloads: Boolean = false

    override suspend fun getLanguages(): List<SpeechLanguageAvailability> = withContext(Dispatchers.IO) {
        SupportedSpeechLanguages.map { language ->
            val client = client(language.localeTag)
            val status = try {
                when (client.checkStatus()) {
                    FeatureStatus.AVAILABLE -> SpeechLanguageStatus.Installed
                    FeatureStatus.DOWNLOADABLE -> SpeechLanguageStatus.Downloadable
                    FeatureStatus.DOWNLOADING -> SpeechLanguageStatus.Downloading
                    else -> SpeechLanguageStatus.Unavailable
                }
            } catch (_: Exception) {
                SpeechLanguageStatus.Unavailable
            } finally {
                client.close()
            }
            SpeechLanguageAvailability(language, status)
        }
    }

    override fun download(localeTag: String): Flow<SpeechModelDownload> = flow {
        val recognizer = client(localeTag)
        try {
            if (recognizer.checkStatus() == FeatureStatus.AVAILABLE) {
                emit(SpeechModelDownload.Completed)
                return@flow
            }
            var totalBytes = 0L
            recognizer.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> {
                        totalBytes = status.bytesToDownload
                        emit(SpeechModelDownload.Started(totalBytes))
                    }
                    is DownloadStatus.DownloadProgress -> emit(
                        SpeechModelDownload.Progress(
                            bytesDownloaded = status.totalBytesDownloaded,
                            totalBytes = totalBytes,
                        )
                    )
                    is DownloadStatus.DownloadCompleted -> emit(SpeechModelDownload.Completed)
                    is DownloadStatus.DownloadFailed -> emit(
                        SpeechModelDownload.Failed(
                            status.e.message ?: "The language could not be downloaded."
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emit(SpeechModelDownload.Failed(e.message ?: "The language could not be downloaded."))
        } finally {
            recognizer.close()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun delete(localeTag: String): String =
        "Android's ML Kit does not currently allow apps to delete speech models. " +
            "Downloaded assets are retained and managed by Android."

    private fun client(localeTag: String) = SpeechRecognition.getClient(
        SpeechRecognizerOptions.Builder().apply {
            locale = Locale.forLanguageTag(localeTag)
            preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC
        }.build()
    )
}
