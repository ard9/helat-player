package io.github.ard9.linguaplayer.data

import java.util.Locale

/** Finds cards by their text, meaning or film name. */
object CardSearch {
    /** Cards whose text, meaning or film contains every word of [query], newest first. */
    fun find(cards: List<Card>, query: String): List<Card> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return cards
            .filter { card ->
                val haystack = normalize("${card.text} ${card.meaning} ${card.sourceTitle}")
                words.all { it in haystack }
            }
            .sortedByDescending { it.createdAt }
    }

    /**
     * Lower case, one space between words, and the Arabic forms of ی and ک (common in Persian
     * subtitles) and the zero-width non-joiner folded, so "می‌خواهم" also finds "ميخواهم".
     */
    internal fun normalize(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
        .replace("‌", "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
