package io.github.ard9.linguaplayer.ui

import io.github.ard9.linguaplayer.data.CardSearch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.OutlinedTextField
import io.github.ard9.linguaplayer.R
import androidx.compose.ui.res.stringResource
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
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    // While searching, results come from every box.
    val shown = if (searching) {
        CardSearch.find(cards, query)
    } else {
        cards.filter { it.box == selectedBox }.sortedByDescending { it.createdAt }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cards_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_cards)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (searching) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (!searching) BoxBars(
                counts = boxCounts(cards),
                selected = selectedBox,
                onSelect = onSelectBox,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            searching -> stringResource(R.string.search_none)
                            selectedBox == 1 -> stringResource(R.string.box1_empty)
                            else -> stringResource(R.string.box_empty)
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
                    items(shown, key = { it.id }) { card ->
                        CardRow(card, repository, showBox = searching) { onOpenCard(card) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardRow(card: Card, repository: CardRepository, showBox: Boolean, onClick: () -> Unit) {
    val thumbnail = rememberCardImage(repository.imageFile(card), maxWidthPx = 320)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.open_card), onClick = onClick),
    ) {
        FilmFrame(image = thumbnail, text = null, modifier = Modifier.width(128.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                card.text.ifBlank { stringResource(R.string.audio_only) },
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
            if (showBox) {
                Text(stringResource(R.string.box_n, card.box), style = MaterialTheme.typography.labelSmall, color = Cinema.Dim)
            }
        }
    }
}
