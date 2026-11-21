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
        installCrashLog()
        settings = Settings(this)
        store = StickerStore(this)
        packs = PackStore(this, store)
    }

    /** Guarda el error si la app se cierra de golpe, para poder enviarlo después. */
    private fun installCrashLog() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                java.io.File(filesDir, CRASH_FILE).writeText(
                    "Stikka ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · " +
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                        "Hilo: ${thread.name}\n\n$sw"
                )
            }
            previous?.uncaughtException(thread, e)
        }
    }

    companion object {
        const val CRASH_FILE = "last_crash.txt"
        lateinit var instance: StikkaApp
            private set
    }
}
