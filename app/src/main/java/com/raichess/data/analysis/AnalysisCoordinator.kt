package com.raichess.data.analysis

import android.content.Context
import android.util.Log
import com.raichess.data.engine.EngineFactory
import com.raichess.data.repository.GameRepository
import com.raichess.domain.model.CompletedGame
import com.raichess.domain.model.PlayerColor
import com.raichess.domain.usecase.GameAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the background post-game analysis work. Runs on an app-lifetime
 * scope, not a ViewModel scope, so leaving the game screen doesn't cancel
 * an in-flight analysis; if the process dies mid-run anyway, the game's
 * PENDING state survives and [analyzePendingGames] finishes the job on the
 * next launch.
 *
 * Analyses are serialized by [engineMutex]: ChessEngine is single-caller,
 * and one full-strength engine at a time is also the right battery/memory
 * behaviour on a phone.
 */
object AnalysisCoordinator {

    private const val TAG = "AnalysisCoordinator"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engineMutex = Mutex()

    /**
     * Persist a finished game and analyze it in the background. Fire and
     * forget — safe to call from the UI at game over.
     */
    fun saveAndAnalyze(context: Context, game: CompletedGame) {
        val appContext = context.applicationContext
        scope.launch {
            val repository = GameRepository(appContext)
            try {
                val gameId = repository.saveCompletedGame(game)
                analyzeGame(appContext, repository, gameId)
            } catch (e: Exception) {
                // Never let a persistence hiccup surface at game over; the
                // ELO update has its own (SharedPreferences) path.
                Log.w(TAG, "failed to save finished game", e)
            }
        }
    }

    /** Drain games whose analysis never completed (app killed mid-run, ...). */
    fun analyzePendingGames(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val repository = GameRepository(appContext)
            try {
                repository.pendingAnalysisGameIds()
                    .forEach { analyzeGame(appContext, repository, it) }
            } catch (e: Exception) {
                Log.w(TAG, "pending-analysis sweep failed", e)
            }
        }
    }

    private suspend fun analyzeGame(
        appContext: Context,
        repository: GameRepository,
        gameId: Long
    ) {
        engineMutex.withLock {
            val game = repository.getGame(gameId) ?: return
            val movesLan = game.movesLan.split(' ').filter { it.isNotBlank() }
            val playerIsWhite = game.playerColor == PlayerColor.WHITE.name

            val engine = EngineFactory.createAnalyzer(appContext)
            try {
                val report = GameAnalyzer(engine).analyze(movesLan, playerIsWhite)
                if (report != null) {
                    repository.recordAnalysis(gameId, report)
                    Log.i(TAG, "analyzed game $gameId: accuracy=${"%.1f".format(report.accuracy)}")
                } else {
                    repository.markAnalysisFailed(gameId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "analysis failed for game $gameId", e)
                try {
                    repository.markAnalysisFailed(gameId)
                } catch (persist: Exception) {
                    Log.w(TAG, "could not mark game $gameId failed", persist)
                }
            } finally {
                engine.close()
            }
        }
    }
}
