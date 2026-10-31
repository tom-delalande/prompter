package co.delalande.prompter.speech

data class MatchResult(
    val newWordIndex: Int,
    val isMatching: Boolean,
    val consumedWords: Int = 0,
    val confidence: Float = 0f
)

object SpeechMatcher {
    private const val NEAR_LOOKAHEAD_WORDS = 8
    private const val MAX_LOOKAHEAD_WORDS = 10
    private const val MAX_RESYNC_LOOKAHEAD_WORDS = 150
    private const val MAX_ADVANCE_PER_MATCH = 12
    private const val HIGH_CONFIDENCE_MATCH_COUNT = 3
    private const val DIRECT_MATCH_RATIO = 0f
    private const val LOOKAHEAD_MATCH_RATIO = 0.6f
    private const val RESYNC_MATCH_RATIO = 0.7f
    private const val NEAR_CONFIDENCE_THRESHOLD = 0.5f
    private const val FAR_CONFIDENCE_THRESHOLD = 2.1f

    private val fillers = setOf(
        "um", "uh", "ah", "er", "hmm"
    )

    fun tokenize(text: String): List<String> =
        text
            .lowercase()
            .trim()
            .split("\\s+".toRegex())
            .map(::normalize)
            .filter { it.isNotBlank() && it !in fillers }

    fun match(
        speechWords: List<String>,
        scriptWords: List<String>,
        currentIndex: Int
    ): MatchResult {
        if (currentIndex >= scriptWords.size) {
            return MatchResult(scriptWords.size, true, confidence = 1f)
        }
        if (speechWords.isEmpty()) {
            return MatchResult(currentIndex, true)
        }

        val directResult = tryMatch(
            speechWords = speechWords,
            scriptWords = scriptWords,
            fromIndex = currentIndex,
            requiredRatio = DIRECT_MATCH_RATIO,
            leading = 1
        )
        if (directResult.isMatching) {
            return MatchResult(
                newWordIndex = directResult.newWordIndex
                    .coerceAtMost(currentIndex + MAX_ADVANCE_PER_MATCH),
                isMatching = true,
                consumedWords = directResult.consumedWords,
                confidence = directResult.confidence()
            )
        }

        if (speechWords.size >= 2) {
            val nearResult = bestSearchMatch(
                speechWords = speechWords,
                scriptWords = scriptWords,
                searchStart = currentIndex + 1,
                searchEnd = minOf(currentIndex + NEAR_LOOKAHEAD_WORDS, scriptWords.lastIndex),
                requiredRatio = LOOKAHEAD_MATCH_RATIO,
                minConfidence = NEAR_CONFIDENCE_THRESHOLD
            )
            if (nearResult != null) {
                val capAdvance = nearResult.matchedCount < HIGH_CONFIDENCE_MATCH_COUNT
                return MatchResult(
                    newWordIndex = if (capAdvance) {
                        nearResult.newWordIndex
                            .coerceAtMost(currentIndex + MAX_ADVANCE_PER_MATCH)
                    } else {
                        nearResult.newWordIndex.coerceAtMost(scriptWords.size)
                    },
                    isMatching = true,
                    consumedWords = nearResult.consumedWords,
                    confidence = nearResult.confidence()
                )
            }
        }

        if (speechWords.size >= 3) {
            val farResult = bestSearchMatch(
                speechWords = speechWords,
                scriptWords = scriptWords,
                searchStart = currentIndex + NEAR_LOOKAHEAD_WORDS + 1,
                searchEnd = minOf(currentIndex + MAX_LOOKAHEAD_WORDS, scriptWords.lastIndex),
                requiredRatio = LOOKAHEAD_MATCH_RATIO,
                minConfidence = FAR_CONFIDENCE_THRESHOLD
            )
            if (farResult != null) {
                return MatchResult(
                    newWordIndex = farResult.newWordIndex.coerceAtMost(scriptWords.size),
                    isMatching = true,
                    consumedWords = farResult.consumedWords,
                    confidence = farResult.confidence()
                )
            }

            val resyncResult = bestSearchMatch(
                speechWords = speechWords,
                scriptWords = scriptWords,
                searchStart = currentIndex + MAX_LOOKAHEAD_WORDS + 1,
                searchEnd = minOf(currentIndex + MAX_RESYNC_LOOKAHEAD_WORDS, scriptWords.lastIndex),
                requiredRatio = RESYNC_MATCH_RATIO,
                minConfidence = FAR_CONFIDENCE_THRESHOLD
            )
            if (resyncResult != null) {
                return MatchResult(
                    newWordIndex = resyncResult.newWordIndex.coerceAtMost(scriptWords.size),
                    isMatching = true,
                    consumedWords = resyncResult.consumedWords,
                    confidence = resyncResult.confidence()
                )
            }
        }

        // Proceed bias: when the speaker is clearly talking (≥2 words) but no confident
        // match was found, advance by however many words still loosely align with the
        // script. This keeps the scroll moving instead of stalling on uncertain speech.
        if (speechWords.size >= 2) {
            val looseResult = tryMatch(
                speechWords = speechWords,
                scriptWords = scriptWords,
                fromIndex = currentIndex,
                requiredRatio = 0f,
                leading = 3
            )
            if (looseResult.matchedCount >= 1) {
                return MatchResult(
                    newWordIndex = looseResult.newWordIndex
                        .coerceAtMost(currentIndex + MAX_ADVANCE_PER_MATCH),
                    isMatching = true,
                    consumedWords = looseResult.consumedWords,
                    confidence = looseResult.confidence()
                )
            }
        }

        return MatchResult(currentIndex, false)
    }

    private fun bestSearchMatch(
        speechWords: List<String>,
        scriptWords: List<String>,
        searchStart: Int,
        searchEnd: Int,
        requiredRatio: Float,
        minConfidence: Float
    ): CandidateMatch? {
        if (searchStart > searchEnd) return null

        var best: CandidateMatch? = null
        for (searchIdx in searchStart..searchEnd) {
            val searchResult = tryMatch(
                speechWords = speechWords,
                scriptWords = scriptWords,
                fromIndex = searchIdx,
                requiredRatio = requiredRatio,
                leading = 0
            )
            if (searchResult.isMatching && searchResult.confidence() >= minConfidence) {
                if (best == null || searchResult.score > best.score) {
                    best = searchResult
                }
            }
        }
        return best
    }

    private fun tryMatch(
        speechWords: List<String>,
        scriptWords: List<String>,
        fromIndex: Int,
        requiredRatio: Float,
        leading: Int
    ): CandidateMatch {
        var matchIndex = fromIndex
        var matchedCount = 0
        var missedAfterMatch = 0
        var leadingMisses = 0
        var processed = 0

        for (word in speechWords) {
            if (matchIndex >= scriptWords.size) break
            processed++

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
                        leadingMisses++
                        if (leadingMisses > leading) {
                            return CandidateMatch(
                                fromIndex, 0, 0f, false, processed
                            )
                        }
                    } else {
                        missedAfterMatch++
                        if (missedAfterMatch > 1) {
                            break
                        }
                    }
                }
            }
        }

        val ratio = matchedCount.toFloat() / maxOf(1, speechWords.size)
        return CandidateMatch(
            newWordIndex = matchIndex,
            matchedCount = matchedCount,
            score = ratio,
            isMatching = matchedCount > 0 && ratio >= requiredRatio,
            consumedWords = processed
        )
    }

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
        val isMatching: Boolean,
        val consumedWords: Int
    ) {
        fun confidence(): Float = matchedCount * score
    }

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
