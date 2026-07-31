package com.teleprompter.speech

data class MatchResult(
    val newWordIndex: Int,
    val isMatching: Boolean
)

object SpeechMatcher {
    private const val MAX_LOOKAHEAD_WORDS = 30
    private const val MAX_RESYNC_LOOKAHEAD_WORDS = 120
    private const val MAX_ADVANCE_PER_MATCH = 12
    private const val HIGH_CONFIDENCE_MATCH_COUNT = 3
    private const val DIRECT_MATCH_RATIO = 0.5f
    private const val LOOKAHEAD_MATCH_RATIO = 0.75f
    private const val RESYNC_MATCH_RATIO = 0.70f
    private const val MIN_RESYNC_WORDS = 2

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

        val speechWords = tokenize(speechText)

        if (speechWords.isEmpty()) {
            return MatchResult(currentIndex, true)
        }

        val directResult = tryMatch(
            speechWords = speechWords,
            scriptWords = scriptWords,
            fromIndex = currentIndex,
            requiredRatio = DIRECT_MATCH_RATIO
        )
        if (directResult.isMatching) {
            return directResult.toMatchResult(
                currentIndex = currentIndex,
                scriptSize = scriptWords.size,
                capAdvance = true
            )
        }

        var bestResult: CandidateMatch? = null
        if (speechWords.size >= 2) {
            val searchLimit = minOf(currentIndex + MAX_LOOKAHEAD_WORDS, scriptWords.lastIndex)
            for (searchIdx in currentIndex + 1..searchLimit) {
                val searchResult = tryMatch(
                    speechWords = speechWords,
                    scriptWords = scriptWords,
                    fromIndex = searchIdx,
                    requiredRatio = LOOKAHEAD_MATCH_RATIO
                )
                if (searchResult.isMatching && searchResult.matchedCount >= 2) {
                    if (bestResult == null || searchResult.score > bestResult.score) {
                        bestResult = searchResult
                    }
                }
            }
        }

        bestResult?.let {
            return it.toMatchResult(
                currentIndex = currentIndex,
                scriptSize = scriptWords.size,
                capAdvance = it.matchedCount < HIGH_CONFIDENCE_MATCH_COUNT
            )
        }

        val resyncResult = findResyncMatch(
            speechWords = speechWords,
            scriptWords = scriptWords,
            currentIndex = currentIndex
        )
        if (resyncResult != null) {
            return resyncResult.toMatchResult(
                currentIndex = currentIndex,
                scriptSize = scriptWords.size,
                capAdvance = false
            )
        }

        return MatchResult(currentIndex, false)
    }

    private fun findResyncMatch(
        speechWords: List<String>,
        scriptWords: List<String>,
        currentIndex: Int
    ): CandidateMatch? {
        if (speechWords.size < MIN_RESYNC_WORDS) return null

        val searchStart = currentIndex + MAX_LOOKAHEAD_WORDS + 1
        val searchEnd = minOf(currentIndex + MAX_RESYNC_LOOKAHEAD_WORDS, scriptWords.lastIndex)
        if (searchStart > searchEnd) return null

        var bestResult: CandidateMatch? = null
        for (searchIdx in searchStart..searchEnd) {
            val searchResult = tryMatch(
                speechWords = speechWords,
                scriptWords = scriptWords,
                fromIndex = searchIdx,
                requiredRatio = RESYNC_MATCH_RATIO
            )
            if (searchResult.isMatching && searchResult.matchedCount >= MIN_RESYNC_WORDS) {
                if (bestResult == null || searchResult.score > bestResult.score) {
                    bestResult = searchResult
                }
            }
        }

        return bestResult
    }

    private fun tryMatch(
        speechWords: List<String>,
        scriptWords: List<String>,
        fromIndex: Int,
        requiredRatio: Float
    ): CandidateMatch {
        var matchIndex = fromIndex
        var matchedCount = 0
        var missedAfterMatch = 0

        for (word in speechWords) {
            if (matchIndex >= scriptWords.size) break

            val scriptWord = scriptWords[matchIndex]

            when {
                word in fillers -> { }
                wordsSimilar(word, scriptWord) -> {
                    matchedCount++
                    matchIndex++
                    missedAfterMatch = 0
                }
                else -> {
                    if (matchedCount == 0) {
                        return CandidateMatch(fromIndex, 0, 0f, false)
                    }
                    missedAfterMatch++
                    if (missedAfterMatch > 1) {
                        break
                    }
                }
            }
        }

        val ratio = matchedCount.toFloat() / maxOf(1, speechWords.size)
        return CandidateMatch(
            newWordIndex = matchIndex,
            matchedCount = matchedCount,
            score = ratio,
            isMatching = matchedCount > 0 && ratio >= requiredRatio
        )
    }

    private fun tokenize(text: String): List<String> =
        text
            .lowercase()
            .trim()
            .split("\\s+".toRegex())
            .map(::normalize)
            .filter { it.isNotBlank() && it !in fillers }

    private fun CandidateMatch.toMatchResult(
        currentIndex: Int,
        scriptSize: Int,
        capAdvance: Boolean
    ): MatchResult =
        MatchResult(
            newWordIndex = if (capAdvance) {
                newWordIndex.coerceAtMost(
                    minOf(scriptSize, currentIndex + MAX_ADVANCE_PER_MATCH)
                )
            } else {
                newWordIndex.coerceAtMost(scriptSize)
            },
            isMatching = isMatching
        )

    private fun wordsSimilar(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)

        if (na == nb) return true
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na.length <= 2 || nb.length <= 2) return false

        if (na.length > 3 && nb.length > 3) {
            if (na.startsWith(nb) || nb.startsWith(na)) return true
        }

        val dist = levenshtein(na, nb)
        val maxEdits = if (na.length == 3 || nb.length == 3) 1 else 2
        if (maxEdits == 1 && na.first() != nb.first()) return false
        return dist <= maxEdits
    }

    private fun normalize(word: String): String =
        word.lowercase().filter { it.isLetterOrDigit() }

    private data class CandidateMatch(
        val newWordIndex: Int,
        val matchedCount: Int,
        val score: Float,
        val isMatching: Boolean
    )

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
