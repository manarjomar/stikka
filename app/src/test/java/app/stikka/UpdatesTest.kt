package app.stikka

import app.stikka.domain.Updates
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(Updates.isNewer("0.5", "0.4.1"))
        assertTrue(Updates.isNewer("0.10", "0.9"))
        assertTrue(Updates.isNewer("1.0", "0.9.9"))
        assertFalse(Updates.isNewer("0.4.1", "0.4.1"))
        assertFalse(Updates.isNewer("0.4", "0.4.1"))
    }
}
