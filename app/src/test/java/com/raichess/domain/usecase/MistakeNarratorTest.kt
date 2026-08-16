package com.raichess.domain.usecase

import com.raichess.domain.model.ThemeTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MistakeNarratorTest {

    // White knight steps onto e4, where the d5 pawn simply takes it
    private val hangFen = "4k3/8/8/3p4/8/6N1/8/4K3 w - - 0 1"

    // White rook d1 vs black queen d8: Rxd8 is on the board
    private val captureFen = "3qk3/8/8/8/8/8/8/3RK3 w - - 0 1"

    @Test
    fun `hanging piece names the piece, its square, and the punishing capture`() {
        val text = MistakeNarrator.narrate(
            fenBefore = hangFen, moveLan = "g3e4",
            bestLan = "e1e2", replyLan = "d5e4",
            themes = setOf(ThemeTag.HANGING_PIECE, ThemeTag.ENDGAME),
            evalBeforeCp = 0, evalAfterCp = -320
        )
        assertTrue(text, "knight on e4" in text)
        assertTrue(text, "d5 → e4 wins it" in text)
        assertTrue(text, "Better was e1 → e2." in text)
    }

    @Test
    fun `allowed tactic names the reply and what it wins`() {
        // After the pawn push, the a8 rook takes the a1 rook
        val text = MistakeNarrator.narrate(
            fenBefore = "r3k3/8/8/8/8/8/7P/R3K3 w - - 0 1", moveLan = "h2h3",
            bestLan = "a1a8", replyLan = "a8a1",
            themes = setOf(ThemeTag.ALLOWED_TACTIC, ThemeTag.ENDGAME),
            evalBeforeCp = 0, evalAfterCp = -500
        )
        assertTrue(text, "runs into a8 → a1" in text)
        assertTrue(text, "winning your rook" in text)
    }

    @Test
    fun `missed capture names the prize and the move that wins it`() {
        val text = MistakeNarrator.narrate(
            fenBefore = captureFen, moveLan = "e1e2",
            bestLan = "d1d8", replyLan = null,
            themes = setOf(ThemeTag.MISSED_CAPTURE, ThemeTag.ENDGAME),
            evalBeforeCp = 900, evalAfterCp = 0
        )
        assertTrue(text, "your opponent's queen" in text)
        assertTrue(text, "d1 → d8" in text)
    }

    @Test
    fun `allowed mate names the move that starts it`() {
        val text = MistakeNarrator.narrate(
            fenBefore = hangFen, moveLan = "g3e4",
            bestLan = "e1e2", replyLan = "d5e4",
            themes = setOf(ThemeTag.ALLOWED_MATE, ThemeTag.HANGING_PIECE),
            evalBeforeCp = 0, evalAfterCp = -1000
        )
        assertTrue(text, "forced mate — d5 → e4 starts it" in text)
        assertTrue(text, "Better was e1 → e2." in text)
    }

    @Test
    fun `missed mate points at the mating start`() {
        val text = MistakeNarrator.narrate(
            fenBefore = captureFen, moveLan = "e1e2",
            bestLan = "d1d8", replyLan = null,
            themes = setOf(ThemeTag.MISSED_MATE, ThemeTag.MISSED_CAPTURE),
            evalBeforeCp = 1000, evalAfterCp = 0
        )
        assertTrue(text, "forced mate on the board — d1 → d8 starts it" in text)
    }

    @Test
    fun `positional slip talks standing and the play to focus on, never numbers`() {
        // +200 (clearly better) to -200 (clearly worse), best was Rxd8
        val text = MistakeNarrator.narrate(
            fenBefore = captureFen, moveLan = "e1e2",
            bestLan = "d1d8", replyLan = null,
            themes = setOf(ThemeTag.MIDDLEGAME),
            evalBeforeCp = 200, evalAfterCp = -200
        )
        assertEquals(
            "This handed your edge to your opponent. " +
                "The move was d1 → d8, taking the queen.",
            text
        )
        assertFalse(text, "pawns" in text)
    }

    @Test
    fun `level game collapsing to lost says so in words`() {
        val text = MistakeNarrator.narrate(
            fenBefore = captureFen, moveLan = "e1e2",
            bestLan = null, replyLan = null,
            themes = emptySet(),
            evalBeforeCp = 0, evalAfterCp = -400
        )
        assertEquals("This turned a level game into a losing one.", text)
    }

    @Test
    fun `castling best move is described as king safety`() {
        val text = MistakeNarrator.narrate(
            fenBefore = "4k3/8/8/8/8/8/8/4K2R w K - 0 1", moveLan = "h1h2",
            bestLan = "e1g1", replyLan = null,
            themes = emptySet(),
            evalBeforeCp = 200, evalAfterCp = -200
        )
        assertTrue(text, "castle" in text)
        assertTrue(text, "king to safety" in text)
    }

    @Test
    fun `malformed fen still narrates the standing shift`() {
        val text = MistakeNarrator.narrate(
            fenBefore = "not a fen", moveLan = "e2e4",
            bestLan = null, replyLan = null,
            themes = emptySet(),
            evalBeforeCp = 300, evalAfterCp = -300
        )
        assertEquals("This threw a winning position away.", text)
    }

    @Test
    fun `labels prefer the tagged pattern and fall back to the shift`() {
        assertEquals(
            "hung a piece",
            MistakeNarrator.label(setOf(ThemeTag.HANGING_PIECE), 0, -300)
        )
        assertEquals(
            "walked into mate",
            // Mate outranks the hang, matching the narration priority
            MistakeNarrator.label(setOf(ThemeTag.ALLOWED_MATE, ThemeTag.HANGING_PIECE), 0, -1000)
        )
        assertEquals(
            "gave a winning game back",
            MistakeNarrator.label(setOf(ThemeTag.MIDDLEGAME), 800, 0)
        )
        assertEquals(
            "gave up your edge",
            MistakeNarrator.label(emptySet(), 200, 0)
        )
        assertEquals(
            "let the game slip into a loss",
            MistakeNarrator.label(emptySet(), 0, -400)
        )
    }
}
