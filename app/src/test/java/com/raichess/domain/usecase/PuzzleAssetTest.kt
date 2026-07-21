package com.raichess.domain.usecase

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.MoveGenerator
import com.raichess.domain.model.Puzzle
import com.raichess.domain.model.PuzzleCsv
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Replays every puzzle bundled in the asset. All lines must be fully
 * legal. Seed puzzles (id "seed_*") additionally must be provably sound
 * without an engine: forced opponent replies and an ending that mates or
 * wins decisive material. Real Lichess puzzles are engine-verified
 * upstream and legitimately contain non-forced replies and positional
 * wins, so they only get the legality check.
 */
class PuzzleAssetTest {

    private fun loadAsset(): List<Puzzle> {
        // Unit tests run from the module (or repo root on some runners)
        val candidates = listOf(
            File("src/main/assets/puzzles/puzzles.csv"),
            File("app/src/main/assets/puzzles/puzzles.csv")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: fail("puzzle asset not found from ${File(".").absolutePath}").let { return emptyList() }
        return PuzzleCsv.parse(file.readText())
    }

    private fun materialCp(board: Board, side: Side): Int {
        var total = 0
        for (i in 0 until 64) {
            val piece = board.getPiece(Square.squareAt(i))
            if (piece == Piece.NONE || piece.pieceSide != side) continue
            total += when (piece.pieceType) {
                PieceType.PAWN -> 100
                PieceType.KNIGHT -> 300
                PieceType.BISHOP -> 320
                PieceType.ROOK -> 500
                PieceType.QUEEN -> 900
                else -> 0
            }
        }
        return total
    }

    private fun diffFor(board: Board, side: Side): Int =
        materialCp(board, side) -
            materialCp(board, if (side == Side.WHITE) Side.BLACK else Side.WHITE)

    @Test
    fun `asset loads and is non-trivial`() {
        assertTrue(loadAsset().size >= 20)
    }

    @Test
    fun `every bundled puzzle line is legal, and seed lines are sound`() {
        for (puzzle in loadAsset()) {
            val strict = puzzle.id.startsWith("seed_")
            val board = Board().apply { loadFromFen(puzzle.fen) }
            val solver = if (board.sideToMove == Side.WHITE) Side.BLACK else Side.WHITE
            var afterSetupDiff = 0
            puzzle.moves.forEachIndexed { i, lan ->
                val move = GameAnalyzer.lanToLegalMove(board, lan)
                    ?: fail("${puzzle.id}: illegal move #$i $lan in ${board.fen}").let { return }
                if (strict && i >= 2 && i % 2 == 0) {
                    val legal = MoveGenerator.generateLegalMoves(board)
                    assertTrue(
                        "${puzzle.id}: opponent reply #$i not forced",
                        legal.size == 1 && legal[0] == move
                    )
                }
                board.doMove(move)
                if (i == 0) afterSetupDiff = diffFor(board, solver)
            }
            if (!strict || board.isMated) continue
            // Seed non-mates must win decisive material even after best reply
            assertTrue("${puzzle.id}: ends on opponent turn without mate",
                board.sideToMove != solver)
            val replies = MoveGenerator.generateLegalMoves(board)
            assertTrue("${puzzle.id}: stalemate finish", replies.isNotEmpty())
            var worst = Int.MAX_VALUE
            for (reply in replies) {
                board.doMove(reply)
                worst = minOf(worst, diffFor(board, solver))
                board.undoMove()
            }
            assertTrue(
                "${puzzle.id}: unsound, worst-case gain ${worst - afterSetupDiff}cp",
                worst - afterSetupDiff >= 250
            )
        }
    }
}
