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
    fun `a single rest day never breaks the run`() {
        var state = DailyStreak.onActivity(DailyStreak.State(), 100)
        state = DailyStreak.onActivity(state, 101)
        // Day 102 off — training resumes on 103 and the run continues
        state = DailyStreak.onActivity(state, 103)
        assertEquals(3, state.streak)
    }

    @Test
    fun `two consecutive days off end the run`() {
        var state = DailyStreak.onActivity(DailyStreak.State(), 100)
        state = DailyStreak.onActivity(state, 101)
        // Days 102 and 103 off
        state = DailyStreak.onActivity(state, 104)
        assertEquals(1, state.streak)
    }

    @Test
    fun `display stays alive through one rest day, dies after two`() {
        var state = DailyStreak.onActivity(DailyStreak.State(), 100)
        state = DailyStreak.onActivity(state, 101)
        assertEquals(2, DailyStreak.displayStreak(state, today = 101))
        // The next morning, nothing done yet: alive
        assertEquals(2, DailyStreak.displayStreak(state, today = 102))
        // One full rest day taken; today could still continue the run
        assertEquals(2, DailyStreak.displayStreak(state, today = 103))
        // Two full days off: the run is over
        assertEquals(0, DailyStreak.displayStreak(state, today = 104))
    }

    @Test
    fun `today count reads zero on a new day`() {
        val state = DailyStreak.onActivity(DailyStreak.State(), 100)
        assertEquals(1, DailyStreak.countToday(state, today = 100))
        assertEquals(0, DailyStreak.countToday(state, today = 101))
    }
}
