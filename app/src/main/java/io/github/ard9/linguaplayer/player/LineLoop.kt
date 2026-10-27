package io.github.ard9.linguaplayer.player

/**
 * Repeats one line: tells the player to jump back to [startMs] whenever playback reaches
 * [endMs]. It never ends the loop by itself; the user does (button, seeking, end of film).
 *
 * After a jump libVLC may report the old position for a moment (on online files for longer),
 * which would look like the end of the line again. So after each jump it waits until the
 * reported time is back near the start, or [settleTimeoutMs] has passed.
 *
 * Pure logic, driven by [next] with the player's time and a monotonic clock.
 */
class LineLoop(
    val startMs: Long,
    val endMs: Long,
    /** Clock time of the first jump to [startMs] (the caller makes it when the loop starts). */
    jumpedAtMs: Long,
    private val settleTimeoutMs: Long = 4_000,
) {
    private var jumpedAt: Long? = jumpedAtMs

    /** Returns true when the player should jump to [startMs] now. */
    fun next(timeMs: Long, clockMs: Long): Boolean {
        jumpedAt?.let { since ->
            val arrived = timeMs in (startMs - NEAR_MS)..<endMs
            if (!arrived && clockMs - since < settleTimeoutMs) return false
            jumpedAt = null
        }
        if (timeMs < endMs) return false
        jumpedAt = clockMs
        return true
    }

    private companion object {
        /** A jump may land a little before the requested time (key frames). */
        const val NEAR_MS = 1_500L
    }
}
