package io.github.ard9.linguaplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ard9.linguaplayer.data.Card
import io.github.ard9.linguaplayer.data.CardRepository
import io.github.ard9.linguaplayer.data.Leitner
import io.github.ard9.linguaplayer.data.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScreen(cards: List<Card>, repository: CardRepository, onBack: () -> Unit) {
    var selectedBox by rememberSaveable { mutableIntStateOf(1) }
    var toDelete by remember { mutableStateOf<Card?>(null) }
    val audio = rememberClipAudioPlayer()
    val inBox = cards.filter { it.box == selectedBox }.sortedByDescending { it.createdAt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learning box") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = selectedBox - 1, edgePadding = 8.dp) {
                for (box in 1..Leitner.BOX_COUNT) {
                    Tab(
                        selected = box == selectedBox,
                        onClick = { selectedBox = box },
                        text = { Text("Box $box (${cards.count { it.box == box }})") },
                    )
                }
            }
            if (inBox.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No cards in this box yet.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(inBox, key = { it.id }) { card ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(card.text.ifBlank { "(audio only)" }, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${card.sourceTitle} · ${formatTime(card.startMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { audio.play(repository.audioFile(card)) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                }
                                IconButton(onClick = { toDelete = card }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete card?") },
            text = { Text(card.text.ifBlank { "(audio only)" }) },
            confirmButton = {
                TextButton(onClick = {
                    repository.delete(card)
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}
