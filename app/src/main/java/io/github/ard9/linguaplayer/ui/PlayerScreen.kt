package io.github.ard9.linguaplayer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.ard9.linguaplayer.clip.ClipService
import io.github.ard9.linguaplayer.data.Card
import io.github.ard9.linguaplayer.data.CardRepository
import io.github.ard9.linguaplayer.data.PlaybackSource
import io.github.ard9.linguaplayer.data.SourceNames
import io.github.ard9.linguaplayer.data.SubtitleStyle
import io.github.ard9.linguaplayer.data.formatTime
import io.github.ard9.linguaplayer.player.FrameGrabber
import io.github.ard9.linguaplayer.player.SubtitleOcr
import io.github.ard9.linguaplayer.player.SubtitleWatcher
import io.github.ard9.linguaplayer.player.openVlcMedia
import io.github.ard9.linguaplayer.speech.SpeechFinder
import io.github.ard9.linguaplayer.subtitle.SubtitleLine
import io.github.ard9.linguaplayer.subtitle.SubtitleLookup
import io.github.ard9.linguaplayer.subtitle.SubtitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.max

/** Extra audio kept before and after each subtitle line so words are not cut off. */
private const val CLIP_PADDING_MS = 250L
private const val SKIP_MS = 10_000L
private const val CONTROLS_TIMEOUT_MS = 4_000L

private val SCALES = listOf(
    MediaPlayer.ScaleType.SURFACE_BEST_FIT to "Best fit",
    MediaPlayer.ScaleType.SURFACE_FIT_SCREEN to "Fit screen",
    MediaPlayer.ScaleType.SURFACE_FILL to "Fill",
    MediaPlayer.ScaleType.SURFACE_16_9 to "16:9",
    MediaPlayer.ScaleType.SURFACE_4_3 to "4:3",
    MediaPlayer.ScaleType.SURFACE_ORIGINAL to "Original",
)

private enum class TrackKind { Audio, Subtitle }

/** Starts playback once, after both the media is set and the video view is attached. */
private class StartGate(private val start: () -> Unit) {
    private var media = false
    private var view = false
    private var started = false

    fun mediaReady() {
        media = true
        maybeStart()
    }

    fun viewReady() {
        view = true
        maybeStart()
    }

    private fun maybeStart() {
        if (media && view && !started) {
            started = true
            start()
        }
    }
}

private data class SaveDraft(val startMs: Long, val endMs: Long, val text: String)

