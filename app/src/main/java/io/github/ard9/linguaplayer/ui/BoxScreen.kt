package io.github.ard9.linguaplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ard9.linguaplayer.data.Card
import io.github.ard9.linguaplayer.data.CardRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScreen(
    cards: List<Card>,
    repository: CardRepository,
    selectedBox: Int,
    onSelectBox: (Int) -> Unit,
    onOpenCard: (Card) -> Unit,
    onBack: () -> Unit,
) {
    val inBox = cards.filter { it.box == selectedBox }.sortedByDescending { it.createdAt }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Cards") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            BoxBars(
                counts = boxCounts(cards),
                selected = selectedBox,
                onSelect = onSelectBox,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (inBox.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (selectedBox == 1) {
                            "New cards start here. Save a line while watching to add one."
                        } else {
                            "Cards move here when you answer \"I knew it\" in review."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cinema.Dim,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(inBox, key = { it.id }) { card ->
                        CardRow(card, repository) { onOpenCard(card) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardRow(card: Card, repository: CardRepository, onClick: () -> Unit) {
    val thumbnail = rememberCardImage(repository.imageFile(card), maxWidthPx = 320)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open card", onClick = onClick),
    ) {
        FilmFrame(image = thumbnail, text = null, modifier = Modifier.width(128.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                card.text.ifBlank { "Audio only" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (card.text.isBlank()) Cinema.Dim else MaterialTheme.colorScheme.onBackground,
            )
            if (card.meaning.isNotBlank()) {
                Text(
                    card.meaning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cinema.Dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
