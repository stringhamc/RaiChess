package com.raichess.domain.usecase

import com.raichess.domain.model.PracticePosition
import com.raichess.domain.model.Puzzle
import com.raichess.domain.model.ThemeTag

/**
 * Pure queue-building policy for the practice screen (Phase D of the
 * coaching roadmap): what to drill, in what order.
 *
 * Two sources, mixable:
 *  - the player's own analyzed mistakes (spaced repetition: overdue and
 *    never-practiced first)
 *  - Lichess-format puzzles (near the player's rating, weakness-matched
 *    themes first, unattempted before repeats)
 *
 * Never persisted — recomputed per session from stored observations, the
 * same recompute-don't-migrate principle as WeaknessProfiler.
 */
object DrillSelector {

    enum class Source { MIXED, MISTAKES, PUZZLES }

    /** One queued drill: exactly one of [puzzle]/[mistake] is set. */
    data class Drill(
        val puzzle: Puzzle? = null,
        val mistake: MistakeDrill? = null
    )

    /** An analyzed mistake from the player's own games. */
    data class MistakeDrill(
        /** Stable id for progress tracking ("mistake:<gameId>:<ply>"). */
        val id: String,
        /** Position before the player's mistake. */
        val fen: String,
        /** The engine's move — the drill's answer key. */
        val bestMoveLan: String,
        /** The move the player actually played back then. */
        val playedLan: String,
        /**
         * Mistake themes from analysis. Not consulted by the current
         * ordering (mistakes sort by due-ness + recency only); carried so
         * a future weakness-first mistake ordering can mirror puzzles.
         */
        val themes: Set<ThemeTag>
    )

    /** Puzzles this far from the player's ELO are filtered out. */
    private const val RATING_WINDOW = 300

    /** Spaced repetition: base interval doubles with each successful rep. */
    private const val BASE_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** Lichess themes matching each of our weakness tags. */
    val WEAKNESS_TO_LICHESS_THEMES: Map<ThemeTag, Set<String>> = mapOf(
        ThemeTag.HANGING_PIECE to setOf("hangingPiece"),
        ThemeTag.MISSED_CAPTURE to setOf("hangingPiece", "crushing", "advantage"),
        ThemeTag.MISSED_MATE to setOf("mate", "mateIn1", "mateIn2"),
        ThemeTag.ALLOWED_MATE to setOf("mate", "mateIn1", "mateIn2"),
        ThemeTag.ALLOWED_TACTIC to setOf("fork", "pin", "skewer", "discoveredAttack")
    )

    /**
     * True when a stored progress row says this drill is due for review:
     * never practiced, or past its doubling interval (halved again while
     * the success rate is poor, so failed material comes back sooner).
     */
    fun isDue(progress: PracticePosition?, nowMs: Long): Boolean {
        if (progress == null) return true
        val last = progress.lastPracticed ?: return true
        var interval = BASE_INTERVAL_MS shl progress.timesPracticed.coerceAtMost(5)
        if (progress.successRate < 0.5) interval /= 2
        return nowMs - last >= interval
    }

    /**
     * Build the drill queue.
     *
     * @param mistakes the player's analyzed mistakes, most recent first
     * @param puzzles the loaded puzzle set
     * @param progressById stored practice progress keyed by drill id
     *   (mistake ids and "puzzle:<id>" for puzzles)
     * @param playerElo for puzzle difficulty filtering
     * @param weaknesses the player's worst themes, worst first
     */
    fun buildQueue(
        source: Source,
        mistakes: List<MistakeDrill>,
        puzzles: List<Puzzle>,
        progressById: Map<String, PracticePosition>,
        playerElo: Int,
        weaknesses: List<ThemeTag>,
        nowMs: Long,
        limit: Int = 20
    ): List<Drill> {
        val mistakeQueue = orderMistakes(mistakes, progressById, nowMs)
            .map { Drill(mistake = it) }
        val puzzleQueue = orderPuzzles(puzzles, progressById, playerElo, weaknesses, nowMs)
            .map { Drill(puzzle = it) }
        val queue = when (source) {
            Source.MISTAKES -> mistakeQueue
            Source.PUZZLES -> puzzleQueue
            Source.MIXED -> interleave(mistakeQueue, puzzleQueue)
        }
        return queue.take(limit)
    }

    /** Due-first, then most recent mistakes first (input order preserved). */
    private fun orderMistakes(
        mistakes: List<MistakeDrill>,
        progress: Map<String, PracticePosition>,
        nowMs: Long
    ): List<MistakeDrill> =
        mistakes.sortedByDescending { isDue(progress[it.id], nowMs) }

    /**
     * Rating-window filter, then: due before not-due, weakness-matched
     * themes before unmatched, closest rating first.
     */
    private fun orderPuzzles(
        puzzles: List<Puzzle>,
        progress: Map<String, PracticePosition>,
        playerElo: Int,
        weaknesses: List<ThemeTag>,
        nowMs: Long
    ): List<Puzzle> {
        val targetThemes = weaknesses
            .flatMap { WEAKNESS_TO_LICHESS_THEMES[it] ?: emptySet() }
            .toSet()
        // A small bundled set may have nothing inside the window; drilling
        // off-level puzzles beats an empty queue
        val inWindow = puzzles
            .filter { kotlin.math.abs(it.rating - playerElo) <= RATING_WINDOW }
            .ifEmpty { puzzles }
        return inWindow
            .sortedWith(
                compareByDescending<Puzzle> { isDue(progress["puzzle:${it.id}"], nowMs) }
                    .thenByDescending { it.themes.any { t -> t in targetThemes } }
                    .thenBy { kotlin.math.abs(it.rating - playerElo) }
            )
    }

    private fun interleave(a: List<Drill>, b: List<Drill>): List<Drill> {
        val out = ArrayList<Drill>(a.size + b.size)
        val ia = a.iterator()
        val ib = b.iterator()
        while (ia.hasNext() || ib.hasNext()) {
            if (ia.hasNext()) out.add(ia.next())
            if (ib.hasNext()) out.add(ib.next())
        }
        return out
    }
}
