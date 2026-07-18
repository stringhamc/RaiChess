package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticePositionStoreTest {

    @org.junit.Test
    fun `updatedProgress creates a first-attempt row`() {
        val row = PracticePositionStore.updatedProgress(
            previous = null, drillId = "puzzle:x", fen = "f", solved = true, nowMs = 42L
        )
        org.junit.Assert.assertEquals("puzzle:x", row.id)
        org.junit.Assert.assertEquals(1, row.timesPracticed)
        org.junit.Assert.assertEquals(1.0, row.successRate, 1e-9)
        org.junit.Assert.assertEquals(42L, row.lastPracticed)
        org.junit.Assert.assertEquals(PracticeCategory.TACTICS, row.category)
    }

    @org.junit.Test
    fun `updatedProgress keeps a running success average`() {
        var row = PracticePositionStore.updatedProgress(null, "d", "f", solved = true, nowMs = 1L)
        row = PracticePositionStore.updatedProgress(row, "d", "f", solved = false, nowMs = 2L)
        org.junit.Assert.assertEquals(2, row.timesPracticed)
        org.junit.Assert.assertEquals(0.5, row.successRate, 1e-9)
        row = PracticePositionStore.updatedProgress(row, "d", "f", solved = false, nowMs = 3L)
        org.junit.Assert.assertEquals(3, row.timesPracticed)
        org.junit.Assert.assertEquals(1.0 / 3.0, row.successRate, 1e-9)
        org.junit.Assert.assertEquals(3L, row.lastPracticed)
    }

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
