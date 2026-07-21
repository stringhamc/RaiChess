package com.raichess.domain.usecase

import com.raichess.domain.model.PracticeCategory
import com.raichess.domain.model.PracticePosition
import com.raichess.domain.model.Puzzle
import com.raichess.domain.model.ThemeTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillSelectorTest {

    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun puzzle(id: String, rating: Int, vararg themes: String) =
        Puzzle(id, "fen-$id", listOf("e2e4", "e7e5"), rating, themes.toSet())

    private fun mistake(id: String) = DrillSelector.MistakeDrill(
        id = id, fen = "fen-$id", bestMoveLan = "e2e4", playedLan = "a2a3",
        themes = setOf(ThemeTag.HANGING_PIECE)
    )

    private fun progress(id: String, times: Int, rate: Double, lastMs: Long) =
        PracticePosition(
            id = id, sourceGameId = null, sourceMoveNumber = 0, fen = "f",
            category = PracticeCategory.TACTICS, timesPracticed = times,
            successRate = rate, lastPracticed = lastMs, createdAt = 0
        )

    @Test
    fun `never practiced is always due`() {
        assertTrue(DrillSelector.isDue(null, now))
        assertTrue(DrillSelector.isDue(progress("x", 1, 1.0, lastMs = now - 3 * day), now))
    }

    @Test
    fun `interval doubles with reps and halves while struggling`() {
        // 2 successful reps: interval 4d — 3d ago is not due yet
        val strong = progress("x", 2, 1.0, lastMs = now - 3 * day)
        assertTrue(!DrillSelector.isDue(strong, now))
        // same recency but failing: interval halves to 2d — due
        val weak = progress("x", 2, 0.0, lastMs = now - 3 * day)
        assertTrue(DrillSelector.isDue(weak, now))
    }

    @Test
    fun `puzzles filter to the target rating window`() {
        val queue = DrillSelector.buildQueue(
            source = DrillSelector.Source.PUZZLES,
            mistakes = emptyList(),
            puzzles = listOf(puzzle("far", 2400), puzzle("near", 900)),
            progressById = emptyMap(),
            targetRating = 800,
            weaknesses = emptyList(),
            nowMs = now
        )
        assertEquals(listOf("near"), queue.map { it.puzzle!!.id })
    }

    @Test
    fun `weakness-matched puzzles win selection when the queue is full`() {
        val queue = DrillSelector.buildQueue(
            source = DrillSelector.Source.PUZZLES,
            mistakes = emptyList(),
            puzzles = listOf(
                puzzle("closer", 800, "endgame"),
                puzzle("themed", 950, "hangingPiece")
            ),
            progressById = emptyMap(),
            targetRating = 800,
            weaknesses = listOf(ThemeTag.HANGING_PIECE),
            nowMs = now,
            limit = 1
        )
        assertEquals(listOf("themed"), queue.map { it.puzzle!!.id })
    }

    @Test
    fun `due puzzles come before recently drilled ones`() {
        val queue = DrillSelector.buildQueue(
            source = DrillSelector.Source.PUZZLES,
            mistakes = emptyList(),
            puzzles = listOf(puzzle("drilled", 800), puzzle("fresh", 850)),
            progressById = mapOf(
                "puzzle:drilled" to progress("puzzle:drilled", 3, 1.0, lastMs = now - day)
            ),
            targetRating = 800,
            weaknesses = emptyList(),
            nowMs = now
        )
        assertEquals("fresh", queue.first().puzzle!!.id)
    }

    @Test
    fun `empty rating window falls back to the whole set`() {
        val queue = DrillSelector.buildQueue(
            source = DrillSelector.Source.PUZZLES,
            mistakes = emptyList(),
            puzzles = listOf(puzzle("only", 2400)),
            progressById = emptyMap(),
            targetRating = 600,
            weaknesses = emptyList(),
            nowMs = now
        )
        assertEquals(1, queue.size)
    }

    @Test
    fun `puzzle sessions ramp from easier to harder`() {
        val queue = DrillSelector.buildQueue(
            source = DrillSelector.Source.PUZZLES,
            mistakes = emptyList(),
            puzzles = listOf(
                puzzle("hard", 950, "fork"),
                puzzle("easy", 700, "pin"),
                puzzle("mid", 800, "mateIn1")
            ),
            progressById = emptyMap(),
            targetRating = 800,
            weaknesses = emptyList(),
            nowMs = now
        )
        assertEquals(listOf("easy", "mid", "hard"), queue.map { it.puzzle!!.id })
    }

    @Test
    fun `same-motif puzzles are spaced apart`() {
        val queue = DrillSelector.buildQueue(
            source = DrillSelector.Source.PUZZLES,
            mistakes = emptyList(),
            puzzles = listOf(
                puzzle("f1", 800, "fork"), puzzle("f2", 810, "fork"),
                puzzle("p1", 820, "pin"), puzzle("p2", 830, "pin")
            ),
            progressById = emptyMap(),
            targetRating = 800,
            weaknesses = emptyList(),
            nowMs = now
        ).map { it.puzzle!! }
        assertEquals(4, queue.size)
        for (i in 1 until queue.size) {
            assertTrue(
                "repeat motif at $i: ${queue.map { it.id }}",
                queue[i].themes != queue[i - 1].themes
            )
        }
    }

    @Test
    fun `equal-priority queues are seed-stable but vary between sessions`() {
        val puzzles = ('a'..'h').map { puzzle("$it", 800, "theme-$it") }
        fun queueAt(seedMs: Long) = DrillSelector.buildQueue(
            source = DrillSelector.Source.PUZZLES,
            mistakes = emptyList(),
            puzzles = puzzles,
            progressById = emptyMap(),
            targetRating = 800,
            weaknesses = emptyList(),
            nowMs = seedMs
        ).map { it.puzzle!!.id }
        assertEquals(queueAt(now), queueAt(now))
        // 8 equal-rating puzzles: some nearby seed must produce a new order
        assertTrue((1L..5L).any { queueAt(now + it) != queueAt(now) })
    }

    @Test
    fun `mixed source interleaves mistakes and puzzles`() {
        val queue = DrillSelector.buildQueue(
            source = DrillSelector.Source.MIXED,
            mistakes = listOf(mistake("mistake:1:1"), mistake("mistake:1:5")),
            puzzles = listOf(puzzle("p1", 800), puzzle("p2", 820)),
            progressById = emptyMap(),
            targetRating = 800,
            weaknesses = emptyList(),
            nowMs = now
        )
        assertEquals(4, queue.size)
        assertTrue(queue[0].mistake != null)
        assertTrue(queue[1].puzzle != null)
        assertTrue(queue[2].mistake != null)
        assertTrue(queue[3].puzzle != null)
    }

    @Test
    fun `sources are pure and respect the limit`() {
        val queue = DrillSelector.buildQueue(
            source = DrillSelector.Source.MISTAKES,
            mistakes = (1..30).map { mistake("mistake:1:$it") },
            puzzles = emptyList(),
            progressById = emptyMap(),
            targetRating = 800,
            weaknesses = emptyList(),
            nowMs = now,
            limit = 20
        )
        assertEquals(20, queue.size)
        assertTrue(queue.all { it.mistake != null })
    }
}
