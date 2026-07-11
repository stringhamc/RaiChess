package com.raichess.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.raichess.domain.model.PracticeCategory
import com.raichess.domain.model.PracticePosition
import com.raichess.domain.model.PracticePositionStore
import java.util.UUID

/**
 * Persists practice positions extracted from play (currently:
 * Training-mode undos as MISTAKE_CORRECTION entries).
 *
 * SharedPreferences + Gson for the MVP; the PracticePosition schema
 * mirrors the practice_positions table in TECHNICAL_PLAN.md so the
 * planned Room migration is a field-for-field mapping.
 */
class PracticeRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<PracticePosition>>() {}.type

    fun getPositions(): List<PracticePosition> {
        val json = prefs.getString(KEY_POSITIONS, null) ?: return emptyList()
        return try {
            gson.fromJson<List<PracticePosition>>(json, listType) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            emptyList()
        }
    }

    /**
     * Record the position a Training-mode undo rolled back to — the board
     * as it stood before the player's mistaken move.
     */
    fun addMistakePosition(fen: String, sourceMoveNumber: Int) {
        val position = PracticePosition(
            id = UUID.randomUUID().toString(),
            sourceMoveNumber = sourceMoveNumber,
            fen = fen,
            category = PracticeCategory.MISTAKE_CORRECTION,
            createdAt = System.currentTimeMillis()
        )
        val updated = PracticePositionStore.withPosition(getPositions(), position)
        prefs.edit().putString(KEY_POSITIONS, gson.toJson(updated, listType)).apply()
    }

    companion object {
        private const val PREFS_NAME = "raichess_practice"
        private const val KEY_POSITIONS = "positions"
    }
}
