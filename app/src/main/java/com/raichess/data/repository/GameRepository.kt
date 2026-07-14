package com.raichess.data.repository

import android.content.Context
import com.raichess.data.database.AnalysisState
import com.raichess.data.database.GameEntity
import com.raichess.data.database.PositionEntity
import com.raichess.data.database.RaiChessDatabase
import com.raichess.domain.model.CompletedGame
import com.raichess.domain.usecase.GameAnalyzer

/**
 * Room-backed game history: finished games plus their per-move analysis.
 * Games are written immediately at game over (analysisState PENDING); the
 * analysis pass fills in positions and accuracy afterwards.
 */
class GameRepository(context: Context) {

    private val dao = RaiChessDatabase.get(context).gameDao()

    /** Persist a finished game; returns its row id for the analysis pass. */
    suspend fun saveCompletedGame(game: CompletedGame): Long =
        dao.insertGame(
            GameEntity(
                pgn = game.pgn,
                movesLan = game.movesLan.joinToString(" "),
                datePlayed = game.datePlayed,
                result = game.result.toPgnResult(game.playerColor),
                playerColor = game.playerColor.name,
                opponentElo = game.opponentElo,
                playerEloBefore = game.playerEloBefore,
                playerEloAfter = game.playerEloAfter,
                gameMode = game.gameMode.name,
                undoCount = game.undoCount,
                accuracy = null,
                analysisState = AnalysisState.PENDING,
                createdAt = System.currentTimeMillis()
            )
        )

    suspend fun getGame(gameId: Long): GameEntity? = dao.getGame(gameId)

    suspend fun recentGames(limit: Int = 50): List<GameEntity> = dao.recentGames(limit)

    suspend fun positionsForGame(gameId: Long): List<PositionEntity> =
        dao.positionsForGame(gameId)

    /** Games saved but not yet (successfully) analyzed, oldest first. */
    suspend fun pendingAnalysisGameIds(): List<Long> = dao.pendingAnalysisGameIds()

    suspend fun recordAnalysis(gameId: Long, report: GameAnalyzer.GameReport) {
        val rows = report.moves.map { move ->
            PositionEntity(
                gameId = gameId,
                ply = move.ply,
                fen = move.fen,
                evaluationCp = move.evaluationCp,
                bestMove = move.bestMoveLan,
                movePlayed = move.movePlayedLan,
                isPlayerMove = move.isPlayerMove,
                centipawnLoss = move.centipawnLoss,
                classification = move.classification?.name,
                analysisDepth = move.depth,
                analyzerVersion = GameAnalyzer.VERSION
            )
        }
        dao.recordAnalysis(gameId, rows, report.accuracy)
    }

    suspend fun markAnalysisFailed(gameId: Long) {
        dao.setAnalysisResult(gameId, accuracy = null, state = AnalysisState.FAILED)
    }
}
