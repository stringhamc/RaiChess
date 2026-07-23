package com.raichess.domain.usecase

import com.raichess.domain.model.PracticePosition
import com.raichess.domain.model.Puzzle
import com.raichess.domain.model.ThemeTag
import kotlin.random.Random

/**
 * Pure queue-building policy for the practice screen (Phase D of the
 * coaching roadmap): what to drill, in what order.
 *
 * Two sources, mixable:
 *  - the player's own analyzed mistakes (spaced repetition: overdue and
 *    never-practiced first)
 *  - Lichess-format puzzles: SELECTED near the target rating (the
 *    adaptive practice rating, see PracticeRating), due and
 *    weakness-matched first with a seeded shuffle so equal candidates
 *    rotate between sessions; then ORDERED as a session ramp — easiest
 *    to hardest, same-motif puzzles spaced apart
 *
 * Never persisted — recomputed per session from stored observations, the
 * same recompute-don't-migrate principle as WeaknessProfiler.
 */
object DrillSelector {

    enum class Source { MIXED, MISTAKES, PUZZLES, LESSON }

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

    /**
     * Lesson difficulty window, skewed upward: lessons exist to stretch,
     * so at-or-above-level material beats comfortable repetition.
     */
    private const val LESSON_WINDOW_BELOW = 150
    private const val LESSON_WINDOW_ABOVE = 400

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

