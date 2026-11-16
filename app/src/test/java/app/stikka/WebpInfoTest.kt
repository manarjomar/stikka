package app.stikka

import app.stikka.domain.WebpInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebpInfoTest {
    private fun header(chunk: String, flags: Int): ByteArray {
        val b = ByteArray(32)
        "RIFF".toByteArray().copyInto(b, 0)
        "WEBP".toByteArray().copyInto(b, 8)
        chunk.toByteArray().copyInto(b, 12)
        b[20] = flags.toByte()
        return b
    }

    @Test
    fun detectsAnimatedWebp() {
        assertTrue(WebpInfo.isAnimated(header("VP8X", 0x12)))
    }

    @Test
    fun staticWebpIsNotAnimated() {
        assertFalse(WebpInfo.isAnimated(header("VP8X", 0x10)))
        assertFalse(WebpInfo.isAnimated(header("VP8 ", 0x02)))
        assertTrue(WebpInfo.isWebp(header("VP8L", 0)))
    }

    @Test
    fun notAWebp() {
        assertFalse(WebpInfo.isWebp(ByteArray(32)))
    }
}
