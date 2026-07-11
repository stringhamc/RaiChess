package com.raichess.domain.model

/**
 * Game mode selected on the setup screen.
 *
 * RATED: no takebacks; the ELO update uses neutral accuracy.
 * TRAINING: undo is allowed but tracked — each undo signals the player
 * noticed a miss/blunder only after moving, so undone positions are
 * captured for practice and the ELO update is accuracy-penalized via
 * [UndoPenalty].
 */
enum class GameMode { RATED, TRAINING }

/**
 * Pure gate for the undo action, extracted for unit testing.
 *
 * moveCount >= 2 alone covers the color edge cases thanks to ply parity:
 * on the player's turn the last move is always the AI's, so playing white
 * gives even counts (0, 2, 4...) and playing black odd counts (1, 3, 5...).
 * A count of 0 (white, game start) or 1 (black, only the AI opener) means
 * the player has nothing to take back.
 */
fun canUndo(
    mode: GameMode,
    isPlaying: Boolean,
    isPlayerTurn: Boolean,
    isAiThinking: Boolean,
    moveCount: Int
): Boolean =
    mode == GameMode.TRAINING &&
        isPlaying &&
        isPlayerTurn &&
        !isAiThinking &&
        moveCount >= 2
