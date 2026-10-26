package io.github.ard9.linguaplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
            LinguaTheme {
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

private sealed interface Screen {
    data object Home : Screen
    data class Player(val source: PlaybackSource) : Screen
    data object Box : Screen
    data object Review : Screen
}

@Composable
private fun AppNavigation(app: LinguaApp, incoming: MutableState<PlaybackSource?>) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var insecureSource by remember { mutableStateOf<PlaybackSource?>(null) }
    val cards by app.cards.cards.collectAsState()
    val recents by app.recents.items.collectAsState()

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

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            recents = recents,
            cards = cards,
            onOpen = ::open,
            onRemoveRecent = { app.recents.remove(it) },
            onOpenBox = { screen = Screen.Box },
            onOpenReview = { screen = Screen.Review },
        )
        is Screen.Player -> key(s.source) {
            PlayerScreen(
                source = s.source,
                repository = app.cards,
                clipper = app.clipper,
                onBack = { screen = Screen.Home },
            )
        }
        Screen.Box -> BoxScreen(cards, app.cards, onBack = { screen = Screen.Home })
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
