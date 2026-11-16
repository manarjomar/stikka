package app.stikka.domain

/** Lectura rápida de la estructura de un WebP. */
object WebpInfo {
    fun isWebp(bytes: ByteArray): Boolean =
        bytes.size >= 16 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"

    /** Número de fotogramas (bloques ANMF). Un WebP normal devuelve 1. */
    fun frameCount(bytes: ByteArray): Int {
        if (!isWebp(bytes)) return 0
        var pos = 12
        var frames = 0
        while (pos + 8 <= bytes.size) {
            val tag = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = (bytes[pos + 4].toInt() and 0xFF) or
                ((bytes[pos + 5].toInt() and 0xFF) shl 8) or
                ((bytes[pos + 6].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 7].toInt() and 0xFF) shl 24)
            if (size < 0) break
            if (tag == "ANMF") frames++
            pos += 8 + size + (size and 1)
        }
        return if (frames == 0) 1 else frames
    }

    /**
     * Animado de verdad: lleva la marca de animación Y más de un fotograma.
     * WhatsApp guarda muchos stickers estáticos con la marca puesta y un solo fotograma.
     */
    fun isAnimated(bytes: ByteArray): Boolean =
        isWebp(bytes) && bytes.size > 21 &&
            String(bytes, 12, 4, Charsets.US_ASCII) == "VP8X" &&
            (bytes[20].toInt() and 0x02) != 0 &&
            frameCount(bytes) > 1
}
