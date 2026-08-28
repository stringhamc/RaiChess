package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EvalPerspectiveTest {

    @Test
    fun `white keeps the stored sign and black flips it`() {
        assertEquals(250, EvalPerspective.toPlayer(250, playerIsWhite = true))
        assertEquals(-250, EvalPerspective.toPlayer(250, playerIsWhite = false))
        assertEquals(250, EvalPerspective.toPlayer(-250, playerIsWhite = false))
    }

    @Test
    fun `after-move eval prefers the next ply's stored row`() {
        // White blundered +200 into -150: the next row (still White
        // perspective) is the truth, not the loss arithmetic
        assertEquals(
            -150,
            EvalPerspective.afterMove(
                evalBeforePlayerCp = 200,
                nextEvalWhiteCp = -150,
                playerIsWhite = true,
                lossCp = 350
            )
        )
        // Black's mistake: the stored +150 (White ahead) reads as -150
        assertEquals(
            -150,
            EvalPerspective.afterMove(
                evalBeforePlayerCp = 200,
                nextEvalWhiteCp = 150,
                playerIsWhite = false,
                lossCp = 350
            )
        )
    }

    @Test
    fun `terminal moves reconstruct from the recorded loss`() {
        // No next row (the move ended the game): before minus loss
        assertEquals(
            -300,
            EvalPerspective.afterMove(
                evalBeforePlayerCp = 100,
                nextEvalWhiteCp = null,
                playerIsWhite = false,
                lossCp = 400
            )
        )
        // Old rows without a recorded loss degrade to "unchanged"
        assertEquals(
            100,
            EvalPerspective.afterMove(
                evalBeforePlayerCp = 100,
                nextEvalWhiteCp = null,
                playerIsWhite = true,
                lossCp = null
            )
        )
    }
}
