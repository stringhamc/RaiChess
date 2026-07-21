package com.raichess.data.repository

import android.content.Context
import com.raichess.data.database.AnalysisState
import com.raichess.data.database.GameEntity
import com.raichess.data.database.PositionEntity
import com.raichess.data.database.RaiChessDatabase
import com.raichess.domain.model.CompletedGame
import com.raichess.domain.model.MoveClassifier
import com.raichess.domain.model.ThemeTag
import com.raichess.domain.usecase.DrillSelector
import com.raichess.domain.usecase.GameAnalyzer
import com.raichess.domain.usecase.MistakeObservation
import com.raichess.domain.usecase.WeaknessProfile
import com.raichess.domain.usecase.WeaknessProfiler

/**
 * Room-backed game history: finished games plus their per-move analysis.
 * Games are written immediately at game over (analysisState PENDING); the
 * analysis pass fills in positions and accuracy afterwards.
 */
class GameRepository(context: Context) {

    private val dao = RaiChessDatabase.get(context).gameDao()
    private val practiceDao = RaiChessDatabase.get(context).practiceDao()

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

    /** Re-queue games analyzed by an older pipeline version for re-analysis. */
    suspend fun requeueOutdatedAnalyses() = dao.requeueOutdatedAnalyses(GameAnalyzer.VERSION)

    /**
     * The player's drillable analyzed mistakes (stored best move = answer
     * key), newest games first, mapped for the practice queue.
     */
    suspend fun mistakeDrills(limit: Int = 100): List<DrillSelector.MistakeDrill> =
        dao.mistakeDrillRows(MoveClassifier.MISTAKE_THRESHOLD_CP, limit).map { row ->
            DrillSelector.MistakeDrill(
                id = "mistake:${row.gameId}:${row.ply}",
                fen = row.fen,
                bestMoveLan = row.bestMove,
                playedLan = row.movePlayed,
                themes = ThemeTag.fromCsv(row.themes)
            )
        }

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
                themes = ThemeTag.toCsv(move.themes),
                analysisDepth = move.depth,
                analyzerVersion = GameAnalyzer.VERSION
            )
        }
        dao.recordAnalysis(gameId, rows, report.accuracy)
    }

    /**
     * The player's current weakness profile, derived on demand from stored
     * mistake observations (never persisted — see WeaknessProfiler). Games
     * are ranked newest-first for the recency decay; a game with no graded
     * mistakes doesn't advance the clock, which slightly *slows* decay for
     * clean stretches — acceptable, since a clean stretch also adds no new
     * observations to outweigh.
     */
    suspend fun weaknessProfile(maxObservations: Int = 500): WeaknessProfile {
        val rows = dao.recentPlayerMistakes(MoveClassifier.MISTAKE_THRESHOLD_CP, maxObservations)
        val gameRank = rows.map { it.gameId }.distinct()
            .withIndex().associate { (rank, id) -> id to rank }
        // Drill progress closes the loop: mistakes the player has since
        // practiced to mastery weigh less (see WeaknessProfiler's discount)
        val progressById = practiceDao.getAll().associateBy { it.id }
        val observations = rows.map { row ->
            val drilled = progressById["mistake:${row.gameId}:${row.ply}"]
            MistakeObservation(
                gamesAgo = gameRank.getValue(row.gameId),
                themes = ThemeTag.fromCsv(row.themes),
                lossCp = row.centipawnLoss,
                timesDrilled = drilled?.timesPracticed ?: 0,
                drillSuccessRate = drilled?.successRate ?: 0.0
            )
        }
        return WeaknessProfiler.build(observations)
    }

    suspend fun markAnalysisFailed(gameId: Long) {
        dao.setAnalysisResult(gameId, accuracy = null, state = AnalysisState.FAILED)
    }
}
