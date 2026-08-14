package co.delalande.prompter.model

import kotlinx.serialization.Serializable

@Serializable
data class Script(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long = 0L,
) {
    val words: List<String>
        get() = WORD_REGEX.findAll(content.trim()).map { it.value }.toList()

    /**
     * For each word index [i], whether a line break should be rendered after
     * [words][i]. Consecutive line breaks in the source content are collapsed
     * into a single one. The flat [words] list stays the source of truth for
     * reading progress, so the displayed line breaks never affect the current
     * word index.
     */
    val lineBreakAfterWord: List<Boolean>
        get() {
            val text = content.trim()
            val matches = WORD_REGEX.findAll(text).toList()
            return List(matches.size) { i ->
                if (i >= matches.size - 1) {
                    false
                } else {
                    val separator = text.substring(matches[i].range.last + 1, matches[i + 1].range.first)
                    separator.contains('\n')
                }
            }
        }

    fun wordCount(): Int = words.size

    private companion object {
        val WORD_REGEX = Regex("\\S+")
    }
}
