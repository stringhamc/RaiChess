package com.raichess.domain.model

import kotlin.math.pow

/**
 * The board and coaching color palettes as packed ARGB values, kept out
 * of Compose so the accessibility contract is unit-testable: every hue
 * pair that distinguishes two ROLES must also differ in relative
 * luminance (PaletteTest enforces the gaps). Hue is never the only
 * signal — the colorized palette reads correctly for colorblind players
 * and even in pure grayscale.
 *
 * Two palettes with identical structure:
 *  - [Colorized] (default): warm cream / muted pine board, steel-blue
 *    selection, gold last-move wash; coach overlays in amber (bright) /
 *    teal (middle) / crimson (dark)
 *  - [Mono]: the original OLED grayscale board; coach overlays become
 *    three grays on the same luminance ladder
 *
 * ChessColors (ui.theme) picks between them at runtime from the
 * "Colorized board" setting.
 */
object Palette {

    object Colorized {
        const val LIGHT_SQUARE = 0xFFEAE0C8L
        const val DARK_SQUARE = 0xFF4F6B58L
        const val SELECTED_SQUARE = 0xFF6C8EBFL
        const val LEGAL_MOVE = 0xFF6C8EBFL
        const val LAST_MOVE = 0x59D9B84CL
        const val LAST_MOVE_OPPONENT = 0x7EE3C860L
        const val LAST_MOVE_OPPONENT_RING = 0xCCFFFFFFL
        const val COACH_REVEAL = 0xFFFFC53DL
        const val COACH_REPLY = 0xFF2EC4B6L
        const val COACH_WRONG = 0xFFD7263DL
    }

    object Mono {
        const val LIGHT_SQUARE = 0xFFE3E3E3L
        const val DARK_SQUARE = 0xFF3D3D3DL
        const val SELECTED_SQUARE = 0xFF8F8F8FL
        const val LEGAL_MOVE = 0xFF8F8F8FL
        const val LAST_MOVE = 0x559F9F9FL
        const val LAST_MOVE_OPPONENT = 0x88BFBFBFL
        const val LAST_MOVE_OPPONENT_RING = 0xCCFFFFFFL
        const val COACH_REVEAL = 0xFFE8E8E8L
        const val COACH_REPLY = 0xFFA8A8A8L
        const val COACH_WRONG = 0xFF707070L
    }

    /**
     * WCAG relative luminance of an opaque packed ARGB color, 0.0 (black)
     * to 1.0 (white). Alpha is ignored — callers compare paint colors,
     * not composited results.
     */
    fun relativeLuminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val srgb = ((argb shr shift) and 0xFF).toDouble() / 255.0
            return if (srgb <= 0.03928) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
