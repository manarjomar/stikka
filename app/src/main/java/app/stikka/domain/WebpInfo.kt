package app.stikka.domain

/** Lectura rápida de la cabecera de un WebP. */
object WebpInfo {
    fun isWebp(bytes: ByteArray): Boolean =
        bytes.size >= 16 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"

    /** Animado si la cabecera extendida (VP8X) lleva la marca de animación. */
    fun isAnimated(bytes: ByteArray): Boolean =
        isWebp(bytes) && bytes.size > 21 &&
            String(bytes, 12, 4, Charsets.US_ASCII) == "VP8X" &&
            (bytes[20].toInt() and 0x02) != 0
}
