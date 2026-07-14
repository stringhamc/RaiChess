package com.raichess.domain.usecase

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveGenerator
import com.raichess.data.engine.ChessEngine
import com.raichess.domain.model.MoveClassification
import com.raichess.domain.model.MoveClassifier
import com.raichess.domain.model.PositionAnalysis

/**
 * Post-game analysis (TECHNICAL_PLAN.md §B): replays a finished game,
 * evaluates every position with the injected engine, and grades the
 * player's moves — centipawn loss, classification, and overall accuracy.
 *
 * Pure domain logic: the engine is injected, so tests drive it with a
 * scripted fake and production wires in the Stockfish analyzer. Runs
 * synchronously on the caller's (background) thread, respecting the
 * engine's single-caller contract.
 */
class GameAnalyzer(
    private val engine: ChessEngine,
    private val moveTimeMs: Long = DEFAULT_MOVE_TIME_MS
) {

    /** The engine's verdict on one played ply. */
    data class MoveReport(
        val ply: Int,
        /** FEN before the move. */
        val fen: String,
        /** True when White was to move at [ply]. */
        val whiteToMove: Boolean,
        val movePlayedLan: String,
        val isPlayerMove: Boolean,
        /** Eval of [fen] in capped centipawns, White's perspective. */
        val evaluationCp: Int,
        val bestMoveLan: String?,
        /** Player moves only; null for the AI's plies. */
        val centipawnLoss: Int?,
        val classification: MoveClassification?,
        val depth: Int
    )

    data class GameReport(
        val moves: List<MoveReport>,
        /** Average centipawn loss over the player's moves. */
        val acpl: Double,
        /** 0–100; [MoveClassifier.accuracyFromAcpl] of [acpl]. */
        val accuracy: Double
    )

    /**
     * Analyze a game given its plies in LAN. Returns null if any move fails
     * to replay or any position fails to analyze — a partial grade would
     * misstate the player's accuracy, so the caller marks the game FAILED
     * and can retry later.
     */
    fun analyze(movesLan: List<String>, playerIsWhite: Boolean): GameReport? {
        if (movesLan.isEmpty()) return null

        // Pass 1: replay the game, evaluating each position before its move.
        data class Step(
            val fen: String,
            val whiteToMove: Boolean,
            val analysis: PositionAnalysis,
            val moveLan: String
        )

        val board = Board()
        val steps = ArrayList<Step>(movesLan.size)
        for (lan in movesLan) {
            val analysis = engine.analyze(board, moveTimeMs) ?: return null
            val move = lanToLegalMove(board, lan) ?: return null
            steps.add(Step(board.fen, board.sideToMove == Side.WHITE, analysis, lan))
            board.doMove(move)
        }

        // Eval after the final move, from the final side-to-move's view.
        // Terminal positions have no moves to search, so score them directly.
        val finalEvalStm = when {
            board.isMated -> -MoveClassifier.EVAL_CAP_CP
            board.isDraw -> 0
            else -> engine.analyze(board, moveTimeMs)?.effectiveCp() ?: return null
        }

        // Pass 2: grade each move by the eval swing it caused.
        val reports = steps.mapIndexed { i, step ->
            val evalBeforeStm = step.analysis.effectiveCp()
            // The next position's eval is from the opponent's perspective;
            // negate it back to the mover's to measure what the move cost.
            val evalAfterNextStm =
                if (i + 1 < steps.size) steps[i + 1].analysis.effectiveCp() else finalEvalStm
            val loss = MoveClassifier.centipawnLoss(evalBeforeStm, -evalAfterNextStm)

            val isPlayerMove = step.whiteToMove == playerIsWhite
            val playedBest = step.moveLan.lowercase() == step.analysis.bestMoveLan?.lowercase()

            MoveReport(
                ply = i,
                fen = step.fen,
                whiteToMove = step.whiteToMove,
                movePlayedLan = step.moveLan.lowercase(),
                isPlayerMove = isPlayerMove,
                evaluationCp = if (step.whiteToMove) evalBeforeStm else -evalBeforeStm,
                bestMoveLan = step.analysis.bestMoveLan,
                centipawnLoss = if (isPlayerMove) loss else null,
                classification = if (isPlayerMove) MoveClassifier.classify(loss, playedBest) else null,
                depth = step.analysis.depth
            )
        }

        val playerLosses = reports.mapNotNull { it.centipawnLoss }
        // No player moves (e.g. an immediate resign as Black): nothing to
        // grade, so report neutral accuracy rather than a perfect 100.
        val acpl = if (playerLosses.isEmpty()) null else playerLosses.average()
        return GameReport(
            moves = reports,
            acpl = acpl ?: 0.0,
            accuracy = acpl?.let { MoveClassifier.accuracyFromAcpl(it) } ?: NEUTRAL_ACCURACY
        )
    }

    companion object {
        /**
         * Bump when the analysis semantics change (thresholds, eval
         * conventions, tagging) so stored rows can be found and re-analyzed.
         */
        const val VERSION = 1

        /** Per-position budget: deep enough to grade honestly, ~15s for a 60-ply game. */
        const val DEFAULT_MOVE_TIME_MS = 250L

        private const val NEUTRAL_ACCURACY = 50.0

        /**
         * Resolve a LAN string against the actual legal moves (same
         * validation approach as StockfishWasmEngine.parseUciBestMove), so a
         * corrupt stored move can never be force-applied.
         */
        fun lanToLegalMove(board: Board, lan: String): Move? {
            val wanted = lan.lowercase()
            return MoveGenerator.generateLegalMoves(board)
                .firstOrNull { it.toString().lowercase() == wanted }
        }
    }
}
