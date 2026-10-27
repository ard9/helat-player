package io.github.ard9.linguaplayer.data

import android.content.Context
import androidx.annotation.StringRes
import io.github.ard9.linguaplayer.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SubtitleSize(@StringRes val label: Int, val sp: Int, val vlcScale: Int) {
    Small(R.string.size_small, 18, 80),
    Medium(R.string.size_medium, 22, 100),
    Large(R.string.size_large, 28, 130),
}

/** The app's language; [System] follows the phone. [tag] is stored in the settings. */
enum class AppLanguage(val tag: String) {
    System("system"),
    English("en"),
    Persian("fa");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: System
    }
}

/** Extra audio kept before and after each saved line, so first and last words are not cut. */
data class ClipMargins(val beforeMs: Long, val afterMs: Long) {
    companion object {
        const val MAX_MS = 1_500L
        val DEFAULT = ClipMargins(450, 450)
    }
}

/** The last role-play choices, offered again next time. */
data class RolePlayPrefs(
    val lineCount: Int,
    val pauseForMe: Boolean,
    val showMyText: Boolean,
    /** The scene is a time range instead of a number of lines from now. */
    val byTime: Boolean = false,
) {
    companion object {
        const val MIN_LINES = 2
        const val MAX_LINES = 200
        val DEFAULT = RolePlayPrefs(lineCount = 10, pauseForMe = true, showMyText = true)
    }
}

/** How subtitles look, in the player and on cards. [color] is 0xRRGGBB. */
data class SubtitleStyle(val color: Int, val size: SubtitleSize)

object SubtitleColors {
    const val YELLOW = 0xFFD84D

    /** Name (string resource) to 0xRRGGBB, in the order shown to the user. */
    val presets = listOf(
        R.string.color_yellow to YELLOW,
        R.string.color_white to 0xF4F6FA,
        R.string.color_cyan to 0x6FE3F2,
        R.string.color_green to 0x8BE38B,
        R.string.color_orange to 0xFFAA55,
        R.string.color_pink to 0xFF8FC7,
    )
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null)))
    val language: StateFlow<AppLanguage> = _language

    /** Saved at once (commit), because the activity is recreated right after a change. */
    fun setLanguage(value: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, value.tag).commit()
        _language.value = value
    }

    private val _welcomeDismissed = MutableStateFlow(prefs.getBoolean(KEY_WELCOME, false))
    /** True once the first-launch welcome card on the home screen was closed. */
    val welcomeDismissed: StateFlow<Boolean> = _welcomeDismissed

    fun dismissWelcome() {
        prefs.edit().putBoolean(KEY_WELCOME, true).apply()
        _welcomeDismissed.value = true
    }

    private val _subtitleStyle = MutableStateFlow(load())
    val subtitleStyle: StateFlow<SubtitleStyle> = _subtitleStyle

    private val _clipMargins = MutableStateFlow(
        ClipMargins(
            prefs.getLong(KEY_BEFORE, ClipMargins.DEFAULT.beforeMs).coerceIn(0, ClipMargins.MAX_MS),
            prefs.getLong(KEY_AFTER, ClipMargins.DEFAULT.afterMs).coerceIn(0, ClipMargins.MAX_MS),
        )
    )
    val clipMargins: StateFlow<ClipMargins> = _clipMargins

    private val _rolePlay = MutableStateFlow(
        RolePlayPrefs(
            prefs.getInt(KEY_RP_COUNT, RolePlayPrefs.DEFAULT.lineCount).coerceIn(RolePlayPrefs.MIN_LINES, RolePlayPrefs.MAX_LINES),
            prefs.getBoolean(KEY_RP_PAUSE, RolePlayPrefs.DEFAULT.pauseForMe),
            prefs.getBoolean(KEY_RP_TEXT, RolePlayPrefs.DEFAULT.showMyText),
            prefs.getBoolean(KEY_RP_BY_TIME, RolePlayPrefs.DEFAULT.byTime),
        )
    )
    val rolePlay: StateFlow<RolePlayPrefs> = _rolePlay

    fun setRolePlay(value: RolePlayPrefs) {
        prefs.edit()
            .putInt(KEY_RP_COUNT, value.lineCount)
            .putBoolean(KEY_RP_PAUSE, value.pauseForMe)
            .putBoolean(KEY_RP_TEXT, value.showMyText)
            .putBoolean(KEY_RP_BY_TIME, value.byTime)
            .apply()
        _rolePlay.value = value
    }

    fun setClipMargins(margins: ClipMargins) {
        prefs.edit().putLong(KEY_BEFORE, margins.beforeMs).putLong(KEY_AFTER, margins.afterMs).apply()
        _clipMargins.value = margins
    }

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

    companion object {
        private const val FILE = "settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_WELCOME = "welcome_dismissed"

        /** The chosen language, read before the app's singletons exist (activity start). */
        fun language(context: Context): AppLanguage = AppLanguage.fromTag(
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
        )

        private const val KEY_COLOR = "subtitle_color"
        private const val KEY_SIZE = "subtitle_size"
        private const val KEY_BEFORE = "clip_before_ms"
        private const val KEY_AFTER = "clip_after_ms"
        private const val KEY_RP_COUNT = "roleplay_lines"
        private const val KEY_RP_PAUSE = "roleplay_pause"
        private const val KEY_RP_TEXT = "roleplay_show_text"
        private const val KEY_RP_BY_TIME = "roleplay_by_time"
    }
}
