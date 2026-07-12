package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticePositionStoreTest {

    private fun position(fen: String, createdAt: Long, timesPracticed: Int = 0) =
        PracticePosition(
            id = "id-$fen-$createdAt",
            sourceMoveNumber = 1,
            fen = fen,
            category = PracticeCategory.MISTAKE_CORRECTION,
            timesPracticed = timesPracticed,
            createdAt = createdAt
        )

    @Test
    fun `new positions are inserted newest first`() {
        var list = emptyList<PracticePosition>()
        list = PracticePositionStore.withPosition(list, position("fen-a", 1))
        list = PracticePositionStore.withPosition(list, position("fen-b", 2))
        assertEquals(listOf("fen-b", "fen-a"), list.map { it.fen })
    }

    @Test
    fun `same fen is deduplicated with refreshed createdAt`() {
        var list = emptyList<PracticePosition>()
        list = PracticePositionStore.withPosition(list, position("fen-a", 1))
        list = PracticePositionStore.withPosition(list, position("fen-b", 2))
        list = PracticePositionStore.withPosition(list, position("fen-a", 3))

        assertEquals(2, list.size)
        assertEquals("fen-a", list.first().fen)
        assertEquals(3L, list.first().createdAt)
    }

    @Test
    fun `dedupe preserves practice progress`() {
        var list = listOf(
            position("fen-a", 1, timesPracticed = 7).copy(successRate = 0.6)
        )
        list = PracticePositionStore.withPosition(list, position("fen-a", 9))
        assertEquals(7, list.first().timesPracticed)
        assertEquals(0.6, list.first().successRate, 0.0001)
        assertEquals(9L, list.first().createdAt)
    }

    @Test
    fun `oldest entries are evicted beyond the cap`() {
        var list = emptyList<PracticePosition>()
        for (i in 1..PracticePositionStore.MAX_POSITIONS + 5) {
            list = PracticePositionStore.withPosition(list, position("fen-$i", i.toLong()))
        }
        assertEquals(PracticePositionStore.MAX_POSITIONS, list.size)
        assertEquals("fen-${PracticePositionStore.MAX_POSITIONS + 5}", list.first().fen)
        assertTrue(list.none { it.fen == "fen-1" })
    }
}
