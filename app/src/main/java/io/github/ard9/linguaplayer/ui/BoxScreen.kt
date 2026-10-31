package io.github.ard9.linguaplayer.ui

import io.github.ard9.linguaplayer.data.CardSearch
import io.github.ard9.linguaplayer.data.BoxIntervals
import io.github.ard9.linguaplayer.data.Leitner
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.OutlinedTextField
import io.github.ard9.linguaplayer.R
import androidx.compose.ui.res.stringResource
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
    intervals: BoxIntervals,
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
    // Cards picked for deleting or moving; a long press on a card starts picking.
    var picked by remember { mutableStateOf(setOf<String>()) }
    // Cards deleted or moved elsewhere drop out of the selection.
    val pickedShown = picked intersect shown.map { it.id }.toSet()
    val picking = pickedShown.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    BackHandler(enabled = picking) { picked = emptySet() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (picking) {
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.selected_count, pickedShown.size, pickedShown.size)) },
                    navigationIcon = {
                        IconButton(onClick = { picked = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel_selection))
                        }
                    },
                    actions = {
                        TextButton(onClick = { picked = shown.map { it.id }.toSet() }) { Text(stringResource(R.string.select_all)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            } else {
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
        },
        bottomBar = {
            if (picking) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    TextButton(onClick = { moving = true }) { Text(stringResource(R.string.move_to_box)) }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
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
                        val isPicked = card.id in pickedShown
                        CardRow(
                            card,
                            repository,
                            showBox = searching,
                            picking = picking,
                            picked = isPicked,
                            onClick = {
                                if (picking) picked = if (isPicked) picked - card.id else picked + card.id else onOpenCard(card)
                            },
                            onLongClick = { picked = picked + card.id },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val count = pickedShown.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(pluralStringResource(R.plurals.delete_many_title, count, count)) },
            text = { Text(stringResource(R.string.delete_many_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    repository.deleteAll(pickedShown)
                    picked = emptySet()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (moving) {
        val chosen = cards.filter { it.id in pickedShown }
        MoveToBoxDialog(
            current = chosen.map { it.box }.distinct().singleOrNull(),
            intervals = intervals,
            onMove = { box ->
                val now = System.currentTimeMillis()
                repository.updateAll(chosen.map { Leitner.moveTo(it, box, now, intervals) })
                moving = false
                picked = emptySet()
            },
            onDismiss = { moving = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardRow(
    card: Card,
    repository: CardRepository,
    showBox: Boolean,
    picking: Boolean,
    picked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val thumbnail = rememberCardImage(repository.imageFile(card), maxWidthPx = 320)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClickLabel = stringResource(if (picking) R.string.select_card else R.string.open_card),
            onLongClickLabel = stringResource(R.string.select_card),
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    ) {
        if (picking) {
            Checkbox(checked = picked, onCheckedChange = { onClick() })
            Spacer(Modifier.width(4.dp))
        }
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
