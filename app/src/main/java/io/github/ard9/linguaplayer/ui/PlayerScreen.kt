package io.github.ard9.linguaplayer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
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
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberUpdatedState
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
import io.github.ard9.linguaplayer.data.ClipMargins
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.max

/** Extra audio kept before and after each subtitle line so words are not cut off. */
private const val SKIP_MS = 10_000L
private const val REPEAT_FALLBACK_MS = 5_000L
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

private val RATES = listOf(1f to "1×", 0.75f to "0.75×", 0.5f to "0.5×", 1.25f to "1.25×")

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

/** Where a line is and what it says, and how that was found. */
private sealed interface LineResult {
    data class Subtitle(
        val startMs: Long,
        val endMs: Long,
        val text: String,
        val meaning: String,
        val note: String,
        /** Kept so the editor can switch to another subtitle track. */
        val search: BuiltInSearch?,
        val trackIndex: Int,
    ) : LineResult

    data class Speech(val startMs: Long, val endMs: Long, val note: String) : LineResult

    data class None(val note: String) : LineResult
}

private data class QuickLine(val startMs: Long, val endMs: Long, val text: String, val meaning: String)

/** A short message next to the quick save button; [undo] offers to remove the card just saved. */
private class QuickNotice(val text: String, val undo: Card? = null)

private val SAVED_HAPTIC = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    HapticFeedbackConstants.CONFIRM
} else {
    HapticFeedbackConstants.KEYBOARD_TAP
}

/** The line of track [index] at the saved moment, with its Persian translation as the meaning. */
private fun lineFromTrack(search: BuiltInSearch, index: Int, margins: ClipMargins): LineResult.Subtitle? {
    val track = search.tracks[index]
    val line = SubtitleLookup.nearMoment(search.lines[track.number].orEmpty(), search.atMs) ?: return null
    val meaning = if (track.isPersian) {
        null
    } else {
        search.tracks.firstOrNull { it.isPersian }
            ?.let { persian -> SubtitleLookup.overlapping(search.lines[persian.number].orEmpty(), line) }
    }
    return LineResult.Subtitle(
        startMs = max(0L, line.startMs - margins.beforeMs),
        endMs = line.endMs + margins.afterMs,
        text = line.text,
        meaning = meaning ?: "",
        note = "Times and text from the subtitle track \"${track.label}\".",
        search = search,
        trackIndex = index,
    )
}

