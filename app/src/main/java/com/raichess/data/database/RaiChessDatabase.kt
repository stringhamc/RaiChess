package com.raichess.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * App database: game history, per-move analysis, and practice positions —
 * the storage layer of the coaching roadmap (TECHNICAL_PLAN.md Phase 1/3).
 *
 * NOTE for the next schema bump: there is deliberately no
 * fallbackToDestructiveMigration() — this data is the user's game history,
 * so version 2 must ship a real Migration or the app will crash on update.
 */
@Database(
    entities = [GameEntity::class, PositionEntity::class, PracticePositionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RaiChessDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao
    abstract fun practiceDao(): PracticeDao

    companion object {
        @Volatile private var instance: RaiChessDatabase? = null

        fun get(context: Context): RaiChessDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RaiChessDatabase::class.java,
                    "raichess.db"
                ).build().also { instance = it }
            }
    }
}
