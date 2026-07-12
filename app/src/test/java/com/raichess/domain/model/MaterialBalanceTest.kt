package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialBalanceTest {

    private fun emptyBoard(): MutableList<Char?> = MutableList(64) { null }

    private fun startingBoard(): List<Char?> {
        val board = emptyBoard()
        val back = "rnbqkbnr"
        for (file in 0..7) {
            board[file] = back[file].uppercaseChar()      // white back rank a1..h1
            board[8 + file] = 'P'                          // white pawns
            board[48 + file] = 'p'                         // black pawns
            board[56 + file] = back[file]                  // black back rank a8..h8
        }
        return board
    }

    @Test
    fun `starting position has no captures and even material`() {
        val balance = MaterialCalculator.compute(startingBoard())
        assertTrue(balance.capturedWhitePieces.isEmpty())
        assertTrue(balance.capturedBlackPieces.isEmpty())
        assertEquals(0, balance.diff)
    }

    @Test
    fun `missing black queen shows as captured and white ahead by nine`() {
        val board = startingBoard().toMutableList()
        board[59] = null // d8 queen removed
        val balance = MaterialCalculator.compute(board)
        assertEquals(listOf('q'), balance.capturedBlackPieces)
        assertTrue(balance.capturedWhitePieces.isEmpty())
        assertEquals(9, balance.diff)
    }

    @Test
    fun `captured list is ordered highest value first`() {
        val board = startingBoard().toMutableList()
        board[48] = null // a7 pawn
        board[59] = null // d8 queen
        board[57] = null // b8 knight
        val balance = MaterialCalculator.compute(board)
        assertEquals(listOf('q', 'n', 'p'), balance.capturedBlackPieces)
        assertEquals(9 + 3 + 1, balance.diff)
    }

    @Test
    fun `a promotion does not show as a captured pawn`() {
        // Turn one white pawn into a second white queen, no captures.
        val board = startingBoard().toMutableList()
        board[8] = 'Q' // a2 pawn promoted (7 white pawns, 2 white queens now)
        val balance = MaterialCalculator.compute(board)
        assertTrue(
            "promotion should not add a phantom captured pawn",
            balance.capturedWhitePieces.isEmpty()
        )
        assertTrue(balance.capturedBlackPieces.isEmpty())
        // A pawn (1) became a queen (9): net +8 for white
        assertEquals(8, balance.diff)
    }

    @Test
    fun `promotion never produces negative captured counts`() {
        val board = emptyBoard()
        board[4] = 'K'
        board[60] = 'k'
        // Two white queens via promotion, no white pawns left
        board[27] = 'Q'
        board[35] = 'Q'
        val balance = MaterialCalculator.compute(board)
        // Queen count (2) exceeds initial (1): clamped, not negative
        assertTrue(balance.capturedWhitePieces.none { it == 'Q' })
        // Diff comes from on-board material so promotions count fully
        assertEquals(18, balance.diff)
    }
}
