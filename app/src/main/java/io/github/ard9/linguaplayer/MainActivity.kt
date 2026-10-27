package io.github.ard9.linguaplayer

import io.github.ard9.linguaplayer.data.BackupReminder
import io.github.ard9.linguaplayer.reminder.ReviewReminder
import android.content.Context
import android.content.Intent
import io.github.ard9.linguaplayer.data.AppLanguage
import io.github.ard9.linguaplayer.data.SettingsRepository
import io.github.ard9.linguaplayer.ui.GuideScreen
import io.github.ard9.linguaplayer.ui.SettingsScreen
import androidx.compose.ui.res.stringResource
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

    /** Applies the language chosen in Settings; "System" leaves the phone's language. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(withLanguage(newBase, SettingsRepository.language(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) incoming.value = sourceFromIntent(intent)
        val app = application as LinguaApp
        setContent {
            val subtitleStyle by app.settings.subtitleStyle.collectAsState()
            val persian = resources.configuration.locales[0].language == "fa"
            LinguaTheme(subtitleStyle, persian) {
                AppNavigation(app, incoming, onLanguageChange = { language ->
                    if (language != app.settings.language.value) {
                        app.settings.setLanguage(language)
                        // Recreating applies the new language and layout direction everywhere.
                        recreate()
                    }
                })
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
    data object Settings : Screen
    /** [fromSettings]: Back returns to Settings instead of Home. */
    data class Guide(val fromSettings: Boolean) : Screen
}

/** Settings and Guide survive the activity being recreated for a language change. */
private fun Screen.savedTag(): String? = when (this) {
    Screen.Settings -> "settings"
    is Screen.Guide -> if (fromSettings) "guide-settings" else "guide"
    else -> null
}

private fun screenFromTag(tag: String?): Screen = when (tag) {
    "settings" -> Screen.Settings
    "guide-settings" -> Screen.Guide(fromSettings = true)
    "guide" -> Screen.Guide(fromSettings = false)
    else -> Screen.Home
}

@Composable
private fun AppNavigation(
    app: LinguaApp,
    incoming: MutableState<PlaybackSource?>,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    var savedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf(screenFromTag(savedTag)) }
    LaunchedEffect(screen) { savedTag = screen.savedTag() }
    val welcomeDismissed by app.settings.welcomeDismissed.collectAsState()
    val language by app.settings.language.collectAsState()
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
    val clipSpeed by app.settings.clipSpeed.collectAsState()
    val reminder by app.settings.reminder.collectAsState()
    val lastBackupAt by app.settings.lastBackupAt.collectAsState()
    val backupSnoozedUntil by app.settings.backupSnoozedUntil.collectAsState()

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
        screen = when (val s = screen) {
            is Screen.CardDetail -> Screen.Box
            is Screen.Guide -> if (s.fromSettings) Screen.Settings else Screen.Home
            else -> Screen.Home
        }
    }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            recents = recents,
            positions = positions,
            cards = cards,
            onOpen = ::open,
            onRemoveRecent = { app.recents.remove(it) },
            onClearRecents = { app.recents.clear() },
            onOpenBox = { screen = Screen.Box },
            onOpenReview = { screen = Screen.Review },
            onOpenSettings = { screen = Screen.Settings },
            onOpenGuide = { screen = Screen.Guide(fromSettings = false) },
            showWelcome = !welcomeDismissed,
            daysWithoutBackup = remember(cards, lastBackupAt, backupSnoozedUntil) {
                BackupReminder.daysWithoutBackup(System.currentTimeMillis(), cards, lastBackupAt, backupSnoozedUntil)
            },
            onSnoozeBackup = { app.settings.snoozeBackupReminder(System.currentTimeMillis() + BackupReminder.SNOOZE_MS) },
            onDismissWelcome = app.settings::dismissWelcome,
            backupBusy = backupBusy,
            onBackup = { uri ->
                backupBusy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val out = context.contentResolver.openOutputStream(uri, "wt")
                                ?: throw IOException(context.getString(R.string.backup_cannot_write))
                            out.use { app.cards.exportTo(it) }
                        }
                    }
                    backupBusy = false
                    result.onFailure { Log.w("Backup", "Export failed", it) }
                    if (result.isSuccess) app.settings.backupDone(System.currentTimeMillis())
                    toast(
                        context,
                        if (result.isSuccess) {
                            context.resources.getQuantityString(R.plurals.backup_saved, cards.size, cards.size)
                        } else {
                            context.getString(R.string.backup_failed, result.exceptionOrNull()?.message.orEmpty())
                        },
                    )
                }
            },
            onRestore = { uri ->
                backupBusy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val input = context.contentResolver.openInputStream(uri)
                                ?: throw IOException(context.getString(R.string.backup_cannot_open))
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
                                    r.added.isEmpty() && r.skipped > 0 -> context.getString(R.string.restore_all_present)
                                    r.skipped > 0 -> context.resources.getQuantityString(
                                        R.plurals.restored_with_skipped, r.added.size, r.added.size, r.skipped,
                                    )
                                    else -> context.resources.getQuantityString(R.plurals.restored, r.added.size, r.added.size)
                                }
                            },
                            onFailure = { context.getString(R.string.restore_failed, it.message.orEmpty()) },
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
                CardScreen(card, app.cards, clipSpeed, app.settings::setClipSpeed, onBack = { screen = Screen.Box })
            }
        }
        Screen.Review -> ReviewScreen(cards, app.cards, clipSpeed, app.settings::setClipSpeed, onBack = { screen = Screen.Home })
        Screen.Settings -> SettingsScreen(
            language = language,
            onLanguageChange = onLanguageChange,
            reminder = reminder,
            onReminderChange = { value ->
                app.settings.setReminder(value)
                if (value.enabled) ReviewReminder.schedule(context, value.minutesOfDay) else ReviewReminder.cancel(context)
            },
            onOpenGuide = { screen = Screen.Guide(fromSettings = true) },
            onBack = { screen = Screen.Home },
        )
        is Screen.Guide -> GuideScreen(onBack = { screen = if (s.fromSettings) Screen.Settings else Screen.Home })
    }

    insecureSource?.let { source ->
        AlertDialog(
            onDismissRequest = { insecureSource = null },
            title = { Text(stringResource(R.string.http_title)) },
            text = { Text(stringResource(R.string.http_text)) },
            confirmButton = {
                TextButton(onClick = {
                    insecureSource = null
                    play(source)
                }) { Text(stringResource(R.string.http_play)) }
            },
            dismissButton = {
                TextButton(onClick = { insecureSource = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
