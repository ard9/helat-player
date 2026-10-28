package io.github.ard9.linguaplayer.text

/**
 * Loose text matching for search: case, the Arabic forms of ی and ک (common in Persian
 * subtitles), the zero-width non-joiner and extra spaces do not matter.
 */
object TextMatch {
    /** [text] normalized, with the index in [text] of every normalized character. */
    private class Normalized(val text: String, val origin: IntArray)

    private fun normalized(text: String): Normalized {
        val out = StringBuilder(text.length)
        val origin = ArrayList<Int>(text.length)
        var lastWasSpace = true // Drops leading spaces.
        for ((i, raw) in text.withIndex()) {
            val c = when (raw) {
                'ي', 'ى' -> 'ی'
                'ك' -> 'ک'
                '‌' -> continue
                else -> raw.lowercaseChar()
            }
            if (c.isWhitespace()) {
                if (lastWasSpace) continue
                out.append(' ')
                origin.add(i)
                lastWasSpace = true
            } else {
                out.append(c)
                origin.add(i)
                lastWasSpace = false
            }
        }
        if (out.endsWith(' ')) {
            out.setLength(out.length - 1)
            origin.removeAt(origin.size - 1)
        }
        return Normalized(out.toString(), origin.toIntArray())
    }

    fun normalize(text: String): String = normalized(text).text

    /** The words of a query, normalized. */
    fun words(query: String): List<String> = normalize(query).split(' ').filter { it.isNotEmpty() }

    /** Whether [text] contains every one of [words] (from [TextMatch.words]). */
    fun containsAll(text: String, words: List<String>): Boolean {
        if (words.isEmpty()) return false
        val haystack = normalize(text)
        return words.all { it in haystack }
    }

    /** Where [words] occur in [text], as ranges of [text] itself, sorted and merged. */
    fun highlights(text: String, words: List<String>): List<IntRange> {
        val n = normalized(text)
        val ranges = mutableListOf<IntRange>()
        for (word in words) {
            var from = n.text.indexOf(word)
            while (from >= 0) {
                ranges.add(n.origin[from]..n.origin[from + word.length - 1])
                from = n.text.indexOf(word, from + 1)
            }
        }
        val merged = mutableListOf<IntRange>()
        for (r in ranges.sortedBy { it.first }) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1) {
                merged[merged.size - 1] = last.first..maxOf(last.last, r.last)
            } else {
                merged.add(r)
            }
        }
        return merged
    }
}
