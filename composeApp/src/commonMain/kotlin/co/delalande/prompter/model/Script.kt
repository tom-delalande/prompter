package co.delalande.prompter.model

import kotlinx.serialization.Serializable

@Serializable
data class Script(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long = 0L,
) {
    private val parsed by lazy { ParsedScript.parse(content) }

    val words: List<String>
        get() = parsed.words

    /**
     * For each word index [i], whether a line break should be rendered before
     * [words][i] (i.e. the whitespace gap before that word contained a newline).
     * Consecutive line breaks collapse into a single one. Always `false` for
     * `i == 0`. The flat [words] list stays the source of truth for reading
     * progress, so the displayed line breaks never affect the current word index.
     */
    val lineBreakBefore: BooleanArray
        get() = parsed.lineBreakBefore

    fun wordCount(): Int = parsed.words.size
}

private class ParsedScript(
    val words: List<String>,
    val lineBreakBefore: BooleanArray,
) {
    companion object {
        fun parse(content: String): ParsedScript {
            val text = content.trim()
            val words = ArrayList<String>()
            val breaks = ArrayList<Boolean>()
            var i = 0
            while (i < text.length) {
                var hasNewline = false
                while (i < text.length && text[i].isWhitespace()) {
                    if (text[i] == '\n') hasNewline = true
                    i++
                }
                if (i >= text.length) break
                val start = i
                while (i < text.length && !text[i].isWhitespace()) i++
                words.add(text.substring(start, i))
                breaks.add(hasNewline)
            }
            return ParsedScript(words, breaks.toBooleanArray())
        }
    }
}
