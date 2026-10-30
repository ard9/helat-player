package io.github.ard9.linguaplayer.player

import io.github.ard9.linguaplayer.data.ClipMargins
import io.github.ard9.linguaplayer.subtitle.SubtitleLine
import io.github.ard9.linguaplayer.subtitle.SubtitleLookup
import io.github.ard9.linguaplayer.subtitle.mkv.MkvSubtitleTrack
import kotlin.math.max

/** Lines of all built-in text tracks around [atMs], kept so the editor can switch tracks. */
class BuiltInSearch(
    val atMs: Long,
    val lines: Map<Long, List<SubtitleLine>>,
    val tracks: List<MkvSubtitleTrack>,
)

/** A line to save: its range with the clip margins, its text and meaning. */
data class FoundLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val meaning: String,
    /** Index in [BuiltInSearch.tracks], or -1 for a subtitle file. */
    val trackIndex: Int,
)

/**
 * Which subtitle line belongs to a moment, from a subtitle file or the film's own text tracks.
 * Pure logic used by the player for saving, Repeat and Loop.
 */
object LineSearch {
    /**
     * Track indexes to try, best first. Cards are for learning the film's language, so a
     * non-Persian track is preferred for the text (the one on screen, then the default), and
     * the Persian one is used for the meaning.
     */
    fun preferredOrder(tracks: List<MkvSubtitleTrack>, shown: MkvSubtitleTrack?): List<Int> {
        val order = mutableListOf<Int>()
        fun addFirst(predicate: (MkvSubtitleTrack) -> Boolean) {
            tracks.indices.firstOrNull { it !in order && predicate(tracks[it]) }?.let(order::add)
        }
        addFirst { it == shown && !it.isPersian }
        addFirst { it.isDefault && !it.isPersian }
        addFirst { !it.isPersian }
        tracks.indices.filter { it !in order }.forEach(order::add)
        return order
    }

    /**
     * Always kept before and after a subtitle line, under the user's margins: subtitle times are
     * set for reading, not to the speech (often a little off), and with small margins words were
     * cut. More before, as starts were cut more often (the owner's choice).
     */
    const val FIXED_BEFORE_MS = 500L
    const val FIXED_AFTER_MS = 250L

    /** The line's range widened by the fixed pads and [margins] (never before 0). */
    fun widen(line: SubtitleLine, margins: ClipMargins): Pair<Long, Long> =
        max(0L, line.startMs - FIXED_BEFORE_MS - margins.beforeMs) to line.endMs + FIXED_AFTER_MS + margins.afterMs

    /** The line of a subtitle file at [atMs]. */
    fun fromFile(lines: List<SubtitleLine>, atMs: Long, margins: ClipMargins): FoundLine? {
        val line = SubtitleLookup.nearMoment(lines, atMs) ?: return null
        val (start, end) = widen(line, margins)
        return FoundLine(start, end, line.text, meaning = "", trackIndex = -1)
    }

    /** The line of track [index] at the searched moment, with the Persian track's line as the meaning. */
    fun fromTrack(search: BuiltInSearch, index: Int, margins: ClipMargins): FoundLine? {
        val track = search.tracks[index]
        val line = SubtitleLookup.nearMoment(search.lines[track.number].orEmpty(), search.atMs) ?: return null
        val meaning = if (track.isPersian) {
            null
        } else {
            search.tracks.firstOrNull { it.isPersian }
                ?.let { persian -> SubtitleLookup.overlapping(search.lines[persian.number].orEmpty(), line) }
        }
        val (start, end) = widen(line, margins)
        return FoundLine(start, end, line.text, meaning.orEmpty(), index)
    }

    /** The line from the best track that has one, see [preferredOrder]. */
    fun best(search: BuiltInSearch, shown: MkvSubtitleTrack?, margins: ClipMargins): FoundLine? =
        preferredOrder(search.tracks, shown).firstNotNullOfOrNull { fromTrack(search, it, margins) }

    /** Whether [startMs]–[endMs] is mostly a line already saved (over half of the shorter one). */
    fun isDuplicate(saved: List<Pair<Long, Long>>, startMs: Long, endMs: Long): Boolean =
        saved.any { (s, e) -> overlapShare(s, e, startMs, endMs) > 0.5 }

    /** How much of the shorter of two ranges the other one covers (0..1). */
    internal fun overlapShare(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Double {
        val overlap = minOf(aEnd, bEnd) - maxOf(aStart, bStart)
        val shorter = minOf(aEnd - aStart, bEnd - bStart)
        return if (overlap <= 0 || shorter <= 0) 0.0 else overlap.toDouble() / shorter
    }
}
