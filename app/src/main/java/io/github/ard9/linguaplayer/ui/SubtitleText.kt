package io.github.ard9.linguaplayer.ui

import io.github.ard9.linguaplayer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ard9.linguaplayer.data.SubtitleColors
import io.github.ard9.linguaplayer.data.SubtitleSize
import io.github.ard9.linguaplayer.data.SubtitleStyle

/**
 * Text drawn the way film subtitles are: the user's colour and size, with a dark outline so
 * it stays readable on any frame.
 */
@Composable
fun SubtitleText(text: String, modifier: Modifier = Modifier, scale: Float = 1f) {
    val style = LocalSubtitleStyle.current
    val base = TextStyle(
        fontFamily = Atkinson,
        fontWeight = FontWeight.Bold,
        fontSize = (style.size.sp * scale).sp,
        lineHeight = (style.size.sp * scale * 1.25f).sp,
        textAlign = TextAlign.Center,
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text,
            style = base.copy(color = Color.Black.copy(alpha = 0.85f), drawStyle = Stroke(width = 6f)),
        )
        Text(text, style = base.copy(color = style.composeColor()))
    }
}

/** Lets the user pick the subtitle colour and size, with a live preview. */
@Composable
fun SubtitleStyleDialog(
    current: SubtitleStyle,
    onChange: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subtitle_style)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Cinema.Night, RoundedCornerShape(8.dp))
                        .padding(vertical = 20.dp, horizontal = 8.dp),
                ) {
                    SubtitleText(stringResource(R.string.style_sample))
                }
                Text(stringResource(R.string.colour), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubtitleColors.presets.forEach { (name, rgb) ->
                        val selected = rgb == current.color
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color(0xFF000000 or rgb.toLong()), CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) Cinema.Screen else Cinema.Aisle,
                                    shape = CircleShape,
                                )
                                .clickable(onClickLabel = stringResource(name)) { onChange(current.copy(color = rgb)) }
                        )
                    }
                }
                Text(stringResource(R.string.size), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubtitleSize.entries.forEach { size ->
                        FilterChip(
                            selected = size == current.size,
                            onClick = { onChange(current.copy(size = size)) },
                            label = { Text(stringResource(size.label)) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.style_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}
