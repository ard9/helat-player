package io.github.ard9.linguaplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ard9.linguaplayer.data.Card
import io.github.ard9.linguaplayer.data.CardRepository
import io.github.ard9.linguaplayer.data.formatTime

/** One card, full size: frame with its line, meaning, and the audio playing on open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(card: Card, repository: CardRepository, onBack: () -> Unit) {
    val audio = rememberClipAudioPlayer()
    val audioFile = repository.audioFile(card)
    val hasAudio = audioFile.exists()
    val picture = rememberCardImage(repository.imageFile(card), maxWidthPx = 1080)
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(card.id) { if (hasAudio) audio.play(audioFile) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Box ${card.box}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            FilmFrame(image = picture, text = card.text, hint = "No text")
            if (card.meaning.isNotBlank()) {
                Text(card.meaning, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "From ${card.sourceTitle} at ${formatTime(card.startMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = Cinema.Dim,
            )
            if (!hasAudio) {
                Text("The audio file for this card is missing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { audio.play(audioFile) }, enabled = hasAudio) { Text("Play") }
                OutlinedButton(onClick = { editing = true }) { Text("Edit") }
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (editing) {
        CardTextDialog(
            initialText = card.text,
            initialMeaning = card.meaning,
            onDismiss = { editing = false },
            onSave = { text, meaning ->
                repository.update(card.copy(text = text, meaning = meaning))
                editing = false
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this card?") },
            text = { Text("Its audio and picture are deleted too.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    repository.delete(card)
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
