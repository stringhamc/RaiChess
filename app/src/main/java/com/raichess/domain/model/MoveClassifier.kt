package com.raichess.domain.model

import kotlin.math.max

/**
 * Quality verdict for a single played move, per the thresholds in
 * TECHNICAL_PLAN.md §B (Game Analysis Algorithm).
 */
enum class MoveClassification {
    /** The engine's own first choice. */
    BEST,
    /** Within 0.6 pawns of best. */
    GOOD,
    /** 0.6–1.0 pawn loss. */
    INACCURACY,
    /** 1.0–3.0 pawn loss. */
    MISTAKE,
    /** 3.0+ pawn loss. */
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

    fun classify(centipawnLoss: Int, playedEngineBest: Boolean): MoveClassification = when {
        playedEngineBest -> MoveClassification.BEST
        centipawnLoss < INACCURACY_THRESHOLD_CP -> MoveClassification.GOOD
        centipawnLoss < MISTAKE_THRESHOLD_CP -> MoveClassification.INACCURACY
        centipawnLoss < BLUNDER_THRESHOLD_CP -> MoveClassification.MISTAKE
        else -> MoveClassification.BLUNDER
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
