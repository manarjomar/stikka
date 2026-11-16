package app.stikka.domain

import java.util.Locale

/** Textos bilingües: español si el móvil está en español, inglés en el resto. */
fun tr(es: String, en: String): String =
    if (Locale.getDefault().language == "es") es else en
