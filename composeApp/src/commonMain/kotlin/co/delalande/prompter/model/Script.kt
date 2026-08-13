package co.delalande.prompter.model

import kotlinx.serialization.Serializable

@Serializable
data class Script(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long = 0L
) {
    val words: List<String>
        get() = content.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }

    fun wordCount(): Int = words.size
}
