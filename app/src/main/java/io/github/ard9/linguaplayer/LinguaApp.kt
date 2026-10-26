package io.github.ard9.linguaplayer

import android.app.Application
import io.github.ard9.linguaplayer.clip.ClipService
import io.github.ard9.linguaplayer.clip.TransformerClipper
import io.github.ard9.linguaplayer.clip.VlcClipper
import io.github.ard9.linguaplayer.data.CardRepository
import io.github.ard9.linguaplayer.data.RecentRepository
import org.videolan.libvlc.LibVLC

class LinguaApp : Application() {
    lateinit var cards: CardRepository
        private set
    lateinit var recents: RecentRepository
        private set
    lateinit var clips: ClipService
        private set

    /** One libVLC instance for the whole app; creating it loads the native libraries. */
    val libVlc: LibVLC by lazy { LibVLC(this, arrayListOf<String>()) }

    override fun onCreate() {
        super.onCreate()
        cards = CardRepository(this)
        recents = RecentRepository(this)
        clips = ClipService(
            transformer = TransformerClipper(this),
            vlc = VlcClipper(this) { libVlc },
            outputFor = cards::newClipFile,
        )
    }
}
