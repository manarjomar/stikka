package app.stikka

import app.stikka.domain.WebpInfo
import app.stikka.domain.WebpMuxer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class WebpMuxerTest {
    private fun chunk(out: ByteArrayOutputStream, tag: String, size: Int, fill: Int) {
        out.write(tag.toByteArray())
        out.write(byteArrayOf(size.toByte(), (size shr 8).toByte(), (size shr 16).toByte(), (size shr 24).toByte()))
        out.write(ByteArray(size) { fill.toByte() })
        if (size % 2 == 1) out.write(0)
    }

    /** WebP estático como los de Android con transparencia: VP8X + ALPH + VP8. */
    private fun still(alphaSize: Int, vp8Size: Int): ByteArray {
        val body = ByteArrayOutputStream()
        body.write("WEBP".toByteArray())
        chunk(body, "VP8X", 10, 0)
        chunk(body, "ALPH", alphaSize, 1)
        chunk(body, "VP8 ", vp8Size, 2)
        val b = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.write(byteArrayOf(b.size.toByte(), (b.size shr 8).toByte(), (b.size shr 16).toByte(), 0))
        out.write(b)
        return out.toByteArray()
    }

    @Test
    fun frameDataKeepsAlphaAndImageButNotVp8x() {
        val data = WebpMuxer.frameData(still(5, 20))
        // ALPH(8+5+1 relleno) + VP8(8+20)
        assertEquals(14 + 28, data.size)
        assertEquals("ALPH", String(data, 0, 4))
        assertEquals("VP8 ", String(data, 14, 4))
    }

    @Test
    fun animationHasAllFramesAndIsAnimated() {
        val anim = WebpMuxer.animate(List(4) { still(7, 30) }, 512, 512, 100)
        assertTrue(WebpInfo.isWebp(anim))
        assertTrue(WebpInfo.isAnimated(anim))
        assertEquals(4, WebpInfo.frameCount(anim))
        // Tamaño del RIFF coherente con el archivo.
        val riff = (anim[4].toInt() and 0xFF) or ((anim[5].toInt() and 0xFF) shl 8) or ((anim[6].toInt() and 0xFF) shl 16)
        assertEquals(anim.size - 8, riff)
        // Lienzo 512x512 (guardado como 511).
        assertEquals(511, (anim[24].toInt() and 0xFF) or ((anim[25].toInt() and 0xFF) shl 8))
    }
}
