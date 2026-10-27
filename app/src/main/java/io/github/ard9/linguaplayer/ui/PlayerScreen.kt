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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import io.github.ard9.linguaplayer.player.openVlcMedia
import io.github.ard9.linguaplayer.subtitle.mkv.MkvSubtitleTrack
import io.github.ard9.linguaplayer.subtitle.mkv.MkvSubtitles
import io.github.ard9.linguaplayer.subtitle.mkv.byteSourceFor
import io.github.ard9.linguaplayer.speech.SpeechFinder
import io.github.ard9.linguaplayer.subtitle.SubtitleLine
import io.github.ard9.linguaplayer.subtitle.SubtitleLookup
import io.github.ard9.linguaplayer.subtitle.SubtitleParser
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.max

/** Extra audio kept before and after each subtitle line so words are not cut off. */
private const val CLIP_PADDING_MS = 450L
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

private const val TAG = "PlayerScreen"

/** Lines of all text tracks around the moment Save was tapped, kept to switch tracks. */
private class BuiltInSearch(
    val atMs: Long,
    val lines: Map<Long, List<SubtitleLine>>,
    val tracks: List<MkvSubtitleTrack>,
)

/**
 * Track indexes to try, best first. Cards are for learning the film's language, so a
 * non-Persian track is preferred for the text (the one on screen, then the default), and
 * the Persian one is used for the meaning.
 */
private fun preferredOrder(tracks: List<MkvSubtitleTrack>, shown: MkvSubtitleTrack?): List<Int> {
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
    var autoFill by remember { mutableStateOf<AutoFill?>(null) }
    var finding by remember { mutableStateOf<String?>(null) }
    var timeNote by remember { mutableStateOf<String?>(null) }
    var draftId by remember { mutableIntStateOf(0) }
    var fillVersion by remember { mutableIntStateOf(0) }
    // Built-in subtitle tracks of MKV/WebM files, read straight from the file (headers only).
    val builtIn = remember {
        scope.async(Dispatchers.IO) {
            runCatching { byteSourceFor(context, source.uri)?.let { MkvSubtitles.open(it) } }
                .onFailure { Log.w(TAG, "Could not read built-in subtitles", it) }
                .getOrNull()
        }
    }
    var builtInSearch by remember { mutableStateOf<BuiltInSearch?>(null) }
    var selectedTrack by remember { mutableIntStateOf(0) }
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

    fun openDraft(startMs: Long, endMs: Long, text: String, note: String? = null) {
        player.pause()
        draftId++
        draft = SaveDraft(startMs, endMs, text)
        autoFill = null
        finding = null
        timeNote = note
        builtInSearch = null
        selectedTrack = 0
        draftImage = null
        videoLayout?.let { layout -> FrameGrabber.grab(layout) { draftImage = it } }
    }

    /** Fills the dialog from the line of track [index] at the saved moment; false if it has none. */
    fun useTrack(search: BuiltInSearch, index: Int): Boolean {
        val track = search.tracks[index]
        val line = SubtitleLookup.nearMoment(search.lines[track.number].orEmpty(), search.atMs) ?: return false
        // The Persian track, when there is one, gives the meaning of a line in another language.
        val meaning = if (track.isPersian) {
            null
        } else {
            search.tracks.firstOrNull { it.isPersian }
                ?.let { persian -> SubtitleLookup.overlapping(search.lines[persian.number].orEmpty(), line) }
        }
        autoFill = AutoFill(
            version = ++fillVersion,
            startMs = max(0L, line.startMs - CLIP_PADDING_MS),
            endMs = line.endMs + CLIP_PADDING_MS,
            text = line.text,
            meaning = meaning ?: "",
        )
        selectedTrack = index
        timeNote = "Times and text from the subtitle track \"${track.label}\"."
        return true
    }

    /** No usable subtitle line: find the pauses around the sentence instead. */
    fun searchSpeech(now: Long, id: Int) {
        finding = "Finding where the sentence starts and ends…"
        speechFinder.find(scope, source.uri, now) { result ->
            if (id != draftId) return@find
            finding = null
            result
                .onSuccess { outcome ->
                    val found = outcome.found
                    if (found == null) {
                        timeNote = "No speech found near this moment (${outcome.details}). Set the times by hand."
                    } else {
                        autoFill = AutoFill(++fillVersion, found.startMs, found.endMs, null, null)
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

    /** The built-in subtitle track VLC is showing, if it can be matched to the file's tracks. */
    fun shownTrack(index: MkvSubtitles): MkvSubtitleTrack? {
        val listed = player.spuTracks?.filter { it.id != -1 } ?: return null
        val position = listed.indexOfFirst { it.id == player.spuTrack }
        return if (position >= 0 && listed.size == index.tracks.size) index.tracks[position] else null
    }

    fun onSaveClicked() {
        val now = player.time
        val fileLine = externalLines?.let { SubtitleLookup.lineForSave(it, now) }
        if (fileLine != null) {
            openDraft(
                max(0L, fileLine.startMs - CLIP_PADDING_MS),
                fileLine.endMs + CLIP_PADDING_MS,
                fileLine.text,
                "Times and text from the subtitle file.",
            )
            return
        }
        openDraft(max(0L, now - 5_000L), now, "")
        val id = draftId
        finding = "Looking for this line in the subtitles…"
        scope.launch {
            val index = builtIn.await()
            val textTracks = index?.textTracks.orEmpty()
            if (index != null && textTracks.isNotEmpty()) {
                val lines = withContext(Dispatchers.IO) { runCatching { index.linesAround(now) } }
                if (id != draftId) return@launch
                lines.onFailure { Log.w(TAG, "Could not read subtitle lines", it) }
                lines.getOrNull()?.let { found ->
                    val search = BuiltInSearch(now, found, textTracks)
                    builtInSearch = search
                    for (i in preferredOrder(textTracks, shownTrack(index))) {
                        if (useTrack(search, i)) {
                            finding = null
                            return@launch
                        }
                    }
                    builtInSearch = null
                }
            }
            if (id == draftId) searchSpeech(now, id)
        }
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
                    .windowInsetsPadding(WindowInsets.safeDrawing)
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
                    // Keep the controls clear of the status bar, camera cutout and navigation bar.
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
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
                text = { Text(if (savingCount > 0) "Saving…" else "Save") },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(end = 16.dp),
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
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
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


        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 56.dp),
        )
    }

    draft?.let { current ->
        CardEditorDialog(
            initialStartMs = current.startMs,
            initialEndMs = current.endMs,
            initialText = current.text,
            autoFill = autoFill,
            finding = finding,
            timeNote = timeNote,
            trackOptions = builtInSearch?.tracks?.map { it.label }.orEmpty(),
            selectedTrack = selectedTrack,
            onSelectTrack = { index ->
                val search = builtInSearch
                if (search != null && !useTrack(search, index)) {
                    selectedTrack = index
                    timeNote = "This track has no line at this moment."
                }
            },
            image = draftImage,
            currentTime = { player.time },
            onPreview = { s, e ->
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
