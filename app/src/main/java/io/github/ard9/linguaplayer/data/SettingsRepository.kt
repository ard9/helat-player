package io.github.ard9.linguaplayer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SubtitleSize(val label: String, val sp: Int, val vlcScale: Int) {
    Small("Small", 18, 80),
    Medium("Medium", 22, 100),
    Large("Large", 28, 130),
}

/** How subtitles look, in the player and on cards. [color] is 0xRRGGBB. */
data class SubtitleStyle(val color: Int, val size: SubtitleSize)

object SubtitleColors {
    const val YELLOW = 0xFFD84D

    /** Name to 0xRRGGBB, in the order shown to the user. */
    val presets = listOf(
        "Yellow" to YELLOW,
        "White" to 0xF4F6FA,
        "Cyan" to 0x6FE3F2,
        "Green" to 0x8BE38B,
        "Orange" to 0xFFAA55,
        "Pink" to 0xFF8FC7,
    )
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _subtitleStyle = MutableStateFlow(load())
    val subtitleStyle: StateFlow<SubtitleStyle> = _subtitleStyle

    fun setSubtitleStyle(style: SubtitleStyle) {
        prefs.edit()
            .putInt(KEY_COLOR, style.color)
            .putString(KEY_SIZE, style.size.name)
            .apply()
        _subtitleStyle.value = style
    }

    private fun load(): SubtitleStyle {
        val color = prefs.getInt(KEY_COLOR, SubtitleColors.YELLOW) and 0xFFFFFF
        val size = SubtitleSize.entries.firstOrNull { it.name == prefs.getString(KEY_SIZE, null) }
            ?: SubtitleSize.Medium
        return SubtitleStyle(color, size)
    }

    private companion object {
        const val KEY_COLOR = "subtitle_color"
        const val KEY_SIZE = "subtitle_size"
    }
}
