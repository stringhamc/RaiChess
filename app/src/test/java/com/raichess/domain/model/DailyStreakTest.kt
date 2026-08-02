package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyStreakTest {

    @Test
    fun `first activity starts a one-day streak`() {
        val state = DailyStreak.onActivity(DailyStreak.State(), today = 100)
        assertEquals(DailyStreak.State(lastDay = 100, streak = 1, todayCount = 1), state)
    }

    @Test
    fun `same-day activity counts up without touching the streak`() {
        var state = DailyStreak.onActivity(DailyStreak.State(), 100)
        state = DailyStreak.onActivity(state, 100)
        state = DailyStreak.onActivity(state, 100)
        assertEquals(1, state.streak)
        assertEquals(3, state.todayCount)
    }

    @Test
    fun `consecutive days build the streak, a gap resets it`() {
        var state = DailyStreak.onActivity(DailyStreak.State(), 100)
        state = DailyStreak.onActivity(state, 101)
        state = DailyStreak.onActivity(state, 102)
        assertEquals(3, state.streak)
        // Day 103 skipped
        state = DailyStreak.onActivity(state, 104)
        assertEquals(1, state.streak)
    }

    @Test
    fun `display streak survives overnight but dies after a skipped day`() {
        var state = DailyStreak.onActivity(DailyStreak.State(), 100)
        state = DailyStreak.onActivity(state, 101)
        assertEquals(2, DailyStreak.displayStreak(state, today = 101))
        // The next morning, before any activity: still alive
        assertEquals(2, DailyStreak.displayStreak(state, today = 102))
        // A full day skipped: gone
        assertEquals(0, DailyStreak.displayStreak(state, today = 103))
    }

    @Test
    fun `today count reads zero on a new day`() {
        val state = DailyStreak.onActivity(DailyStreak.State(), 100)
        assertEquals(1, DailyStreak.countToday(state, today = 100))
        assertEquals(0, DailyStreak.countToday(state, today = 101))
    }
}
