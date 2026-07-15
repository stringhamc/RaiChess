package com.raichess.data.analysis

import android.content.Context
import android.util.Log
import com.raichess.data.database.AnalysisState
import com.raichess.data.engine.ChessEngine
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
import java.util.concurrent.atomic.AtomicBoolean

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

    /** Max games one pending sweep re-analyzes (~15s of engine time each). */
    private const val MAX_GAMES_PER_SWEEP = 10

    // IO, not Default: analysis time is dominated by blocking waits on the
    // engine's output queue, and Default's small pool is what the live
    // game's own move search runs on — analysis must not starve it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engineMutex = Mutex()
    // The PENDING backlog only needs draining once per process; later games
    // are analyzed by their own saveAndAnalyze call.
    private val sweepRequested = AtomicBoolean(false)

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
                Log.w(TAG, "failed to save or analyze finished game", e)
            }
        }
    }

    /**
     * Drain games whose analysis never completed (app killed mid-run, ...).
     * The whole backlog shares one engine instance — a WebView-backed
     * Stockfish is expensive to spin up, so a sweep of N games must not pay
     * N init/teardown cycles.
     */
    fun analyzePendingGames(context: Context) {
        // MainActivity calls this from onCreate, which re-runs on every
        // activity re-creation (rotation, theme change) — sweep only once
        if (!sweepRequested.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            val repository = GameRepository(appContext)
            try {
                // Analyzer semantics changed (e.g. themes in v2)? Re-analyze
                // old games so the weakness profile isn't sparse for history.
                repository.requeueOutdatedAnalyses()
                val pending = repository.pendingAnalysisGameIds()
                if (pending.isEmpty()) return@launch
                // Cap the per-launch batch: a long history re-queued by an
                // analyzer bump would otherwise mean hours of background
                // engine time in one sweep. The remainder stays PENDING
                // (old rows keep serving queries until replaced) and the
                // next launch continues the drain. Fresh games are analyzed
                // by saveAndAnalyze directly, never queued behind this.
                val batch = pending.take(MAX_GAMES_PER_SWEEP)
                if (pending.size > batch.size) {
                    Log.i(TAG, "analysis backlog: ${pending.size} games, processing ${batch.size} this launch")
                }
                withAnalysisEngine(appContext) { engine ->
                    batch.forEach { analyzeGameWith(engine, repository, it) }
                }
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
        withAnalysisEngine(appContext) { engine ->
            analyzeGameWith(engine, repository, gameId)
        }
    }

    /**
     * Run [block] with a fresh full-strength engine under [engineMutex]
     * (ChessEngine is single-caller, and one engine at a time is also the
     * right battery/memory behaviour), releasing the engine afterwards.
     */
    private suspend fun withAnalysisEngine(
        appContext: Context,
        block: suspend (ChessEngine) -> Unit
    ) {
        engineMutex.withLock {
            val engine = EngineFactory.createAnalyzer(appContext)
            try {
                block(engine)
            } finally {
                engine.close()
            }
        }
    }

    /** Analyze one stored game. Caller supplies the engine and holds [engineMutex]. */
    private suspend fun analyzeGameWith(
        engine: ChessEngine,
        repository: GameRepository,
        gameId: Long
    ) {
        val game = repository.getGame(gameId) ?: return
        // Re-check state: a game saved and analyzed by saveAndAnalyze while a
        // sweep was waiting on the mutex must not be analyzed twice.
        if (game.analysisState != AnalysisState.PENDING) return
        val movesLan = game.movesLan.split(' ').filter { it.isNotBlank() }
        val playerIsWhite = game.playerColor == PlayerColor.WHITE.name

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
        }
    }
}
