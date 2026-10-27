package io.github.ard9.linguaplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ard9.linguaplayer.data.ClipMargins
import java.util.Locale
import kotlin.math.roundToLong

/** Sets how much audio is kept before and after each saved line, in 0.1 s steps. */
@Composable
fun ClipMarginsDialog(current: ClipMargins, onChange: (ClipMargins) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clip margins") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Extra audio kept around each saved line, so the first and last words are not cut off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MarginSlider("Before the line", current.beforeMs) { onChange(current.copy(beforeMs = it)) }
                MarginSlider("After the line", current.afterMs) { onChange(current.copy(afterMs = it)) }
                TextButton(
                    onClick = { onChange(ClipMargins.DEFAULT) },
                    enabled = current != ClipMargins.DEFAULT,
                ) { Text("Reset to default") }
                Text(
                    "Applies to the next lines you save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun MarginSlider(label: String, valueMs: Long, onChange: (Long) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(String.format(Locale.US, "%.1f s", valueMs / 1000.0), style = MaterialTheme.typography.titleSmall)
        }
        Slider(
            value = valueMs.toFloat(),
            onValueChange = { onChange((it / 100).roundToLong() * 100) },
            valueRange = 0f..ClipMargins.MAX_MS.toFloat(),
            steps = (ClipMargins.MAX_MS / 100 - 1).toInt(),
        )
    }
}
