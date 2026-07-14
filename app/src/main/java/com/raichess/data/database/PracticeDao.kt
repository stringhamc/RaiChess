package com.raichess.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PracticeDao {

    @Query("SELECT * FROM practice_positions ORDER BY createdAt DESC")
    suspend fun getAll(): List<PracticePositionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(positions: List<PracticePositionEntity>)

    @Query("DELETE FROM practice_positions")
    suspend fun clear()

    /**
     * Replace the whole stored list. The list is small by construction
     * (PracticePositionStore caps it at 200), so list-level maintenance —
     * FEN dedup, eviction — stays in the already-tested
     * [com.raichess.domain.model.PracticePositionStore] instead of being
     * reimplemented in SQL.
     */
    @Transaction
    suspend fun replaceAll(positions: List<PracticePositionEntity>) {
        clear()
        insertAll(positions)
    }
}
