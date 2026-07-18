package com.raichess.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.raichess.data.database.RaiChessDatabase
import com.raichess.data.database.toDomain
import com.raichess.data.database.toEntity
import com.raichess.domain.model.PracticeCategory
import com.raichess.domain.model.PracticePosition
import com.raichess.domain.model.PracticePositionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Persists practice positions extracted from play (currently:
 * Training-mode undos as MISTAKE_CORRECTION entries).
 *
 * Room-backed (practice_positions), completing the migration the
 * SharedPreferences MVP was staged for: the legacy JSON blob, if present,
 * is imported once on first access and the schema is field-for-field the
 * old [PracticePosition]. List maintenance (FEN dedup, the 200-entry cap)
 * stays in the pure, tested [PracticePositionStore].
 */
class PracticeRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = RaiChessDatabase.get(appContext).practiceDao()
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun getPositions(): List<PracticePosition> {
        importLegacyIfNeeded()
        return dao.getAll().map { it.toDomain() }
    }

    /**
     * Fire-and-forget variant of [addMistakePosition] for in-game callers:
     * runs on a repository-owned, process-lifetime scope so tearing down
     * the calling screen right after an undo can't cancel the write and
     * silently drop the observation (a viewModelScope launch would).
     */
    fun recordMistakePosition(
        fen: String,
        sourceMoveNumber: Int,
        sourceGameId: Long? = null
    ) {
        writeScope.launch {
            try {
                addMistakePosition(fen, sourceMoveNumber, sourceGameId)
            } catch (e: CancellationException) {
                throw e // never swallow cancellation
            } catch (e: Exception) {
                Log.w(TAG, "failed to record mistake position", e)
            }
        }
    }

    /**
     * Record the position a Training-mode undo rolled back to — the board
     * as it stood before the player's mistaken move.
     *
     * @param sourceGameId row id in the games table, when known. Undo-time
     *   captures pass null: the game row is only created at game over.
     */
    suspend fun addMistakePosition(
        fen: String,
        sourceMoveNumber: Int,
        sourceGameId: Long? = null
    ) {
        importLegacyIfNeeded()
        val position = PracticePosition(
            id = UUID.randomUUID().toString(),
            sourceGameId = sourceGameId,
            sourceMoveNumber = sourceMoveNumber,
            fen = fen,
            category = PracticeCategory.MISTAKE_CORRECTION,
            createdAt = System.currentTimeMillis()
        )
        writeMutex.withLock {
            val updated = PracticePositionStore.withPosition(
                dao.getAll().map { it.toDomain() },
                position
            )
            dao.replaceAll(updated.map { it.toEntity() })
        }
    }

    /**
     * Record one practice attempt for a drill (spaced-repetition inputs:
     * attempt count, running success rate, recency). Upserts by [drillId] —
     * puzzle drills use "puzzle:<lichessId>", mistake drills
     * "mistake:<gameId>:<ply>" — so progress rows exist only for material
     * the player has actually attempted. Deliberately outside the 200-row
     * mistake-capture cap: [PracticePositionStore.withPosition] maintains
     * that list; this is per-drill bookkeeping.
     */
    suspend fun recordDrillResult(drillId: String, fen: String, solved: Boolean) {
        importLegacyIfNeeded()
        writeMutex.withLock {
            val now = System.currentTimeMillis()
            val previous = dao.getAll().map { it.toDomain() }.firstOrNull { it.id == drillId }
            val updated = if (previous != null) {
                previous.copy(
                    timesPracticed = previous.timesPracticed + 1,
                    successRate = (previous.successRate * previous.timesPracticed +
                        (if (solved) 1.0 else 0.0)) / (previous.timesPracticed + 1),
                    lastPracticed = now
                )
            } else {
                PracticePosition(
                    id = drillId,
                    sourceGameId = null,
                    sourceMoveNumber = 0,
                    fen = fen,
                    category = PracticeCategory.TACTICS,
                    timesPracticed = 1,
                    successRate = if (solved) 1.0 else 0.0,
                    lastPracticed = now,
                    createdAt = now
                )
            }
            dao.insertAll(listOf(updated.toEntity())) // REPLACE conflict = upsert
        }
    }

    /** Stored drill/practice progress keyed by id, for queue building. */
    suspend fun progressById(): Map<String, PracticePosition> =
        getPositions().associateBy { it.id }

    /**
     * One-time import of the pre-Room SharedPreferences store. The flag is
     * set even when the blob is absent or corrupt — either way there will
     * never be anything (more) to import.
     */
    private suspend fun importLegacyIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        writeMutex.withLock {
            if (prefs.getBoolean(KEY_MIGRATED, false)) return
            val json = prefs.getString(KEY_POSITIONS, null)
            if (json != null) {
                val legacy = try {
                    val listType = object : TypeToken<List<PracticePosition>>() {}.type
                    Gson().fromJson<List<PracticePosition>>(json, listType) ?: emptyList()
                } catch (e: JsonParseException) {
                    // Fail closed on a corrupt blob, exactly as the old store did
                    emptyList()
                }
                if (legacy.isNotEmpty()) {
                    dao.insertAll(legacy.map { it.toEntity() })
                }
            }
            prefs.edit()
                .putBoolean(KEY_MIGRATED, true)
                .remove(KEY_POSITIONS)
                .apply()
        }
    }

    companion object {
        private const val TAG = "PracticeRepository"
        private const val PREFS_NAME = "raichess_practice"
        private const val KEY_POSITIONS = "positions"
        private const val KEY_MIGRATED = "migrated_to_room"

        // Process-lifetime so recordMistakePosition writes survive the
        // teardown of whichever screen triggered them.
        private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Companion-scoped (not per-instance) so read-merge-write updates
        // stay serialized even if a second screen ever constructs its own
        // PracticeRepository — all instances share the singleton database.
        private val writeMutex = Mutex()
    }
}
