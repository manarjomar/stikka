package app.stikka

import android.app.Application
import app.stikka.data.StickerStore

class StikkaApp : Application() {
    lateinit var store: StickerStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = StickerStore(this)
    }

    companion object {
        lateinit var instance: StikkaApp
            private set
    }
}
