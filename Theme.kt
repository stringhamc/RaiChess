package com.raichess.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
 * Chess-specific colors for board and pieces
 */
object ChessColors {
    // Board squares
    val LightSquare = Color(0xFFFFFFFF)
    val DarkSquare = Color(0xFF000000)
    val SquareBorder = Color(0xFF888888)
    
    // Selection and highlighting
    val SelectedSquare = Color(0xFF666666)
    val LegalMoveIndicator = Color(0xFF888888)
    val LastMove = Color(0xFF444444)
    val CheckHighlight = Color(0xFFAAAAAA)
    
    // Pieces (using high contrast)
    val WhitePiece = Color(0xFFFFFFFF)
    val BlackPiece = Color(0xFF000000)
    val PieceOutline = Color(0xFF888888)
    
    // Evaluation colors (grayscale)
    val EvalPositive = Color(0xFFCCCCCC) // Light gray for advantage
    val EvalNegative = Color(0xFF444444) // Dark gray for disadvantage
    val EvalNeutral = Color(0xFF888888)  // Mid gray for equal
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
