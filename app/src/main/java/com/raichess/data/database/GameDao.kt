package com.raichess.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface GameDao {

    @Insert
    suspend fun insertGame(game: GameEntity): Long

    @Insert
    suspend fun insertPositions(positions: List<PositionEntity>)

    @Query("SELECT * FROM games WHERE id = :gameId")
    suspend fun getGame(gameId: Long): GameEntity?

    @Query("SELECT * FROM games ORDER BY datePlayed DESC LIMIT :limit")
    suspend fun recentGames(limit: Int): List<GameEntity>

    @Query("SELECT * FROM positions WHERE gameId = :gameId ORDER BY ply")
    suspend fun positionsForGame(gameId: Long): List<PositionEntity>

    /** Oldest-first so an interrupted backlog drains in play order. */
    @Query("SELECT id FROM games WHERE analysisState = 'PENDING' ORDER BY datePlayed ASC")
    suspend fun pendingAnalysisGameIds(): List<Long>

    @Query("UPDATE games SET accuracy = :accuracy, analysisState = :state WHERE id = :gameId")
    suspend fun setAnalysisResult(gameId: Long, accuracy: Double?, state: String)

    /** Clear any half-written rows from a previous attempt, then store the new ones. */
    @Query("DELETE FROM positions WHERE gameId = :gameId")
    suspend fun deletePositionsForGame(gameId: Long)

    @Transaction
    suspend fun recordAnalysis(gameId: Long, positions: List<PositionEntity>, accuracy: Double?) {
        deletePositionsForGame(gameId)
        insertPositions(positions)
        setAnalysisResult(gameId, accuracy, AnalysisState.DONE)
    }
}
