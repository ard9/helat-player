package io.github.ard9.linguaplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.ard9.linguaplayer.R
import io.github.ard9.linguaplayer.data.BoxIntervals
import io.github.ard9.linguaplayer.data.Leitner

/** Chooses the box to move cards to; [current] (one card's box) is marked, if known. */
@Composable
fun MoveToBoxDialog(
    current: Int?,
    intervals: BoxIntervals,
    onMove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                for (box in 1..Leitner.BOX_COUNT) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onMove(box) },
                    ) {
                        RadioButton(selected = box == current, onClick = { onMove(box) })
                        val days = intervals.daysFor(box)
                        Text(pluralStringResource(R.plurals.box_every, days, box, days), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    stringResource(R.string.move_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = Cinema.Dim,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
