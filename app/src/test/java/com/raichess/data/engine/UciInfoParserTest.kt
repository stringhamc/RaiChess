package com.raichess.data.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UciInfoParserTest {

    @Test
    fun `parses a standard cp info line with pv`() {
        val info = UciInfoParser.parse(
            "info depth 18 seldepth 24 multipv 1 score cp 34 nodes 123456 nps 987654 time 1000 pv e2e4 e7e5 g1f3"
        )!!
        assertEquals(18, info.depth)
        assertEquals(1, info.multipv)
        assertEquals(34, info.scoreCp)
        assertNull(info.scoreMate)
        assertEquals(false, info.isBound)
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), info.pv)
    }

    @Test
    fun `parses negative cp and mate scores`() {
        val losing = UciInfoParser.parse("info depth 12 score cp -250 pv d7d5")!!
        assertEquals(-250, losing.scoreCp)

        val mating = UciInfoParser.parse("info depth 20 score mate 3 pv h5f7")!!
        assertEquals(3, mating.scoreMate)
        assertNull(mating.scoreCp)

        val mated = UciInfoParser.parse("info depth 20 score mate -2 pv e8e7")!!
        assertEquals(-2, mated.scoreMate)
    }

    @Test
    fun `flags bound scores`() {
        val lower = UciInfoParser.parse("info depth 10 score cp 50 lowerbound nodes 100")!!
        assertTrue(lower.isBound)
        val upper = UciInfoParser.parse("info depth 10 score cp -10 upperbound nodes 100")!!
        assertTrue(upper.isBound)
    }

    @Test
    fun `defaults multipv to 1 and reads explicit ranks`() {
        assertEquals(1, UciInfoParser.parse("info depth 5 score cp 0 pv e2e4")!!.multipv)
        assertEquals(
            2,
            UciInfoParser.parse("info depth 5 multipv 2 score cp -12 pv d2d4")!!.multipv
        )
    }

    @Test
    fun `ignores lines without a score`() {
        assertNull(UciInfoParser.parse("info depth 5 currmove e2e4 currmovenumber 1"))
        assertNull(UciInfoParser.parse("info string NNUE evaluation disabled"))
        assertNull(UciInfoParser.parse("bestmove e2e4 ponder e7e5"))
        assertNull(UciInfoParser.parse("readyok"))
        assertNull(UciInfoParser.parse(""))
    }

    @Test
    fun `survives malformed numeric fields`() {
        // A garbled score value must not crash; without a usable score the line is dropped
        assertNull(UciInfoParser.parse("info depth x score cp abc pv e2e4"))
    }
}
