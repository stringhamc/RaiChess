package com.raichess.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineDiagnosticsTest {

    @Test
    fun `append keeps order and drops the oldest past the cap`() {
        var log = emptyList<String>()
        for (i in 1..EngineDiagnostics.MAX_ENTRIES + 3) {
            log = EngineDiagnostics.appended(log, "event $i", EngineDiagnostics.MAX_ENTRIES)
        }
        assertEquals(EngineDiagnostics.MAX_ENTRIES, log.size)
        assertEquals("event 4", log.first())
        assertEquals("event ${EngineDiagnostics.MAX_ENTRIES + 3}", log.last())
    }

    @Test
    fun `append below the cap keeps everything`() {
        val log = EngineDiagnostics.appended(listOf("a", "b"), "c", 100)
        assertEquals(listOf("a", "b", "c"), log)
    }
}
