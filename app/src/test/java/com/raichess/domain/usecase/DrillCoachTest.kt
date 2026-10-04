package com.raichess.domain.usecase

import com.raichess.domain.model.CoachPersonality
import com.raichess.domain.model.ThemeTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillCoachTest {

    @Test
    fun `miss ladder climbs try-again, guidance, piece, reveal`() {
        assertEquals(DrillCoach.Assist.NONE, DrillCoach.assistForMisses(0))
        assertEquals(DrillCoach.Assist.NONE, DrillCoach.assistForMisses(1))
        assertEquals(DrillCoach.Assist.GUIDANCE, DrillCoach.assistForMisses(2))
        assertEquals(DrillCoach.Assist.PIECE, DrillCoach.assistForMisses(3))
        assertEquals(DrillCoach.Assist.REVEAL, DrillCoach.assistForMisses(4))
        // Past the reveal it stays revealed — no wrap-around
        assertEquals(DrillCoach.Assist.REVEAL, DrillCoach.assistForMisses(7))
    }

    @Test
    fun `piece hint names the piece and its square but never the move`() {
        // Knight on g3 (a1=0..h8=63: g3 = rank 2 · file 6 = 22)
        val squares = List(64) { if (it == 22) 'N' else null }
        for (persona in CoachPersonality.entries) {
            val text = DrillCoach.pieceHint("g3e4", squares, persona)
            assertTrue(text, "knight" in text)
            assertTrue(text, "g3" in text)
            // The destination stays the player's job on this rung
            assertTrue(text, "e4" !in text)
        }
        assertEquals(22, DrillCoach.pieceHintSquare("g3e4"))
    }

    @Test
    fun `piece hint degrades gracefully off an unreadable board`() {
        val text = DrillCoach.pieceHint("g3e4", emptyList())
        assertTrue(text, "piece" in text)
        assertTrue(text, "g3" in text)
    }

    @Test
    fun `reveal names the move in arrow notation`() {
        val text = DrillCoach.reveal("a1a8")
        assertTrue(text, "a1 → a8" in text)
    }

    @Test
    fun `puzzle guidance picks the most specific theme`() {
        // mateIn2 must beat the generic mate entry when both are tagged
        val text = DrillCoach.guidance(setOf("mate", "mateIn2"), multiMove = true)
        assertTrue(text, "mate in two" in text)
    }

    @Test
    fun `puzzle guidance teaches the motif without naming the move`() {
        val text = DrillCoach.guidance(setOf("hangingPiece"), multiMove = false)
        assertTrue(text, "undefended" in text)
    }

    @Test
    fun `unmatched themes fall back by line length`() {
        val multi = DrillCoach.guidance(setOf("opening", "short"), multiMove = true)
        assertTrue(multi, "series of moves" in multi)
        val single = DrillCoach.guidance(setOf("opening", "short"), multiMove = false)
        assertTrue(single, "forcing moves" in single)
    }

    @Test
    fun `mistake guidance names the recorded punishment`() {
        val text = DrillCoach.guidance(setOf(ThemeTag.ALLOWED_TACTIC), punishLan = "f3d4")
        assertTrue(text, "f3 → d4" in text)
        // Without a recorded punisher it degrades to the general frame
        val vague = DrillCoach.guidance(setOf(ThemeTag.ALLOWED_TACTIC), punishLan = null)
        assertTrue(vague, "sidesteps" in vague)
    }

    @Test
    fun `mistake guidance ranks mates over material`() {
        val text = DrillCoach.guidance(
            setOf(ThemeTag.ALLOWED_MATE, ThemeTag.HANGING_PIECE),
            punishLan = "d8h4"
        )
        assertTrue(text, "mate" in text)
        assertTrue(text, "d8 → h4" in text)
    }

    @Test
    fun `threat clause only fires for threat-shaped mistakes`() {
        assertEquals(
            " (f3 → d4 was the threat)",
            DrillCoach.threatClause(setOf(ThemeTag.ALLOWED_TACTIC), "f3d4")
        )
        assertEquals("", DrillCoach.threatClause(setOf(ThemeTag.ALLOWED_TACTIC), null))
        // A missed capture has no incoming threat to name
        assertEquals("", DrillCoach.threatClause(setOf(ThemeTag.MISSED_CAPTURE), "f3d4"))
        // A hung piece names the capture that wins it
        assertEquals(
            " (a1 → a2 takes it)",
            DrillCoach.threatClause(setOf(ThemeTag.HANGING_PIECE), "a1a2")
        )
    }

    @Test
    fun `try-again escalates its wording on repeat misses`() {
        assertTrue(DrillCoach.tryAgain(1) != DrillCoach.tryAgain(2))
    }

    @Test
    fun `default persona keeps the original wording`() {
        // MENTOR is the default parameter AND the original voice: a player
        // who never opens the setting sees exactly the old coach
        assertEquals("Not that one — try again.", DrillCoach.tryAgain(1))
        assertEquals("Here it is: a1 → a8 — follow the arrow.", DrillCoach.reveal("a1a8"))
        assertEquals("Solved!", DrillCoach.solvedClean(streak = 1))
        assertEquals("There it is — you worked for that one.", DrillCoach.solvedEarned())
        assertEquals("It'll come back around.", DrillCoach.failed())
    }

    @Test
    fun `each personality speaks with its own voice`() {
        for (line in listOf(
            { p: CoachPersonality -> DrillCoach.tryAgain(1, p) },
            { p: CoachPersonality -> DrillCoach.reveal("a1a8", p) },
            { p: CoachPersonality -> DrillCoach.solvedClean(1, p) },
            { p: CoachPersonality -> DrillCoach.solvedEarned(p) },
            { p: CoachPersonality -> DrillCoach.failed(p) },
            { p: CoachPersonality -> DrillCoach.lineComplete(p) },
            { p: CoachPersonality -> DrillCoach.pieceHint("g3e4", emptyList(), p) }
        )) {
            val variants = CoachPersonality.entries.map { line(it) }
            assertEquals(
                "personas must not share lines: $variants",
                variants.size, variants.toSet().size
            )
        }
    }

    @Test
    fun `personality changes delivery, never the information`() {
        for (persona in CoachPersonality.entries) {
            // Every reveal names the move — no style may withhold the answer
            assertTrue(DrillCoach.reveal("a1a8", persona), "a1 → a8" in DrillCoach.reveal("a1a8", persona))
            assertTrue("b2b4 in ${persona.name}", "b2 → b4" in DrillCoach.solvedAsGood("b2b4", persona))
            // Every streak celebration carries the count
            assertTrue("streak in ${persona.name}", "4" in DrillCoach.solvedClean(4, persona))
            // Every first-miss line still escalates on repeat misses
            assertTrue(DrillCoach.tryAgain(1, persona) != DrillCoach.tryAgain(2, persona))
        }
    }

    @Test
    fun `mistake recap explains the hung piece by the capture that won it`() {
        // Ng3-e4 drops the knight to the d5 pawn; Ke2 has nothing to explain
        assertEquals(
            "In your game you played g3 → e4, which left a piece where it could be " +
                "taken for free: d5 → e4 wins your undefended knight.",
            DrillCoach.mistakeRecap(
                fen = "4k3/8/8/3p4/8/6N1/8/4K3 w - - 0 1",
                bestLan = "e1e2",
                playedLan = "g3e4",
                mistakeThemes = setOf(ThemeTag.HANGING_PIECE, ThemeTag.ENDGAME),
                punishLan = "d5e4"
            )
        )
    }

    @Test
    fun `mistake recap says why the best move works`() {
        // Untagged blunder: the best move's reason, then the reply it ran into
        assertEquals(
            "d1 → d8 wins the queen for your rook and gives check. " +
                "In your game you played e1 → e2, and d8 → d1 takes your rook and gives check.",
            DrillCoach.mistakeRecap(
                fen = "3qk3/8/8/8/8/8/8/3RK3 w - - 0 1",
                bestLan = "d1d8",
                playedLan = "e1e2",
                mistakeThemes = setOf(ThemeTag.ENDGAME),
                punishLan = "d8d1"
            )
        )
    }

    @Test
    fun `mistake recap is null when there is nothing concrete to say`() {
        assertNull(
            DrillCoach.mistakeRecap(
                fen = "4k3/8/8/8/8/8/8/4K3 w - - 0 1",
                bestLan = "e1e2",
                playedLan = "e1f2",
                mistakeThemes = setOf(ThemeTag.ENDGAME),
                punishLan = null
            )
        )
    }

    @Test
    fun `mistake recap names the tactic the game move allowed`() {
        // h2-h3 lets the a8 rook take the loose a1 rook
        assertEquals(
            "a1 → a8 wins the undefended rook and gives check. In your game you played " +
                "h2 → h3, which allowed a tactic that wins material: a8 → a1 wins your " +
                "undefended rook and gives check.",
            DrillCoach.mistakeRecap(
                fen = "r3k3/8/8/8/8/8/7P/R3K3 w - - 0 1",
                bestLan = "a1a8",
                playedLan = "h2h3",
                mistakeThemes = setOf(ThemeTag.ALLOWED_TACTIC, ThemeTag.ENDGAME),
                punishLan = "a8a1"
            )
        )
    }

    @Test
    fun `mistake recap keeps the mate threat clause instead of describing the reply`() {
        // Fool's mate: g2-g4 allows Qh4#
        val recap = DrillCoach.mistakeRecap(
            fen = "rnbqkbnr/pppp1ppp/8/4p3/8/5P2/PPPPP1PP/RNBQKBNR w KQkq - 0 2",
            bestLan = "e2e4",
            playedLan = "g2g4",
            mistakeThemes = setOf(ThemeTag.ALLOWED_MATE, ThemeTag.OPENING),
            punishLan = "d8h4"
        )
        assertEquals(
            "In your game you played g2 → g4, which gave the opponent a forced mate " +
                "(d8 → h4 was the threat).",
            recap
        )
    }

    @Test
    fun `field report recap makes no hung-queen claim when the engine declines the capture`() {
        // ...Qa2 isn't tagged once the engine's reply (f2-f4) doesn't take
        // the queen, and that quiet reply has nothing to describe
        assertEquals(
            "d5 → a2 attacks the rook on a1.",
            DrillCoach.mistakeRecap(
                fen = "1r5r/p1p1k1pp/2n1bp2/3qP3/Q2P2P1/P1pP3P/4PP1N/RN2KBR1 b - - 0 25",
                bestLan = "d5a2",
                playedLan = "c6d4",
                mistakeThemes = setOf(ThemeTag.MIDDLEGAME),
                punishLan = "f2f4"
            )
        )
    }
}
