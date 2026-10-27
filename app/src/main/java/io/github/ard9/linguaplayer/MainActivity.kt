package io.github.ard9.linguaplayer

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.ard9.linguaplayer.data.PlaybackSource
import io.github.ard9.linguaplayer.data.SourceNames
import io.github.ard9.linguaplayer.ui.BoxScreen
import io.github.ard9.linguaplayer.ui.CardScreen
import io.github.ard9.linguaplayer.ui.HomeScreen
import io.github.ard9.linguaplayer.ui.LinguaTheme
import io.github.ard9.linguaplayer.ui.PlayerScreen
import io.github.ard9.linguaplayer.ui.ReviewScreen

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<PlaybackSource?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) incoming.value = sourceFromIntent(intent)
        val app = application as LinguaApp
        setContent {
            val subtitleStyle by app.settings.subtitleStyle.collectAsState()
            LinguaTheme(subtitleStyle) {
                AppNavigation(app, incoming)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming.value = sourceFromIntent(intent)
    }

    /** Accepts "Open with" requests for local files and http(s) links only. */
    private fun sourceFromIntent(intent: Intent?): PlaybackSource? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (uri.scheme !in setOf("content", "http", "https")) return null
        return PlaybackSource(uri, SourceNames.displayName(this, uri))
    }
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

private sealed interface Screen {
    data object Home : Screen
    data class Player(val source: PlaybackSource) : Screen
    data object Box : Screen
    data class CardDetail(val cardId: String) : Screen
    data object Review : Screen
}

@Composable
private fun AppNavigation(app: LinguaApp, incoming: MutableState<PlaybackSource?>) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var insecureSource by remember { mutableStateOf<PlaybackSource?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedBox by rememberSaveable { mutableIntStateOf(1) }
    val cards by app.cards.cards.collectAsState()
    val recents by app.recents.items.collectAsState()
    val clipMargins by app.settings.clipMargins.collectAsState()
    val positions by app.recents.positions.collectAsState()
    val rolePlayPrefs by app.settings.rolePlay.collectAsState()

    fun play(source: PlaybackSource) {
        app.recents.add(source)
        screen = Screen.Player(source)
    }

    fun open(source: PlaybackSource) {
        if (source.uri.scheme == "http") insecureSource = source else play(source)
    }

    LaunchedEffect(incoming.value) {
        incoming.value?.let {
            open(it)
            incoming.value = null
        }
    }

    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen is Screen.CardDetail) Screen.Box else Screen.Home
    }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            recents = recents,
            positions = positions,
            cards = cards,
            onOpen = ::open,
            onRemoveRecent = { app.recents.remove(it) },
            onOpenBox = { screen = Screen.Box },
            onOpenReview = { screen = Screen.Review },
            backupBusy = backupBusy,
            onBackup = { uri ->
                backupBusy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val out = context.contentResolver.openOutputStream(uri, "wt")
                                ?: throw IOException("Cannot write to this place")
                            out.use { app.cards.exportTo(it) }
                        }
                    }
                    backupBusy = false
                    result.onFailure { Log.w("Backup", "Export failed", it) }
                    toast(context, if (result.isSuccess) "Backup saved (${cards.size} cards)" else "Could not save the backup: ${result.exceptionOrNull()?.message}")
                }
            },
            onRestore = { uri ->
                backupBusy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val input = context.contentResolver.openInputStream(uri)
                                ?: throw IOException("Cannot open this file")
                            input.use { app.cards.importFrom(it) }
                        }
                    }
                    backupBusy = false
                    result.onFailure { Log.w("Backup", "Import failed", it) }
                    toast(
                        context,
                        result.fold(
                            onSuccess = { r ->
                                when {
                                    r.added.isEmpty() && r.skipped > 0 -> "All cards in this backup are already here"
                                    r.skipped > 0 -> "Restored ${r.added.size} cards (${r.skipped} already here or incomplete)"
                                    else -> "Restored ${r.added.size} cards"
                                }
                            },
                            onFailure = { "Could not restore: ${it.message}" },
                        ),
                    )
                }
            },
        )
        is Screen.Player -> key(s.source) {
            // One libVLC instance per playback, created with the current subtitle style.
            val libVlc = remember { app.acquireLibVlc() }
            DisposableEffect(libVlc) { onDispose { app.releaseLibVlc(libVlc) } }
            PlayerScreen(
                source = s.source,
                libVlc = libVlc,
                repository = app.cards,
                clips = app.clips,
                speechFinder = app.speechFinder,
                onSubtitleStyleChange = app.settings::setSubtitleStyle,
                clipMargins = clipMargins,
                onClipMarginsChange = app.settings::setClipMargins,
                rolePlayPrefs = rolePlayPrefs,
                onRolePlayPrefsChange = app.settings::setRolePlay,
                resumeAtMs = remember { app.recents.positions.value[s.source.uri.toString()] },
                onSavePosition = { position, length -> app.recents.savePosition(s.source.uri, position, length) },
                onBack = { screen = Screen.Home },
            )
        }
        Screen.Box -> BoxScreen(
            cards = cards,
            repository = app.cards,
            selectedBox = selectedBox,
            onSelectBox = { selectedBox = it },
            onOpenCard = { screen = Screen.CardDetail(it.id) },
            onBack = { screen = Screen.Home },
        )
        is Screen.CardDetail -> {
            val card = cards.firstOrNull { it.id == s.cardId }
            if (card == null) {
                LaunchedEffect(Unit) { screen = Screen.Box }
            } else {
                CardScreen(card, app.cards, onBack = { screen = Screen.Box })
            }
        }
        Screen.Review -> ReviewScreen(cards, app.cards, onBack = { screen = Screen.Home })
    }

    insecureSource?.let { source ->
        AlertDialog(
            onDismissRequest = { insecureSource = null },
            title = { Text("Unencrypted link") },
            text = {
                Text(
                    "This link uses http://, so anyone on your network can see or change " +
                        "what is streamed. Play it anyway?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    insecureSource = null
                    play(source)
                }) { Text("Play anyway") }
            },
            dismissButton = {
                TextButton(onClick = { insecureSource = null }) { Text("Cancel") }
            },
        )
    }
}
