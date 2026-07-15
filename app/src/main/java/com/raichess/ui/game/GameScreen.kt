package com.raichess.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raichess.domain.model.GameMode
import com.raichess.domain.model.MaterialCalculator
import com.raichess.domain.model.PlayerColor
import com.raichess.domain.usecase.HintAdvisor
import com.raichess.ui.theme.ChessColors
import kotlin.math.roundToInt

/**
 * The main game screen: board, status, captured material, move list,
 * and controls.
 */
@Composable
fun GameScreen(
    state: GameUiState,
    onSquareTapped: (Int) -> Unit,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onResign: () -> Unit,
    onNewGame: () -> Unit
) {
    val flipped = state.playerColor == PlayerColor.BLACK
    val material = remember(state.squares) { MaterialCalculator.compute(state.squares) }
    val playerIsWhite = state.playerColor == PlayerColor.WHITE
    // Positive = the player is ahead on material
    val playerDiff = if (playerIsWhite) material.diff else -material.diff
    // Each row shows the trophies for the side nearest it: the opponent's
    // captures (the player's own lost pieces) go in the top row, the
    // player's captures in the bottom row.
    val opponentCaptures =
        if (playerIsWhite) material.capturedWhitePieces else material.capturedBlackPieces
    val playerCaptures =
        if (playerIsWhite) material.capturedBlackPieces else material.capturedWhitePieces

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${state.engineLabel} · ${state.opponentElo} ELO" +
                if (state.gameMode == GameMode.TRAINING) " · Training" else "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = statusText(state),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        // Coach line: a hint the player asked for, or a passive warning
        // that the last move lost serious ground (Training mode only)
        val coachText = state.hintText?.let { "Hint: $it" }
            ?: if (state.coachWarning) {
                "Coach: that last move may have lost ground — consider Undo."
            } else {
                null
            }
        if (coachText != null) {
            Text(
                text = coachText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        CapturedRow(pieces = opponentCaptures, advantage = -playerDiff)

        AnimatedChessBoard(
            state = state,
            flipped = flipped,
            onSquareTapped = onSquareTapped
        )

        CapturedRow(pieces = playerCaptures, advantage = playerDiff)

        Spacer(modifier = Modifier.height(4.dp))

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
                if (state.gameMode == GameMode.TRAINING) {
                    OutlinedButton(onClick = onUndo, enabled = state.canUndo) {
                        Text(if (state.undoCount > 0) "Undo (${state.undoCount})" else "Undo")
                    }
                    OutlinedButton(
                        onClick = onHint,
                        enabled = state.canHint &&
                            state.isPlayerTurn &&
                            !state.isAiThinking &&
                            state.hintLevel < HintAdvisor.MAX_LEVEL
                    ) {
                        Text(if (state.hintCount > 0) "Hint (${state.hintCount})" else "Hint")
                    }
                }
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

/**
 * Board with an optional slide-animation overlay. When animations are
 * disabled (the default) the overlay layer is not composed at all and the
 * board renders instantly, exactly as before.
 */
@Composable
private fun AnimatedChessBoard(
    state: GameUiState,
    flipped: Boolean,
    onSquareTapped: (Int) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val squareSize = maxWidth / 8
        val squareSizePx = with(LocalDensity.current) { squareSize.toPx() }

        fun offsetOf(index: Int): Offset {
            val file = index % 8
            val rank = index / 8
            val col = if (flipped) 7 - file else file
            val row = if (flipped) rank else 7 - rank
            return Offset(col * squareSizePx, row * squareSizePx)
        }

        var hiddenSquare by remember { mutableStateOf<Int?>(null) }
        val slide = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

        if (state.animationsEnabled) {
            // moveSeq increments only when a move is applied — undo and new
            // game never animate. Known cosmetic limits (all invisible when
            // the toggle is off): on castling only the king slides; an
            // en-passant victim disappears at start; a promoting pawn slides
            // as the promoted piece, since squares[to] is already the queen.
            LaunchedEffect(state.moveSeq) {
                val lastMove = state.lastMove
                if (state.moveSeq > 0 && lastMove != null) {
                    hiddenSquare = lastMove.to
                    slide.snapTo(offsetOf(lastMove.from))
                    slide.animateTo(
                        offsetOf(lastMove.to),
                        tween(durationMillis = 150, easing = LinearOutSlowInEasing)
                    )
                    hiddenSquare = null
                } else {
                    // moveSeq reset to 0 (new game) cancels any in-flight
                    // animation before its hiddenSquare = null runs; clear it
                    // here so a stale hidden square can't blank a square in
                    // the new game
                    hiddenSquare = null
                }
            }
        }

        ChessBoard(
            squares = state.squares,
            selectedSquare = state.selectedSquare,
            legalTargets = state.legalTargets,
            hintHighlights = state.hintHighlights,
            lastMove = state.lastMove,
            lastMoveByOpponent = state.lastMoveByOpponent,
            checkedKingSquare = if (state.isPlayerInCheck) {
                findKingSquare(state.squares, state.playerColor)
            } else {
                null
            },
            hiddenSquare = if (state.animationsEnabled) hiddenSquare else null,
            flipped = flipped,
            onSquareTapped = onSquareTapped
        )

        if (state.animationsEnabled) {
            val hidden = hiddenSquare
            val piece = hidden?.let { state.squares.getOrNull(it) }
            if (piece != null) {
                Image(
                    painter = painterResource(ChessPieceIcons.forChar(piece)),
                    contentDescription = null,
                    modifier = Modifier
                        .size(squareSize)
                        .offset {
                            IntOffset(
                                slide.value.x.roundToInt(),
                                slide.value.y.roundToInt()
                            )
                        }
                        .padding(squareSize * 0.075f)
                )
            }
        }
    }
}

@Composable
fun ChessBoard(
    squares: List<Char?>,
    selectedSquare: Int?,
    legalTargets: Set<Int>,
    hintHighlights: Set<Int> = emptySet(),
    lastMove: LastMove?,
    lastMoveByOpponent: Boolean,
    checkedKingSquare: Int?,
    hiddenSquare: Int?,
    flipped: Boolean,
    onSquareTapped: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(2.dp, ChessColors.SquareBorder)
            .padding(2.dp)
    ) {
        // Rank 8 at the top for white, rank 1 at the top when flipped
        val ranks = if (flipped) 0..7 else 7 downTo 0
        for (rank in ranks) {
            Row(modifier = Modifier.weight(1f)) {
                val files = if (flipped) 7 downTo 0 else 0..7
                for (file in files) {
                    val index = rank * 8 + file
                    val isBottomRow = rank == if (flipped) 7 else 0
                    val isLeftColumn = file == if (flipped) 7 else 0
                    val onLastMove = lastMove?.let { index == it.from || index == it.to } ?: false
                    BoardSquare(
                        piece = if (index == hiddenSquare) null else squares.getOrNull(index),
                        isLight = (rank + file) % 2 == 1,
                        isSelected = index == selectedSquare,
                        isLegalTarget = index in legalTargets,
                        isCaptureTarget = index in legalTargets && squares.getOrNull(index) != null,
                        isLastMove = onLastMove,
                        isOpponentLastMove = onLastMove && lastMoveByOpponent,
                        isHintHighlight = index in hintHighlights,
                        isCheckedKing = index == checkedKingSquare,
                        fileLabel = if (isBottomRow) ('a' + file) else null,
                        rankLabel = if (isLeftColumn) ('1' + rank) else null,
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
    isCaptureTarget: Boolean,
    isLastMove: Boolean,
    isOpponentLastMove: Boolean,
    isHintHighlight: Boolean,
    isCheckedKing: Boolean,
    fileLabel: Char?,
    rankLabel: Char?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = when {
        isSelected -> ChessColors.SelectedSquare
        isLight -> ChessColors.LightSquare
        else -> ChessColors.DarkSquare
    }
    val labelColor = if (isLight) ChessColors.DarkSquare else ChessColors.LightSquare

    Box(
        modifier = modifier
            .background(background)
            .clickable(onClick = onTap)
    ) {
        if (isLastMove && !isSelected) {
            // The opponent's last move gets a stronger fill plus a ring so the
            // player can immediately see what the AI just played; the player's
            // own last move stays subtle.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isOpponentLastMove) ChessColors.LastMoveOpponent
                        else ChessColors.LastMove
                    )
            )
            if (isOpponentLastMove) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, ChessColors.LastMoveOpponentRing)
                )
            }
        }
        if (isCheckedKing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.5.dp, labelColor)
            )
        }
        if (isHintHighlight) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.5.dp, ChessColors.LegalMoveIndicator)
            )
        }
        rankLabel?.let {
            Text(
                text = it.toString(),
                fontSize = 9.sp,
                color = labelColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 2.dp)
            )
        }
        fileLabel?.let {
            Text(
                text = it.toString(),
                fontSize = 9.sp,
                color = labelColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 2.dp)
            )
        }
        if (piece != null) {
            Image(
                painter = painterResource(ChessPieceIcons.forChar(piece)),
                contentDescription = ChessPieceIcons.contentDescription(piece),
                modifier = Modifier
                    .fillMaxSize(0.85f)
                    .align(Alignment.Center)
            )
        }
        if (isLegalTarget) {
            if (isCaptureTarget) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.9f)
                        .align(Alignment.Center)
                        .border(2.dp, ChessColors.LegalMoveIndicator, CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.25f)
                        .align(Alignment.Center)
                        .background(ChessColors.LegalMoveIndicator, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun CapturedRow(pieces: List<Char>, advantage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pieces.forEach { piece ->
            Image(
                painter = painterResource(ChessPieceIcons.forChar(piece)),
                contentDescription = ChessPieceIcons.contentDescription(piece),
                modifier = Modifier.size(16.dp)
            )
        }
        if (advantage > 0) {
            Text(
                text = "+$advantage",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 4.dp)
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
            .padding(vertical = 6.dp)
    )
}

private fun findKingSquare(squares: List<Char?>, playerColor: PlayerColor): Int? {
    val king = if (playerColor == PlayerColor.WHITE) 'K' else 'k'
    val index = squares.indexOf(king)
    return if (index >= 0) index else null
}

private fun statusText(state: GameUiState): String {
    val moveNumber = state.moveHistorySan.size / 2 + 1
    return when {
        state.ending != null -> when (state.ending) {
            GameEnding.CHECKMATE_WIN -> "Checkmate — you win!" + eloDeltaText(state)
            GameEnding.CHECKMATE_LOSS -> "Checkmate — you lose." + eloDeltaText(state)
            GameEnding.DRAW -> "Draw." + eloDeltaText(state)
            GameEnding.RESIGNED -> "You resigned." + eloDeltaText(state)
        }
        state.isAiThinking -> "Move $moveNumber · ${state.engineLabel} is thinking…"
        state.isPlayerInCheck -> "Move $moveNumber · Check!"
        state.isPlayerTurn -> "Move $moveNumber · Your move"
        else -> ""
    }
}

private fun eloDeltaText(state: GameUiState): String {
    val delta = state.eloDelta ?: return ""
    val sign = if (delta >= 0) "+" else ""
    return " ELO $sign$delta → ${state.playerStats?.currentElo ?: ""}"
}
