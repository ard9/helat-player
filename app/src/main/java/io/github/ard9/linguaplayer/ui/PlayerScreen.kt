package io.github.ard9.linguaplayer.ui

import android.net.Uri
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.ard9.linguaplayer.clip.AudioClipper
import io.github.ard9.linguaplayer.data.Card
import io.github.ard9.linguaplayer.data.CardRepository
import io.github.ard9.linguaplayer.data.PlaybackSource
import io.github.ard9.linguaplayer.data.SourceNames
import io.github.ard9.linguaplayer.subtitle.EmbeddedCueTracker
import io.github.ard9.linguaplayer.subtitle.EmbeddedCueTracker.SaveTarget
import io.github.ard9.linguaplayer.subtitle.SubtitleLine
import io.github.ard9.linguaplayer.subtitle.SubtitleLookup
import io.github.ard9.linguaplayer.subtitle.SubtitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Extra audio kept before and after each line so words are not cut off. */
private const val CLIP_PADDING_MS = 250L

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    source: PlaybackSource,
    repository: CardRepository,
    clipper: AudioClipper,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredTextLanguage("en")
                .setSelectUndeterminedTextLanguage(true)
                .build()
            setMediaItem(MediaItem.fromUri(source.uri))
            prepare()
            playWhenReady = true
        }
    }
    val tracker = remember { EmbeddedCueTracker() }
    var externalLines by remember { mutableStateOf<List<SubtitleLine>?>(null) }
    var externalName by remember { mutableStateOf<String?>(null) }
    var currentText by remember { mutableStateOf("") }
    var savingCount by remember { mutableIntStateOf(0) }
    var saveWhenLineEnds by remember { mutableStateOf(false) }

    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    fun saveLine(line: SubtitleLine) {
        val start = max(0L, line.startMs - CLIP_PADDING_MS)
        val end = line.endMs + CLIP_PADDING_MS
        val id = Card.newId()
        val output = repository.newClipFile(id)
        savingCount++
        clipper.clip(source.uri, start, end, output) { result ->
            savingCount--
            result
                .onSuccess {
                    val now = System.currentTimeMillis()
                    repository.add(
                        Card(
                            id = id,
                            text = line.text,
                            sourceTitle = source.title,
                            sourceUri = source.uri.toString(),
                            startMs = line.startMs,
                            endMs = line.endMs,
                            audioFileName = output.name,
                            box = 1,
                            createdAt = now,
                            nextReviewAt = now,
                        )
                    )
                    message("Saved to Box 1")
                }
                .onFailure { message("Could not cut the audio: ${it.message}") }
        }
    }

    fun onSaveClicked() {
        val position = player.currentPosition
        val lines = externalLines
        if (lines != null) {
            val line = SubtitleLookup.lineForSave(lines, position)
            if (line == null) message("No subtitle line here yet") else saveLine(line)
            return
        }
        when (val target = tracker.lineForSave(position)) {
            is SaveTarget.Ready -> saveLine(target.line)
            SaveTarget.WaitForEnd -> {
                saveWhenLineEnds = true
                message("Will save when this line ends")
            }
            SaveTarget.NoLine ->
                message("No subtitles yet. Load a subtitle file or pick a subtitle track.")
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                if (externalLines != null) return
                val text = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n").trim()
                val closed = tracker.onCues(text, player.currentPosition)
                currentText = text
                if (saveWhenLineEnds && closed != null) {
                    saveWhenLineEnds = false
                    saveLine(closed)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    tracker.dropOpenLine()
                    saveWhenLineEnds = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                message("Playback error: ${error.errorCodeName}")
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Pause when the app goes to the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // With an external file the timing is known, so follow the playback position.
    LaunchedEffect(externalLines) {
        val lines = externalLines ?: return@LaunchedEffect
        while (true) {
            currentText = SubtitleLookup.activeAt(lines, player.currentPosition)?.text ?: ""
            delay(150)
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { SubtitleParser.parse(SubtitleParser.readLimited(context.contentResolver, uri)) }
            }
            result
                .onSuccess { lines ->
                    if (lines.isEmpty()) {
                        message("No subtitle lines found in this file (SRT or VTT expected)")
                    } else {
                        externalLines = lines
                        externalName = SourceNames.displayName(context, uri)
                        saveWhenLineEnds = false
                        message("Loaded ${lines.size} subtitle lines")
                    }
                }
                .onFailure { message("Could not read subtitles: ${it.message}") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(source.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        keepScreenOn = true
                        setShowSubtitleButton(true)
                        // Subtitles are drawn below the video instead, larger and easier to read.
                        subtitleView?.visibility = View.GONE
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
            )
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.heightIn(min = 96.dp).padding(16.dp),
                ) {
                    Text(
                        text = currentText,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OutlinedButton(
                    onClick = { subtitlePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                ) { Text("Load subtitles") }
                Button(
                    onClick = ::onSaveClicked,
                    modifier = Modifier.weight(1f),
                ) { Text(if (savingCount > 0) "Saving…" else "Save line") }
            }
            externalName?.let {
                Text(
                    "Subtitles: $it",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}
