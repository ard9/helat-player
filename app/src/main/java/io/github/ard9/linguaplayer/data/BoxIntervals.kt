package io.github.ard9.linguaplayer.data

/**
 * How many days a card in each Leitner box waits before it is due again (index 0 is Box 1).
 * Chosen by the user in Settings. A box never comes sooner than the box before it, or knowing
 * a card would bring it back earlier.
 */
data class BoxIntervals(val days: List<Int>) {
    init {
        require(days.size == Leitner.BOX_COUNT) { "One interval per box" }
    }

    fun daysFor(box: Int): Int = days[box.coerceIn(1, Leitner.BOX_COUNT) - 1]

    /**
     * Box [box] set to [value] days (kept in [MIN_DAYS]..[MAX_DAYS]); later boxes shorter than it
     * are raised to it and earlier boxes longer than it are lowered to it, so the order holds.
     */
    fun with(box: Int, value: Int): BoxIntervals {
        val index = box.coerceIn(1, Leitner.BOX_COUNT) - 1
        val v = value.coerceIn(MIN_DAYS, MAX_DAYS)
        return BoxIntervals(
            days.mapIndexed { i, d ->
                when {
                    i == index -> v
                    i > index -> maxOf(d, v)
                    else -> minOf(d, v)
                }
            }
        )
    }

    companion object {
        const val MIN_DAYS = 1
        const val MAX_DAYS = 365
        val DEFAULT = BoxIntervals(listOf(1, 2, 4, 8, 16))

        /** Stored values made usable: in range and in order (missing ones from the defaults). */
        fun sanitized(values: List<Int?>): BoxIntervals {
            var previous = MIN_DAYS
            return BoxIntervals(
                List(Leitner.BOX_COUNT) { i ->
                    val d = (values.getOrNull(i) ?: DEFAULT.days[i]).coerceIn(MIN_DAYS, MAX_DAYS)
                    maxOf(d, previous).also { previous = it }
                }
            )
        }
    }
}
