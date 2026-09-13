package com.raichess.domain.model

/**
 * Converts stored White-perspective evals ([com.raichess.data.database
 * .PositionEntity.evaluationCp]) into the player's perspective for the
 * review screen's mistake narration. Extracted from ReviewViewModel so
 * the sign flip for Black — exactly the kind of bug that slips in
 * silently — is locked by unit test.
 */
object EvalPerspective {

    /** A White-perspective eval as seen by the player. */
    fun toPlayer(evalWhiteCp: Int, playerIsWhite: Boolean): Int =
        if (playerIsWhite) evalWhiteCp else -evalWhiteCp

    /**
     * The player-perspective eval after their move: the next ply's stored
     * eval when that row exists, else the pre-move eval minus the move's
     * recorded loss (an equivalent reconstruction — loss is defined as
     * exactly that difference — used when the move ended the game and no
     * next row was stored).
     *
     * @param evalBeforePlayerCp pre-move eval already in player perspective
     * @param nextEvalWhiteCp next row's stored eval, White perspective
     */
    fun afterMove(
        evalBeforePlayerCp: Int,
        nextEvalWhiteCp: Int?,
        playerIsWhite: Boolean,
        lossCp: Int?
    ): Int = when {
        nextEvalWhiteCp != null -> toPlayer(nextEvalWhiteCp, playerIsWhite)
        else -> evalBeforePlayerCp - (lossCp ?: 0)
    }
}
