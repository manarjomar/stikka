package app.stikka.domain

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Una versión publicada en GitHub. */
data class Release(val version: String, val notes: String, val pageUrl: String, val apkUrl: String?)

/** Busca versiones nuevas en las releases de GitHub (la app no está en Play Store). */
object Updates {
    const val REPO = "manarjomar/stikka"
    const val REPO_URL = "https://github.com/$REPO"

    /** Última release publicada, o null si no hay conexión. */
    fun latest(): Release? = runCatching {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != 200) return null
            val o = JSONObject(conn.inputStream.bufferedReader().readText())
            val assets = o.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url")
                }
            }
            Release(
                version = o.optString("tag_name").removePrefix("v"),
                notes = o.optString("body"),
                pageUrl = o.optString("html_url", "$REPO_URL/releases/latest"),
                apkUrl = apk,
            )
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** ¿Es [candidate] más nueva que [current]? Compara por números: 0.10 > 0.9. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
