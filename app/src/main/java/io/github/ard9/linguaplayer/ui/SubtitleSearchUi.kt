package io.github.ard9.linguaplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.ard9.linguaplayer.R
import io.github.ard9.linguaplayer.data.formatTime
import io.github.ard9.linguaplayer.subtitle.SubtitleLine
import io.github.ard9.linguaplayer.subtitle.SubtitleSearch
import io.github.ard9.linguaplayer.text.TextMatch

/** What the subtitle search can search in. */
sealed interface SearchIndex {
    /** Reading the film's lines; [progress] is 0..1. */
    data class Loading(val progress: Float) : SearchIndex

    /** Named sources (tracks or the subtitle file), best first. */
    // Not a data class: compared by identity, so a long list is not compared on every keystroke.
    class Ready(val sources: List<Pair<String, List<SubtitleLine>>>) : SearchIndex

    /** An online film whose lines are not indexed: a subtitle file is needed. */
    data object NeedsFile : SearchIndex

    /** No text subtitles at all. */
    data object NoSubtitles : SearchIndex

    data class Failed(val error: Throwable) : SearchIndex
}

/**
 * Searches the film's subtitles. Picking a line plays the film from there; [onLoadFile] and
 * [onRetry] help when there is nothing to search yet.
 */
@Composable
fun SubtitleSearchDialog(
    index: SearchIndex,
    onPick: (SubtitleLine) -> Unit,
    onLoadFile: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(16.dp).widthIn(max = 640.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.search_subtitles),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_subtitles_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                when (index) {
                    is SearchIndex.Loading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.search_reading, (index.progress * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cinema.Dim,
                        )
                        LinearProgressIndicator(progress = { index.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is SearchIndex.Ready -> Results(index, query, onPick)
                    SearchIndex.NeedsFile -> Notice(stringResource(R.string.search_needs_file), stringResource(R.string.load_sub_file), onLoadFile)
                    SearchIndex.NoSubtitles -> Notice(stringResource(R.string.search_no_subtitles), stringResource(R.string.load_sub_file), onLoadFile)
                    is SearchIndex.Failed -> Notice(
                        stringResource(R.string.search_failed, LocalContext.current.describe(index.error)),
                        stringResource(R.string.try_again),
                        onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, action: String, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Cinema.Dim)
        OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun Results(index: SearchIndex.Ready, query: String, onPick: (SubtitleLine) -> Unit) {
    val words = remember(query) { TextMatch.words(query) }
    val hits = remember(index, words) { SubtitleSearch.find(index.sources.map { it.second }, query) }
    when {
        words.isEmpty() -> Text(stringResource(R.string.search_subtitles_empty), style = MaterialTheme.typography.bodyMedium, color = Cinema.Dim)
        hits.isEmpty() -> Text(stringResource(R.string.search_nothing), style = MaterialTheme.typography.bodyMedium, color = Cinema.Dim)
        else -> {
            Text(
                pluralStringResource(R.plurals.search_found, hits.size, hits.size),
                style = MaterialTheme.typography.labelMedium,
                color = Cinema.Dim,
            )
            val accent = MaterialTheme.colorScheme.primary
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(hits) { hit ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = stringResource(R.string.search_play_here)) { onPick(hit.line) }
                            .padding(vertical = 10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(formatTime(hit.line.startMs), style = MaterialTheme.typography.labelLarge, color = accent)
                            if (index.sources.size > 1) {
                                Text(index.sources[hit.source].first, style = MaterialTheme.typography.labelMedium, color = Cinema.Dim)
                            }
                        }
                        Text(highlighted(hit.line.text, words, accent), style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider(color = Cinema.Aisle)
                }
            }
        }
    }
}

private fun highlighted(text: String, words: List<String>, color: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        for (range in TextMatch.highlights(text, words)) {
            addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), range.first, range.last + 1)
        }
    }
