package com.raichess.domain.model

import kotlin.math.roundToInt

/**
 * The player's puzzle-solving strength, tracked separately from game ELO.
 *
 * Game ELO measures results against an engine; this rating moves with
 * each puzzle drill result instead, so drill difficulty follows the
 * skill the player actually demonstrates solving puzzles. It is seeded
 * from game ELO the first time practice runs (PlayerProfileRepository)
 * and then drifts up as puzzles get solved and down as they don't —
 * which in turn recenters DrillSelector's rating window, making sessions
 * grow in complexity with the player.
 */
object PracticeRating {

    /** Same K as games: ~16 points per even-odds solve, so the window
     *  shifts meaningfully within a session or two. */
    const val K_FACTOR = 32

    fun updated(current: Int, puzzleRating: Int, solved: Boolean): Int {
        val expected = EloCalculator.calculateExpectedScore(current, puzzleRating)
        val score = if (solved) 1.0 else 0.0
        val change = (K_FACTOR * (score - expected)).roundToInt()
        return (current + change).coerceIn(EloCalculator.MIN_ELO, EloCalculator.MAX_ELO)
    }
}
