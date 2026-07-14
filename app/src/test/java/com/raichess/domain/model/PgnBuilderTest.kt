package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PgnBuilderTest {

    @Test
    fun `builds headers and numbered movetext`() {
        val pgn = PgnBuilder.build(
            sanMoves = listOf("e4", "e5", "Nf3", "Nc6", "Bb5"),
            result = GameResult.WIN,
            playerColor = PlayerColor.WHITE,
            opponentElo = 1500,
            datePlayed = 0L
        )
        assertTrue(pgn.contains("[Event \"RaiChess\"]"))
        assertTrue(pgn.contains("[White \"Player\"]"))
        assertTrue(pgn.contains("[Black \"RaiChess AI (1500)\"]"))
        assertTrue(pgn.contains("[Result \"1-0\"]"))
        assertTrue(pgn.contains(Regex("\\[Date \"\\d{4}\\.\\d{2}\\.\\d{2}\"\\]")))
        assertTrue(pgn.endsWith("1. e4 e5 2. Nf3 Nc6 3. Bb5 1-0"))
    }

    @Test
    fun `player as black swaps the name tags and result`() {
        val pgn = PgnBuilder.build(
            sanMoves = listOf("e4", "e5"),
            result = GameResult.LOSS,
            playerColor = PlayerColor.BLACK,
            opponentElo = 2000,
            datePlayed = 0L
        )
        assertTrue(pgn.contains("[White \"RaiChess AI (2000)\"]"))
        assertTrue(pgn.contains("[Black \"Player\"]"))
        assertTrue(pgn.contains("[Result \"1-0\"]"))
        assertTrue(pgn.endsWith("1. e4 e5 1-0"))
    }

    @Test
    fun `draw uses the draw tag`() {
        val pgn = PgnBuilder.build(
            sanMoves = listOf("e4"),
            result = GameResult.DRAW,
            playerColor = PlayerColor.WHITE,
            opponentElo = 1200,
            datePlayed = 0L
        )
        assertTrue(pgn.endsWith("1. e4 1/2-1/2"))
    }

    @Test
    fun `toPgnResult round-trips with fromPgnResult`() {
        for (result in GameResult.values()) {
            for (color in PlayerColor.values()) {
                val tag = result.toPgnResult(color)
                assertEquals(result, GameResult.fromPgnResult(tag, color))
            }
        }
    }
}
