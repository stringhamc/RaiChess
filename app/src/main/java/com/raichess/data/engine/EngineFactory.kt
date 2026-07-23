package com.raichess.data.engine

import android.content.Context
import com.raichess.data.diagnostics.EngineDiagnostics

/**
 * Chooses the opponent engine for a target ELO.
 *
 * The bundled Stockfish is strength-limited via `Skill Level` (0–20), and even
 * at Skill Level 0 it plays around a ~1300 club level — too strong to convince
 * as a true beginner. The near-random beginner band is exactly what the tunable
 * [RaiEngine] is for, so the low band uses RaiEngine and the strong band uses
 * Stockfish. The Stockfish engine also carries a RaiEngine fallback in case the
 * WASM/WebView bridge fails on a given device.
 */
object EngineFactory {

    // Skill Level 0 is roughly where Stockfish stops feeling like a beginner
    // (~1300). Below this, RaiEngine gives a more convincing weak opponent.
    const val STOCKFISH_MIN_ELO = 1350

    /** Pure band-selection predicate, extracted for unit testing. */
    fun usesStockfish(targetElo: Int): Boolean = targetElo >= STOCKFISH_MIN_ELO

    fun create(context: Context, targetElo: Int): ChessEngine {
        val stockfish = usesStockfish(targetElo)
        // Game-start header in the engine log: frames any fallback events
        // that follow (and makes "1350 → Stockfish" band routing visible)
        EngineDiagnostics.record(
            context,
            "game start: targetElo $targetElo → ${if (stockfish) "Stockfish" else "RaiEngine band"}"
        )
        return if (stockfish) {
            StockfishWasmEngine(context, targetElo, fallback = RaiEngine(targetElo))
        } else {
            RaiEngine(targetElo)
        }
    }

    /**
     * Full-strength analyzer for post-game analysis and coaching: Stockfish
     * with no skill limiting, falling back to RaiEngine's fixed-depth
     * analysis if the WASM/WebView bridge fails. Callers own the instance
     * and must [ChessEngine.close] it when the analysis run is done.
     */
    fun createAnalyzer(context: Context): ChessEngine =
        StockfishWasmEngine(
            context,
            targetElo = RaiEngine.MAX_ELO,
            fallback = RaiEngine(RaiEngine.MAX_ELO),
            analysisMode = true
        )
}
