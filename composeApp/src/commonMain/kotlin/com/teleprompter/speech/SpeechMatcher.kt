package com.teleprompter.speech

data class MatchResult(
    val newWordIndex: Int,
    val isMatching: Boolean
)

object SpeechMatcher {
    private val fillers = setOf(
        "um", "uh", "ah", "er", "hmm"
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
            .filter { it.isNotBlank() }

        if (speechWords.isEmpty()) {
            return MatchResult(currentIndex, true)
        }

        val directResult = tryMatch(speechWords, scriptWords, currentIndex)
        if (directResult.newWordIndex > currentIndex) {
            return directResult
        }

        val searchLimit = minOf(currentIndex + 50, scriptWords.size)
        var bestResult = directResult
        for (searchIdx in currentIndex + 1 until searchLimit) {
            val searchResult = tryMatch(speechWords, scriptWords, searchIdx)
            if (searchResult.isMatching && searchResult.newWordIndex > searchIdx) {
                return searchResult
            }
            if (searchResult.isMatching && searchResult.newWordIndex > bestResult.newWordIndex) {
                bestResult = searchResult
            }
        }

        return bestResult
    }

    private fun tryMatch(
        speechWords: List<String>,
        scriptWords: List<String>,
        fromIndex: Int
    ): MatchResult {
        var matchIndex = fromIndex
        var matchedCount = 0

        for (word in speechWords) {
            if (matchIndex >= scriptWords.size) break

            val scriptWord = scriptWords[matchIndex]

            when {
                word in fillers -> { }
                wordsSimilar(word, scriptWord) -> {
                    matchedCount++
                    matchIndex++
                }
                else -> {
                    if (matchedCount.toFloat() / maxOf(1, speechWords.size) < 0.3f) {
                        return MatchResult(fromIndex, false)
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

    private fun wordsSimilar(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)

        if (na == nb) return true
        if (na.isEmpty() || nb.isEmpty()) return false

        if (na.length > 3 && nb.length > 3) {
            if (na.startsWith(nb) || nb.startsWith(na)) return true
        }

        val dist = levenshtein(na, nb)
        val maxEdits = if (na.length <= 3 || nb.length <= 3) 1 else 2
        return dist <= maxEdits
    }

    private fun normalize(word: String): String =
        word.lowercase().filter { it.isLetterOrDigit() }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = i - 1
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1
                )
                prev = temp
            }
        }
        return dp[b.length]
    }
}
