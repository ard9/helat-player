package io.github.ard9.linguaplayer.ui

import androidx.compose.ui.text.style.TextDirection
import androidx.compose.material3.LocalTextStyle
import io.github.ard9.linguaplayer.R
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ard9.linguaplayer.data.formatClock
import io.github.ard9.linguaplayer.data.parseClock

const val MAX_CLIP_MS = 60_000L

/**
 * Values found after the dialog opened. Each new [version] replaces the fields;
 * a null [text] or [meaning] leaves that field as it is.
 */
data class AutoFill(
    val version: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String?,
    val meaning: String?,
)

/**
 * Shown when the user taps Save: start/end time (prefilled from the subtitle line when there
 * is one), text, meaning and the captured frame. [currentTime] is read when a "Now" button is
 * pressed, so it follows a running preview.
 */
@Composable
fun CardEditorDialog(
    initialStartMs: Long,
    initialEndMs: Long,
    initialText: String,
    autoFill: AutoFill?,
    /** What is being looked for right now, shown with a spinner; null when idle. */
    finding: String?,
    /** A short explanation of where the times came from, or why they were not found. */
    timeNote: String?,
    /** Subtitle tracks the text can come from; shown as choices when there are several. */
    trackOptions: List<String>,
    selectedTrack: Int,
    onSelectTrack: (Int) -> Unit,
    image: Bitmap?,
    currentTime: () -> Long,
    onPreview: (startMs: Long, endMs: Long) -> Unit,
    onDismiss: () -> Unit,
    onSave: (startMs: Long, endMs: Long, text: String, meaning: String) -> Unit,
) {
    var startText by remember { mutableStateOf(formatClock(initialStartMs)) }
    var endText by remember { mutableStateOf(formatClock(initialEndMs)) }
    var text by remember { mutableStateOf(initialText) }
    var meaning by remember { mutableStateOf("") }
    LaunchedEffect(autoFill?.version) {
        autoFill?.let { fill ->
            startText = formatClock(fill.startMs)
            endText = formatClock(fill.endMs)
            fill.text?.let { text = it }
            fill.meaning?.let { meaning = it }
        }
    }

    val start = parseClock(startText)
    val end = parseClock(endText)
    val error = if (start == null) {
        R.string.err_start_invalid
    } else if (end == null) {
        R.string.err_end_invalid
    } else if (end <= start) {
        R.string.err_end_before_start
    } else if (end - start > MAX_CLIP_MS) {
        R.string.err_too_long
    } else {
        null
    }
    val imageBitmap = remember(image) { image?.asImageBitmap() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_card)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = stringResource(R.string.video_frame),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp),
                    )
                }
                if (finding != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(finding, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (timeNote != null) {
                    Text(timeNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (trackOptions.size > 1) {
                    Text(stringResource(R.string.text_from), style = MaterialTheme.typography.labelMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        trackOptions.forEachIndexed { index, label ->
                            FilterChip(
                                selected = index == selectedTrack,
                                onClick = { onSelectTrack(index) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                TimeRow(stringResource(R.string.time_start), startText, { startText = it }) { startText = formatClock(currentTime()) }
                TimeRow(stringResource(R.string.time_end), endText, { endText = it }) { endText = formatClock(currentTime()) }
                OutlinedButton(
                    onClick = { if (start != null && end != null && error == null) onPreview(start, end) },
                    enabled = error == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.preview)) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.text)) },
                    placeholder = { Text(stringResource(R.string.text_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = meaning,
                    onValueChange = { meaning = it },
                    label = { Text(stringResource(R.string.meaning)) },
                    placeholder = { Text(stringResource(R.string.meaning_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(stringResource(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (start != null && end != null && error == null) onSave(start, end, text.trim(), meaning.trim())
                },
                enabled = error == null,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Edits the text and meaning of an existing card. */
@Composable
fun CardTextDialog(
    initialText: String,
    initialMeaning: String,
    onDismiss: () -> Unit,
    onSave: (text: String, meaning: String) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var meaning by remember { mutableStateOf(initialMeaning) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_card)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.text)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = meaning,
                    onValueChange = { meaning = it },
                    label = { Text(stringResource(R.string.meaning)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim(), meaning.trim()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A time field (m:ss.s, left to right) with a "Now" button. */
@Composable
internal fun TimeRow(label: String, value: String, onChange: (String) -> Unit, onNow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            // Times are typed as m:ss.s, left to right, also in Persian.
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onNow) { Text(stringResource(R.string.now)) }
    }
}
