package com.raichess.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Projection row for [GameDao.recentPlayerMistakes]. */
data class MistakeRow(
    val gameId: Long,
    val datePlayed: Long,
    val themes: String,
    val centipawnLoss: Int
)

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

    /**
     * Oldest-first so an interrupted backlog drains in play order. The
     * state is interpolated from [AnalysisState.PENDING] — legal in a
     * Room query because const val interpolation is a compile-time
     * constant — so a rename can't silently desync the SQL.
     */
    @Query("SELECT id FROM games WHERE analysisState = '${AnalysisState.PENDING}' ORDER BY datePlayed ASC")
    suspend fun pendingAnalysisGameIds(): List<Long>

    @Query("UPDATE games SET accuracy = :accuracy, analysisState = :state WHERE id = :gameId")
    suspend fun setAnalysisResult(gameId: Long, accuracy: Double?, state: String)

    /**
     * The player's graded mistakes across analyzed games, newest game
     * first — the observation stream the weakness profile is derived from.
     */
    @Query(
        "SELECT p.gameId AS gameId, g.datePlayed AS datePlayed, " +
            "p.themes AS themes, p.centipawnLoss AS centipawnLoss " +
            "FROM positions p JOIN games g ON p.gameId = g.id " +
            "WHERE p.isPlayerMove = 1 AND p.centipawnLoss >= :minLossCp " +
            "ORDER BY g.datePlayed DESC, p.ply ASC LIMIT :limit"
    )
    suspend fun recentPlayerMistakes(minLossCp: Int, limit: Int): List<MistakeRow>

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
