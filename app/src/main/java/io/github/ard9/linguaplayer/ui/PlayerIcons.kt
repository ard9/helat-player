package io.github.ard9.linguaplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small icons that are not in material-icons-core (avoids the large extended set). */
object PlayerIcons {
    val Pause: ImageVector = ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.White)) {
        moveTo(6f, 5f)
        horizontalLineTo(10f)
        verticalLineTo(19f)
        horizontalLineTo(6f)
        close()
        moveTo(14f, 5f)
        horizontalLineTo(18f)
        verticalLineTo(19f)
        horizontalLineTo(14f)
        close()
    }.build()

    val Bookmark: ImageVector = ImageVector.Builder(
        name = "Bookmark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.White)) {
        moveTo(6f, 3f)
        horizontalLineTo(18f)
        verticalLineTo(21f)
        lineTo(12f, 17f)
        lineTo(6f, 21f)
        close()
    }.build()
}
