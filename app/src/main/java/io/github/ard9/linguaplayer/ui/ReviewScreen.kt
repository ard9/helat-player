package io.github.ard9.linguaplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ard9.linguaplayer.data.Card
import io.github.ard9.linguaplayer.data.CardRepository
import io.github.ard9.linguaplayer.data.Leitner

/**
 * Flashcard review. Front: the video frame and the audio. Back: the text and its meaning.
 */
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
    val picture = rememberCardImage(card?.let { repository.imageFile(it) }, maxWidthPx = 1080)

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
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                Text("Box ${card.box} · ${card.sourceTitle}", style = MaterialTheme.typography.labelMedium)
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        if (picture != null) {
                            Image(
                                bitmap = picture,
                                contentDescription = "Video frame",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                        if (!hasAudio) {
                            Text("Audio file is missing for this card.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (revealed || !hasAudio) {
                            HorizontalDivider()
                            Text(
                                card.text.ifBlank { "No text" },
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                            )
                            if (card.meaning.isNotBlank()) {
                                Text(
                                    card.meaning,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            Text("Listen, then show the answer", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasAudio && audioFile != null) {
                        OutlinedButton(onClick = { audio.play(audioFile) }) { Text("Play again") }
                    }
                    if (!revealed && hasAudio) {
                        FilledTonalButton(onClick = { revealed = true }) { Text("Show answer") }
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
