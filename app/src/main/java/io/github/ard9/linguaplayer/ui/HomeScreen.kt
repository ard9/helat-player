package io.github.ard9.linguaplayer.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.ard9.linguaplayer.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ard9.linguaplayer.data.Card
import io.github.ard9.linguaplayer.data.Leitner
import io.github.ard9.linguaplayer.data.PlaybackSource
import io.github.ard9.linguaplayer.data.SourceNames
import io.github.ard9.linguaplayer.data.formatTime
import java.time.LocalDate

@Composable
fun HomeScreen(
    recents: List<PlaybackSource>,
    /** Saved playback positions in ms, by URI string. */
    positions: Map<String, Long>,
    cards: List<Card>,
    onOpen: (PlaybackSource) -> Unit,
    onRemoveRecent: (PlaybackSource) -> Unit,
    onClearRecents: () -> Unit,
    onOpenBox: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    /** Shows the first-launch card that suggests the guide. */
    showWelcome: Boolean,
    onDismissWelcome: () -> Unit,
    /** Days since the last backup when the home screen should suggest one, else null. */
    daysWithoutBackup: Int?,
    onSnoozeBackup: () -> Unit,
    /** True while a backup is written or restored. */
    backupBusy: Boolean,
    onBackup: (Uri) -> Unit,
    onRestore: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var confirmBackup by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val backupTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) onBackup(uri)
    }
    val restoreSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onRestore(uri)
    }
    var link by rememberSaveable { mutableStateOf("") }
    var linkOpen by rememberSaveable { mutableStateOf(false) }
    var linkError by remember { mutableStateOf(false) }
    val dueCount = remember(cards) {
        val now = System.currentTimeMillis()
        cards.count { Leitner.isDue(it, now) }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                // Keep access so the file can be reopened from the recent list.
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Some providers do not offer persistent access; playback still works now.
            }
            onOpen(PlaybackSource(uri, SourceNames.displayName(context, uri)))
        }
    }

    fun openLink() {
        val uri = Uri.parse(link.trim())
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            linkError = true
            return
        }
        linkError = false
        onOpen(PlaybackSource(uri, uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: uri.host!!))
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
        ) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onOpenGuide) {
                            Icon(PlayerIcons.Help, contentDescription = stringResource(R.string.guide), tint = Cinema.Dim)
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = Cinema.Dim)
                        }
                    }
                    Text(
                        stringResource(R.string.home_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cinema.Dim,
                    )
                    Text(
                        stringResource(R.string.home_version, appVersion(context)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Cinema.Dim.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (showWelcome) {
                item { WelcomeCard(onOpenGuide = onOpenGuide, onDismiss = onDismissWelcome) }
            }

            if (daysWithoutBackup != null && !backupBusy) {
                item {
                    BackupNudge(
                        days = daysWithoutBackup,
                        onBackup = { confirmBackup = true },
                        onDismiss = onSnoozeBackup,
                    )
                }
            }

            recents.firstOrNull()?.let { last ->
                item {
                    ContinueCard(last, positions[last.uri.toString()], onRemove = { onRemoveRecent(last) }) { onOpen(last) }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { filePicker.launch(arrayOf("video/*", "audio/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.open_file)) }
                    OutlinedButton(
                        onClick = { linkOpen = !linkOpen },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(if (linkOpen) R.string.hide_link else R.string.paste_link)) }
                }
            }

            if (linkOpen) {
                item {
                    val error = linkError
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = link,
                            onValueChange = { link = it; linkError = false },
                            label = { Text(stringResource(R.string.link_label)) },
                            singleLine = true,
                            isError = error,
                            supportingText = if (error) {
                                { Text(stringResource(R.string.link_error)) }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { openLink() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { openLink() },
                            enabled = link.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.play_link)) }
                    }
                }
            }

            item {
                Surface(
                    color = Cinema.Seat,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.your_cards), style = MaterialTheme.typography.titleLarge)
                        if (cards.isEmpty()) {
                            Text(
                                stringResource(R.string.cards_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Cinema.Dim,
                            )
                        } else {
                            Text(
                                if (dueCount == 0) {
                                    stringResource(R.string.due_none)
                                } else {
                                    pluralStringResource(R.plurals.due_count, dueCount, dueCount)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            BoxBars(counts = boxCounts(cards))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = onOpenReview, enabled = dueCount > 0) { Text(stringResource(R.string.start_review)) }
                                TextButton(onClick = onOpenBox) { Text(stringResource(R.string.browse_cards)) }
                            }
                        }
                        HorizontalDivider(color = Cinema.Aisle)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(if (backupBusy) R.string.backup_working else R.string.cards_local_only),
                                style = MaterialTheme.typography.bodySmall,
                                color = Cinema.Dim,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { confirmBackup = true },
                                enabled = cards.isNotEmpty() && !backupBusy,
                            ) { Text(stringResource(R.string.back_up)) }
                            TextButton(
                                onClick = { restoreSource.launch(arrayOf("application/zip", "application/octet-stream")) },
                                enabled = !backupBusy,
                            ) { Text(stringResource(R.string.restore)) }
                        }
                    }
                }
            }

            val older = recents.drop(1)
            if (recents.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            stringResource(if (older.isNotEmpty()) R.string.recently_played else R.string.history),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.clear_history)) }
                    }
                }
            }
            if (older.isNotEmpty()) {
                items(older, key = { it.uri.toString() }) { source ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(source) },
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text(
                                source.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(sourceKind(source, context), style = MaterialTheme.typography.bodySmall, color = Cinema.Dim)
                        }
                        IconButton(onClick = { onRemoveRecent(source) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_recent), tint = Cinema.Dim)
                        }
                    }
                    HorizontalDivider(color = Cinema.Aisle)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearRecents()
                }) { Text(stringResource(R.string.clear_history)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmBackup) {
        AlertDialog(
            onDismissRequest = { confirmBackup = false },
            title = { Text(stringResource(R.string.backup_title)) },
            text = { Text(stringResource(R.string.backup_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBackup = false
                    backupTarget.launch("lingua-cards-${LocalDate.now()}.zip")
                }) { Text(stringResource(R.string.backup_choose)) }
            },
            dismissButton = { TextButton(onClick = { confirmBackup = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Suggests a backup when the cards have not been backed up for a while. */
@Composable
private fun BackupNudge(days: Int, onBackup: () -> Unit, onDismiss: () -> Unit) {
    Surface(color = Cinema.Seat, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 20.dp, top = 8.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pluralStringResource(R.plurals.backup_nudge, days, days), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.backup_nudge_text), style = MaterialTheme.typography.bodySmall, color = Cinema.Dim)
                TextButton(onClick = onBackup) { Text(stringResource(R.string.back_up)) }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Cinema.Dim)
            }
        }
    }
}

/** Shown until closed: points new users to the guide. */
@Composable
private fun WelcomeCard(onOpenGuide: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = Cinema.Seat,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 20.dp, top = 8.dp, end = 4.dp, bottom = 12.dp)) {
            Column(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.welcome_text), style = MaterialTheme.typography.bodyMedium, color = Cinema.Dim)
                Button(onClick = onOpenGuide) { Text(stringResource(R.string.welcome_open)) }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Cinema.Dim)
            }
        }
    }
}

@Composable
private fun ContinueCard(source: PlaybackSource, resumeAtMs: Long?, onRemove: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(
        color = Cinema.Aisle,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.continue_watching), onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Cinema.Night, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.continue_watching), style = MaterialTheme.typography.labelMedium, color = Cinema.Dim)
                Spacer(Modifier.height(2.dp))
                Text(
                    source.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (resumeAtMs != null) {
                        stringResource(R.string.source_from, sourceKind(source, context), formatTime(resumeAtMs))
                    } else {
                        sourceKind(source, context)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Cinema.Dim,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_recent), tint = Cinema.Dim)
            }
        }
    }
}

/** The version shown on the home screen; its last number is the GitHub build number. */
private fun appVersion(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
} catch (e: Exception) {
    "?"
}

private fun sourceKind(source: PlaybackSource, context: android.content.Context): String =
    if (source.uri.scheme == "content") {
        context.getString(R.string.source_file)
    } else {
        source.uri.host ?: context.getString(R.string.source_link)
    }
