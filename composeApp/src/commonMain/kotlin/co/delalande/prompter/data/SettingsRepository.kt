package co.delalande.prompter.data

import co.delalande.prompter.model.Settings
import co.delalande.prompter.speech.DEFAULT_SPEECH_LANGUAGE_TAG

class SettingsRepository(storage: PlatformStorage) {
    private val json = JsonStorage(storage)
    private var cached: Settings? = null

    fun load(): Settings {
        if (cached == null) {
            val loaded = json.loadOr(SETTINGS_FILE, Settings())
            cached = if (loaded.preferredSpeechLanguageTag == null) {
                loaded.copy(preferredSpeechLanguageTag = DEFAULT_SPEECH_LANGUAGE_TAG).also {
                    json.save(SETTINGS_FILE, it)
                }
            } else {
                loaded
            }
        }
        return cached!!
    }

    fun save(settings: Settings) {
        cached = settings
        json.save(SETTINGS_FILE, settings)
    }

    companion object {
        private const val SETTINGS_FILE = "settings.json"
    }
}
