package com.raichess.domain.usecase

import com.raichess.domain.model.CoachPersonality
import com.raichess.domain.model.ThemeTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillCoachTest {

    @Test
    fun `miss ladder climbs try-again to guidance to reveal`() {
        assertEquals(DrillCoach.Assist.NONE, DrillCoach.assistForMisses(0))
        assertEquals(DrillCoach.Assist.NONE, DrillCoach.assistForMisses(1))
        assertEquals(DrillCoach.Assist.GUIDANCE, DrillCoach.assistForMisses(2))
        assertEquals(DrillCoach.Assist.REVEAL, DrillCoach.assistForMisses(3))
        // Past the reveal it stays revealed — no wrap-around
        assertEquals(DrillCoach.Assist.REVEAL, DrillCoach.assistForMisses(7))
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
            { p: CoachPersonality -> DrillCoach.lineComplete(p) }
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
}
