package com.raichess.domain.model

import kotlin.math.max

/**
 * Quality verdict for a single played move. Graded on BOTH centipawn loss
 * and win-probability drop (see [MoveClassifier.classify]); the labels
 * below describe the cp component near an even position.
 */
enum class MoveClassification {
    /** The engine's own first choice. */
    BEST,
    /** Close to best, or a swing that barely moves the win chances. */
    GOOD,
    /** A clear loss of ground. */
    INACCURACY,
    /** A serious error. */
    MISTAKE,
    /** A game-changing error. */
    BLUNDER
}

/**
 * Pure move-quality math shared by post-game analysis (and, later, live
 * coaching): centipawn loss, classification thresholds, and the
 * accuracy-from-ACPL formula. Kept free of Android/engine types so it is
 * trivially unit-testable and the thresholds live in exactly one place.
 */
object MoveClassifier {

    /** Mate scores and runaway evals are clamped to ±this before loss math. */
    const val EVAL_CAP_CP = 1000

    // Raised from 30 on field feedback: a move within ~half a pawn of the
    // engine's choice is close to the same score — often within the noise
    // of the coach's short searches — and flagging it read as nagging.
    // "Inaccuracy" now starts where the loss is unambiguous.
    private const val INACCURACY_THRESHOLD_CP = 60

    /** Public: ThemeTagger and the weakness profile gate on mistake-or-worse. */
    const val MISTAKE_THRESHOLD_CP = 100

    /** Public: the live coach warns at blunder-level loss. */
    const val BLUNDER_THRESHOLD_CP = 300

    /**
     * Centipawns thrown away by a move, never negative. Both arguments are
     * from the mover's perspective and already capped (see
     * [PositionAnalysis.effectiveCp]): the eval before the move, and the eval
     * of the resulting position negated back to the mover's point of view.
     */
    fun centipawnLoss(evalBeforeCp: Int, evalAfterCp: Int): Int =
        max(0, evalBeforeCp - evalAfterCp)

    /**
     * Centipawns lost by the move that turned [before] into [after], both
     * engine verdicts from their own side-to-move's perspective — so
     * [before] is from the mover's view and [after] from the opponent's,
     * and the sign flip back to the mover happens here, in exactly one
     * tested place. Used by the live coach; GameAnalyzer does the same
     * flip against terminal-position evals that have no analysis object.
     */
    fun lossBetween(before: PositionAnalysis, after: PositionAnalysis): Int =
        centipawnLoss(before.effectiveCp(), -after.effectiveCp())

    // Win-probability-drop thresholds, in percentage points (Lichess-style
    // judgment: ≥10 inaccuracy, ≥20 mistake, ≥30 blunder).
    const val INACCURACY_DROP_PP = 10
    const val MISTAKE_DROP_PP = 20
    const val BLUNDER_DROP_PP = 30

    /**
     * Percentage points of winning chances thrown away by a move, never
     * negative. Both evals from the mover's perspective, already capped.
     */
    fun winDrop(evalBeforeCp: Int, evalAfterCp: Int): Int =
        max(0, WinProbability.percent(evalBeforeCp) - WinProbability.percent(evalAfterCp))

    /**
     * Grade a move from the eval before it and the eval after it (both
     * from the mover's perspective, already capped).
     *
     * Hybrid on purpose — a move is only as bad as BOTH scales agree
     * (field feedback: a 1.2-pawn "Mistake" at move 3 of an even opening
     * read as engine preference, not error):
     * - Centipawns alone over-flag swings that barely change the outcome —
     *   openings graded by a short search, conversions in won positions,
     *   shuffling in lost ones. The win-probability drop discounts those.
     * - Win-probability alone over-flags tiny cp swings in sharp positions
     *   where the logistic curve is steep. The cp floor discounts those.
     */
    fun classify(
        evalBeforeCp: Int,
        evalAfterCp: Int,
        playedEngineBest: Boolean
    ): MoveClassification {
        if (playedEngineBest) return MoveClassification.BEST
        val cpLoss = centipawnLoss(evalBeforeCp, evalAfterCp)
        val drop = winDrop(evalBeforeCp, evalAfterCp)
        val cpSeverity = when {
            cpLoss < INACCURACY_THRESHOLD_CP -> 0
            cpLoss < MISTAKE_THRESHOLD_CP -> 1
            cpLoss < BLUNDER_THRESHOLD_CP -> 2
            else -> 3
        }
        val dropSeverity = when {
            drop < INACCURACY_DROP_PP -> 0
            drop < MISTAKE_DROP_PP -> 1
            drop < BLUNDER_DROP_PP -> 2
            else -> 3
        }
        return when (minOf(cpSeverity, dropSeverity)) {
            0 -> MoveClassification.GOOD
            1 -> MoveClassification.INACCURACY
            2 -> MoveClassification.MISTAKE
            else -> MoveClassification.BLUNDER
        }
    }

    /**
     * Accuracy percentage from average centipawn loss. Linear and deliberately
     * simple: 0 ACPL = 100%, 100 ACPL (a mistake every move) = 75%, floor at
     * 0. Feeds [EloCalculator]'s moveAccuracy input, where 50 is neutral.
     *
     * Intentionally gentler than TECHNICAL_PLAN.md's original `100 - ACPL`
     * draft, which hit 0% at one pawn of average loss — the plan doc has been
     * updated to match this coefficient.
     */
    fun accuracyFromAcpl(acplCp: Double): Double =
        max(0.0, 100.0 - acplCp * 0.25)
}
