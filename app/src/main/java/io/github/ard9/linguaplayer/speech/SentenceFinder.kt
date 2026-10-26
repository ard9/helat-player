package io.github.ard9.linguaplayer.speech

/**
 * Finds the spoken sentence around a moment from per-frame speech probabilities.
 * Pure logic (no Android), tuned with a Python prototype on Silero's sample audio.
 */
object SentenceFinder {
    /** One probability per 512 samples at 16 kHz. */
    const val FRAME_MS = 32.0

    private const val START_PROB = 0.5f
    private const val END_PROB = 0.35f
    /** Pauses shorter than this are between words, not between sentences. */
    private const val MIN_GAP_MS = 400.0
    private const val MIN_SPEECH_MS = 200.0
    /** A press this close to a sentence counts as inside it. */
    private const val NEAR_MS = 150.0
    /** Longer "sentences" are split again at short dips in speech probability. */
    private const val LONG_MS = 8000.0
    private const val DIP_PROB = 0.7f
    private const val MIN_DIP_MS = 128.0

    /** A sentence relative to the start of the analysed audio, in milliseconds. */
    data class Span(val startMs: Double, val endMs: Double)

    /**
     * The sentence being spoken at [atMs], or, if [atMs] falls in a pause, the one that ended
     * just before it (or else the next one). Null when there is no speech at all.
     */
    fun find(probabilities: FloatArray, atMs: Double): Span? {
        val chosen = choose(segments(probabilities), atMs) ?: return null
        if (chosen.endMs - chosen.startMs <= LONG_MS) return chosen
        return choose(splitAtDips(probabilities, chosen), atMs) ?: chosen
    }

    private fun segments(probabilities: FloatArray): List<Span> {
        val raw = mutableListOf<Span>()
        var speaking = false
        var start = 0
        probabilities.forEachIndexed { i, p ->
            if (!speaking && p >= START_PROB) {
                speaking = true
                start = i
            } else if (speaking && p < END_PROB) {
                speaking = false
                raw.add(Span(start * FRAME_MS, i * FRAME_MS))
            }
        }
        if (speaking) raw.add(Span(start * FRAME_MS, probabilities.size * FRAME_MS))

        val merged = mutableListOf<Span>()
        for (span in raw) {
            val last = merged.lastOrNull()
            if (last != null && span.startMs - last.endMs < MIN_GAP_MS) {
                merged[merged.size - 1] = Span(last.startMs, span.endMs)
            } else {
                merged.add(span)
            }
        }
        return merged.filter { it.endMs - it.startMs >= MIN_SPEECH_MS }
    }

    private fun splitAtDips(probabilities: FloatArray, span: Span): List<Span> {
        val first = (span.startMs / FRAME_MS).toInt()
        val last = (span.endMs / FRAME_MS).toInt().coerceAtMost(probabilities.size)
        val parts = mutableListOf<Span>()
        var partStart = span.startMs
        var dipStart = -1
        for (i in first until last) {
            if (probabilities[i] < DIP_PROB) {
                if (dipStart < 0) dipStart = i
            } else {
                if (dipStart >= 0 && (i - dipStart) * FRAME_MS >= MIN_DIP_MS) {
                    parts.add(Span(partStart, dipStart * FRAME_MS))
                    partStart = i * FRAME_MS
                }
                dipStart = -1
            }
        }
        parts.add(Span(partStart, span.endMs))
        return parts.filter { it.endMs - it.startMs >= MIN_SPEECH_MS }
    }

    private fun choose(spans: List<Span>, atMs: Double): Span? {
        spans.firstOrNull { atMs >= it.startMs - NEAR_MS && atMs <= it.endMs + NEAR_MS }?.let { return it }
        spans.lastOrNull { it.endMs <= atMs }?.let { return it }
        return spans.firstOrNull { it.startMs >= atMs }
    }
}
