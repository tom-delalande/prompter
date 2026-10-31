package co.delalande.prompter.data

import co.delalande.prompter.model.Script
import kotlin.time.Clock

class ScriptRepository(storage: PlatformStorage) {
    private val json = JsonStorage(storage)
    private var scripts: MutableList<Script> = mutableListOf()
    private var loaded = false

    fun loadAll(): List<Script> {
        if (!loaded) {
            scripts = json.loadOr(SCRIPTS_FILE, emptyList<Script>()).toMutableList()
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
        val now = Clock.System.now().toEpochMilliseconds()
        val index = scripts.indexOfFirst { it.id == script.id }
        if (index >= 0) {
            val existing = scripts[index]
            if (existing.title == script.title && existing.content == script.content) {
                persist()
                return false
            }
            scripts[index] = script.copy(updatedAt = now)
        } else {
            scripts.add(script.copy(updatedAt = now))
        }
        persist()
        return true
    }

    fun delete(id: String) {
        loadAll()
        scripts.removeAll { it.id == id }
        persist()
    }

    private fun persist() {
        json.save(SCRIPTS_FILE, scripts)
    }

    companion object {
        private const val SCRIPTS_FILE = "scripts.json"
    }
}
