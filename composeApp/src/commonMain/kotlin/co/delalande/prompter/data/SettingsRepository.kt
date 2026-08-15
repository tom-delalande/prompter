package co.delalande.prompter.data

import co.delalande.prompter.model.Settings

class SettingsRepository(storage: PlatformStorage) {
    private val json = JsonStorage(storage)
    private var cached: Settings? = null

    fun load(): Settings {
        if (cached == null) {
            cached = json.loadOr(SETTINGS_FILE, Settings())
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
