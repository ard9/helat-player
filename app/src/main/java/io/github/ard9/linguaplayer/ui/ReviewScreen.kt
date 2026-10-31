package io.github.ard9.linguaplayer.ui

import androidx.compose.runtime.key
import androidx.compose.ui.res.pluralStringResource
import io.github.ard9.linguaplayer.R
import androidx.compose.ui.res.stringResource
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import io.github.ard9.linguaplayer.data.BoxIntervals
import io.github.ard9.linguaplayer.data.Leitner

/** Flashcard review. Front: the frame and the audio. Back: the line on the frame and its meaning. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    cards: List<Card>,
    repository: CardRepository,
    intervals: BoxIntervals,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onBack: () -> Unit,
) {
    // Cards answered in this session move to a future date and drop out of the queue.
    val sessionStart = remember { System.currentTimeMillis() }
    val due = cards.filter { Leitner.isDue(it, sessionStart) }.sortedBy { it.nextReviewAt }
    val card = due.firstOrNull()
    // Everything starts hidden: listen first, then reveal each part.
    var showPicture by remember(card?.id) { mutableStateOf(false) }
    var showText by remember(card?.id) { mutableStateOf(false) }
    var showMeaning by remember(card?.id) { mutableStateOf(false) }
    val audio = rememberClipAudioPlayer()
    audio.speed = speed
    val audioFile = card?.let { repository.audioFile(it) }
    val hasAudio = audioFile?.exists() == true
    val picture = rememberCardImage(card?.let { repository.imageFile(it) }, maxWidthPx = 1080)

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
                            0 -> stringResource(R.string.review_title)
                            1 -> stringResource(R.string.review_last)
                            else -> pluralStringResource(R.plurals.review_left, due.size, due.size)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { padding ->
        if (card == null) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.review_done),
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
                val hasText = card.text.isNotBlank()
                val hasMeaning = card.meaning.isNotBlank()
                // Decided from the file, not the loaded image, so nothing flashes while it loads.
                val hasPicture = repository.imageFile(card)?.exists() == true
                // Without audio there is nothing to listen to, so the text is shown.
                val textShown = showText || !hasAudio || !hasText
                val allShown = (showPicture || !hasPicture) && textShown && (showMeaning || !hasMeaning)

                if (hasPicture) {
                    Hidden(revealed = showPicture, what = stringResource(R.string.what_picture), onReveal = { showPicture = true }) {
                        FilmFrame(image = picture, text = null)
                    }
                }
                Hidden(revealed = textShown, what = stringResource(R.string.what_text), onReveal = { showText = true }) {
                    Surface(color = Cinema.Seat, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 20.dp), contentAlignment = Alignment.Center) {
                            if (hasText) {
                                SubtitleText(card.text, modifier = Modifier.fillMaxWidth())
                            } else {
                                Text(stringResource(R.string.no_text), style = MaterialTheme.typography.bodyLarge, color = Cinema.Dim)
                            }
                        }
                    }
                }
                if (hasMeaning) {
                    Hidden(revealed = showMeaning, what = stringResource(R.string.what_meaning), onReveal = { showMeaning = true }) {
                        Surface(color = Cinema.Seat, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                card.meaning,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.review_card_info, card.box, card.sourceTitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Cinema.Dim,
                )
                if (!hasAudio) {
                    Text(
                        stringResource(R.string.audio_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    if (hasAudio && audioFile != null) {
                        OutlinedButton(onClick = { audio.play(audioFile) }) { Text(stringResource(R.string.play_again)) }
                        SpeedButton(speed) {
                            onSpeedChange(it)
                            audio.play(audioFile)
                        }
                    }
                    if (!allShown) {
                        TextButton(onClick = {
                            showPicture = true
                            showText = true
                            showMeaning = true
                        }) { Text(stringResource(R.string.show_all)) }
                    }
                }
                if (hasAudio && audioFile != null) {
                    // A new card starts without a recording.
                    key(card.id) { PracticeRow(audioFile, card.endMs - card.startMs, audio) }
                }
                // Answer once the text has been checked.
                if (textShown) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { repository.update(Leitner.forgot(card, System.currentTimeMillis(), intervals)) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.didnt_know)) }
                        Button(
                            onClick = { repository.update(Leitner.knew(card, System.currentTimeMillis(), intervals)) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.knew_it)) }
                    }
                }
            }
        }
    }
}

/**
 * Blurs [content] until tapped, so the card can be listened to first. On Android 11 and older,
 * where blur is not available, the content is covered instead.
 */
@Composable
private fun Hidden(revealed: Boolean, what: String, onReveal: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !revealed, onClickLabel = stringResource(R.string.show_what, what), onClick = onReveal),
    ) {
        Box(
            if (revealed) {
                Modifier
            } else {
                // Screen readers must not read out what is hidden.
                Modifier.blur(18.dp).clearAndSetSemantics { }
            }
        ) { content() }
        if (!revealed) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .matchParentSize()
                    .background(Cinema.Night.copy(alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.3f else 0.95f)),
            ) {
                Text(stringResource(R.string.tap_to_show, what), style = MaterialTheme.typography.labelLarge, color = Cinema.Screen)
            }
        }
    }
}
