package io.github.ard9.linguaplayer.ui

import io.github.ard9.linguaplayer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.media3.common.PlaybackParameters
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/** Speeds offered for card audio, in the order the speed button cycles through them. */
val CLIP_SPEEDS = listOf(1f, 0.75f, 0.5f)

/** "1×", "0.75×", "0.5×". */
fun speedLabel(speed: Float): String = if (speed == 1f) "1×" else "${speed}×"

/** Button that cycles the card audio speed; it shows the current one. */
@Composable
fun SpeedButton(speed: Float, onChange: (Float) -> Unit) {
    TextButton(onClick = {
        val next = CLIP_SPEEDS[(CLIP_SPEEDS.indexOf(speed) + 1) % CLIP_SPEEDS.size]
        onChange(next)
    }) {
        Text(stringResource(R.string.speed, speedLabel(speed)))
    }
}

/** Small player for saved audio clips in the box and review screens. */
class ClipAudioPlayer(context: Context) {
    private val player = ExoPlayer.Builder(context).build()

    /** Playback speed; slower speech keeps its pitch. */
    var speed: Float = 1f
        set(value) {
            field = value
            player.playbackParameters = PlaybackParameters(value)
        }

    fun play(file: File) {
        player.playbackParameters = PlaybackParameters(speed)
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
        player.play()
    }

    fun release() = player.release()
}

@Composable
fun rememberClipAudioPlayer(): ClipAudioPlayer {
    val context = LocalContext.current
    val player = remember { ClipAudioPlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
