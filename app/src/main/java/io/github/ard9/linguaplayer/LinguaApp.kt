package io.github.ard9.linguaplayer

import android.app.Application
import io.github.ard9.linguaplayer.clip.AudioClipper
import io.github.ard9.linguaplayer.data.CardRepository
import io.github.ard9.linguaplayer.data.RecentRepository

class LinguaApp : Application() {
    lateinit var cards: CardRepository
        private set
    lateinit var recents: RecentRepository
        private set
    lateinit var clipper: AudioClipper
        private set

    override fun onCreate() {
        super.onCreate()
        cards = CardRepository(this)
        recents = RecentRepository(this)
        clipper = AudioClipper(this)
    }
}
