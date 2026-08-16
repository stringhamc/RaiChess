package com.raichess.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.raichess.domain.model.Palette

/**
 * RaiChess (来Chess) - The Next Chess App
 *
 * Minimal Black & White Color Scheme
 * Optimized for OLED screens to save battery
 *
 * Pure black. Pure white. Pure focus.
 */
private val ChessColorScheme = darkColorScheme(
    // Pure black background for OLED power savings
    background = Color(0xFF000000),
    surface = Color(0xFF000000),

    // Pure white for text and UI elements
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),

    // Primary colors (white on black)
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),

    // Secondary colors (minimal gray for accents)
    secondary = Color(0xFF888888),
    onSecondary = Color(0xFF000000),

    // Error state (light gray, no red to maintain B&W theme)
    error = Color(0xFFAAAAAA),
    onError = Color(0xFF000000),

    // Container colors
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF222222),
    onSecondaryContainer = Color(0xFFFFFFFF),

    // Surface variants
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFFFFFFF),

    // Outline colors
    outline = Color(0xFF666666),
    outlineVariant = Color(0xFF333333)
)

/**
 * Chess-specific colors for board and pieces.
 *
 * Board and coaching colors switch between the colorized and grayscale
 * palettes (see [Palette]) — hue never carries information on its own,
 * and PaletteTest proves the luminance separation, so both modes work
 * for colorblind players. [colorized] is Compose state: flipping the
 * Settings toggle recomposes every board on screen. Chrome (controls,
 * labels, evals) deliberately stays grayscale in both modes — the OLED
 * minimalism is the app's look; color is reserved for the 64 squares
 * where it means something.
 */
object ChessColors {
    /** Set from the "Colorized board" setting (MainActivity/Settings). */
    var colorized by mutableStateOf(true)

    private fun pick(color: Long, mono: Long): Color =
        Color(if (colorized) color else mono)

    // Board squares. The mono grays keep the two-tone outlined pieces
    // readable; the colorized pair preserves the same luminance contrast
    val LightSquare: Color
        get() = pick(Palette.Colorized.LIGHT_SQUARE, Palette.Mono.LIGHT_SQUARE)
    val DarkSquare: Color
        get() = pick(Palette.Colorized.DARK_SQUARE, Palette.Mono.DARK_SQUARE)
    val SquareBorder = Color(0xFF888888)

    // Selection and highlighting
    val SelectedSquare: Color
        get() = pick(Palette.Colorized.SELECTED_SQUARE, Palette.Mono.SELECTED_SQUARE)
    val LegalMoveIndicator: Color
        get() = pick(Palette.Colorized.LEGAL_MOVE, Palette.Mono.LEGAL_MOVE)
    // The player's own last move: subtle. The opponent's last move: stronger
    // fill plus a ring, so it's easy to see what the AI just played.
    val LastMove: Color
        get() = pick(Palette.Colorized.LAST_MOVE, Palette.Mono.LAST_MOVE)
    val LastMoveOpponent: Color
        get() = pick(Palette.Colorized.LAST_MOVE_OPPONENT, Palette.Mono.LAST_MOVE_OPPONENT)
    val LastMoveOpponentRing: Color
        get() = pick(
            Palette.Colorized.LAST_MOVE_OPPONENT_RING,
            Palette.Mono.LAST_MOVE_OPPONENT_RING
        )

    // Grayscale control colors (sliders, switches) — single source of truth
    val ControlActive = Color(0xFFFFFFFF)
    val ControlTrackActive = Color(0xFF555555)
    val ControlThumbInactive = Color(0xFF888888)
    val ControlTrackInactive = Color(0xFF222222)
    val SliderInactiveTrack = Color(0xFF333333)

    // Evaluation colors (grayscale)
    val EvalPositive = Color(0xFFCCCCCC) // Light gray for advantage
    val EvalNegative = Color(0xFF444444) // Dark gray for disadvantage
    val EvalNeutral = Color(0xFF888888)  // Mid gray for equal

    // Coaching overlays — three roles at three clearly separated
    // luminance steps (bright / middle / dark) in BOTH palettes, so
    // arrows and markers stay tellable-apart for colorblind players,
    // in grayscale mode, and in pure-luminance terms.
    val CoachReveal: Color // the coach's move: arrow + squares
        get() = pick(Palette.Colorized.COACH_REVEAL, Palette.Mono.COACH_REVEAL)
    val CoachReply: Color // the opponent's scripted reply
        get() = pick(Palette.Colorized.COACH_REPLY, Palette.Mono.COACH_REPLY)
    val CoachWrong: Color // the player's last wrong try
        get() = pick(Palette.Colorized.COACH_WRONG, Palette.Mono.COACH_WRONG)
}

@Composable
fun RaiChessTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ChessColorScheme,
        typography = ChessTypography,
        content = content
    )
}