@Composable
fun PlayerScreen(
    source: PlaybackSource,
    libVlc: LibVLC,
    repository: CardRepository,
    clips: ClipService,
    speechFinder: SpeechFinder,
    onSubtitleStyleChange: (SubtitleStyle) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val opened = remember { runCatching { openVlcMedia(context, libVlc, source.uri) } }
    val player = remember { MediaPlayer(libVlc) }
    val startGate = remember { StartGate { player.play() } }

    var timeMs by remember { mutableLongStateOf(0L) }
    var lengthMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    // Follows the phone's sensor; the full-screen button can lock one orientation.
    var orientationLock by remember { mutableStateOf(OrientationLock.None) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var styleDialog by remember { mutableStateOf(false) }
    var scaleIndex by remember { mutableIntStateOf(0) }
    var seekFraction by remember { mutableStateOf<Float?>(null) }
    var externalLines by remember { mutableStateOf<List<SubtitleLine>?>(null) }
    var externalName by remember { mutableStateOf<String?>(null) }
    var savingCount by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<SaveDraft?>(null) }
    var draftImage by remember { mutableStateOf<Bitmap?>(null) }
    var videoLayout by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var detectedText by remember { mutableStateOf("") }
    var readingText by remember { mutableStateOf(false) }
    var waitingForLine by remember { mutableStateOf(false) }
    var detectedRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var findingSentence by remember { mutableStateOf(false) }
    var timeNote by remember { mutableStateOf<String?>(null) }
    var draftId by remember { mutableIntStateOf(0) }
    // Times built-in subtitles from the moment they appear and disappear on screen.
    val watcher = remember {
        SubtitleWatcher(
            layout = { videoLayout },
            clock = { player.time },
            isActive = { player.isPlaying && externalLines == null },
        )
    }
    var previewEndMs by remember { mutableStateOf<Long?>(null) }
    var trackDialog by remember { mutableStateOf<TrackKind?>(null) }

    fun message(text: String, sticky: Boolean = false) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                message = text,
                withDismissAction = sticky,
                duration = if (sticky) SnackbarDuration.Indefinite else SnackbarDuration.Short,
            )
        }
    }

    fun touch() {
        interaction++
    }

    fun seekTo(ms: Long) {
        val upper = if (lengthMs > 0) lengthMs else Long.MAX_VALUE
        watcher.reset()
        waitingForLine = false
        player.setTime(ms.coerceIn(0L, upper))
    }

    fun saveClip(startMs: Long, endMs: Long, text: String, meaning: String, image: Bitmap?) {
        val id = Card.newId()
        val imageFile = image?.let { bitmap ->
            repository.newImageFile(id).takeIf { FrameGrabber.saveJpeg(bitmap, it) }
        }
        savingCount++
        clips.clip(source.uri, startMs, endMs, id) { result ->
            savingCount--
            result
                .onSuccess { file ->
                    val now = System.currentTimeMillis()
                    repository.add(
                        Card(
                            id = id,
                            text = text,
                            meaning = meaning,
                            sourceTitle = source.title,
                            sourceUri = source.uri.toString(),
                            startMs = startMs,
                            endMs = endMs,
                            audioFileName = file.name,
                            imageFileName = imageFile?.name,
                            box = 1,
                            createdAt = now,
                            nextReviewAt = now,
                        )
                    )
                    message("Saved to Box 1")
                }
                .onFailure {
                    imageFile?.delete()
                    message("Could not cut the audio: ${it.message}", sticky = true)
                }
        }
    }

    fun openDraft(startMs: Long, endMs: Long, text: String, subtitleImage: Bitmap?, note: String? = null) {
        player.pause()
        draftId++
        draft = SaveDraft(startMs, endMs, text)
        detectedRange = null
        findingSentence = false
        timeNote = note
        draftImage = null
        detectedText = ""
        readingText = subtitleImage != null
        videoLayout?.let { layout -> FrameGrabber.grab(layout) { draftImage = it } }
        subtitleImage?.let { image ->
            SubtitleOcr.read(image) {
                detectedText = it
                readingText = false
            }
        }
    }

    fun openDraftFor(line: SubtitleWatcher.Line) = openDraft(
        max(0L, line.startMs - CLIP_PADDING_MS),
        line.endMs + CLIP_PADDING_MS,
        "",
        line.image,
        "Times come from when the subtitle appeared and disappeared.",
    )

    /** No subtitles: open the dialog now and fill in the times once the pauses are found. */
    fun openDraftWithSpeechSearch(now: Long) {
        openDraft(max(0L, now - 5_000L), now, "", null)
        val id = draftId
        findingSentence = true
        speechFinder.find(scope, source.uri, now) { result ->
            if (id != draftId) return@find
            findingSentence = false
            result
                .onSuccess { outcome ->
                    val found = outcome.found
                    if (found == null) {
                        timeNote = "No speech found near this moment (${outcome.details}). Set the times by hand."
                    } else {
                        detectedRange = found.startMs to found.endMs
                        timeNote = when {
                            found.cutAtStart && found.cutAtEnd -> "Found speech, but it is longer than the part checked. Check both times."
                            found.cutAtStart -> "The sentence may start earlier. Check the start time."
                            found.cutAtEnd -> "The sentence may continue. Check the end time."
                            else -> "Times found from the pauses around the sentence."
                        }
                    }
                }
                .onFailure { timeNote = "Could not check the audio: ${it.message}. Set the times by hand." }
        }
    }

    fun onSaveClicked() {
        if (waitingForLine) return
        val now = player.time
        val fileLine = externalLines?.let { SubtitleLookup.lineForSave(it, now) }
        if (fileLine != null) {
            openDraft(max(0L, fileLine.startMs - CLIP_PADDING_MS), fileLine.endMs + CLIP_PADDING_MS, fileLine.text, null)
            return
        }
        when (val target = if (externalLines == null) watcher.lineForSave(now) else SubtitleWatcher.SaveTarget.NoLine) {
            is SubtitleWatcher.SaveTarget.Ready -> openDraftFor(target.line)
            SubtitleWatcher.SaveTarget.WaitForEnd -> {
                // Keep playing until the subtitle disappears, so its end time is exact.
                waitingForLine = true
                if (!player.isPlaying) player.play()
                message("Saving when this line ends…")
            }
            SubtitleWatcher.SaveTarget.NoLine -> openDraftWithSpeechSearch(now)
        }
    }

    DisposableEffect(watcher) {
        watcher.onLineClosed = { line ->
            if (waitingForLine) {
                waitingForLine = false
                openDraftFor(line)
            }
        }
        watcher.start()
        onDispose { watcher.stop() }
    }

    DisposableEffect(player) {
        player.setEventListener(MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.TimeChanged -> timeMs = event.timeChanged
                MediaPlayer.Event.LengthChanged -> lengthMs = event.lengthChanged
                MediaPlayer.Event.Playing -> isPlaying = true
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> isPlaying = false
                MediaPlayer.Event.EndReached -> {
                    isPlaying = false
                    controlsVisible = true
                }
                MediaPlayer.Event.EncounteredError -> {
                    isPlaying = false
                    message("VLC could not play this media", sticky = true)
                }
            }
        })
        opened
            .onSuccess {
                player.setMedia(it.media)
                it.media.release()
                startGate.mediaReady()
            }
            .onFailure { message("Could not open: ${it.message}", sticky = true) }
        onDispose {
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
            opened.getOrNull()?.close()
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

    LaunchedEffect(orientationLock) {
        activity?.requestedOrientation = when (orientationLock) {
            OrientationLock.None -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            OrientationLock.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationLock.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }
    // Landscape is full screen: hide the system bars.
    LaunchedEffect(landscape) { activity?.let { setSystemBarsHidden(it, landscape) } }
    DisposableEffect(activity) {
        onDispose {
            activity?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                setSystemBarsHidden(it, false)
            }
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, interaction) {
        if (controlsVisible && isPlaying) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    // Stop a preview at the end of its range.
    LaunchedEffect(previewEndMs) {
        val end = previewEndMs ?: return@LaunchedEffect
        while (player.time < end) delay(50)
        player.pause()
        previewEndMs = null
    }

    BackHandler {
        if (orientationLock == OrientationLock.Landscape) orientationLock = OrientationLock.None else onBack()
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
                        player.setSpuTrack(-1) // Hide built-in subtitles so text is not shown twice.
                        message("Loaded ${lines.size} subtitle lines")
                    }
                }
                .onFailure { message("Could not read subtitles: ${it.message}") }
        }
    }

    val currentText = externalLines?.let { SubtitleLookup.activeAt(it, timeMs)?.text }.orEmpty()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    keepScreenOn = true
                    videoLayout = this
                    // Attach only once the view is in the window; attaching earlier sometimes
                    // left the video black while the audio played.
                    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            player.attachViews(this@apply, null, true, false)
                            startGate.viewReady()
                        }

                        override fun onViewDetachedFromWindow(v: View) {
                            player.detachViews()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Tap anywhere to show or hide the controls.
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            }
        )

        if (currentText.isNotBlank()) {
            SubtitleText(
                text = currentText,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = if (controlsVisible) 128.dp else 28.dp),
            )
        }

        if (controlsVisible) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    source.title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            ExtendedFloatingActionButton(
                onClick = ::onSaveClicked,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        when {
                            waitingForLine -> "Waiting…"
                            savingCount > 0 -> "Saving…"
                            else -> "Save"
                        }
                    )
                },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center),
            ) {
                TextButton(onClick = { seekTo(player.time - SKIP_MS); touch() }) {
                    Text("−10s", color = Color.White)
                }
                IconButton(
                    onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                        touch()
                    },
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        if (isPlaying) PlayerIcons.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }
                TextButton(onClick = { seekTo(player.time + SKIP_MS); touch() }) {
                    Text("+10s", color = Color.White)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val shownMs = seekFraction?.let { (it * lengthMs).toLong() } ?: timeMs
                    Text(formatTime(shownMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = seekFraction ?: if (lengthMs > 0) (timeMs.toFloat() / lengthMs).coerceIn(0f, 1f) else 0f,
                        onValueChange = {
                            seekFraction = it
                            touch()
                        },
                        onValueChangeFinished = {
                            seekFraction?.let { seekTo((it * lengthMs).toLong()) }
                            seekFraction = null
                        },
                        enabled = lengthMs > 0,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(formatTime(lengthMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    ControlButton("Aspect: ${SCALES[scaleIndex].second}") {
                        scaleIndex = (scaleIndex + 1) % SCALES.size
                        player.setVideoScale(SCALES[scaleIndex].first)
                        touch()
                    }
                    ControlButton("Audio") { trackDialog = TrackKind.Audio; touch() }
                    ControlButton("Subtitles") { trackDialog = TrackKind.Subtitle; touch() }
                    ControlButton("Load subtitle file") { subtitlePicker.launch(arrayOf("*/*")); touch() }
                    ControlButton("Subtitle style") { styleDialog = true; touch() }
                    ControlButton(if (landscape) "Portrait" else "Full screen") {
                        orientationLock = if (landscape) OrientationLock.Portrait else OrientationLock.Landscape
                        touch()
                    }
                    if (orientationLock != OrientationLock.None) {
                        ControlButton("Auto-rotate") { orientationLock = OrientationLock.None; touch() }
                    }
                }
                externalName?.let {
                    Text(
                        "Subtitle file: $it",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }


        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).padding(top = 56.dp))
    }

    draft?.let { current ->
        CardEditorDialog(
            initialStartMs = current.startMs,
            initialEndMs = current.endMs,
            initialText = current.text,
            detectedText = detectedText,
            readingText = readingText,
            detectedRange = detectedRange,
            findingSentence = findingSentence,
            timeNote = timeNote,
            image = draftImage,
            currentTime = { player.time },
            onPreview = { s, e ->
                watcher.reset()
                player.setTime(s)
                player.play()
                previewEndMs = e
            },
            onDismiss = {
                draftId++
                draft = null
                draftImage = null
                previewEndMs = null
            },
            onSave = { s, e, text, meaning ->
                draftId++
                val image = draftImage
                draft = null
                draftImage = null
                previewEndMs = null
                player.pause()
                saveClip(s, e, text, meaning, image)
            },
        )
    }

    if (styleDialog) {
        SubtitleStyleDialog(
            current = LocalSubtitleStyle.current,
            onChange = onSubtitleStyleChange,
            onDismiss = { styleDialog = false },
        )
    }

    trackDialog?.let { kind ->
        TrackDialog(
            title = if (kind == TrackKind.Audio) "Audio track" else "Subtitle track",
            tracks = (if (kind == TrackKind.Audio) player.audioTracks else player.spuTracks)
                ?.map { it.id to it.name }
                .orEmpty(),
            selectedId = if (kind == TrackKind.Audio) player.audioTrack else player.spuTrack,
            onSelect = { id ->
                if (kind == TrackKind.Audio) {
                    player.setAudioTrack(id)
                } else {
                    player.setSpuTrack(id)
                    if (id != -1) {
                        externalLines = null
                        externalName = null
                    }
                }
                trackDialog = null
            },
            onDismiss = { trackDialog = null },
        )
    }
}

@Composable
private fun ControlButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label, color = Color.White) }
}

@Composable
private fun TrackDialog(
    title: String,
    tracks: List<Pair<Int, String>>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (tracks.isEmpty()) {
                Text("No tracks available.")
            } else {
                Column {
                    tracks.forEach { (id, name) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(id) },
                        ) {
                            RadioButton(selected = id == selectedId, onClick = { onSelect(id) })
                            Text(name)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private enum class OrientationLock { None, Landscape, Portrait }

private fun setSystemBarsHidden(activity: Activity, hidden: Boolean) {
    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    if (hidden) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}
