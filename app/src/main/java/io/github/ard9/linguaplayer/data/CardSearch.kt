package io.github.ard9.linguaplayer.data

import io.github.ard9.linguaplayer.text.TextMatch

/** Finds cards by their text, meaning or film name. */
object CardSearch {
    /**
     * Cards whose text, meaning or film contains every word of [query], newest first. Matching
     * is loose (see [TextMatch]), so "می‌خواهم" also finds "ميخواهم".
     */
    fun find(cards: List<Card>, query: String): List<Card> {
        val words = TextMatch.words(query)
        if (words.isEmpty()) return emptyList()
        return cards
            .filter { TextMatch.containsAll("${it.text} ${it.meaning} ${it.sourceTitle}", words) }
            .sortedByDescending { it.createdAt }
    }
}
