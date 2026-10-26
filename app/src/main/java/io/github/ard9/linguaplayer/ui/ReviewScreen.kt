package io.github.ard9.linguaplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

/** Flashcard review. Front: the frame and the audio. Back: the line on the frame and its meaning. */
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
    val showAnswer = revealed || !hasAudio

    LaunchedEffect(card?.id) {
        if (audioFile != null && hasAudio) audio.play(audioFile)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (due.size) {
                            0 -> "Review"
                            1 -> "Last card"
                            else -> "${due.size} cards left"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { padding ->
        if (card == null) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "All done for today. New reviews appear as cards come due.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Cinema.Dim,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                FilmFrame(
                    image = picture,
                    text = if (showAnswer) card.text.ifBlank { null } else null,
                    hint = if (showAnswer) "No text" else "Listen",
                )
                Text(
                    "Box ${card.box} · ${card.sourceTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cinema.Dim,
                )
                if (showAnswer && card.meaning.isNotBlank()) {
                    Text(card.meaning, style = MaterialTheme.typography.bodyLarge)
                }
                if (!hasAudio) {
                    Text(
                        "The audio file for this card is missing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    if (hasAudio && audioFile != null) {
                        OutlinedButton(onClick = { audio.play(audioFile) }) { Text("Play again") }
                    }
                    if (!showAnswer) {
                        Button(onClick = { revealed = true }) { Text("Show answer") }
                    }
                }
                if (showAnswer) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { repository.update(Leitner.forgot(card, System.currentTimeMillis())) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Didn't know") }
                        Button(
                            onClick = { repository.update(Leitner.knew(card, System.currentTimeMillis())) },
                            modifier = Modifier.weight(1f),
                        ) { Text("I knew it") }
                    }
                }
            }
        }
    }
}
