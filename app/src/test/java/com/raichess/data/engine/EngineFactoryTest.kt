package com.raichess.data.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineFactoryTest {

    @Test
    fun `stockfish is used at and above the min elo`() {
        assertTrue(EngineFactory.usesStockfish(EngineFactory.STOCKFISH_MIN_ELO))
        assertTrue(EngineFactory.usesStockfish(EngineFactory.STOCKFISH_MIN_ELO + 500))
        assertTrue(EngineFactory.usesStockfish(2800))
    }

    @Test
    fun `stockfish is not used below the min elo`() {
        assertFalse(EngineFactory.usesStockfish(EngineFactory.STOCKFISH_MIN_ELO - 1))
        assertFalse(EngineFactory.usesStockfish(800))
        assertFalse(EngineFactory.usesStockfish(400))
    }

    @Test
    fun `the maia band sits between raiengine and stockfish`() {
        // Below 1100: RaiEngine territory, no net
        assertNull(MaiaEngine.netBandFor(400))
        assertNull(MaiaEngine.netBandFor(1099))
        // 1100-1599: nearest bundled net in 100s
        assertEquals(1100, MaiaEngine.netBandFor(1100))
        assertEquals(1100, MaiaEngine.netBandFor(1149))
        assertEquals(1200, MaiaEngine.netBandFor(1150))
        assertEquals(1300, MaiaEngine.netBandFor(1300))
        assertEquals(1500, MaiaEngine.netBandFor(1500))
        // Top of the band clamps to the strongest bundled net
        assertEquals(1500, MaiaEngine.netBandFor(1599))
        // 1600+: Stockfish territory, no net
        assertNull(MaiaEngine.netBandFor(1600))
        assertNull(MaiaEngine.netBandFor(2800))
    }

    @Test
    fun `maia and stockfish bands never overlap`() {
        for (elo in 400..2800 step 50) {
            val maia = MaiaEngine.netBandFor(elo) != null
            val stockfish = EngineFactory.usesStockfish(elo)
            assertFalse("both engines claim $elo", maia && stockfish)
        }
        // And together with RaiEngine below, every ELO is covered:
        // netBandFor's range starts exactly where RaiEngine's ends
        assertEquals(EngineFactory.STOCKFISH_MIN_ELO, MaiaEngine.MAIA_MAX_ELO_EXCLUSIVE)
    }
}
