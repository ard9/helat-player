package io.github.ard9.linguaplayer.ui

import io.github.ard9.linguaplayer.R
import androidx.compose.ui.res.stringResource
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
import kotlin.math.roundToLong

/** Sets how much audio is kept before and after each saved line, in 0.1 s steps. */
@Composable
fun ClipMarginsDialog(current: ClipMargins, onChange: (ClipMargins) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clip_margins)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.margins_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MarginSlider(stringResource(R.string.margins_before), current.beforeMs) { onChange(current.copy(beforeMs = it)) }
                MarginSlider(stringResource(R.string.margins_after), current.afterMs) { onChange(current.copy(afterMs = it)) }
                TextButton(
                    onClick = { onChange(ClipMargins.DEFAULT) },
                    enabled = current != ClipMargins.DEFAULT,
                ) { Text(stringResource(R.string.reset_default)) }
                Text(
                    stringResource(R.string.margins_applies),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun MarginSlider(label: String, valueMs: Long, onChange: (Long) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.seconds_value, valueMs / 1000.0), style = MaterialTheme.typography.titleSmall)
        }
        Slider(
            value = valueMs.toFloat(),
            onValueChange = { onChange((it / 100).roundToLong() * 100) },
            valueRange = 0f..ClipMargins.MAX_MS.toFloat(),
            steps = (ClipMargins.MAX_MS / 100 - 1).toInt(),
        )
    }
}
