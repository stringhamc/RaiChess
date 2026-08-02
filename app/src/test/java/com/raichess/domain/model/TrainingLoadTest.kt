package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingLoadTest {

    /** Build a day-count log from (daysAgo → count) pairs, today = 100. */
    private fun log(vararg entries: Pair<Long, Int>): Map<Long, Int> =
        entries.associate { (daysAgo, count) -> (TODAY - daysAgo) to count }

    @Test
    fun `no history means no verdict`() {
        assertNull(TrainingLoad.status(emptyMap(), TODAY))
    }

    @Test
    fun `record increments today and trims old history`() {
        var counts = TrainingLoad.record(emptyMap(), TODAY)
        counts = TrainingLoad.record(counts, TODAY)
        assertEquals(2, TrainingLoad.countToday(counts, TODAY))

        val withAncient = counts + ((TODAY - 30) to 5)
        val trimmed = TrainingLoad.record(withAncient, TODAY)
        assertTrue(trimmed.keys.all { it > TODAY - TrainingLoad.RETENTION_DAYS })
    }

    @Test
    fun `regular work across the week is productive`() {
        // Active 4 of the last 7 days
        val counts = log(0L to 2, 1L to 3, 3L to 2, 5L to 4)
        assertEquals(TrainingStatus.PRODUCTIVE, TrainingLoad.status(counts, TODAY))
    }

    @Test
    fun `a rest day after real work is recovery, not a broken chain`() {
        // Nothing today; trained yesterday and twice earlier this week
        val counts = log(1L to 3, 2L to 2, 4L to 3)
        assertEquals(TrainingStatus.RECOVERY, TrainingLoad.status(counts, TODAY))
    }

    @Test
    fun `several days off is detraining`() {
        val counts = log(4L to 3, 5L to 2)
        assertEquals(TrainingStatus.DETRAINING, TrainingLoad.status(counts, TODAY))
    }

    @Test
    fun `occasional light work is maintaining`() {
        // One light session today, nothing else this week
        val counts = log(0L to 1)
        assertEquals(TrainingStatus.MAINTAINING, TrainingLoad.status(counts, TODAY))
    }

    @Test
    fun `a huge single day is overreaching regardless of the week`() {
        val counts = log(0L to TrainingLoad.OVERREACHING_DAY_COUNT)
        assertEquals(TrainingStatus.OVERREACHING, TrainingLoad.status(counts, TODAY))
    }

    @Test
    fun `resuming after a lapse reads as maintaining, not detraining`() {
        // 5 days off, then a session today: back to work, no guilt verdict
        val counts = log(0L to 2, 5L to 3)
        assertEquals(TrainingStatus.MAINTAINING, TrainingLoad.status(counts, TODAY))
    }

    companion object {
        private const val TODAY = 100L
    }
}
