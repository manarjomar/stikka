package app.stikka

import app.stikka.domain.PixelBox
import app.stikka.domain.StickerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerMathTest {

    @Test
    fun alphaIsSoftAtTheEdge() {
        assertEquals(0, StickerMath.alphaFor(0.1f))
        assertEquals(255, StickerMath.alphaFor(0.9f))
        val mid = StickerMath.alphaFor(0.5f)
        assertTrue(mid in 120..135)
    }

    @Test
    fun boundsFindTheSubject() {
        val w = 10
        val h = 8
        val mask = FloatArray(w * h)
        mask[2 * w + 3] = 1f
        mask[5 * w + 7] = 0.8f
        assertEquals(PixelBox(3, 2, 8, 6), StickerMath.maskBounds(mask, w, h))
    }

    @Test
    fun emptyMaskHasNoBounds() {
        assertNull(StickerMath.maskBounds(FloatArray(16), 4, 4))
    }

    @Test
    fun expandStaysInsideTheImage() {
        val box = StickerMath.expand(PixelBox(0, 0, 100, 50), 100, 50, 0.1f)
        assertEquals(PixelBox(0, 0, 100, 50), box)
    }

    @Test
    fun fitKeepsAspectAndPadding() {
        val wide = StickerMath.fitInto(1000, 500)
        assertEquals(480f, wide.width, 0.01f)
        assertEquals(240f, wide.height, 0.01f)
        assertEquals(16f, wide.left, 0.01f)
        assertEquals(136f, wide.top, 0.01f)

        val tall = StickerMath.fitInto(100, 400)
        assertEquals(480f, tall.height, 0.01f)
        assertEquals(196f, tall.left, 0.01f)
    }

    @Test
    fun coverageCountsSubjectPixels() {
        val mask = floatArrayOf(1f, 0f, 0.6f, 0.2f)
        assertEquals(0.5f, StickerMath.coverage(mask), 0.001f)
    }
}
