package co.delalande.prompter.data

import co.delalande.prompter.model.Script
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class ScriptRepository(private val storage: PlatformStorage) {
    private val json = Json { ignoreUnknownKeys = true }
    private var scripts: MutableList<Script> = mutableListOf()
    private var loaded = false

    fun loadAll(): List<Script> {
        if (!loaded) {
            val data = storage.readFile(SCRIPTS_FILE)
            if (data != null) {
                try {
                    scripts = json.decodeFromString<List<Script>>(data).toMutableList()
                } catch (_: Exception) {
                    scripts = mutableListOf()
                }
            }
            loaded = true
        }
        return scripts.toList()
    }

    fun getById(id: String): Script? {
        loadAll()
        return scripts.find { it.id == id }
    }

    fun save(script: Script): Boolean {
        loadAll()
        var saved = false
        val index = scripts.indexOfFirst { it.id == script.id }
        if (index >= 0) {
            val existing = scripts[index]
            if (existing.words != script.words) {
                saved = true
                scripts[index] = script.copy(updatedAt = Clock.System.now().toEpochMilliseconds())
            }
        } else {
            saved = true
            scripts.add(script.copy(updatedAt = Clock.System.now().toEpochMilliseconds()))
        }
        persist()
        return saved
    }

    fun delete(id: String) {
        loadAll()
        scripts.removeAll { it.id == id }
        persist()
    }

    private fun persist() {
        val data = json.encodeToString(scripts)
        storage.writeFile(SCRIPTS_FILE, data)
    }

    companion object {
        private const val SCRIPTS_FILE = "scripts.json"
    }
}
