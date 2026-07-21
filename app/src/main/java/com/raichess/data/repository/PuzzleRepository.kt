package com.raichess.data.repository

import android.content.Context
import android.util.Log
import com.raichess.domain.model.Puzzle
import com.raichess.domain.model.PuzzleCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads the bundled puzzle set (Lichess CSV schema, see [PuzzleCsv]) from
 * assets. The seed asset ships a small machine-verified set; replace or
 * extend it with real Lichess puzzles via tools/fetch_lichess_puzzles.py —
 * the format is identical, so nothing here changes.
 */
class PuzzleRepository(context: Context) {

    private val appContext = context.applicationContext

    @Volatile private var cache: List<Puzzle>? = null
    private val loadMutex = Mutex()

    suspend fun getPuzzles(): List<Puzzle> {
        cache?.let { return it }
        // Mutex so concurrent first callers parse the asset once, not twice
        return loadMutex.withLock {
            cache ?: withContext(Dispatchers.IO) {
                try {
                    appContext.assets.open(ASSET_PATH)
                        .bufferedReader()
                        .use { it.readText() }
                        .let { PuzzleCsv.parse(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "failed to load puzzle asset", e)
                    emptyList()
                }
            }.also { cache = it }
        }
    }

    companion object {
        private const val TAG = "PuzzleRepository"
        private const val ASSET_PATH = "puzzles/puzzles.csv"
    }
}
