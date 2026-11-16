package app.stikka

import android.app.Application
import app.stikka.data.PackStore
import app.stikka.data.Settings
import app.stikka.data.StickerStore

class StikkaApp : Application() {
    lateinit var store: StickerStore
        private set
    lateinit var packs: PackStore
        private set
    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = Settings(this)
        store = StickerStore(this)
        packs = PackStore(this, store)
    }

    companion object {
        lateinit var instance: StikkaApp
            private set
    }
}
