package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LanFormatTest {

    @Test
    fun `plain moves render as an arrow`() {
        assertEquals("e2 → e4", LanFormat.arrow("e2e4"))
    }

    @Test
    fun `promotions keep the piece suffix`() {
        assertEquals("e7 → e8 (=Q)", LanFormat.arrow("e7e8q"))
        assertEquals("a2 → a1 (=N)", LanFormat.arrow("a2a1n"))
    }

    @Test
    fun `malformed input passes through untouched`() {
        assertEquals("e2", LanFormat.arrow("e2"))
        assertEquals("", LanFormat.arrow(""))
    }
}
