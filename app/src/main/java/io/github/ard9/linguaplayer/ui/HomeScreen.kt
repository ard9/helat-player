package io.github.ard9.linguaplayer.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recents: List<PlaybackSource>,
    cards: List<Card>,
    onOpen: (PlaybackSource) -> Unit,
    onRemoveRecent: (PlaybackSource) -> Unit,
    onOpenBox: () -> Unit,
    onOpenReview: () -> Unit,
) {
    val context = LocalContext.current
    var link by rememberSaveable { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }
    val dueCount = remember(cards) {
        val now = System.currentTimeMillis()
        cards.count { Leitner.isDue(it, now) }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                // Keep access so the file can be reopened from "Recent".
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
            linkError = "Enter a valid http:// or https:// link"
            return
        }
        linkError = null
        onOpen(PlaybackSource(uri, uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: uri.host!!))
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Lingua Player") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(
                    onClick = { filePicker.launch(arrayOf("video/*", "audio/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open video file") }
            }
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
                    FilledTonalButton(
                        onClick = { openLink() },
                        enabled = link.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Play link") }
                }
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Learning box", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${cards.size} cards · $dueCount due for review",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onOpenReview, enabled = dueCount > 0) { Text("Review") }
                            FilledTonalButton(onClick = onOpenBox) { Text("Browse boxes") }
                        }
                    }
                }
            }
            if (recents.isNotEmpty()) {
                item { Text("Recent", style = MaterialTheme.typography.titleMedium) }
                items(recents, key = { it.uri.toString() }) { source ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(source) },
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(source.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (source.uri.scheme == "content") "Local file" else source.uri.host ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                        IconButton(onClick = { onRemoveRecent(source) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove from recent")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
