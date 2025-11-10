package com.raichess.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

/**
 * Minimal typography for the RaiChess app
 * High contrast white text on black background
 */
val ChessTypography = Typography(
    // Large display text (ELO rating, big numbers)
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = 0.sp,
        color = Color.White
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
        color = Color.White
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
        color = Color.White
    ),

    // Headlines (screen titles)
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
        color = Color.White
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        color = Color.White
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = Color.White
    ),

    // Titles (section headers)
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = Color.White
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        color = Color.White
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = Color.White
    ),

    // Body text (main content)
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = Color.White
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
        color = Color.White
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = Color.White
    ),

    // Labels (buttons, tabs)
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = Color.White
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = Color.White
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = Color.White
    )
)

/**
 * Chess piece symbols (Unicode)
 * For rendering pieces on the board
 */
object ChessPieceSymbols {
    const val WHITE_KING = "♔"
    const val WHITE_QUEEN = "♕"
    const val WHITE_ROOK = "♖"
    const val WHITE_BISHOP = "♗"
    const val WHITE_KNIGHT = "♘"
    const val WHITE_PAWN = "♙"

    const val BLACK_KING = "♚"
    const val BLACK_QUEEN = "♛"
    const val BLACK_ROOK = "♜"
    const val BLACK_BISHOP = "♝"
    const val BLACK_KNIGHT = "♞"
    const val BLACK_PAWN = "♟"

    /**
     * Get chess piece symbol for given piece character
     */
    fun getSymbol(piece: Char): String {
        return when (piece) {
            'K' -> WHITE_KING
            'Q' -> WHITE_QUEEN
            'R' -> WHITE_ROOK
            'B' -> WHITE_BISHOP
            'N' -> WHITE_KNIGHT
            'P' -> WHITE_PAWN
            'k' -> BLACK_KING
            'q' -> BLACK_QUEEN
            'r' -> BLACK_ROOK
            'b' -> BLACK_BISHOP
            'n' -> BLACK_KNIGHT
            'p' -> BLACK_PAWN
            else -> ""
        }
    }
}

/**
 * Text style for chess pieces (larger font)
 */
val ChessPieceTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 48.sp,
    lineHeight = 48.sp,
    letterSpacing = 0.sp
)
