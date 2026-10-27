package io.github.ard9.linguaplayer.roleplay

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

/** What the session needs from the video player. Called on the main thread. */
interface ScenePlayer {
    val timeMs: Long
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun setMuted(muted: Boolean)
}

/**
 * Plays a scene where the actor says some lines and the user says the others: on the user's
 * lines the film is silent while their voice is recorded. Afterwards the scene can be played
 * back with the user's recordings in place of the actor's voice.
 */
class RolePlaySession(
    val lines: List<RoleLine>,
    /** Pause the film on the user's lines until they finish, instead of carrying on. */
    val pauseForMe: Boolean,
    val showMyText: Boolean,
    private val folder: File,
    private val player: ScenePlayer,
    private val recorder: VoiceRecorder,
    private val scope: CoroutineScope,
) {
    enum class Phase { Acting, Finished, PlayingBack, Comparing }

    var phase by mutableStateOf(Phase.Acting)
        private set
    /** Index of the line being played or said; -1 before the first. */
    var current by mutableIntStateOf(-1)
        private set
    /** True while the user's voice is being recorded. */
    var myTurn by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Recordings by line index; lines the user skipped or did not speak have none. */
    val recordings = mutableStateMapOf<Int, File>()

    @Volatile private var stopRecording = false
    private var job: Job? = null

    init {
        folder.mkdirs()
    }

    /** Ends the user's turn now (they finished speaking). */
    fun done() {
        stopRecording = true
    }

    /** Skips the user's line; the actor's voice will be used for it in the playback. */
    fun skip() {
        skipped = true
        stopRecording = true
    }

    private var skipped = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() = launch(Phase.Acting) {
        recordings.values.forEach { it.delete() }
        recordings.clear()
        beginAt(lines.first().startMs)
        for ((i, line) in lines.withIndex()) {
            current = i
            if (line.mine) actMyLine(i, line) else waitUntil { player.timeMs >= line.endMs }
        }
        finishScene()
    }

    /** Plays the scene again with the user's recordings in place of their lines. */
    fun playBack() = launch(Phase.PlayingBack) {
        beginAt(lines.first().startMs)
        for ((i, line) in lines.withIndex()) {
            current = i
            val recording = recordings[i]
            if (recording == null) {
                waitUntil { player.timeMs >= line.endMs }
                continue
            }
            waitUntil { player.timeMs >= line.startMs - EARLY_MS }
            player.setMuted(true)
            player.pause()
            playRecording(recording)
            continueAfter(line)
        }
        finishScene()
    }

    /** Plays one line as the actor said it. */
    fun playActor(index: Int) = launch(Phase.Comparing) {
        val line = lines[index]
        current = index
        player.setMuted(false)
        player.seekTo(max(0L, line.startMs - EARLY_MS))
        player.play()
        delay(SEEK_SETTLE_MS)
        waitUntil { player.timeMs >= line.endMs + EARLY_MS }
        player.pause()
        phase = Phase.Finished
    }

    /** Plays the user's recording of one line. */
    fun playMine(index: Int) = launch(Phase.Comparing) {
        current = index
        player.pause()
        recordings[index]?.let { playRecording(it) }
        phase = Phase.Finished
    }

    /** Stops whatever is running and leaves the film paused with its sound on. */
    fun stop() {
        job?.cancel()
        stopRecording = true
        player.setMuted(false)
        player.pause()
        if (phase != Phase.Acting) phase = Phase.Finished
    }

    /** Deletes the recordings; call when the session is closed. */
    fun close() {
        stop()
        folder.deleteRecursively()
    }

    private fun launch(newPhase: Phase, block: suspend () -> Unit): Job {
        job?.cancel()
        phase = newPhase
        error = null
        return scope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Role-play failed", e)
                error = e.message ?: "Something went wrong"
                player.setMuted(false)
                player.pause()
                myTurn = false
                phase = Phase.Finished
            }
        }.also { job = it }
    }

    @Suppress("MissingPermission") // Checked by the caller of start().
    private suspend fun actMyLine(index: Int, line: RoleLine) {
        waitUntil { player.timeMs >= line.startMs - EARLY_MS }
        player.setMuted(true)
        if (pauseForMe) player.pause()
        val file = File(folder, "line-$index.wav")
        stopRecording = false
        skipped = false
        myTurn = true
        val durationMs = line.endMs - line.startMs
        val result = try {
            coroutineScope {
                // A child of this job, so stopping the session also ends the recording.
                val recording = async(Dispatchers.IO) {
                    recorder.record(
                        file = file,
                        stopWhenSilent = pauseForMe,
                        maxMs = if (pauseForMe) max(MIN_TURN_MS, durationMs * 3 + 3_000) else durationMs + 2_000,
                        shouldStop = { stopRecording },
                    )
                }
                if (!pauseForMe) {
                    // In real time the turn ends with the actor's line.
                    while (!recording.isCompleted) {
                        if (player.timeMs >= line.endMs + LATE_MS) stopRecording = true
                        delay(POLL_MS)
                    }
                }
                recording.await()
            }
        } finally {
            stopRecording = true
            myTurn = false
        }
        if (skipped || !result.heardSpeech) file.delete() else recordings[index] = file
        if (pauseForMe) continueAfter(line) else {
            waitUntil { player.timeMs >= line.endMs }
            player.setMuted(false)
        }
    }

    /** Jumps over the actor's voice for [line] and plays on with sound. */
    private suspend fun continueAfter(line: RoleLine) {
        player.seekTo(line.endMs + EARLY_MS)
        player.play()
        delay(SEEK_SETTLE_MS) // Do not let a moment of the old position be heard.
        player.setMuted(false)
    }

    private suspend fun beginAt(firstLineMs: Long) {
        player.setMuted(false)
        player.seekTo(max(0L, firstLineMs - LEAD_MS))
        player.play()
        delay(SEEK_SETTLE_MS)
    }

    private suspend fun finishScene() {
        waitUntil { player.timeMs >= lines.last().endMs + TAIL_MS }
        player.pause()
        player.setMuted(false)
        current = -1
        phase = Phase.Finished
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        while (!condition()) delay(POLL_MS)
    }

    private companion object {
        const val TAG = "RolePlay"
        const val POLL_MS = 30L
        /** Act a little before the subtitle appears, so the first syllable is not heard. */
        const val EARLY_MS = 120L
        const val LATE_MS = 250L
        const val LEAD_MS = 1_500L
        const val TAIL_MS = 600L
        const val SEEK_SETTLE_MS = 200L
        const val MIN_TURN_MS = 8_000L
    }
}
