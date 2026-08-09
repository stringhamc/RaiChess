package com.raichess.data.engine

import android.content.Context
import com.raichess.data.diagnostics.EngineDiagnostics

/**
 * Chooses the opponent engine for a target ELO. Three bands:
 *
 * - Below 1100: [RaiEngine]. The near-random beginner bot is the point —
 *   nothing else can credibly play that weak.
 * - 1100–1599: [MaiaEngine] — human-like neural nets trained on real
 *   games at each rating. CI calibration showed maia-1100 beating our
 *   synthetic "1100" 12-0 while measuring near its label; this band is
 *   where "plays like a person" matters most.
 * - 1600+: Stockfish, strength-limited via Skill Level (native binary
 *   preferred, WASM fallback).
 *
 * Every engine carries a RaiEngine fallback so play never breaks; when
 * the Maia assets aren't in the build (local dev without the CI weights
 * step) the band falls back to the pre-Maia routing.
 */
object EngineFactory {

    /** Stockfish takes over where Maia's bundled nets end. */
    const val STOCKFISH_MIN_ELO = 1600

    /**
     * The pre-Maia Stockfish floor, still used when the Maia assets are
     * absent: Skill Level 0 plays ~1300, so 1350+ went to Stockfish and
     * everything below to RaiEngine.
     */
    const val LEGACY_STOCKFISH_MIN_ELO = 1350

    /** Pure band-selection predicate, extracted for unit testing. */
    fun usesStockfish(targetElo: Int): Boolean = targetElo >= STOCKFISH_MIN_ELO

    fun create(context: Context, targetElo: Int): ChessEngine {
        val stockfish = usesStockfish(targetElo)
        val maiaBand = MaiaEngine.netBandFor(targetElo)
        val maia = maiaBand != null && MaiaEngine.isAvailable(context, maiaBand)
        // No Maia in this build: the band splits the old way (1350+
        // Stockfish, below RaiEngine)
        val legacyStockfish = !stockfish && !maia && targetElo >= LEGACY_STOCKFISH_MIN_ELO
        val native = (stockfish || legacyStockfish) && StockfishNativeEngine.isAvailable(context)

        // Game-start header in the engine log: frames any fallback events
        // that follow, and makes both the band routing and the chosen
        // backend visible
        val backend = when {
            maia -> "Maia $maiaBand"
            stockfish || legacyStockfish ->
                if (native) "Stockfish (native)" else "Stockfish (wasm)"
            else -> "RaiEngine band"
        }
        EngineDiagnostics.record(context, "game start: targetElo $targetElo → $backend")

        return when {
            maia -> MaiaEngine(context, maiaBand!!, fallback = RaiEngine(targetElo))
            stockfish || legacyStockfish ->
                if (native) {
                    StockfishNativeEngine(context, targetElo, fallback = RaiEngine(targetElo))
                } else {
                    StockfishWasmEngine(context, targetElo, fallback = RaiEngine(targetElo))
                }
            else -> RaiEngine(targetElo)
        }
    }

    /**
     * Full-strength analyzer for post-game analysis and coaching: always
     * Stockfish (never Maia — a policy net is a move-picker, not an
     * analyst), with RaiEngine's fixed-depth analysis as the last resort.
     * Callers own the instance and must [ChessEngine.close] it.
     */
    fun createAnalyzer(context: Context): ChessEngine =
        if (StockfishNativeEngine.isAvailable(context)) {
            StockfishNativeEngine(
                context,
                targetElo = RaiEngine.MAX_ELO,
                fallback = RaiEngine(RaiEngine.MAX_ELO),
                analysisMode = true
            )
        } else {
            StockfishWasmEngine(
                context,
                targetElo = RaiEngine.MAX_ELO,
                fallback = RaiEngine(RaiEngine.MAX_ELO),
                analysisMode = true
            )
        }
}
