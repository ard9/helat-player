package io.github.ard9.linguaplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ard9.linguaplayer.data.Card
import io.github.ard9.linguaplayer.data.CardRepository
import io.github.ard9.linguaplayer.data.Leitner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(cards: List<Card>, repository: CardRepository, onBack: () -> Unit) {
    // Cards answered in this session move to a future date and drop out of the queue.
    val sessionStart = remember { System.currentTimeMillis() }
    val due = cards.filter { Leitner.isDue(it, sessionStart) }.sortedBy { it.nextReviewAt }
    val card = due.firstOrNull()
    var revealed by remember(card?.id) { mutableStateOf(false) }
    val audio = rememberClipAudioPlayer()
    val audioFile = card?.let { repository.audioFile(it) }
    val hasAudio = audioFile?.exists() == true

    LaunchedEffect(card?.id) {
        if (audioFile != null && hasAudio) audio.play(audioFile)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (card == null) "Review" else "Review · ${due.size} left") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            if (card == null) {
                Text("Nothing to review right now. Come back later!", textAlign = TextAlign.Center)
            } else Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Box ${card.box} · ${card.sourceTitle}", style = MaterialTheme.typography.labelMedium)
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (revealed || !hasAudio) {
                                card.text.ifBlank { "No text" }
                            } else {
                                "Listen, then reveal the text"
                            },
                            style = if (revealed || !hasAudio) {
                                MaterialTheme.typography.headlineSmall
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (!hasAudio) {
                    Text("Audio file is missing for this card.", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasAudio && audioFile != null) {
                        OutlinedButton(onClick = { audio.play(audioFile) }) { Text("Play again") }
                    }
                    if (!revealed && hasAudio) {
                        FilledTonalButton(onClick = { revealed = true }) { Text("Show text") }
                    }
                }
                if (revealed || !hasAudio) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            repository.update(Leitner.forgot(card, System.currentTimeMillis()))
                        }) { Text("Didn't know") }
                        Button(onClick = {
                            repository.update(Leitner.knew(card, System.currentTimeMillis()))
                        }) { Text("I knew it") }
                    }
                }
            }
        }
    }
}
