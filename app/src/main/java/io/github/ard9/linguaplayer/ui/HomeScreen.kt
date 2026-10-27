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
    onOpenBox: () -> Unit,
    onOpenReview: () -> Unit,
    /** True while a backup is written or restored. */
    backupBusy: Boolean,
    onBackup: (Uri) -> Unit,
    onRestore: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var confirmBackup by remember { mutableStateOf(false) }
    val backupTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) onBackup(uri)
    }
    val restoreSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onRestore(uri)
    }
    var link by rememberSaveable { mutableStateOf("") }
    var linkOpen by rememberSaveable { mutableStateOf(false) }
    var linkError by remember { mutableStateOf<String?>(null) }
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
            linkError = "Enter a link that starts with http:// or https://"
            return
        }
        linkError = null
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
                    Text("Lingua Player", style = MaterialTheme.typography.displaySmall)
                    Text(
                        "Watch, keep the lines you want to learn, review them later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cinema.Dim,
                    )
                }
            }

            recents.firstOrNull()?.let { last ->
                item { ContinueCard(last, positions[last.uri.toString()]) { onOpen(last) } }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { filePicker.launch(arrayOf("video/*", "audio/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Open file") }
                    OutlinedButton(
                        onClick = { linkOpen = !linkOpen },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (linkOpen) "Hide link" else "Paste link") }
                }
            }

            if (linkOpen) {
                item {
                    val error = linkError
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = link,
                            onValueChange = { link = it; linkError = null },
                            label = { Text("Direct link or stream URL") },
                            singleLine = true,
                            isError = error != null,
                            supportingText = if (error != null) {
                                { Text(error) }
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
                        ) { Text("Play link") }
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
                        Text("Your cards", style = MaterialTheme.typography.titleLarge)
                        if (cards.isEmpty()) {
                            Text(
                                "While watching, tap the bookmark button to keep a line here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Cinema.Dim,
                            )
                        } else {
                            Text(
                                when (dueCount) {
                                    0 -> "Nothing to review today."
                                    1 -> "1 card to review today."
                                    else -> "$dueCount cards to review today."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            BoxBars(counts = boxCounts(cards))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = onOpenReview, enabled = dueCount > 0) { Text("Start review") }
                                TextButton(onClick = onOpenBox) { Text("Browse cards") }
                            }
                        }
                        HorizontalDivider(color = Cinema.Aisle)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (backupBusy) "Working on the backup…" else "Cards are kept only on this phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Cinema.Dim,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { confirmBackup = true },
                                enabled = cards.isNotEmpty() && !backupBusy,
                            ) { Text("Back up") }
                            TextButton(
                                onClick = { restoreSource.launch(arrayOf("application/zip", "application/octet-stream")) },
                                enabled = !backupBusy,
                            ) { Text("Restore") }
                        }
                    }
                }
            }

            val older = recents.drop(1)
            if (older.isNotEmpty()) {
                item {
                    Text(
                        "Recently played",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
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
                            Text(sourceKind(source), style = MaterialTheme.typography.bodySmall, color = Cinema.Dim)
                        }
                        IconButton(onClick = { onRemoveRecent(source) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove from recent", tint = Cinema.Dim)
                        }
                    }
                    HorizontalDivider(color = Cinema.Aisle)
                }
            }
        }
    }

    if (confirmBackup) {
        AlertDialog(
            onDismissRequest = { confirmBackup = false },
            title = { Text("Back up cards") },
            text = {
                Text(
                    "Saves all cards, with their audio and pictures, in one file you can keep elsewhere " +
                        "and restore later. The file also lists the links of the videos the cards came from, " +
                        "so keep it private."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBackup = false
                    backupTarget.launch("lingua-cards-${LocalDate.now()}.zip")
                }) { Text("Choose where to save") }
            },
            dismissButton = { TextButton(onClick = { confirmBackup = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ContinueCard(source: PlaybackSource, resumeAtMs: Long?, onClick: () -> Unit) {
    Surface(
        color = Cinema.Aisle,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Continue watching", onClick = onClick),
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
                Text("Continue watching", style = MaterialTheme.typography.labelMedium, color = Cinema.Dim)
                Spacer(Modifier.height(2.dp))
                Text(
                    source.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (resumeAtMs != null) "${sourceKind(source)} · from ${formatTime(resumeAtMs)}" else sourceKind(source),
                    style = MaterialTheme.typography.bodySmall,
                    color = Cinema.Dim,
                )
            }
        }
    }
}

private fun sourceKind(source: PlaybackSource): String =
    if (source.uri.scheme == "content") "File on this phone" else source.uri.host ?: "Link"
