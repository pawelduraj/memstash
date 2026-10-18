package dev.pawelduraj.memstash.data

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

object MemeSearch {
    private val combiningMarks = "\\p{Mn}+".toRegex()
    private val separators = "[^\\p{L}\\p{N}]+".toRegex()

    fun words(text: String): List<String> {
        val lower = text.lowercase(Locale.ROOT).replace('ł', 'l')
        val plain = combiningMarks.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
        return plain.split(separators).filter { it.isNotEmpty() }
    }

    fun search(memes: List<Meme>, query: String, usage: Map<String, Long>): List<Meme> {
        val tokens = words(query)
        if (tokens.isEmpty()) return emptyList()
        return memes.mapNotNull { meme ->
            var score = 0
            for (token in tokens) {
                val s = tokenScore(token, meme)
                if (s == 0) return@mapNotNull null
                score += s
            }
            Scored(meme, score, usage[meme.path] ?: 0L)
        }.sortedWith(
            compareByDescending<Scored> { it.score }
                .thenByDescending { it.lastUsed }
                .thenBy { it.meme.name.lowercase() }
        ).map { it.meme }
    }

    private fun tokenScore(token: String, meme: Meme): Int {
        val typos = allowedTypos(token)
        val best = meme.nameWords.maxOfOrNull { word ->
            when {
                word == token -> 6
                word.startsWith(token) -> 5
                word.contains(token) -> 3
                typos > 0 && (isWithinTypos(token, word, typos) ||
                    (word.length > token.length && isWithinTypos(token, word.take(token.length), typos))) -> 2
                else -> 0
            }
        } ?: 0
        if (best > 0) return best
        if (token.length >= 3 && meme.nameJoined.contains(token)) return 2
        return if (meme.folderWords.any { it.contains(token) } || (ImageTypes.extension(meme.name) == token || meme.matchingExtension.lowercase() == token)) 1 else 0
    }

    /** As search engines usually do: none for one or two letters, one up to five letters, two from six. */
    private fun allowedTypos(token: String): Int = when {
        token.length < 3 -> 0
        token.length < 6 -> 1
        else -> 2
    }

    /**
     * Typos are wrong, missing or extra letters and two neighbouring letters swapped. Runs for every word of every
     * meme on each keystroke, so it keeps only three rows of the table and stops once a row is over [max].
     */
    private fun isWithinTypos(a: String, b: String, max: Int): Boolean {
        if (abs(a.length - b.length) > max) return false
        var older = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = i
            for (j in 1..b.length) {
                var d = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d = minOf(d, older[j - 2] + 1)
                current[j] = d
                rowMin = minOf(rowMin, d)
            }
            if (rowMin > max) return false
            val spare = older
            older = previous
            previous = current
            current = spare
        }
        return previous[b.length] <= max
    }

    private data class Scored(val meme: Meme, val score: Int, val lastUsed: Long)
}
