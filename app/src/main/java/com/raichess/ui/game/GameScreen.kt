package com.raichess.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raichess.domain.model.PlayerColor
import com.raichess.ui.theme.ChessColors
import com.raichess.ui.theme.ChessPieceSymbols

/**
 * The main game screen: board, status, move list, and controls.
 */
@Composable
fun GameScreen(
    state: GameUiState,
    onSquareTapped: (Int) -> Unit,
    onResign: () -> Unit,
    onNewGame: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Opponent info
        Text(
            text = "RaiEngine · ${state.opponentElo} ELO",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = statusText(state),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        ChessBoard(
            squares = state.squares,
            selectedSquare = state.selectedSquare,
            legalTargets = state.legalTargets,
            lastMove = state.lastMove,
            flipped = state.playerColor == PlayerColor.BLACK,
            onSquareTapped = onSquareTapped
        )

        Spacer(modifier = Modifier.height(12.dp))

        MoveHistory(
            moves = state.moveHistorySan,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (state.phase == GamePhase.PLAYING) {
                OutlinedButton(onClick = onResign) {
                    Text("Resign")
                }
            } else {
                OutlinedButton(onClick = onNewGame) {
                    Text("New Game")
                }
            }
        }
    }
}

@Composable
fun ChessBoard(
    squares: List<Char?>,
    selectedSquare: Int?,
    legalTargets: Set<Int>,
    lastMove: Pair<Int, Int>?,
    flipped: Boolean,
    onSquareTapped: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(2.dp, ChessColors.SquareBorder)
    ) {
        // Draw rank 8 at the top for white, rank 1 at the top when flipped
        val ranks = if (flipped) 0..7 else 7 downTo 0
        for (rank in ranks) {
            Row(modifier = Modifier.weight(1f)) {
                val files = if (flipped) 7 downTo 0 else 0..7
                for (file in files) {
                    val index = rank * 8 + file
                    BoardSquare(
                        piece = squares.getOrNull(index),
                        isLight = (rank + file) % 2 == 1,
                        isSelected = index == selectedSquare,
                        isLegalTarget = index in legalTargets,
                        isLastMove = lastMove?.let { index == it.first || index == it.second }
                            ?: false,
                        onTap = { onSquareTapped(index) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardSquare(
    piece: Char?,
    isLight: Boolean,
    isSelected: Boolean,
    isLegalTarget: Boolean,
    isLastMove: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = when {
        isSelected -> ChessColors.SelectedSquare
        isLight -> ChessColors.LightSquare
        else -> ChessColors.DarkSquare
    }

    Box(
        modifier = modifier
            .background(background)
            .border(0.5.dp, ChessColors.SquareBorder)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        if (isLastMove && !isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x55888888))
            )
        }
        if (piece != null) {
            val isWhitePiece = piece.isUpperCase()
            Text(
                text = ChessPieceSymbols.getSymbol(piece),
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                color = if (isWhitePiece) ChessColors.WhitePiece else ChessColors.BlackPiece,
                style = MaterialTheme.typography.bodyLarge.copy(
                    // Shadow keeps pieces visible on same-color squares
                    shadow = Shadow(
                        color = if (isWhitePiece) Color.Black else Color.White,
                        offset = Offset(0f, 0f),
                        blurRadius = 6f
                    )
                )
            )
        }
        if (isLegalTarget) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(ChessColors.LegalMoveIndicator, CircleShape)
            )
        }
    }
}

@Composable
private fun MoveHistory(moves: List<String>, modifier: Modifier = Modifier) {
    val movesText = buildString {
        moves.chunked(2).forEachIndexed { i, pair ->
            append("${i + 1}. ${pair.joinToString(" ")}  ")
        }
    }
    Text(
        text = if (movesText.isBlank()) "Moves will appear here" else movesText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    )
}

private fun statusText(state: GameUiState): String = when {
    state.ending != null -> when (state.ending) {
        GameEnding.CHECKMATE_WIN -> "Checkmate — you win!" + eloDeltaText(state)
        GameEnding.CHECKMATE_LOSS -> "Checkmate — you lose." + eloDeltaText(state)
        GameEnding.DRAW -> "Draw." + eloDeltaText(state)
        GameEnding.RESIGNED -> "You resigned." + eloDeltaText(state)
    }
    state.isAiThinking -> "RaiEngine is thinking…"
    state.isPlayerInCheck -> "Check! Your move."
    state.isPlayerTurn -> "Your move"
    else -> ""
}

private fun eloDeltaText(state: GameUiState): String {
    val delta = state.eloDelta ?: return ""
    val sign = if (delta >= 0) "+" else ""
    return " ELO $sign$delta → ${state.playerStats?.currentElo ?: ""}"
}
