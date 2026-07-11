package com.raichess.ui.game

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exercises the chesslib-level mechanics that GameViewModel.undoMove()
 * relies on — reverting two plies on the live Board and rebuilding the
 * MoveList from the remaining moves — without needing the Android
 * ViewModel (so it runs in plain testDebugUnitTest, like RaiEngineTest).
 *
 * The rebuild uses a fresh MoveList exactly as the ViewModel does:
 * MoveList().apply { remaining.forEach { add(it) } }. chesslib's MoveList
 * starts dirty and add() keeps it dirty, so toSanArray() recomputes from
 * the standard start position — the reason the rebuild yields correct SAN.
 */
class UndoMechanicsTest {

    /** Play a standard-start SAN line and return the Move objects in order. */
    private fun movesFromSan(san: String): List<Move> =
        MoveList().apply { loadFromSan(san) }.toList()

    /** Reproduce the ViewModel's undo: pop two plies, rebuild the MoveList. */
    private fun undoTwice(board: Board, moveList: MoveList): MoveList {
        board.undoMove()
        board.undoMove()
        val remaining = moveList.toList().dropLast(2)
        return MoveList().apply { remaining.forEach { add(it) } }
    }

    private fun playAll(moves: List<Move>): Pair<Board, MoveList> {
        val board = Board()
        val moveList = MoveList()
        for (move in moves) {
            board.doMove(move)
            moveList.add(move)
        }
        return board to moveList
    }

    @Test
    fun `undo restores board and san to the position two plies earlier`() {
        val moves = movesFromSan("e4 e5 Nf3 Nc6 Bb5 a6")
        val (board, moveList) = playAll(moves)

        val reference = Board()
        moves.dropLast(2).forEach { reference.doMove(it) }
        val referenceSan = MoveList().apply { moves.dropLast(2).forEach { add(it) } }
            .toSanArray().toList()

        val rebuilt = undoTwice(board, moveList)

        assertEquals(reference.fen, board.fen)
        assertEquals(4, rebuilt.size)
        assertEquals(referenceSan, rebuilt.toSanArray().toList())
    }

    @Test
    fun `undo correctly reverses a castling move`() {
        // Ends with white kingside castling; undo must revert O-O + the
        // preceding black move, restoring king and rook to their squares.
        val moves = movesFromSan("e4 e5 Bc4 Bc5 Nf3 Nc6 O-O")
        val (board, moveList) = playAll(moves)

        val reference = Board()
        moves.dropLast(2).forEach { reference.doMove(it) }

        val rebuilt = undoTwice(board, moveList)

        // reference.fen has the king on e1 and kingside rook on h1;
        // matching it proves the castling was fully reverted
        assertEquals(reference.fen, board.fen)
        assertEquals(5, rebuilt.size)
    }

    @Test
    fun `repeated undos stay consistent`() {
        val moves = movesFromSan("e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7")
        val (board, moveList) = playAll(moves)

        var currentList = undoTwice(board, moveList)
        // A second undo from the new state
        currentList = undoTwice(board, currentList)

        val reference = Board()
        moves.dropLast(4).forEach { reference.doMove(it) }

        assertEquals(reference.fen, board.fen)
        assertEquals(6, currentList.size)
        assertEquals(
            MoveList().apply { moves.dropLast(4).forEach { add(it) } }.toSanArray().toList(),
            currentList.toSanArray().toList()
        )
    }
}
