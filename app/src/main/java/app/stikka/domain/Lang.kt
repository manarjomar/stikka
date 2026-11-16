package app.stikka.domain

import java.util.Locale

/** Idioma elegido en Ajustes: "es", "en" o null para seguir al móvil. */
@Volatile
var langOverride: String? = null

/** Textos bilingües: español si el idioma es español, inglés en el resto. */
fun tr(es: String, en: String): String {
    val lang = langOverride ?: Locale.getDefault().language
    return if (lang == "es") es else en
}
