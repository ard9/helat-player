package io.github.ard9.linguaplayer

import android.app.Application
import android.util.Log
import io.github.ard9.linguaplayer.clip.ClipService
import io.github.ard9.linguaplayer.clip.TransformerClipper
import io.github.ard9.linguaplayer.clip.VlcClipper
import io.github.ard9.linguaplayer.data.CardRepository
import io.github.ard9.linguaplayer.data.RecentRepository
import io.github.ard9.linguaplayer.data.SettingsRepository
import io.github.ard9.linguaplayer.data.SubtitleStyle
import io.github.ard9.linguaplayer.speech.SpeechFinder
import org.videolan.libvlc.LibVLC

class LinguaApp : Application() {
    lateinit var cards: CardRepository
        private set
    lateinit var recents: RecentRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var clips: ClipService
        private set
    lateinit var speechFinder: SpeechFinder
        private set

    // libVLC reads subtitle colour and size only when an instance is created, so a new
    // instance is made after the style changes. Old ones are released once nothing uses them.
    private var vlc: LibVLC? = null
    private var vlcStyle: SubtitleStyle? = null
    private val vlcUsers = mutableMapOf<LibVLC, Int>()

    override fun onCreate() {
        super.onCreate()
        cards = CardRepository(this)
        recents = RecentRepository(this)
        settings = SettingsRepository(this)
        val vlcClipper = VlcClipper(this, ::acquireLibVlc, ::releaseLibVlc)
        clips = ClipService(
            transformer = TransformerClipper(this),
            vlc = vlcClipper,
            outputFor = cards::newClipFile,
        )
        speechFinder = SpeechFinder(this, vlcClipper)
    }

    /** Returns a libVLC instance for the current subtitle style; pair with [releaseLibVlc]. */
    @Synchronized
    fun acquireLibVlc(): LibVLC {
        val style = settings.subtitleStyle.value
        val current = vlc
        val instance = if (current != null && vlcStyle == style) {
            current
        } else {
            if (current != null && (vlcUsers[current] ?: 0) == 0) {
                vlcUsers.remove(current)
                current.release()
            }
            createLibVlc(style).also {
                vlc = it
                vlcStyle = style
            }
        }
        vlcUsers[instance] = (vlcUsers[instance] ?: 0) + 1
        return instance
    }

    @Synchronized
    fun releaseLibVlc(instance: LibVLC) {
        val remaining = (vlcUsers[instance] ?: 1) - 1
        if (remaining > 0) {
            vlcUsers[instance] = remaining
            return
        }
        vlcUsers.remove(instance)
        if (instance !== vlc) instance.release()
    }

    private fun createLibVlc(style: SubtitleStyle): LibVLC = try {
        LibVLC(
            this,
            arrayListOf(
                "--freetype-color=${style.color}",
                "--sub-text-scale=${style.size.vlcScale}",
            ),
        )
    } catch (e: Throwable) {
        // Never fail playback because of a style option.
        Log.w("LinguaApp", "libVLC rejected subtitle options, using defaults", e)
        LibVLC(this, arrayListOf<String>())
    }
}
