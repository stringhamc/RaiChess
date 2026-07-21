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

    @Query("SELECT * FROM practice_positions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PracticePositionEntity?

    @Query(
        "SELECT * FROM practice_positions WHERE category = :category " +
            "ORDER BY createdAt DESC"
    )
    suspend fun getByCategory(category: String): List<PracticePositionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(positions: List<PracticePositionEntity>)

    @Query("DELETE FROM practice_positions WHERE category = :category")
    suspend fun deleteCategory(category: String)

    /**
     * Replace one category's rows, leaving every other category alone —
     * the mistake-capture cap must never evict drill-progress rows that
     * happen to share the table. The replaced list is small by
     * construction (PracticePositionStore caps it at 200), so list-level
     * maintenance — FEN dedup, eviction — stays in the already-tested
     * [com.raichess.domain.model.PracticePositionStore] instead of being
     * reimplemented in SQL.
     */
    @Transaction
    suspend fun replaceCategory(category: String, positions: List<PracticePositionEntity>) {
        deleteCategory(category)
        insertAll(positions)
    }
}
