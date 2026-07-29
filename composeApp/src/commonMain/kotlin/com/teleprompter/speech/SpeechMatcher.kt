package com.teleprompter.speech

data class MatchResult(
    val newWordIndex: Int,
    val isMatching: Boolean
)

object SpeechMatcher {
    private val fillers = setOf(
        "um", "uh", "ah", "er", "like", "well", "so", "actually",
        "basically", "literally", "you", "know", "i", "mean"
    )

    fun match(
        speechText: String,
        scriptWords: List<String>,
        currentIndex: Int
    ): MatchResult {
        if (currentIndex >= scriptWords.size) {
            return MatchResult(scriptWords.size, true)
        }

        val speechWords = speechText
            .lowercase()
            .trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() && it.length > 1 }

        if (speechWords.isEmpty()) {
            return MatchResult(currentIndex, true)
        }

        var matchIndex = currentIndex
        var matchedCount = 0

        for (word in speechWords) {
            if (matchIndex >= scriptWords.size) break

            val scriptWord = scriptWords[matchIndex].lowercase().trimEnd(',', '.', '!', '?', ';', ':')

            when {
                word == scriptWord -> {
                    matchedCount++
                    matchIndex++
                }
                word.length > 3 && (word.startsWith(scriptWord) || scriptWord.startsWith(word)) -> {
                    matchedCount++
                    matchIndex++
                }
                word in fillers -> {
                    // skip filler words
                }
                else -> {
                    // word doesn't match — could be ad-lib
                    if (matchedCount.toFloat() / maxOf(1, speechWords.size) < 0.3f) {
                        return MatchResult(currentIndex, false)
                    }
                }
            }
        }

        val ratio = matchedCount.toFloat() / maxOf(1, speechWords.size)
        return MatchResult(
            newWordIndex = matchIndex,
            isMatching = ratio >= 0.25f
        )
    }
}
