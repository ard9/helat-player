package io.github.ard9.linguaplayer.ui

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ard9.linguaplayer.R
import io.github.ard9.linguaplayer.roleplay.VoiceRecorder
import io.github.ard9.linguaplayer.roleplay.playRecording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

private enum class PracticeState { Idle, Recording, Playing }

/**
 * Pronunciation practice for one card: record yourself saying the line, then hear your voice
 * alone or followed by the original. The recording is kept only while this card is shown.
 */
@Composable
fun PracticeRow(clipFile: File, clipDurationMs: Long, audio: ClipAudioPlayer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    val file = remember { File(context.cacheDir, "practice/voice.wav") }
    val stop = remember { AtomicBoolean(false) }
    var state by remember { mutableStateOf(PracticeState.Idle) }
    // Where the voice starts in the recording; null until there is a recording with speech.
    var voiceStartMs by remember { mutableStateOf<Long?>(null) }
    var problem by remember { mutableStateOf<Int?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            stop.set(true)
            job?.cancel()
            file.delete()
        }
    }

    @Suppress("MissingPermission") // Checked before record() is called.
    fun record() {
        problem = null
        stop.set(false)
        state = PracticeState.Recording
        job = scope.launch {
            val result = runCatching {
                file.parentFile?.mkdirs()
                withContext(Dispatchers.IO) {
                    recorder.record(
                        file = file,
                        stopWhenSilent = true,
                        maxMs = max(MIN_RECORDING_MS, clipDurationMs * 3),
                        shouldStop = { stop.get() },
                    )
                }
            }
            state = PracticeState.Idle
            result
                .onSuccess { recording ->
                    if (recording.heardSpeech) {
                        voiceStartMs = max(0L, recording.speechStartMs - VOICE_LEAD_MS)
                    } else {
                        voiceStartMs = null
                        file.delete()
                        problem = R.string.practice_nothing_heard
                    }
                }
                .onFailure {
                    Log.w("Practice", "Recording failed", it)
                    voiceStartMs = null
                    problem = R.string.practice_failed
                }
        }
    }

    fun play(thenOriginal: Boolean) {
        val start = voiceStartMs ?: return
        state = PracticeState.Playing
        job = scope.launch {
            runCatching { playRecording(file, start) }.onFailure { Log.w("Practice", "Playback failed", it) }
            if (thenOriginal) audio.play(clipFile)
            state = PracticeState.Idle
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) record() else problem = R.string.practice_needs_mic
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                PracticeState.Recording -> {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.practice_listening), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { stop.set(true) }) { Text(stringResource(R.string.done)) }
                }
                else -> {
                    val idle = state == PracticeState.Idle
                    OutlinedButton(
                        onClick = { if (recorder.hasPermission()) record() else permission.launch(Manifest.permission.RECORD_AUDIO) },
                        enabled = idle,
                    ) { Text(stringResource(if (voiceStartMs == null) R.string.practice_record else R.string.practice_again)) }
                    if (voiceStartMs != null) {
                        TextButton(onClick = { play(thenOriginal = false) }, enabled = idle) {
                            Text(stringResource(R.string.practice_mine))
                        }
                        Button(onClick = { play(thenOriginal = true) }, enabled = idle) {
                            Text(stringResource(R.string.practice_compare))
                        }
                    }
                }
            }
        }
        problem?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private const val MIN_RECORDING_MS = 8_000L
/** Keep a moment before the first word so it is not clipped. */
private const val VOICE_LEAD_MS = 150L
