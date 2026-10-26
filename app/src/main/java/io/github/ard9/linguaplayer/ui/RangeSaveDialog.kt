package io.github.ard9.linguaplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ard9.linguaplayer.data.formatClock
import io.github.ard9.linguaplayer.data.parseClock

const val MAX_CLIP_MS = 60_000L

/**
 * Lets the user pick a start and end time by hand when there is no subtitle line to save.
 * [currentTime] is read when a "Now" button is pressed, so it follows a running preview.
 */
@Composable
fun RangeSaveDialog(
    initialStartMs: Long,
    initialEndMs: Long,
    currentTime: () -> Long,
    onPreview: (startMs: Long, endMs: Long) -> Unit,
    onDismiss: () -> Unit,
    onSave: (startMs: Long, endMs: Long, text: String) -> Unit,
) {
    var startText by remember { mutableStateOf(formatClock(initialStartMs)) }
    var endText by remember { mutableStateOf(formatClock(initialEndMs)) }
    var note by remember { mutableStateOf("") }

    val start = parseClock(startText)
    val end = parseClock(endText)
    val error = if (start == null) {
        "Start time is not valid (use m:ss or m:ss.s)"
    } else if (end == null) {
        "End time is not valid (use m:ss or m:ss.s)"
    } else if (end <= start) {
        "End must be after start"
    } else if (end - start > MAX_CLIP_MS) {
        "A clip can be at most 60 seconds"
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save a part") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeRow("Start", startText, { startText = it }) { startText = formatClock(currentTime()) }
                TimeRow("End", endText, { endText = it }) { endText = formatClock(currentTime()) }
                OutlinedButton(
                    onClick = { if (start != null && end != null && error == null) onPreview(start, end) },
                    enabled = error == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Preview") }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Text (optional)") },
                    placeholder = { Text("What is said in this part") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (start != null && end != null && error == null) onSave(start, end, note.trim()) },
                enabled = error == null,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TimeRow(label: String, value: String, onChange: (String) -> Unit, onNow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onNow) { Text("Now") }
    }
}
