package io.github.ard9.linguaplayer.ui

import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import io.github.ard9.linguaplayer.subtitle.mkv.NoSubtitleIndexException
import io.github.ard9.linguaplayer.subtitle.SubtitleIndexCache
import io.github.ard9.linguaplayer.player.LineSearch
import io.github.ard9.linguaplayer.player.FoundLine
import io.github.ard9.linguaplayer.player.BuiltInSearch
import kotlinx.coroutines.Job
import android.os.SystemClock
import io.github.ard9.linguaplayer.player.LineLoop
import io.github.ard9.linguaplayer.player.LineStep
import io.github.ard9.linguaplayer.data.parseClock
import io.github.ard9.linguaplayer.data.formatClock
import io.github.ard9.linguaplayer.roleplay.SceneRange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import io.github.ard9.linguaplayer.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.Manifest
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
import androidx.compose.runtime.mutableStateListOf
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
import io.github.ard9.linguaplayer.data.RolePlayPrefs
import io.github.ard9.linguaplayer.roleplay.RoleLine
import io.github.ard9.linguaplayer.roleplay.RolePlaySession
import io.github.ard9.linguaplayer.roleplay.Roles
import io.github.ard9.linguaplayer.roleplay.ScenePlayer
import io.github.ard9.linguaplayer.roleplay.VoiceRecorder
import java.io.File
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
/** Back and forward without subtitle lines. */
private const val SKIP_MS = 5_000L
private const val REPEAT_FALLBACK_MS = 5_000L
private const val CONTROLS_TIMEOUT_MS = 4_000L
/** Wait after playback starts before reading the film's subtitle lines in the background. */
private const val PRELOAD_DELAY_MS = 3_000L
/**
 * The loop jumps back this much before the end of its range: the player reports time in
 * steps, and the jump itself takes a moment, so this shortens the pause between repeats. The
 * range already includes the after-line clip margin.
 */
private const val LOOP_EARLY_MS = 150L
/** A found line plays from a moment before it, so its first word is heard. */
private const val SEARCH_LEAD_MS = 300L
/** Lines read ahead for the forward button before all lines of the film are known. */
private const val STEP_READ_LINES = 3

/** Scale types with their names: a string resource, or null for a ratio shown as is. */
private val SCALES = listOf(
    Triple(MediaPlayer.ScaleType.SURFACE_BEST_FIT, R.string.scale_best_fit, ""),
    Triple(MediaPlayer.ScaleType.SURFACE_FIT_SCREEN, R.string.scale_fit_screen, ""),
    Triple(MediaPlayer.ScaleType.SURFACE_FILL, R.string.scale_fill, ""),
    Triple(MediaPlayer.ScaleType.SURFACE_16_9, null, "16:9"),
    Triple(MediaPlayer.ScaleType.SURFACE_4_3, null, "4:3"),
    Triple(MediaPlayer.ScaleType.SURFACE_ORIGINAL, R.string.scale_original, ""),
)

private enum class TrackKind { Audio, Subtitle }

/** Id of the loaded subtitle file in the subtitle list (libVLC ids are -1 or more). */
private const val FILE_TRACK_ID = Int.MIN_VALUE

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

/** A track's name for the user, with its language named in the app's language. */
private fun Context.trackLabel(track: MkvSubtitleTrack): String =
    track.label(resources.configuration.locales[0], getString(R.string.track_unnamed, track.number))

/** A line found in a built-in track, with a note naming the track for the editor. */
private fun Context.subtitleResult(found: FoundLine, search: BuiltInSearch) = LineResult.Subtitle(
    startMs = found.startMs,
    endMs = found.endMs,
    text = found.text,
    meaning = found.meaning,
    note = getString(R.string.note_track, trackLabel(search.tracks[found.trackIndex])),
    search = search,
    trackIndex = found.trackIndex,
)

