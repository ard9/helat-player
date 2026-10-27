package io.github.ard9.linguaplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ard9.linguaplayer.data.RolePlayPrefs
import io.github.ard9.linguaplayer.data.formatTime
import io.github.ard9.linguaplayer.roleplay.RolePlaySession
import io.github.ard9.linguaplayer.subtitle.SubtitleLine

/**
 * Chooses the scene: how many lines from now, who says which line, and how the user's turns
 * work. [lines] is null while they are being read.
 */
@Composable
fun RolePlaySetupDialog(
    lines: List<SubtitleLine>?,
    mine: List<Boolean>,
    prefs: RolePlayPrefs,
    iStart: Boolean,
    onPrefsChange: (RolePlayPrefs) -> Unit,
    onIStartChange: (Boolean) -> Unit,
    onToggle: (Int) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    var countText by remember(prefs.lineCount) { mutableStateOf(prefs.lineCount.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Role-play") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "You and the actor take turns. On your lines the film goes quiet and your voice is " +
                        "recorded. Use headphones so the film is not recorded too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lines", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { onPrefsChange(prefs.copy(lineCount = (prefs.lineCount - 1).coerceAtLeast(RolePlayPrefs.MIN_LINES))) },
                    ) { Text("−") }
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { text ->
                            countText = text.filter { it.isDigit() }.take(3)
                            countText.toIntOrNull()?.let {
                                onPrefsChange(prefs.copy(lineCount = it.coerceIn(RolePlayPrefs.MIN_LINES, RolePlayPrefs.MAX_LINES)))
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.widthIn(max = 72.dp),
                    )
                    OutlinedButton(
                        onClick = { onPrefsChange(prefs.copy(lineCount = (prefs.lineCount + 1).coerceAtMost(RolePlayPrefs.MAX_LINES))) },
                    ) { Text("+") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !iStart, onClick = { onIStartChange(false) }, label = { Text("Actor starts") })
                    FilterChip(selected = iStart, onClick = { onIStartChange(true) }, label = { Text("I start") })
                }
                SwitchRow("Pause the film on my lines", prefs.pauseForMe) { onPrefsChange(prefs.copy(pauseForMe = it)) }
                SwitchRow("Show my lines", prefs.showMyText) { onPrefsChange(prefs.copy(showMyText = it)) }
                Text("Tap a line to change who says it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    lines == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Reading the subtitles…", style = MaterialTheme.typography.bodySmall)
                    }
                    lines.isEmpty() -> Text("No subtitle lines from here on.", style = MaterialTheme.typography.bodyMedium)
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.heightIn(max = 280.dp),
                    ) {
                        itemsIndexed(lines) { i, line ->
                            LineRow(line, mine.getOrElse(i) { false }) { onToggle(i) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onStart,
                enabled = !lines.isNullOrEmpty() && mine.any { it },
            ) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LineRow(line: SubtitleLine, mine: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (mine) accent.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClickLabel = if (mine) "Give this line to the actor" else "Say this line myself", onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            if (mine) "Me" else "Actor",
            style = MaterialTheme.typography.labelLarge,
            color = if (mine) accent else Cinema.Dim,
            modifier = Modifier.widthIn(min = 48.dp),
        )
        Text(
            line.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(formatTime(line.startMs), style = MaterialTheme.typography.labelSmall, color = Cinema.Dim)
    }
}

/** What is shown on top of the film while a role-play session is open. */
@Composable
fun BoxScope.RolePlayOverlay(session: RolePlaySession, onClose: () -> Unit) {
    val total = session.lines.size
    val current = session.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            when (session.phase) {
                RolePlaySession.Phase.Acting -> if (current >= 0) "Role-play · line ${current + 1} of $total" else "Role-play · get ready"
                RolePlaySession.Phase.PlayingBack -> "Playing your scene"
                else -> "Role-play"
            },
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClose) { Text("Close", color = Color.White) }
    }

    when (session.phase) {
        RolePlaySession.Phase.Acting, RolePlaySession.Phase.PlayingBack -> {
            val line = session.lines.getOrNull(current)
            if (session.myTurn && line != null) {
                YourTurn(session, line.text)
            } else if (line != null && session.phase == RolePlaySession.Phase.Acting) {
                Text(
                    if (line.mine) "Get ready" else "Actor",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(start = 16.dp, top = 52.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            if (session.phase == RolePlaySession.Phase.PlayingBack) {
                OutlinedButton(
                    onClick = { session.stop() },
                    modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 16.dp),
                ) { Text("Stop", color = Color.White) }
            }
        }
        RolePlaySession.Phase.Finished, RolePlaySession.Phase.Comparing -> Results(session)
    }
}

@Composable
private fun BoxScope.YourTurn(session: RolePlaySession, text: String) {
    Surface(
        color = Cinema.Night.copy(alpha = 0.88f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(Cinema.Exit, RoundedCornerShape(50)))
                Text("Your turn: speak now", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (session.showMyText) {
                SubtitleText(text, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { session.skip() }) { Text("Skip") }
                Button(onClick = { session.done() }) { Text("Done") }
            }
        }
    }
}

@Composable
private fun BoxScope.Results(session: RolePlaySession) {
    Surface(
        color = Cinema.Night.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .align(Alignment.Center)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            Text("Scene finished", style = MaterialTheme.typography.titleLarge)
            session.error?.let { Text("Stopped: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            val said = session.recordings.size
            val mineCount = session.lines.count { it.mine }
            Text(
                "You said $said of your $mineCount lines.",
                style = MaterialTheme.typography.bodyMedium,
                color = Cinema.Dim,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { session.playBack() }, enabled = said > 0) { Text("Play my scene") }
                @Suppress("MissingPermission") // The permission was granted before the first start.
                OutlinedButton(onClick = { session.start() }) { Text("Try again") }
            }
            if (said > 0) {
                Text("Compare line by line", style = MaterialTheme.typography.titleSmall)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 220.dp)) {
                    itemsIndexed(session.lines) { i, line ->
                        if (!line.mine) return@itemsIndexed
                        val busy = session.phase == RolePlaySession.Phase.Comparing && session.current == i
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                line.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start,
                                color = if (busy) MaterialTheme.colorScheme.primary else Cinema.Screen,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { session.playActor(i) }) { Text("Actor") }
                            TextButton(onClick = { session.playMine(i) }, enabled = session.recordings[i] != null) { Text("Mine") }
                        }
                    }
                }
            }
        }
    }
}
