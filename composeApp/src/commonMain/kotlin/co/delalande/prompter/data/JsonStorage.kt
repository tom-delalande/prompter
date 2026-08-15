package co.delalande.prompter.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Thin JSON read/write wrapper over [PlatformStorage], shared by the repositories. */
internal class JsonStorage(private val storage: PlatformStorage) {
    private val json = Json { ignoreUnknownKeys = true }

    inline fun <reified T> loadOr(fileName: String, default: T): T {
        val data = storage.readFile(fileName) ?: return default
        return try {
            json.decodeFromString<T>(data)
        } catch (_: Exception) {
            default
        }
    }

    inline fun <reified T> save(fileName: String, value: T) {
        storage.writeFile(fileName, json.encodeToString(value))
    }
}
