package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PuzzleCsvTest {

    private val line =
        "00sHx,q3k1nr/1pp1nQpp/3p4/1P2p3/4P3/B2P4/P1P3PP/RNB1K2R b KQ - 1 5," +
            "e8d7 a2e6 d7d8 f7f8,1760,80,83,72,mate mateIn2 middlegame short," +
            "https://lichess.org/yyznGmXs/black#10,Italian_Game"

    @Test
    fun `parses a real lichess row`() {
        val p = PuzzleCsv.parseLine(line)!!
        assertEquals("00sHx", p.id)
        assertEquals("q3k1nr/1pp1nQpp/3p4/1P2p3/4P3/B2P4/P1P3PP/RNB1K2R b KQ - 1 5", p.fen)
        assertEquals(listOf("e8d7", "a2e6", "d7d8", "f7f8"), p.moves)
        assertEquals(1760, p.rating)
        assertEquals(setOf("mate", "mateIn2", "middlegame", "short"), p.themes)
        assertEquals(2, p.playerMoveCount)
    }

    @Test
    fun `parses a file and skips the header and blank lines`() {
        val csv = "PuzzleId,FEN,Moves,Rating,RatingDeviation,Popularity,NbPlays,Themes,GameUrl,OpeningTags\n" +
            line + "\n\n"
        assertEquals(1, PuzzleCsv.parse(csv).size)
    }

    @Test
    fun `rejects malformed rows`() {
        assertNull(PuzzleCsv.parseLine(""))
        assertNull(PuzzleCsv.parseLine("id,fen,e2e4,notanumber,1,1,1,themes,,"))
        // odd-length line (ends on an opponent move)
        assertNull(PuzzleCsv.parseLine("id,fen,e2e4 e7e5 g1f3,1000,1,1,1,t,,"))
        // missing fields
        assertNull(PuzzleCsv.parseLine("id,fen,e2e4 e7e5"))
    }

    @Test
    fun `handles quoted fields`() {
        val quoted = "id,\"fen with, comma\",e2e4 e7e5,900,1,1,1,tactics,,"
        val p = PuzzleCsv.parseLine(quoted)!!
        assertEquals("fen with, comma", p.fen)
    }

    @Test
    fun `moves are normalized to lowercase`() {
        val p = PuzzleCsv.parseLine("id,fen,E2E4 E7E8Q,900,1,1,1,promotion,,")!!
        assertEquals(listOf("e2e4", "e7e8q"), p.moves)
    }
}
