package com.raichess.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * App database: game history, per-move analysis, and practice positions —
 * the storage layer of the coaching roadmap (TECHNICAL_PLAN.md Phase 1/3).
 *
 * NOTE for every schema bump: there is deliberately no
 * fallbackToDestructiveMigration() — this data is the user's game history,
 * so each new version must ship a real Migration or the app will crash on
 * update. v2: positions.acceptableMoves (drill alternatives).
 */
@Database(
    entities = [GameEntity::class, PositionEntity::class, PracticePositionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class RaiChessDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao
    abstract fun practiceDao(): PracticeDao

    companion object {
        @Volatile private var instance: RaiChessDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE positions ADD COLUMN acceptableMoves TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun get(context: Context): RaiChessDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RaiChessDatabase::class.java,
                    "raichess.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
