package com.raichess.data.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import com.raichess.domain.model.PositionAnalysis

/**
 * A chess opponent. Implemented by the pure-Kotlin [RaiEngine] (weak/beginner
 * band) and the Stockfish-backed [StockfishWasmEngine] (strong play).
 *
 * The contract matches RaiEngine's original shape so the ViewModel is engine
 * agnostic: [selectMove] is synchronous (the caller runs it off the UI thread)
 * and must leave [board] in its original state.
 */
interface ChessEngine {

    /**
     * Select a move for the side to move on [board], or null if the game is
     * over (no legal moves) or no move could be produced.
     *
     * Not thread-safe: call from a single sequential caller (one move at a
     * time), off the UI thread. Implementations may hold per-call state that
     * overlapping calls would corrupt — [StockfishWasmEngine] shares one UCI
     * output queue across the call, for example. [GameViewModel] satisfies this
     * by driving the engine from a single coroutine.
     */
    fun selectMove(board: Board): Move?

    /**
     * Evaluate the position on [board] at full strength: score for the side
     * to move plus the best continuation found. Returns null when analysis
     * is unavailable (no legal moves, or the implementation cannot analyze).
     *
     * Unlike [selectMove] this is never strength-limited — it powers
     * post-game analysis and coaching, where an honest eval is the point.
     * Same threading contract as [selectMove]: single sequential caller, off
     * the UI thread, and [board] is left in its original state.
     *
     * @param moveTimeMs search budget; fixed-depth implementations may ignore it
     */
    fun analyze(board: Board, moveTimeMs: Long): PositionAnalysis? = null

    /**
     * Short human-readable label for the engine currently producing moves,
     * for a UI indicator. May change across [selectMove] calls if a stronger
     * engine degrades to a weaker one — e.g. [StockfishWasmEngine] reports
     * "Stockfish" until its WebView/WASM bridge fails, then "RaiEngine
     * (fallback)".
     */
    val activeEngineLabel: String

    /** Release any held resources (e.g. a WebView). No-op by default. */
    fun close() {}
}
