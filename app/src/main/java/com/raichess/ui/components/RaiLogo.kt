package com.raichess.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raichess.ui.theme.ChessColors

/**
 * RaiChess brand mark: the kanji 来 ("rai" — next) standing on a 2×2
 * chessboard, drawn white with a drop shadow — the same treatment the board
 * pieces use — so it stays legible over the light squares. Used as the
 * home-screen logo; the same composition is intended to back the launcher
 * icon (a separate adaptive-icon drawable, TODO).
 */
@Composable
fun RaiLogo(
    modifier: Modifier = Modifier,
    size: Dp = 76.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.15f))
        ) {
            LogoRow(left = ChessColors.LightSquare, right = ChessColors.DarkSquare)
            LogoRow(left = ChessColors.DarkSquare, right = ChessColors.LightSquare)
        }
        Text(
            text = "来",
            color = Color.White,
            fontSize = (size.value * 0.6f).sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.75f),
                    offset = Offset(0f, 3f),
                    blurRadius = 9f
                )
            )
        )
    }
}

@Composable
private fun ColumnScope.LogoRow(left: Color, right: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(left)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(right)
        )
    }
}