/** How much of the shorter of two ranges the other one covers (0..1). */
private fun overlapShare(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Double {
    val overlap = minOf(aEnd, bEnd) - maxOf(aStart, bStart)
    val shorter = minOf(aEnd - aStart, bEnd - bStart)
    return if (overlap <= 0 || shorter <= 0) 0.0 else overlap.toDouble() / shorter
}

/** Small, faint button that turns repeating the current line on and off. */
@Composable
private fun LoopButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) accent.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.35f))
            .clickable(onClickLabel = if (active) "Stop repeating this line" else "Repeat this line", onClick = onClick),
    ) {
        Icon(
            PlayerIcons.Repeat,
            contentDescription = if (active) "Stop repeating this line" else "Repeat this line",
            tint = if (active) Cinema.Night else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Small, faint save button that stays on screen while watching. Tap saves the current line
 * in the background; holding opens the editor.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickSaveButton(
    busy: Boolean,
    notice: QuickNotice?,
    onTap: () -> Unit,
    onHold: () -> Unit,
    onUndo: (Card) -> Unit,
    onNoticeShown: (QuickNotice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    if (notice != null) {
        LaunchedEffect(notice) {
            delay(if (notice.undo != null) 3_500 else 2_500)
            onNoticeShown(notice)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        AnimatedVisibility(visible = notice != null, enter = fadeIn(), exit = fadeOut()) {
            val shown = notice
            if (shown != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .background(Cinema.Night.copy(alpha = 0.85f), RoundedCornerShape(50))
                        .padding(start = 14.dp, end = if (shown.undo != null) 4.dp else 14.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Text(shown.text, color = Cinema.Screen, style = MaterialTheme.typography.bodySmall)
                    shown.undo?.let { card ->
                        TextButton(onClick = { onUndo(card) }) { Text("Undo") }
                    }
                }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .combinedClickable(
                    onClickLabel = "Save this line",
                    onLongClickLabel = "Save with options",
                    onClick = onTap,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHold()
                    },
                ),
        ) {
            Icon(
                PlayerIcons.Bookmark,
                contentDescription = "Save this line",
                tint = Color.White.copy(alpha = if (busy) 0.9f else 0.6f),
                modifier = Modifier.size(22.dp),
            )
            if (busy) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

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
    clipMargins: ClipMargins,
    onClipMarginsChange: (ClipMargins) -> Unit,
    /** Where to continue from, if this video was left part way. */
    resumeAtMs: Long?,
    onSavePosition: (positionMs: Long, lengthMs: Long) -> Unit,
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
    var marginsDialog by remember { mutableStateOf(false) }
    // Read when a line is found, so a change applies to the next save without reopening.
    val currentMargins = rememberUpdatedState(clipMargins)
    var scaleIndex by remember { mutableIntStateOf(0) }
    var seekFraction by remember { mutableStateOf<Float?>(null) }
    var externalLines by remember { mutableStateOf<List<SubtitleLine>?>(null) }
    var externalName by remember { mutableStateOf<String?>(null) }
    var savingCount by remember { mutableIntStateOf(0) }
    var quickBusy by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<QuickNotice?>(null) }
    // Lines saved while this video is open, to skip a second tap on the same line.
    val savedRanges = remember { mutableListOf<Pair<Long, Long>>() }
    val view = LocalView.current
    // Applied once playback has started; until then the saved position must not be overwritten.
    var resumePending by remember { mutableStateOf(resumeAtMs) }
    val savePosition by rememberUpdatedState(onSavePosition)
    var rateIndex by remember { mutableIntStateOf(0) }
    // Repeats this range while set (start to end of the current line, with margins).
    var loopRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    // Subtitle lines read for repeat/loop, reused while playback stays near them.
    var lineCache by remember { mutableStateOf<BuiltInSearch?>(null) }
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

    /** Cuts the audio and adds the card; [onDone] gets the card, or null if it failed. */
    fun saveClip(
        startMs: Long,
        endMs: Long,
        text: String,
        meaning: String,
        image: Bitmap?,
        onDone: (Card?) -> Unit,
    ) {
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
                    val card = Card(
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
                    repository.add(card)
                    savedRanges.add(startMs to endMs)
                    onDone(card)
                }
                .onFailure {
                    Log.w(TAG, "Could not cut the audio", it)
                    imageFile?.delete()
                    onDone(null)
                }
        }
    }

    /** The built-in subtitle track VLC is showing, if it can be matched to the file's tracks. */
    fun shownTrack(index: MkvSubtitles): MkvSubtitleTrack? {
        val listed = player.spuTracks?.filter { it.id != -1 } ?: return null
        val position = listed.indexOfFirst { it.id == player.spuTrack }
        return if (position >= 0 && listed.size == index.tracks.size) index.tracks[position] else null
    }

    /** Where the line around [now] starts and ends, and what it says, from the best source. */
    suspend fun findLine(now: Long, onStage: (String) -> Unit = {}): LineResult {
        val margins = currentMargins.value
        externalLines?.let { lines ->
            SubtitleLookup.nearMoment(lines, now)?.let { line ->
                return LineResult.Subtitle(
                    max(0L, line.startMs - margins.beforeMs),
                    line.endMs + margins.afterMs,
                    line.text,
                    "",
                    "Times and text from the subtitle file.",
                    null,
                    0,
                )
            }
        }
        val index = builtIn.await()
        val textTracks = index?.textTracks.orEmpty()
        if (index != null && textTracks.isNotEmpty()) {
            val lines = withContext(Dispatchers.IO) { runCatching { index.linesAround(now) } }
            lines.onFailure { Log.w(TAG, "Could not read subtitle lines", it) }
            lines.getOrNull()?.let { found ->
                val search = BuiltInSearch(now, found, textTracks)
                for (i in preferredOrder(textTracks, shownTrack(index))) {
                    lineFromTrack(search, i, margins)?.let { return it }
                }
            }
        }
        onStage("Finding where the sentence starts and ends…")
        val result = suspendCancellableCoroutine { continuation ->
            speechFinder.find(scope, source.uri, now) { continuation.resume(it) }
        }
        val outcome = result.getOrElse {
            return LineResult.None("Could not check the audio: ${it.message}.")
        }
        val found = outcome.found ?: return LineResult.None("No speech found near this moment (${outcome.details}).")
        return LineResult.Speech(
            max(0L, found.startMs - margins.beforeMs),
            found.endMs + margins.afterMs,
            when {
                found.cutAtStart && found.cutAtEnd -> "Found speech, but it is longer than the part checked. Check both times."
                found.cutAtStart -> "The sentence may start earlier. Check the start time."
                found.cutAtEnd -> "The sentence may continue. Check the end time."
                else -> "Times found from the pauses around the sentence."
            },
        )
    }

    fun applyToDialog(result: LineResult) {
        when (result) {
            is LineResult.Subtitle -> {
                autoFill = AutoFill(++fillVersion, result.startMs, result.endMs, result.text, result.meaning)
                builtInSearch = result.search
                selectedTrack = result.trackIndex
                timeNote = result.note
            }
            is LineResult.Speech -> {
                autoFill = AutoFill(++fillVersion, result.startMs, result.endMs, null, null)
                timeNote = result.note
            }
            is LineResult.None -> timeNote = result.note + " Set the times by hand."
        }
    }

    /**
     * Start and end (with margins) of the subtitle line at [now], from the subtitle file or the
     * video's own text tracks; null without subtitles.
     */
    suspend fun lineRangeAt(now: Long): Pair<Long, Long>? {
        val margins = currentMargins.value
        externalLines?.let { lines ->
            return SubtitleLookup.nearMoment(lines, now)
                ?.let { max(0L, it.startMs - margins.beforeMs) to it.endMs + margins.afterMs }
        }
        val index = builtIn.await() ?: return null
        val textTracks = index.textTracks
        if (textTracks.isEmpty()) return null
        val search = lineCache?.takeIf { now <= it.atMs + 500 && now >= it.atMs - 6_000 }
            ?: withContext(Dispatchers.IO) { runCatching { index.linesAround(now) }.getOrNull() }
                ?.let { BuiltInSearch(now, it, textTracks) }
                ?.also { lineCache = it }
            ?: return null
        for (i in preferredOrder(textTracks, shownTrack(index))) {
            val line = SubtitleLookup.nearMoment(search.lines[textTracks[i].number].orEmpty(), now) ?: continue
            return max(0L, line.startMs - margins.beforeMs) to line.endMs + margins.afterMs
        }
        return null
    }

    /** Plays the current line again (or the last 5 seconds without subtitles). */
    fun repeatLine() {
        val now = player.time
        scope.launch {
            val start = lineRangeAt(now)?.first ?: max(0L, now - REPEAT_FALLBACK_MS)
            player.setTime(start)
            if (!player.isPlaying) player.play()
        }
    }

    fun toggleLoop() {
        if (loopRange != null) {
            loopRange = null
            message("Loop off")
            return
        }
        val now = player.time
        scope.launch {
            val range = lineRangeAt(now) ?: (max(0L, now - REPEAT_FALLBACK_MS) to now)
            loopRange = range
            player.setTime(range.first)
            if (!player.isPlaying) player.play()
            message("Repeating this line. Tap Loop again to stop.")
        }
    }

    /** Long press: pause and open the editor, filling it in as the line is found. */
    fun openEditor() {
        val now = player.time
        player.pause()
        draftId++
        val id = draftId
        draft = SaveDraft(max(0L, now - 5_000L), now, "")
        autoFill = null
        timeNote = null
        builtInSearch = null
        selectedTrack = 0
        draftImage = null
        videoLayout?.let { layout -> FrameGrabber.grab(layout) { draftImage = it } }
        finding = "Looking for this line…"
        scope.launch {
            val result = findLine(now) { stage -> if (id == draftId) finding = stage }
            if (id != draftId) return@launch
            finding = null
            applyToDialog(result)
        }
    }

    /** Tap: save the line in the background while the film keeps playing. */
    fun quickSave() {
        val now = player.time
        quickBusy++
        scope.launch {
            try {
                val image = videoLayout?.let { layout ->
                    suspendCancellableCoroutine { c -> FrameGrabber.grab(layout) { c.resume(it) } }
                }
                val line = when (val result = findLine(now)) {
                    is LineResult.Subtitle -> QuickLine(result.startMs, result.endMs, result.text, result.meaning)
                    is LineResult.Speech -> QuickLine(result.startMs, result.endMs, "", "")
                    is LineResult.None -> {
                        notice = QuickNotice("No line found. Hold the button to save by hand.")
                        return@launch
                    }
                }
                // A second tap on the same line would only make a duplicate card.
                if (savedRanges.any { (s, e) -> overlapShare(s, e, line.startMs, line.endMs) > 0.5 }) {
                    notice = QuickNotice("Already saved")
                    return@launch
                }
                val card = suspendCancellableCoroutine { c ->
                    saveClip(line.startMs, line.endMs, line.text, line.meaning, image) { c.resume(it) }
                }
                notice = if (card == null) {
                    QuickNotice("Could not save this line")
                } else {
                    view.performHapticFeedback(SAVED_HAPTIC)
                    QuickNotice("Saved", undo = card)
                }
            } finally {
                quickBusy--
            }
        }
    }

    DisposableEffect(player) {
        player.setEventListener(MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.TimeChanged -> timeMs = event.timeChanged
                MediaPlayer.Event.LengthChanged -> lengthMs = event.lengthChanged
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    resumePending?.let { position ->
                        resumePending = null
                        player.setTime(position)
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val result = snackbar.showSnackbar(
                                message = "Continuing from ${formatTime(position)}",
                                actionLabel = "Start over",
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) player.setTime(0)
                        }
                    }
                }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> isPlaying = false
                MediaPlayer.Event.EndReached -> {
                    isPlaying = false
                    loopRange = null
                    savePosition(player.length, player.length)
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
            if (resumePending == null) savePosition(player.time, player.length)
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
            opened.getOrNull()?.close()
        }
    }

    // While looping, jump back at the end of the line; seeking far away ends the loop.
    LaunchedEffect(loopRange) {
        val (start, end) = loopRange ?: return@LaunchedEffect
        while (true) {
            val t = player.time
            if (t < start - 2_000 || t > end + 3_000) {
                loopRange = null
                break
            }
            if (t >= end) {
                player.setTime(start)
                delay(300) // Let the jump take effect before checking again.
            }
            delay(50)
        }
    }

    // Pause when the app goes to the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                if (resumePending == null) savePosition(player.time, player.length)
            }
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center),
            ) {
                TextButton(onClick = { repeatLine(); touch() }) {
                    Text("Repeat", color = Color.White)
                }
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
                    ControlButton("Speed ${RATES[rateIndex].second}") {
                        rateIndex = (rateIndex + 1) % RATES.size
                        player.setRate(RATES[rateIndex].first)
                        touch()
                    }
                    ControlButton("Clip margins") { marginsDialog = true; touch() }
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

        // The loop button sits just above the save button, in the same quiet style.
        LoopButton(
            active = loopRange != null,
            onClick = ::toggleLoop,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(end = 12.dp, bottom = 112.dp),
        )

        QuickSaveButton(
            busy = quickBusy > 0,
            notice = notice,
            onTap = ::quickSave,
            onHold = ::openEditor,
            onUndo = { card ->
                repository.delete(card)
                savedRanges.remove(card.startMs to card.endMs)
                notice = QuickNotice("Removed")
            },
            onNoticeShown = { shown -> if (notice === shown) notice = null },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(end = 12.dp),
        )

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
                val line = search?.let { lineFromTrack(it, index, currentMargins.value) }
                if (line != null) {
                    applyToDialog(line)
                } else {
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
                saveClip(s, e, text, meaning, image) { card ->
                    message(if (card != null) "Saved to Box 1" else "Could not cut the audio", sticky = card == null)
                }
            },
        )
    }

    if (marginsDialog) {
        ClipMarginsDialog(
            current = clipMargins,
            onChange = onClipMarginsChange,
            onDismiss = { marginsDialog = false },
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
