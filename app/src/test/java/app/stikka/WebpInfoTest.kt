package app.stikka

import app.stikka.domain.WebpInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class WebpInfoTest {
    private fun chunk(out: ByteArrayOutputStream, tag: String, size: Int) {
        out.write(tag.toByteArray())
        out.write(byteArrayOf(size.toByte(), (size shr 8).toByte(), (size shr 16).toByte(), (size shr 24).toByte()))
        out.write(ByteArray(size + (size and 1)))
    }

    private fun webp(first: String, flags: Int, frames: Int): ByteArray {
        val body = ByteArrayOutputStream()
        body.write("WEBP".toByteArray())
        if (first == "VP8X") {
            body.write("VP8X".toByteArray())
            body.write(byteArrayOf(10, 0, 0, 0))
            body.write(byteArrayOf(flags.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0))
            if (frames > 0) chunk(body, "ANIM", 6)
            repeat(frames) { chunk(body, "ANMF", 21) }
            if (frames == 0) chunk(body, "VP8 ", 30)
        } else {
            chunk(body, first, 30)
        }
        val b = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.write(byteArrayOf(b.size.toByte(), (b.size shr 8).toByte(), 0, 0))
        out.write(b)
        return out.toByteArray()
    }

    @Test
    fun realAnimationIsAnimated() {
        val w = webp("VP8X", 0x12, 5)
        assertEquals(5, WebpInfo.frameCount(w))
        assertTrue(WebpInfo.isAnimated(w))
    }

    @Test
    fun singleFrameWithAnimFlagIsStatic() {
        assertFalse(WebpInfo.isAnimated(webp("VP8X", 0x12, 1)))
    }

    @Test
    fun plainWebpIsStatic() {
        assertFalse(WebpInfo.isAnimated(webp("VP8X", 0x10, 0)))
        assertFalse(WebpInfo.isAnimated(webp("VP8L", 0, 0)))
        assertEquals(1, WebpInfo.frameCount(webp("VP8 ", 0, 0)))
    }

    @Test
    fun notAWebp() {
        assertFalse(WebpInfo.isWebp(ByteArray(32)))
    }
}
