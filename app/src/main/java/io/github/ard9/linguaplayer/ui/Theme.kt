package io.github.ard9.linguaplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.ard9.linguaplayer.R
import io.github.ard9.linguaplayer.data.SubtitleSize
import io.github.ard9.linguaplayer.data.SubtitleStyle

/** "Cinema subtitle" palette: a deep night-blue auditorium lit by subtitle-coloured text. */
object Cinema {
    val Night = Color(0xFF14203A)
    val Seat = Color(0xFF1C2B4B)
    val Aisle = Color(0xFF26375C)
    val Screen = Color(0xFFEEF1F7)
    val Dim = Color(0xFF8C9CC4)
    val Exit = Color(0xFFFF7A7A)
}

/** Reading face, made for legibility: subtitles, cards, body text. */
val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

/** Condensed display face for titles and labels. */
val ArchivoCondensed = FontFamily(
    Font(R.font.archivo_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_condensed_bold, FontWeight.Bold),
)

/** Persian face (Vazirmatn), used for all text when the app is in Persian. */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    // No separate semibold file: the bold face stands in for it.
    Font(R.font.vazirmatn_bold, FontWeight.SemiBold),
)

/** The user's subtitle style, used by the player overlay and by cards. */
val LocalSubtitleStyle = staticCompositionLocalOf { SubtitleStyle(0xFFD84D, SubtitleSize.Medium) }

fun SubtitleStyle.composeColor(): Color = Color(0xFF000000 or color.toLong())

/** [persian]: Atkinson and Archivo have no Persian letters, so Vazirmatn replaces both. */
private fun typography(persian: Boolean): Typography {
    val base = Typography()
    val readingFont = if (persian) Vazirmatn else Atkinson
    val displayFont = if (persian) Vazirmatn else ArchivoCondensed
    fun TextStyle.reading() = copy(fontFamily = readingFont)
    fun TextStyle.display() = copy(fontFamily = displayFont, fontWeight = FontWeight.Bold)
    return base.copy(
        displaySmall = base.displaySmall.display().copy(fontSize = 40.sp, lineHeight = 44.sp),
        headlineMedium = base.headlineMedium.display(),
        headlineSmall = base.headlineSmall.display(),
        titleLarge = base.titleLarge.display().copy(fontSize = 24.sp),
        titleMedium = base.titleMedium.copy(fontFamily = displayFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        titleSmall = base.titleSmall.copy(fontFamily = displayFont, fontWeight = FontWeight.SemiBold),
        // Persian letters need more room between lines.
        bodyLarge = base.bodyLarge.reading().copy(fontSize = 17.sp, lineHeight = if (persian) 29.sp else 25.sp),
        bodyMedium = base.bodyMedium.reading().copy(lineHeight = if (persian) 25.sp else 21.sp),
        bodySmall = base.bodySmall.reading(),
        labelLarge = base.labelLarge.copy(fontFamily = displayFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        labelMedium = base.labelMedium.reading(),
        labelSmall = base.labelSmall.reading(),
    )
}

@Composable
fun LinguaTheme(subtitleStyle: SubtitleStyle, persian: Boolean, content: @Composable () -> Unit) {
    val accent = subtitleStyle.composeColor()
    val colors = remember(accent) {
        darkColorScheme(
            primary = accent,
            onPrimary = Cinema.Night,
            primaryContainer = Cinema.Aisle,
            onPrimaryContainer = Cinema.Screen,
            secondary = Cinema.Dim,
            onSecondary = Cinema.Night,
            secondaryContainer = Cinema.Aisle,
            onSecondaryContainer = Cinema.Screen,
            background = Cinema.Night,
            onBackground = Cinema.Screen,
            surface = Cinema.Night,
            onSurface = Cinema.Screen,
            surfaceVariant = Cinema.Seat,
            onSurfaceVariant = Cinema.Dim,
            surfaceContainerLowest = Cinema.Night,
            surfaceContainerLow = Cinema.Seat,
            surfaceContainer = Cinema.Seat,
            surfaceContainerHigh = Cinema.Aisle,
            surfaceContainerHighest = Cinema.Aisle,
            outline = Cinema.Dim,
            outlineVariant = Cinema.Aisle,
            error = Cinema.Exit,
            onError = Cinema.Night,
        )
    }
    val type = remember(persian) { typography(persian) }
    CompositionLocalProvider(LocalSubtitleStyle provides subtitleStyle) {
        MaterialTheme(colorScheme = colors, typography = type, content = content)
    }
}
