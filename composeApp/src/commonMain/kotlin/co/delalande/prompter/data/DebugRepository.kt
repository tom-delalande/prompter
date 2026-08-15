package co.delalande.prompter.data

import co.delalande.prompter.log.Log
import co.delalande.prompter.model.DebugSettings

class DebugRepository(storage: PlatformStorage) {
    private val json = JsonStorage(storage)
    private var cached: DebugSettings? = null

    fun load(): DebugSettings {
        if (cached == null) {
            cached = json.loadOr(DEBUG_FILE, DebugSettings())
            Log.verboseEnabled = cached!!.verboseLogging
        }
        return cached!!
    }

    fun save(settings: DebugSettings) {
        cached = settings
        Log.verboseEnabled = settings.verboseLogging
        Log.d(TAG) { "Debug settings saved: $settings" }
        json.save(DEBUG_FILE, settings)
    }

    companion object {
        private const val TAG = "DebugRepo"
        private const val DEBUG_FILE = "debug.json"
    }
}
