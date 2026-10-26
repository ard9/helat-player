package io.github.ard9.linguaplayer.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/** Small player for saved audio clips in the box and review screens. */
class ClipAudioPlayer(context: Context) {
    private val player = ExoPlayer.Builder(context).build()

    fun play(file: File) {
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