/** Small, faint button that turns repeating the current line on and off. */
@Composable
private fun LoopButton(active: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val label = stringResource(if (active) R.string.loop_stop else R.string.loop_start)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) accent.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.35f))
            .clickable(onClickLabel = label, onClick = onClick),
    ) {
        Icon(
            PlayerIcons.Repeat,
            contentDescription = label,
            tint = if (active) Cinema.Night else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp),
        )
        if (busy) {
            CircularProgressIndicator(color = Cinema.Night, strokeWidth = 2.dp, modifier = Modifier.size(40.dp))
        }
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
                        TextButton(onClick = { onUndo(card) }) { Text(stringResource(R.string.undo)) }
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
                    onClickLabel = stringResource(R.string.save_line),
                    onLongClickLabel = stringResource(R.string.save_with_options),
                    onClick = onTap,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHold()
                    },
                ),
        ) {
            Icon(
                PlayerIcons.Bookmark,
                contentDescription = stringResource(R.string.save_line),
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
    rolePlayPrefs: RolePlayPrefs,
    onRolePlayPrefsChange: (RolePlayPrefs) -> Unit,
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
    // The media in use; replaced when an online film falls back from the cache to the link.
    var currentMedia by remember { mutableStateOf(opened.getOrNull()) }
    var triedDirect by remember { mutableStateOf(false) }
    val player = remember { MediaPlayer(libVlc) }
    val startGate = remember { StartGate { player.play() } }

    var timeMs by remember { mutableLongStateOf(0L) }
    var lengthMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    // While a finger is on the screen the controls stay; they hide 4 s after it is lifted.
    var fingerDown by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    // Follows the phone's sensor; the full-screen button can lock one orientation.
    var orientationLock by remember { mutableStateOf(OrientationLock.None) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var styleDialog by remember { mutableStateOf(false) }
    var marginsDialog by remember { mutableStateOf(false) }
    // Read when a line is found, so a change applies to the next save without reopening.
    val currentMargins = rememberUpdatedState(clipMargins)
    var scaleIndex by remember { mutableIntStateOf(0) }
    // Video outputs libVLC has open. A scale set before there is one ends up wrong (black
    // picture), so the chosen scale is applied when the video output appears.
    var voutCount by remember { mutableIntStateOf(0) }
    // The back/forward jump being worked out (finding the line can take a moment online).
    var stepJob by remember { mutableStateOf<Job?>(null) }
    // Whether the film has built-in text tracks; the back/forward buttons then step by line.
    var hasTextTracks by remember { mutableStateOf(false) }
    // Set when the video views were detached because the app went to the background.
    var viewsDetachedForStop by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableStateOf<Float?>(null) }
    var externalLines by remember { mutableStateOf<List<SubtitleLine>?>(null) }
    var externalName by remember { mutableStateOf<String?>(null) }
    var savingCount by remember { mutableIntStateOf(0) }
    var quickBusy by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<QuickNotice?>(null) }
    // Lines saved while this video is open, to skip a second tap on the same line.
    val savedRanges = remember { mutableListOf<Pair<Long, Long>>() }
    val view = LocalView.current
    // Role-play: setup dialog state and the running session.
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    var rolePlaySetup by remember { mutableStateOf(false) }
    var sceneFrom by remember { mutableLongStateOf(0L) }
    // Time range of the scene, as typed (m:ss), used when role-play is set to a time range.
    var rangeFromText by remember { mutableStateOf("") }
    var rangeToText by remember { mutableStateOf("") }
    var sceneLines by remember { mutableStateOf<List<SubtitleLine>?>(null) }
    var iStart by remember { mutableStateOf(false) }
    val sceneMine = remember { mutableStateListOf<Boolean>() }
    var session by remember { mutableStateOf<RolePlaySession?>(null) }
    val scenePlayer = remember {
        object : ScenePlayer {
            override val timeMs: Long get() = player.time
            override fun play() = player.play()
            override fun pause() = player.pause()
            override fun seekTo(ms: Long) {
                player.setTime(ms)
            }
            override fun setMuted(muted: Boolean) {
                player.setVolume(if (muted) 0 else 100)
            }
        }
    }
    // Applied once playback has started; until then the saved position must not be overwritten.
    var resumePending by remember { mutableStateOf(resumeAtMs) }
    val savePosition by rememberUpdatedState(onSavePosition)
    var rateIndex by remember { mutableIntStateOf(0) }
    // Repeats this range while set (start to end of the current line, with margins).
    var loopRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    // Set while the line to loop is being found (the button already shows it is on).
    var loopJob by remember { mutableStateOf<Job?>(null) }
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
    // Subtitle search: the dialog, and the whole film's built-in lines once read.
    var searchOpen by remember { mutableStateOf(false) }
    var resumeAfterSearch by remember { mutableStateOf(false) }
    var builtInSearchIndex by remember { mutableStateOf<SearchIndex?>(null) }
    // Every line of the film's text tracks, once read: finding a line then needs no download.
    var filmLines by remember { mutableStateOf<Map<Long, List<SubtitleLine>>?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val indexCache = remember { SubtitleIndexCache(File(context.cacheDir, "subtitle-index")) }

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

    /** Ends the loop (also one still being set up); [announce] shows "Loop off". */
    fun stopLoop(announce: Boolean = true) {
        if (loopRange == null && loopJob == null) return
        loopJob?.cancel()
        loopJob = null
        loopRange = null
        if (announce) message(context.getString(R.string.loop_off))
    }

    /** A jump chosen by the user; it ends the loop. */
    fun seekTo(ms: Long) {
        stopLoop()
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
            LineSearch.fromFile(lines, now, margins)?.let { found ->
                return LineResult.Subtitle(
                    found.startMs,
                    found.endMs,
                    found.text,
                    found.meaning,
                    context.getString(R.string.note_subtitle_file),
                    null,
                    0,
                )
            }
        }
        val index = builtIn.await()
        val textTracks = index?.textTracks.orEmpty()
        if (index != null && textTracks.isNotEmpty()) {
            val lines = filmLines?.let { Result.success(it) }
                ?: withContext(Dispatchers.IO) { runCatching { index.linesAround(now) } }
            lines.onFailure { Log.w(TAG, "Could not read subtitle lines", it) }
            lines.getOrNull()?.let { found ->
                val search = BuiltInSearch(now, found, textTracks)
                LineSearch.best(search, shownTrack(index), margins)?.let { return context.subtitleResult(it, search) }
            }
        }
        onStage(context.getString(R.string.finding_sentence))
        val result = suspendCancellableCoroutine { continuation ->
            speechFinder.find(scope, source.uri, now) { continuation.resume(it) }
        }
        val outcome = result.getOrElse {
            return LineResult.None(context.getString(R.string.audio_check_failed, context.describe(it)))
        }
        val found = outcome.found ?: return LineResult.None(context.getString(R.string.no_speech).also { Log.i(TAG, "No speech: ${outcome.details}") })
        return LineResult.Speech(
            max(0L, found.startMs - margins.beforeMs),
            found.endMs + margins.afterMs,
            context.getString(
                when {
                    found.cutAtStart && found.cutAtEnd -> R.string.speech_cut_both
                    found.cutAtStart -> R.string.speech_cut_start
                    found.cutAtEnd -> R.string.speech_cut_end
                    else -> R.string.speech_found
                }
            ),
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
            is LineResult.None -> timeNote = context.getString(R.string.set_by_hand, result.note)
        }
    }

    /**
     * Start and end (with margins) of the subtitle line at [now], from the subtitle file or the
     * video's own text tracks; null without subtitles.
     */
    suspend fun lineRangeAt(now: Long): Pair<Long, Long>? {
        val margins = currentMargins.value
        externalLines?.let { lines ->
            return LineSearch.fromFile(lines, now, margins)?.let { it.startMs to it.endMs }
        }
        val index = builtIn.await() ?: return null
        val textTracks = index.textTracks
        if (textTracks.isEmpty()) return null
        val search = filmLines?.let { BuiltInSearch(now, it, textTracks) }
            ?: lineCache?.takeIf { now <= it.atMs + 500 && now >= it.atMs - 6_000 }
            ?: withContext(Dispatchers.IO) { runCatching { index.linesAround(now) }.getOrNull() }
                ?.let { BuiltInSearch(now, it, textTracks) }
                ?.also { lineCache = it }
            ?: return null
        // The cached lines may have been read a little earlier; look for the line at [now].
        return LineSearch.best(BuiltInSearch(now, search.lines, textTracks), shownTrack(index), margins)
            ?.let { it.startMs to it.endMs }
    }

    /** Reads scene lines from the subtitle file if loaded, else from the best built-in text track. */
    suspend fun readScene(
        fromFile: (List<SubtitleLine>) -> List<SubtitleLine>,
        fromTrack: (MkvSubtitles, Long) -> List<SubtitleLine>,
    ): List<SubtitleLine> {
        externalLines?.let { return fromFile(it) }
        val index = builtIn.await() ?: return emptyList()
        val tracks = index.textTracks
        if (tracks.isEmpty()) return emptyList()
        val track = tracks[LineSearch.preferredOrder(tracks, shownTrack(index)).first()]
        filmLines?.let { return fromFile(it[track.number].orEmpty()) }
        return withContext(Dispatchers.IO) {
            runCatching { fromTrack(index, track.number) }
                .onFailure { Log.w(TAG, "Could not read the scene", it) }
                .getOrDefault(emptyList())
        }
    }

    /** Up to [count] subtitle lines from [fromMs] on, from the subtitle file or the best text track. */
    suspend fun sceneLinesFrom(fromMs: Long, count: Int): List<SubtitleLine> =
        readScene({ SubtitleLookup.scene(it, fromMs, count) }) { index, track -> index.linesFrom(fromMs, track, count) }

    /** Subtitle lines that start inside [range], from the subtitle file or the best text track. */
    suspend fun sceneLinesBetween(range: SceneRange.Ok): List<SubtitleLine> =
        readScene({ SubtitleLookup.between(it, range.fromMs, range.toMs).take(RolePlayPrefs.MAX_LINES) }) { index, track ->
            index.linesBetween(range.fromMs, range.toMs, track, RolePlayPrefs.MAX_LINES)
        }

    /** Reads every line of the film's text tracks (from the cache if read before). */
    /**
     * Reads every line of the film's text tracks (from the cache if read before), for search and
     * for finding lines without a download. In the [background] (when the film opens) it only
     * reads when that is cheap: from the cache, or through the subtitle cues; it fails quietly.
     */
    fun loadSearchIndex(background: Boolean = false) {
        searchJob?.cancel()
        builtInSearchIndex = SearchIndex.Loading(0f)
        searchJob = scope.launch {
            val index = builtIn.await()
            val tracks = index?.textTracks.orEmpty()
            if (index == null || tracks.isEmpty()) {
                builtInSearchIndex = if (background) null else SearchIndex.NoSubtitles
                return@launch
            }
            val ordered = LineSearch.preferredOrder(tracks, shownTrack(index)).map { tracks[it] }
            val key = source.uri.toString()
            val online = source.uri.scheme == "http" || source.uri.scheme == "https"
            val cached = withContext(Dispatchers.IO) { runCatching { indexCache.load(key, tracks.map { it.number }.toSet()) }.getOrNull() }
            if (background && cached == null && !index.canReadAllLinesCheaply) {
                builtInSearchIndex = null // Reading would mean the whole file; wait for a search.
                return@launch
            }
            builtInSearchIndex = try {
                val lines = cached
                    ?: runInterruptible(Dispatchers.IO) {
                        index.allLines(
                            onProgress = { p -> builtInSearchIndex = SearchIndex.Loading(p) },
                            // Online, each line is a small request; a few at once is much faster.
                            parallelism = if (online) 6 else 1,
                        )
                    }.also { read -> withContext(Dispatchers.IO) { runCatching { indexCache.save(key, read) } } }
                filmLines = lines
                SearchIndex.Ready(ordered.map { context.trackLabel(it) to lines[it.number].orEmpty() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSubtitleIndexException) {
                SearchIndex.NeedsFile
            } catch (e: Exception) {
                Log.w(TAG, "Could not read all subtitle lines", e)
                if (background) null else SearchIndex.Failed(e)
            }
        }
    }

    fun openSearch() {
        resumeAfterSearch = player.isPlaying
        player.pause()
        searchOpen = true
        val current = builtInSearchIndex
        if (externalLines == null && (current == null || current is SearchIndex.Failed)) loadSearchIndex()
    }

    fun openRolePlay() {
        player.pause()
        stopLoop(announce = false)
        sceneFrom = player.time
        rangeFromText = formatClock(sceneFrom)
        rangeToText = formatClock(sceneFrom + SceneRange.DEFAULT_MS)
        sceneLines = null
        rolePlaySetup = true
    }

    fun startRolePlay() {
        val lines = sceneLines ?: return
        val roles = lines.mapIndexed { i, line -> RoleLine(line.startMs, line.endMs, line.text, sceneMine.getOrElse(i) { false }) }
        rolePlaySetup = false
        controlsVisible = false
        session?.close()
        val started = RolePlaySession(
            lines = roles,
            pauseForMe = rolePlayPrefs.pauseForMe,
            showMyText = rolePlayPrefs.showMyText,
            folder = File(context.cacheDir, "roleplay/${System.currentTimeMillis()}"),
            player = scenePlayer,
            recorder = recorder,
            scope = scope,
        )
        session = started
        @Suppress("MissingPermission") // Checked in onStartRolePlay.
        started.start()
    }

    fun closeRolePlay() {
        session?.close()
        session = null
    }

    /**
     * The lines the back and forward buttons step through: the subtitle file, else the best
     * built-in text track (all lines once read, else the lines near [now] in that direction).
     */
    suspend fun stepLines(now: Long, forward: Boolean): List<SubtitleLine> {
        externalLines?.let { return it }
        val index = builtIn.await() ?: return emptyList()
        val tracks = index.textTracks
        if (tracks.isEmpty()) return emptyList()
        val track = tracks[LineSearch.preferredOrder(tracks, shownTrack(index)).first()]
        filmLines?.let { return it[track.number].orEmpty() }
        return withContext(Dispatchers.IO) {
            runCatching {
                if (forward) index.linesFrom(now, track.number, STEP_READ_LINES) else index.linesAround(now)[track.number].orEmpty()
            }.onFailure { Log.w(TAG, "Could not read lines to step through", it) }.getOrDefault(emptyList())
        }
    }

    /** Back or forward one subtitle line, or [SKIP_MS] without lines. */
    fun stepLine(forward: Boolean) {
        stopLoop()
        val now = player.time
        stepJob?.cancel()
        stepJob = scope.launch {
            val lines = stepLines(now, forward)
            val start = if (forward) LineStep.nextStart(lines, now) else LineStep.previousStart(lines, now)
            val target = start?.let { max(0L, it - SEARCH_LEAD_MS) } ?: (if (forward) now + SKIP_MS else now - SKIP_MS)
            seekTo(target)
        }
    }

    /** Plays the current line again (or the last 5 seconds without subtitles). */
    fun repeatLine() {
        stopLoop()
        val now = player.time
        scope.launch {
            val start = lineRangeAt(now)?.first ?: max(0L, now - REPEAT_FALLBACK_MS)
            player.setTime(start)
            if (!player.isPlaying) player.play()
        }
    }

    fun toggleLoop() {
        if (loopRange != null || loopJob != null) {
            stopLoop()
            return
        }
        val now = player.time
        // The button turns on at once; finding the line can take a moment on online files.
        loopJob = scope.launch {
            val range = lineRangeAt(now) ?: (max(0L, now - REPEAT_FALLBACK_MS) to now)
            loopJob = null
            loopRange = range
            message(context.getString(R.string.loop_on))
        }
    }

    /** Long press: pause and open the editor, filling it in as the line is found. */
    fun openEditor() {
        // The editor's Preview plays the range, which a running loop would fight with.
        stopLoop()
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
        finding = context.getString(R.string.looking_line)
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
                        notice = QuickNotice(context.getString(R.string.quick_no_line))
                        return@launch
                    }
                }
                // A second tap on the same line would only make a duplicate card.
                if (LineSearch.isDuplicate(savedRanges, line.startMs, line.endMs)) {
                    notice = QuickNotice(context.getString(R.string.quick_already))
                    return@launch
                }
                val card = suspendCancellableCoroutine { c ->
                    saveClip(line.startMs, line.endMs, line.text, line.meaning, image) { c.resume(it) }
                }
                notice = if (card == null) {
                    QuickNotice(context.getString(R.string.quick_failed))
                } else {
                    view.performHapticFeedback(SAVED_HAPTIC)
                    QuickNotice(context.getString(R.string.quick_saved), undo = card)
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
                MediaPlayer.Event.Vout -> {
                    voutCount = event.voutCount
                    // A new video helper (first start, or after the background) starts at best fit.
                    if (voutCount > 0 && scaleIndex != 0) player.setVideoScale(SCALES[scaleIndex].first)
                }
                MediaPlayer.Event.LengthChanged -> lengthMs = event.lengthChanged
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    resumePending?.let { position ->
                        resumePending = null
                        player.setTime(position)
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val result = snackbar.showSnackbar(
                                message = context.getString(R.string.continuing_from, formatTime(position)),
                                actionLabel = context.getString(R.string.start_over),
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) player.setTime(0)
                        }
                    }
                }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> isPlaying = false
                MediaPlayer.Event.EndReached -> {
                    isPlaying = false
                    stopLoop()
                    savePosition(player.length, player.length)
                    controlsVisible = true
                }
                MediaPlayer.Event.EncounteredError -> {
                    isPlaying = false
                    val proxy = currentMedia?.proxy
                    if (proxy != null && !triedDirect) {
                        // The cache failed: play the link directly, and say why so it can be fixed.
                        triedDirect = true
                        val details = proxy.diagnostics()
                        Log.w(TAG, "Playing through the cache failed: $details")
                        scope.launch {
                            val position = player.time
                            currentMedia?.close()
                            runCatching { openVlcMedia(context, libVlc, source.uri, useCache = false) }
                                .onSuccess { direct ->
                                    currentMedia = direct
                                    player.setMedia(direct.media)
                                    direct.media.release()
                                    player.play()
                                    if (position > 0) player.setTime(position)
                                    message(context.getString(R.string.cache_fallback, details), sticky = true)
                                }
                                .onFailure { message(context.getString(R.string.vlc_error), sticky = true) }
                        }
                    } else {
                        message(context.getString(R.string.vlc_error), sticky = true)
                    }
                }
            }
        })
        opened
            .onSuccess {
                player.setMedia(it.media)
                it.media.release()
                startGate.mediaReady()
            }
            .onFailure { message(context.getString(R.string.open_failed, context.describe(it)), sticky = true) }
        onDispose {
            session?.close()
            if (resumePending == null) savePosition(player.time, player.length)
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
            currentMedia?.close()
        }
    }

    LaunchedEffect(Unit) { hasTextTracks = builtIn.await()?.textTracks?.isNotEmpty() == true }

    // Once the film plays, read its subtitle lines in the background when that is cheap, so
    // Loop, Repeat, saving and role-play find lines without waiting for a download.
    LaunchedEffect(Unit) {
        snapshotFlow { isPlaying }.first { it }
        delay(PRELOAD_DELAY_MS) // Let playback settle before using the network for this.
        if (builtInSearchIndex == null) loadSearchIndex(background = true)
    }

    // While looping, jump back to the start at the end of the line (see LineLoop).
    LaunchedEffect(loopRange) {
        val (start, end) = loopRange ?: return@LaunchedEffect
        player.setTime(start)
        if (!player.isPlaying) player.play()
        val loop = LineLoop(start, end, jumpedAtMs = SystemClock.elapsedRealtime(), earlyMs = LOOP_EARLY_MS)
        while (true) {
            if (loop.next(player.time, SystemClock.elapsedRealtime())) player.setTime(start)
            delay(50)
        }
    }

    // Pause when the app goes to the background (also while the subtitle file picker is open).
    // libVLC turns the video off when its surface is destroyed and does not turn it back on,
    // which left a black picture with sound; so the views are detached here and attached again
    // on return, as the VLC app does.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    session?.stop()
                    player.pause()
                    if (resumePending == null) savePosition(player.time, player.length)
                    if (videoLayout?.isAttachedToWindow == true) {
                        player.detachViews()
                        viewsDetachedForStop = true
                    }
                }
                Lifecycle.Event.ON_START -> {
                    val layout = videoLayout
                    if (viewsDetachedForStop && layout != null && layout.isAttachedToWindow) {
                        viewsDetachedForStop = false
                        player.attachViews(layout, null, true, false)
                    }
                }
                else -> Unit
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

    // A dialog opened from the controls keeps them on screen too.
    val dialogOpen = styleDialog || marginsDialog || trackDialog != null || draft != null || rolePlaySetup || searchOpen
    LaunchedEffect(controlsVisible, isPlaying, interaction, fingerDown, dialogOpen) {
        if (controlsVisible && isPlaying && !fingerDown && !dialogOpen) {
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
        if (session != null) closeRolePlay() else if (orientationLock == OrientationLock.Landscape) orientationLock = OrientationLock.None else onBack()
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
                        message(context.getString(R.string.no_sub_lines))
                    } else {
                        externalLines = lines
                        externalName = SourceNames.displayName(context, uri)
                        player.setSpuTrack(-1) // Hide built-in subtitles so text is not shown twice.
                        message(context.resources.getQuantityString(R.plurals.loaded_sub_lines, lines.size, lines.size))
                    }
                }
                .onFailure { message(context.getString(R.string.read_sub_failed, context.describe(it))) }
        }
    }

    val currentText = externalLines?.let { SubtitleLookup.activeAt(it, timeMs)?.text }.orEmpty()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Sees every touch first (also scrolling the control row) without taking it.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        fingerDown = event.changes.any { it.pressed }
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    keepScreenOn = true
                    videoLayout = this
                    // Attach only once the view is in the window; attaching earlier sometimes
                    // left the video black while the audio played.
                    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            viewsDetachedForStop = false
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
                detectTapGestures(onTap = { if (session == null) controlsVisible = !controlsVisible })
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

        if (controlsVisible && session == null) {
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                }
                Text(
                    source.title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            // Back and forward, and the seek bar below, stay left to right in Persian too.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center),
            ) {
                TextButton(onClick = { repeatLine(); touch() }) {
                    Text(stringResource(R.string.repeat), color = Color.White)
                }
                val byLine = externalLines != null || hasTextTracks
                TextButton(onClick = { stepLine(forward = false); touch() }) {
                    Text(stringResource(if (byLine) R.string.line_back else R.string.skip_back), color = Color.White)
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
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                        tint = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }
                TextButton(onClick = { stepLine(forward = true); touch() }) {
                    Text(stringResource(if (byLine) R.string.line_forward else R.string.skip_forward), color = Color.White)
                }
            } }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Row(verticalAlignment = Alignment.CenterVertically) {
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
                } }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    val scale = SCALES[scaleIndex]
                    ControlButton(stringResource(R.string.aspect, scale.second?.let { stringResource(it) } ?: scale.third)) {
                        scaleIndex = (scaleIndex + 1) % SCALES.size
                        // Before the picture appears, only remember it (see voutCount).
                        if (voutCount > 0) player.setVideoScale(SCALES[scaleIndex].first)
                        touch()
                    }
                    ControlButton(stringResource(R.string.audio)) { trackDialog = TrackKind.Audio; touch() }
                    ControlButton(stringResource(R.string.subtitles)) { trackDialog = TrackKind.Subtitle; touch() }
                    ControlButton(stringResource(R.string.search_subtitles)) { openSearch(); touch() }
                    ControlButton(stringResource(R.string.load_sub_file)) { subtitlePicker.launch(arrayOf("*/*")); touch() }
                    ControlButton(stringResource(R.string.subtitle_style)) { styleDialog = true; touch() }
                    ControlButton(stringResource(R.string.speed, RATES[rateIndex].second)) {
                        rateIndex = (rateIndex + 1) % RATES.size
                        player.setRate(RATES[rateIndex].first)
                        touch()
                    }
                    ControlButton(stringResource(R.string.role_play)) { openRolePlay(); touch() }
                    ControlButton(stringResource(R.string.clip_margins)) { marginsDialog = true; touch() }
                    ControlButton(stringResource(if (landscape) R.string.portrait else R.string.full_screen)) {
                        orientationLock = if (landscape) OrientationLock.Portrait else OrientationLock.Landscape
                        touch()
                    }
                    if (orientationLock != OrientationLock.None) {
                        ControlButton(stringResource(R.string.auto_rotate)) { orientationLock = OrientationLock.None; touch() }
                    }
                }
                externalName?.let {
                    Text(
                        stringResource(R.string.subtitle_file, it),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        session?.let { RolePlayOverlay(it, onClose = ::closeRolePlay) }

        // The loop button sits just above the save button, in the same quiet style.
        if (session == null) LoopButton(
            active = loopRange != null || loopJob != null,
            busy = loopJob != null,
            onClick = ::toggleLoop,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(end = 12.dp, bottom = 112.dp),
        )

        if (session == null) QuickSaveButton(
            busy = quickBusy > 0,
            notice = notice,
            onTap = ::quickSave,
            onHold = ::openEditor,
            onUndo = { card ->
                repository.delete(card)
                savedRanges.remove(card.startMs to card.endMs)
                notice = QuickNotice(context.getString(R.string.quick_removed))
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
            trackOptions = builtInSearch?.tracks?.map { context.trackLabel(it) }.orEmpty(),
            selectedTrack = selectedTrack,
            onSelectTrack = { index ->
                val search = builtInSearch
                val line = search?.let { s -> LineSearch.fromTrack(s, index, currentMargins.value)?.let { context.subtitleResult(it, s) } }
                if (line != null) {
                    applyToDialog(line)
                } else {
                    selectedTrack = index
                    timeNote = context.getString(R.string.track_no_line)
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
                    message(context.getString(if (card != null) R.string.saved_box1 else R.string.cut_failed), sticky = card == null)
                }
            },
        )
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRolePlay() else message(context.getString(R.string.mic_needed), sticky = true)
    }

    val sceneRange = SceneRange.check(parseClock(rangeFromText), parseClock(rangeToText))
    val byTime = rolePlayPrefs.byTime
    // Reads the scene again when what defines it changes (only the keys of the chosen mode).
    LaunchedEffect(rolePlaySetup, byTime, if (byTime) sceneRange else rolePlayPrefs.lineCount, sceneFrom) {
        if (!rolePlaySetup) return@LaunchedEffect
        sceneLines = null
        delay(300) // Wait for typing to settle.
        val lines = if (byTime) {
            (sceneRange as? SceneRange.Ok)?.let { sceneLinesBetween(it) } ?: emptyList()
        } else {
            sceneLinesFrom(sceneFrom, rolePlayPrefs.lineCount)
        }
        sceneLines = lines
        sceneMine.clear()
        sceneMine.addAll(Roles.alternate(lines, iStart).map { it.mine })
    }

    if (rolePlaySetup) {
        RolePlaySetupDialog(
            lines = sceneLines,
            mine = sceneMine,
            prefs = rolePlayPrefs,
            rangeFrom = rangeFromText,
            rangeTo = rangeToText,
            range = sceneRange,
            onRangeFromChange = { rangeFromText = it },
            onRangeToChange = { rangeToText = it },
            currentTime = { player.time },
            iStart = iStart,
            onPrefsChange = onRolePlayPrefsChange,
            onIStartChange = { value ->
                iStart = value
                sceneLines?.let { lines ->
                    sceneMine.clear()
                    sceneMine.addAll(Roles.alternate(lines, value).map { it.mine })
                }
            },
            onToggle = { i -> if (i in sceneMine.indices) sceneMine[i] = !sceneMine[i] },
            onStart = {
                if (recorder.hasPermission()) startRolePlay() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = { rolePlaySetup = false },
        )
    }

    if (marginsDialog) {
        ClipMarginsDialog(
            current = clipMargins,
            onChange = onClipMarginsChange,
            onDismiss = { marginsDialog = false },
        )
    }

    if (searchOpen) {
        // A loaded subtitle file is searched instead of the film's own tracks.
        val fileIndex = remember(externalLines, externalName) {
            externalLines?.let { SearchIndex.Ready(listOf(externalName.orEmpty() to it)) }
        }
        SubtitleSearchDialog(
            index = fileIndex ?: builtInSearchIndex ?: SearchIndex.Loading(0f),
            onPick = { line ->
                searchOpen = false
                seekTo(max(0L, line.startMs - SEARCH_LEAD_MS))
                player.play()
            },
            onLoadFile = { subtitlePicker.launch(arrayOf("*/*")) },
            onRetry = ::loadSearchIndex,
            onDismiss = {
                searchOpen = false
                if (resumeAfterSearch) player.play()
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
        val vlcTracks = (if (kind == TrackKind.Audio) player.audioTracks else player.spuTracks)
            ?.map { it.id to it.name }
            .orEmpty()
        // A loaded subtitle file is shown by the app, not libVLC, so it is listed here too.
        val fileEntry = if (kind == TrackKind.Subtitle && externalLines != null) {
            FILE_TRACK_ID to stringResource(R.string.subtitle_file_entry, externalName ?: stringResource(R.string.subtitle_file_unnamed))
        } else {
            null
        }
        TrackDialog(
            title = stringResource(if (kind == TrackKind.Audio) R.string.audio_track else R.string.subtitle_track),
            tracks = listOfNotNull(fileEntry) + vlcTracks,
            selectedId = when {
                fileEntry != null -> FILE_TRACK_ID
                kind == TrackKind.Audio -> player.audioTrack
                else -> player.spuTrack
            },
            onSelect = { id ->
                if (kind == TrackKind.Audio) {
                    player.setAudioTrack(id)
                } else if (id != FILE_TRACK_ID) {
                    // Another track, or off: the file's text is no longer shown or used.
                    player.setSpuTrack(id)
                    externalLines = null
                    externalName = null
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
                Text(stringResource(R.string.no_tracks))
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
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
