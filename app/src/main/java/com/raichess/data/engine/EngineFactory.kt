package com.raichess.data.engine

import android.content.Context

/**
 * Chooses the opponent engine for a target ELO.
 *
 * Stockfish can't genuinely play below ~1300 (its UCI_Elo floor), and the
 * near-random beginner band is exactly what the tunable [RaiEngine] is for, so
 * the low band uses RaiEngine and the strong band uses Stockfish. The
 * Stockfish engine also carries a RaiEngine fallback in case the WASM/WebView
 * bridge fails on a given device.
 */
object EngineFactory {

    const val STOCKFISH_MIN_ELO = 1300

    fun create(context: Context, targetElo: Int): ChessEngine {
        return if (targetElo >= STOCKFISH_MIN_ELO) {
            StockfishWasmEngine(context, targetElo, fallback = RaiEngine(targetElo))
        } else {
            RaiEngine(targetElo)
        }
    }
}
