package io.github.ard9.linguaplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A card shown as the film frame it came from, with its line drawn as a subtitle.
 * [text] null or blank shows the frame alone; [hint] is shown dimmed when there is no text.
 */
@Composable
fun FilmFrame(
    image: ImageBitmap?,
    text: String?,
    modifier: Modifier = Modifier,
    textScale: Float = 0.85f,
    hint: String? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Video frame",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!text.isNullOrBlank()) {
            SubtitleText(
                text = text,
                scale = textScale,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 10.dp),
            )
        } else if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = Cinema.Dim,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * The five Leitner boxes as columns whose height shows how many cards each holds.
 * With [onSelect] the columns act as tabs and [selected] is highlighted.
 */
@Composable
fun BoxBars(
    counts: List<Int>,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
) {
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val accent = MaterialTheme.colorScheme.primary
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.fillMaxWidth().height(112.dp),
    ) {
        counts.forEachIndexed { index, count ->
            val box = index + 1
            val isSelected = selected == box
            val barColor = when {
                selected == null -> accent.copy(alpha = 0.35f + 0.16f * index)
                isSelected -> accent
                else -> Cinema.Aisle
            }
            val clickModifier = if (onSelect != null) {
                Modifier.clickable(role = Role.Tab, onClickLabel = "Show box $box") { onSelect(box) }
            } else {
                Modifier
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f).fillMaxHeight().then(clickModifier),
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected || selected == null) Cinema.Screen else Cinema.Dim,
                )
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .height((6f + 58f * count / max).dp)
                        .background(barColor, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                )
                Text(
                    "Box $box",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) accent else Cinema.Dim,
                )
            }
        }
    }
}

fun boxCounts(cards: List<io.github.ard9.linguaplayer.data.Card>): List<Int> =
    (1..io.github.ard9.linguaplayer.data.Leitner.BOX_COUNT).map { box -> cards.count { it.box == box } }
