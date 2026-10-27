package io.github.ard9.linguaplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
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

    /** Two arrows chasing each other (the Material "repeat" shape). */
    val Repeat: ImageVector = ImageVector.Builder(
        name = "Repeat",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes("M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z"),
        fill = SolidColor(Color.White),
    ).build()

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

    /** A question mark in a circle (the Material "help outline" shape), for the guide. */
    val Help: ImageVector = ImageVector.Builder(
        name = "Help",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M11,18h2v-2h-2v2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
                "M12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z" +
                "M12,6c-2.21,0 -4,1.79 -4,4h2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,2 -3,1.75 -3,5h2c0,-2.25 3,-2.5 3,-5 0,-2.21 -1.79,-4 -4,-4z"
        ),
        fill = SolidColor(Color.White),
    ).build()
}
