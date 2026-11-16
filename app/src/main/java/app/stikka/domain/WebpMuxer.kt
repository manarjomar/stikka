package app.stikka.domain

import java.io.ByteArrayOutputStream

/**
 * Monta un WebP animado a partir de WebP estáticos (los que genera Android con Bitmap.compress).
 * Android sabe leer WebP animados pero no crearlos; el formato es un contenedor RIFF sencillo:
 * RIFF/WEBP + VP8X (lienzo) + ANIM (bucle) + un ANMF por fotograma con sus bloques ALPH/VP8 o VP8L.
 */
object WebpMuxer {

    /** Bloques de imagen de un WebP estático (ALPH + VP8, o VP8L), listos para meter en un ANMF. */
    fun frameData(still: ByteArray): ByteArray {
        require(WebpInfo.isWebp(still)) { "not a webp" }
        val out = ByteArrayOutputStream()
        var pos = 12
        var found = false
        while (pos + 8 <= still.size) {
            val tag = String(still, pos, 4, Charsets.US_ASCII)
            val size = le32(still, pos + 4)
            val total = 8 + size + (size and 1)
            if (pos + 8 + size > still.size) break
            if (tag == "ALPH" || tag == "VP8 " || tag == "VP8L") {
                out.write(still, pos, minOf(total, still.size - pos))
                if (total > still.size - pos) out.write(0) // relleno que faltase
                if (tag != "ALPH") found = true
            }
            pos += total
        }
        require(found) { "no image data" }
        return out.toByteArray()
    }

    /**
     * WebP animado con todos los fotogramas del mismo tamaño que el lienzo.
     * [durationMs]: duración de cada fotograma. [loops]: 0 = infinito.
     */
    fun animate(stills: List<ByteArray>, width: Int, height: Int, durationMs: Int, loops: Int = 0): ByteArray {
        require(stills.isNotEmpty())
        val body = ByteArrayOutputStream()
        body.write("WEBP".toByteArray(Charsets.US_ASCII))

        // VP8X: animación + transparencia, tamaño del lienzo.
        val vp8x = ByteArray(10)
        vp8x[0] = (0x02 or 0x10).toByte()
        put24(vp8x, 4, width - 1)
        put24(vp8x, 7, height - 1)
        chunk(body, "VP8X", vp8x)

        // ANIM: fondo transparente y número de repeticiones.
        val anim = ByteArray(6)
        anim[4] = (loops and 0xFF).toByte()
        anim[5] = ((loops shr 8) and 0xFF).toByte()
        chunk(body, "ANIM", anim)

        val d = durationMs.coerceIn(8, 0xFFFFFF)
        for (still in stills) {
            val data = frameData(still)
            val header = ByteArray(16)
            // desplazamiento X/Y = 0
            put24(header, 6, width - 1)
            put24(header, 9, height - 1)
            put24(header, 12, d)
            // Sin mezclar con el anterior (cada fotograma lo sustituye entero) y sin borrar.
            header[15] = 0x02
            chunk(body, "ANMF", header + data)
        }

        val b = body.toByteArray()
        val out = ByteArrayOutputStream(b.size + 8)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(le32Bytes(b.size))
        out.write(b)
        return out.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, tag: String, data: ByteArray) {
        out.write(tag.toByteArray(Charsets.US_ASCII))
        out.write(le32Bytes(data.size))
        out.write(data)
        if (data.size % 2 == 1) out.write(0)
    }

    private fun le32(b: ByteArray, p: Int): Int =
        (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or
            ((b[p + 2].toInt() and 0xFF) shl 16) or ((b[p + 3].toInt() and 0xFF) shl 24)

    private fun le32Bytes(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    private fun put24(b: ByteArray, p: Int, v: Int) {
        b[p] = v.toByte()
        b[p + 1] = (v shr 8).toByte()
        b[p + 2] = (v shr 16).toByte()
    }
}