    /** Lichess themes matching each game-phase tag. */
    val PHASE_TO_LICHESS_THEMES: Map<ThemeTag, Set<String>> = mapOf(
        ThemeTag.OPENING to setOf("opening"),
        ThemeTag.MIDDLEGAME to setOf("middlegame"),
        ThemeTag.ENDGAME to setOf(
            "endgame", "rookEndgame", "pawnEndgame",
            "knightEndgame", "bishopEndgame", "queenEndgame"
        )
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
     * @param targetRating center of the puzzle difficulty window — the
     *   player's practice rating, which adapts to drill results
     * @param weaknesses the player's worst themes, worst first
     * @param weakPhases game phases ranked by mistake frequency, worst
     *   first; only the top phase influences selection
     * @param nowMs drives due-ness and seeds the session shuffle
     */
    fun buildQueue(
        source: Source,
        mistakes: List<MistakeDrill>,
        puzzles: List<Puzzle>,
        progressById: Map<String, PracticePosition>,
        targetRating: Int,
        weaknesses: List<ThemeTag>,
        nowMs: Long,
        limit: Int = 20,
        weakPhases: List<ThemeTag> = emptyList()
    ): List<Drill> {
        val mistakeQueue = orderMistakes(mistakes, progressById, nowMs)
            .map { Drill(mistake = it) }
        val puzzleQueue =
            orderPuzzles(puzzles, progressById, targetRating, weaknesses, weakPhases, nowMs, limit)
                .map { Drill(puzzle = it) }
        val queue = when (source) {
            Source.MISTAKES -> mistakeQueue
            Source.PUZZLES -> puzzleQueue
            // LESSON has its own entry point (buildLessonQueue); routed
            // here it degrades to the mixed queue rather than crashing
            Source.MIXED, Source.LESSON -> interleave(mistakeQueue, puzzleQueue)
        }
        return queue.take(limit)
    }

    /**
     * Queue for one lesson (see LessonPlanner): only puzzles carrying the
     * lesson's themes, in an upward-skewed rating window, interleaved with
     * the player's own mistakes matching the lesson's weakness tag. Same
     * ramp/spacing/session-shuffle treatment as the regular queue.
     */
    fun buildLessonQueue(
        lesson: LessonPlanner.Lesson,
        mistakes: List<MistakeDrill>,
        puzzles: List<Puzzle>,
        progressById: Map<String, PracticePosition>,
        targetRating: Int,
        nowMs: Long,
        limit: Int = 20
    ): List<Drill> {
        val lessonMistakes = lesson.weaknessTheme?.let { tag ->
            mistakes.filter { tag in it.themes }
        } ?: emptyList()
        val mistakeQueue = orderMistakes(lessonMistakes, progressById, nowMs)
            .map { Drill(mistake = it) }

        val themed = puzzles.filter { p -> p.themes.any { it in lesson.themes } }
        val inWindow = themed
            .filter {
                it.rating >= targetRating - LESSON_WINDOW_BELOW &&
                    it.rating <= targetRating + LESSON_WINDOW_ABOVE
            }
            .ifEmpty { themed }
        val selected = inWindow
            .shuffled(Random(nowMs))
            .sortedByDescending { isDue(progressById["puzzle:${it.id}"], nowMs) }
            .take(limit)
        val (due, notDue) = selected.partition { isDue(progressById["puzzle:${it.id}"], nowMs) }
        val puzzleQueue = (
            spaceOutThemes(due.sortedBy { it.rating }) +
                spaceOutThemes(notDue.sortedBy { it.rating })
            ).map { Drill(puzzle = it) }

        return interleave(mistakeQueue, puzzleQueue).take(limit)
    }

    /** Due-first, then most recent mistakes first (input order preserved). */
    private fun orderMistakes(
        mistakes: List<MistakeDrill>,
        progress: Map<String, PracticePosition>,
        nowMs: Long
    ): List<MistakeDrill> =
        mistakes.sortedByDescending { isDue(progress[it.id], nowMs) }

    /**
     * Two phases. SELECT which puzzles make the session: rating-window
     * filter, then due before not-due and weakness-matched before
     * unmatched, with a seeded shuffle underneath so equal candidates
     * rotate between sessions instead of repeating the same queue.
     * ORDER the session as a ramp: easiest first, hardest last (due
     * drills ahead of not-due filler), same-motif puzzles spaced apart.
     */
    private fun orderPuzzles(
        puzzles: List<Puzzle>,
        progress: Map<String, PracticePosition>,
        targetRating: Int,
        weaknesses: List<ThemeTag>,
        weakPhases: List<ThemeTag>,
        nowMs: Long,
        limit: Int
    ): List<Puzzle> {
        val targetThemes = weaknesses
            .flatMap { WEAKNESS_TO_LICHESS_THEMES[it] ?: emptySet() }
            .toSet()
        // A player whose mistakes cluster in one phase gets that phase's
        // puzzles preferred (below weakness match — the what beats the when)
        val phaseThemes = weakPhases.firstOrNull()
            ?.let { PHASE_TO_LICHESS_THEMES[it] }
            ?: emptySet()
        // A small bundled set may have nothing inside the window; drilling
        // off-level puzzles beats an empty queue
        val inWindow = puzzles
            .filter { kotlin.math.abs(it.rating - targetRating) <= RATING_WINDOW }
            .ifEmpty { puzzles }
        // Stable sorts preserve the shuffle among equal-priority puzzles
        val selected = inWindow
            .shuffled(Random(nowMs))
            .sortedWith(
                compareByDescending<Puzzle> { isDue(progress["puzzle:${it.id}"], nowMs) }
                    .thenByDescending { it.themes.any { t -> t in targetThemes } }
                    .thenByDescending { it.themes.any { t -> t in phaseThemes } }
            )
            .take(limit)
        val (due, notDue) = selected.partition { isDue(progress["puzzle:${it.id}"], nowMs) }
        return spaceOutThemes(due.sortedBy { it.rating }) +
            spaceOutThemes(notDue.sortedBy { it.rating })
    }

    /**
     * Break up runs of identically-themed puzzles: keep input order but
     * pull the next differently-themed puzzle forward when two in a row
     * share a motif, so the session doesn't feel like the same trick
     * with slightly different furniture.
     */
    private fun spaceOutThemes(puzzles: List<Puzzle>): List<Puzzle> {
        val remaining = puzzles.toMutableList()
        val out = ArrayList<Puzzle>(puzzles.size)
        var lastThemes: Set<String>? = null
        while (remaining.isNotEmpty()) {
            val idx = remaining.indexOfFirst { it.themes != lastThemes }
            val pick = remaining.removeAt(if (idx >= 0) idx else 0)
            out.add(pick)
            lastThemes = pick.themes
        }
        return out
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
