package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WinProbabilityTest {

    @Test
    fun `level position is a coin flip`() {
        assertEquals(50, WinProbability.percent(0))
    }

    @Test
    fun `curve is symmetric around fifty`() {
        for (cp in intArrayOf(50, 100, 300, 700, 1000)) {
            assertEquals(100, WinProbability.percent(cp) + WinProbability.percent(-cp))
        }
    }

    @Test
    fun `curve is monotonic and saturates near the mate cap`() {
        var previous = -1
        for (cp in -1000..1000 step 100) {
            val percent = WinProbability.percent(cp)
            assertTrue(percent >= previous)
            previous = percent
        }
        assertTrue(WinProbability.percent(1000) in 95..100)
        assertTrue(WinProbability.percent(-1000) in 0..5)
        assertTrue(WinProbability.percent(300) in 70..80)
    }
}
