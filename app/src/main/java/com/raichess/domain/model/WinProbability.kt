package com.raichess.domain.model

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Maps a centipawn evaluation to a winning-chances percentage for the live
 * move-rating display, using the Lichess-style logistic curve
 * (2 / (1 + e^(-0.00368208·cp)) − 1, rescaled to 0–100%). 0cp → 50%,
 * ±300cp → ~75%/25%, mate-capped ±1000cp → ~97%/3%.
 *
 * A calibration convenience for display, not a statistical claim — the
 * curve's shape (monotonic, symmetric, saturating) is what matters.
 */
object WinProbability {

    private const val SCALE = 0.00368208

    /** Win percentage 0..100 from an eval in the player's perspective. */
    fun percent(cpFromPlayerPerspective: Int): Int {
        val chances = 2.0 / (1.0 + exp(-SCALE * cpFromPlayerPerspective)) - 1.0
        return (((chances + 1.0) / 2.0) * 100.0).roundToInt().coerceIn(0, 100)
    }
}
