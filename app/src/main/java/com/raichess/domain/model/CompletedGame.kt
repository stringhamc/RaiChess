package com.raichess.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything worth persisting about a finished game, captured at the moment
 * the result is recorded. [movesLan] is kept alongside the PGN so the
 * analysis pipeline can replay the game without a PGN parser, and so stored
 * games can be re-analyzed later (e.g. at higher depth) from raw moves.
 */
data class CompletedGame(
    val pgn: String,
    /** Every ply in lowercase LAN ("e2e4"), in play order. */
    val movesLan: List<String>,
    /** Outcome from the player's perspective. */
    val result: GameResult,
    val playerColor: PlayerColor,
    val opponentElo: Int,
    val playerEloBefore: Int,
    val playerEloAfter: Int,
    val gameMode: GameMode,
    /** Training-mode undos used (0 in Rated). */
    val undoCount: Int,
    val datePlayed: Long
)

/**
 * Builds a minimal, valid PGN for a finished RaiChess game. Pure string
 * assembly — unit-testable without Android or chesslib.
 */
object PgnBuilder {

    fun build(
        sanMoves: List<String>,
        result: GameResult,
        playerColor: PlayerColor,
        opponentElo: Int,
        datePlayed: Long
    ): String {
        val resultTag = result.toPgnResult(playerColor)
        val date = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(datePlayed))
        val ai = "RaiChess AI ($opponentElo)"
        val white = if (playerColor == PlayerColor.WHITE) "Player" else ai
        val black = if (playerColor == PlayerColor.BLACK) "Player" else ai

        val moveText = buildString {
            sanMoves.forEachIndexed { index, san ->
                if (index % 2 == 0) {
                    if (index > 0) append(' ')
                    append(index / 2 + 1).append(". ")
                } else {
                    append(' ')
                }
                append(san)
            }
            if (sanMoves.isNotEmpty()) append(' ')
            append(resultTag)
        }

        return buildString {
            appendLine("[Event \"RaiChess\"]")
            appendLine("[Site \"RaiChess (offline)\"]")
            appendLine("[Date \"$date\"]")
            appendLine("[Round \"-\"]")
            appendLine("[White \"$white\"]")
            appendLine("[Black \"$black\"]")
            appendLine("[Result \"$resultTag\"]")
            appendLine()
            append(moveText)
        }
    }
}
