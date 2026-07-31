package com.teleprompter.data

import com.teleprompter.model.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsRepository(private val storage: PlatformStorage) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cached: Settings? = null

    fun load(): Settings {
        if (cached == null) {
            val data = storage.readFile(SETTINGS_FILE)
            cached = if (data != null) {
                try {
                    json.decodeFromString<Settings>(data)
                } catch (_: Exception) {
                    Settings()
                }
            } else {
                Settings()
            }
        }
        return cached!!
    }

    fun save(settings: Settings) {
        cached = settings
        storage.writeFile(SETTINGS_FILE, json.encodeToString(settings))
    }

    companion object {
        private const val SETTINGS_FILE = "settings.json"
    }
}
