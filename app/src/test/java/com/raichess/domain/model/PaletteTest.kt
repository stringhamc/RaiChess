package com.raichess.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palette's accessibility contract: colors that distinguish ROLES
 * must differ in relative luminance, not just hue, in BOTH palettes —
 * so the colorized board reads correctly for colorblind players and
 * even in pure grayscale, and turning color off loses nothing.
 */
class PaletteTest {

    private fun lum(argb: Long) = Palette.relativeLuminance(argb)

    /** Minimum luminance gap between adjacent coach-overlay roles. */
    private val coachGap = 0.15

    @Test
    fun `luminance math matches known anchors`() {
        assertEquals(0.0, lum(0xFF000000L), 1e-9)
        assertEquals(1.0, lum(0xFFFFFFFFL), 1e-6)
        // Alpha must be ignored: same paint, different alpha, same result
        assertEquals(lum(0xFF9F9F9FL), lum(0x559F9F9FL), 1e-9)
    }

    @Test
    fun `coach overlays keep a bright-middle-dark ladder in the colorized palette`() {
        val reveal = lum(Palette.Colorized.COACH_REVEAL)
        val reply = lum(Palette.Colorized.COACH_REPLY)
        val wrong = lum(Palette.Colorized.COACH_WRONG)
        assertTrue("reveal ($reveal) must outshine reply ($reply)", reveal - reply >= coachGap)
        assertTrue("reply ($reply) must outshine wrong ($wrong)", reply - wrong >= coachGap)
    }

    @Test
    fun `coach overlays keep the same ladder in the mono palette`() {
        val reveal = lum(Palette.Mono.COACH_REVEAL)
        val reply = lum(Palette.Mono.COACH_REPLY)
        val wrong = lum(Palette.Mono.COACH_WRONG)
        assertTrue("reveal ($reveal) must outshine reply ($reply)", reveal - reply >= coachGap)
        assertTrue("reply ($reply) must outshine wrong ($wrong)", reply - wrong >= coachGap)
    }

    @Test
    fun `board squares keep strong light-dark contrast in both palettes`() {
        val colorized =
            lum(Palette.Colorized.LIGHT_SQUARE) - lum(Palette.Colorized.DARK_SQUARE)
        val mono = lum(Palette.Mono.LIGHT_SQUARE) - lum(Palette.Mono.DARK_SQUARE)
        assertTrue("colorized board contrast $colorized", colorized >= 0.4)
        assertTrue("mono board contrast $mono", mono >= 0.4)
    }

    @Test
    fun `selection reads as a mid-tone between the square colors`() {
        // A selected square must change visibly whether it was light or
        // dark, so its luminance sits strictly between the two
        listOf(
            Triple(
                Palette.Colorized.SELECTED_SQUARE,
                Palette.Colorized.DARK_SQUARE,
                Palette.Colorized.LIGHT_SQUARE
            ),
            Triple(
                Palette.Mono.SELECTED_SQUARE,
                Palette.Mono.DARK_SQUARE,
                Palette.Mono.LIGHT_SQUARE
            )
        ).forEach { (selected, dark, light) ->
            assertTrue(lum(selected) > lum(dark) + 0.05)
            assertTrue(lum(selected) < lum(light) - 0.05)
        }
    }

    @Test
    fun `coach wrong marker stays visible against the dark square`() {
        // The crimson/dark-gray marker draws on dark squares too — it must
        // not sink into them (the mono value is tuned to match crimson's
        // luminance, keeping the two modes equally legible)
        listOf(
            Palette.Colorized.COACH_WRONG to Palette.Colorized.DARK_SQUARE,
            Palette.Mono.COACH_WRONG to Palette.Mono.DARK_SQUARE
        ).forEach { (wrong, darkSquare) ->
            assertTrue(lum(wrong) - lum(darkSquare) >= 0.03)
        }
    }
}
