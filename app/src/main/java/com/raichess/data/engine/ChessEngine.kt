package com.raichess.data.engine

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move

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
     */
    fun selectMove(board: Board): Move?

    /** Release any held resources (e.g. a WebView). No-op by default. */
    fun close() {}
}
