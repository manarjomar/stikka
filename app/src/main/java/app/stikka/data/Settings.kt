package app.stikka.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.stikka.domain.langOverride

/** Ajustes de la app, observables desde Compose. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** "system", "light" o "dark". */
    var theme by mutableStateOf(prefs.getString("theme", "system") ?: "system")
        private set
    var dynamicColor by mutableStateOf(prefs.getBoolean("dynamic", false))
        private set
    /** "system", "es" o "en". */
    var language by mutableStateOf(prefs.getString("lang", "system") ?: "system")
        private set
    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false))
        private set
    var autoUpdateCheck by mutableStateOf(prefs.getBoolean("auto_update", true))
        private set

    init {
        applyLanguage()
    }

    fun updateTheme(v: String) { theme = v; prefs.edit().putString("theme", v).apply() }
    fun updateDynamic(v: Boolean) { dynamicColor = v; prefs.edit().putBoolean("dynamic", v).apply() }
    fun updateLanguage(v: String) { language = v; prefs.edit().putString("lang", v).apply(); applyLanguage() }
    fun finishOnboarding() { onboarded = true; prefs.edit().putBoolean("onboarded", true).apply() }
    fun updateAutoUpdate(v: Boolean) { autoUpdateCheck = v; prefs.edit().putBoolean("auto_update", v).apply() }

    var publisher: String
        get() = prefs.getString("publisher", null).orEmpty()
        set(v) { prefs.edit().putString("publisher", v).apply() }

    var lastUpdateCheck: Long
        get() = prefs.getLong("last_update_check", 0L)
        set(v) { prefs.edit().putLong("last_update_check", v).apply() }

    private fun applyLanguage() {
        langOverride = language.takeIf { it != "system" }
    }
}
