package co.delalande.prompter.data

import co.delalande.prompter.log.Log
import co.delalande.prompter.model.DebugSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DebugRepository(private val storage: PlatformStorage) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cached: DebugSettings? = null

    fun load(): DebugSettings {
        if (cached == null) {
            val data = storage.readFile(DEBUG_FILE)
            cached = if (data != null) {
                try {
                    json.decodeFromString<DebugSettings>(data)
                } catch (_: Exception) {
                    DebugSettings()
                }
            } else {
                DebugSettings()
            }
            Log.verboseEnabled = cached!!.verboseLogging
        }
        return cached!!
    }

    fun save(settings: DebugSettings) {
        cached = settings
        Log.verboseEnabled = settings.verboseLogging
        Log.d(TAG) { "Debug settings saved: $settings" }
        storage.writeFile(DEBUG_FILE, json.encodeToString(settings))
    }

    companion object {
        private const val TAG = "DebugRepo"
        private const val DEBUG_FILE = "debug.json"
    }
}
