package com.raichess.data.engine

import org.junit.Assert.assertFalse
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
    fun `raiengine is used below the min elo`() {
        assertFalse(EngineFactory.usesStockfish(EngineFactory.STOCKFISH_MIN_ELO - 1))
        assertFalse(EngineFactory.usesStockfish(800))
        assertFalse(EngineFactory.usesStockfish(400))
    }
}
