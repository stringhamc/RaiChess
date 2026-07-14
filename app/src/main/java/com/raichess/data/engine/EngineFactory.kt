package com.raichess.data.engine

import android.content.Context

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
        return if (usesStockfish(targetElo)) {
            StockfishWasmEngine(context, targetElo, fallback = RaiEngine(targetElo))
        } else {
            RaiEngine(targetElo)
        }
    }
}
