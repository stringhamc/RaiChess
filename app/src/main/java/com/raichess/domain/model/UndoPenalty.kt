package com.raichess.domain.model

/**
 * Maps Training-mode assistance (undos plus revealed hint rungs — callers
 * pass the combined count) to the moveAccuracy input of
 * [EloCalculator.calculateNewElo].
 *
 * Accuracy 50 is neutral. The calculator applies
 * accuracyBonus = (accuracy - 50) / 100 (clamped to +/-0.5), weighted 0.3
 * into the game score, with K = 32. Each undo therefore costs roughly
 * 32 * 0.3 * (5 / 100) ~= 0.5 ELO, up to ~4 ELO at the floor (8+ undos).
 *
 * Known nuance inherited from the calculator: the adjusted score is
 * clamped to [0, 1], so the penalty cannot push a LOSS below 0 — undos
 * only reduce the gain from wins and draws. Undoing your way to a loss
 * already costs the full loss ELO.
 */
object UndoPenalty {
    const val NEUTRAL_ACCURACY = 50.0
    const val PENALTY_PER_UNDO = 5.0
    const val MIN_ACCURACY = 10.0

    fun accuracyAfterUndos(undoCount: Int): Double =
        (NEUTRAL_ACCURACY - PENALTY_PER_UNDO * undoCount.coerceAtLeast(0))
            .coerceAtLeast(MIN_ACCURACY)
}
