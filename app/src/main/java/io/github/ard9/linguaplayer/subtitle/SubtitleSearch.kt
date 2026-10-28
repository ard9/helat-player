package io.github.ard9.linguaplayer.subtitle

import io.github.ard9.linguaplayer.text.TextMatch

/** Finds lines in the subtitles of one film. */
object SubtitleSearch {
    /** A found line and which of the searched sources (tracks) it came from. */
    data class Hit(val source: Int, val line: SubtitleLine)

    const val MAX_HITS = 300

    /**
     * Lines of [sources] (e.g. the film's text tracks) that contain every word of [query], in
     * time order; at most [max].
     */
    fun find(sources: List<List<SubtitleLine>>, query: String, max: Int = MAX_HITS): List<Hit> {
        val words = TextMatch.words(query)
        if (words.isEmpty()) return emptyList()
        return sources
            .flatMapIndexed { i, lines -> lines.filter { TextMatch.containsAll(it.text, words) }.map { Hit(i, it) } }
            .sortedWith(compareBy({ it.line.startMs }, { it.source }))
            .take(max)
    }
}
