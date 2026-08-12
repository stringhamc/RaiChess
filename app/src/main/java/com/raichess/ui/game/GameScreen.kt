package com.raichess.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raichess.domain.model.GameMode
import com.raichess.domain.model.MaterialCalculator
import com.raichess.domain.model.MoveClassification
import com.raichess.domain.model.PlayerColor
import com.raichess.domain.usecase.HintAdvisor
import com.raichess.ui.theme.ChessColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

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
    onWhyTapped: () -> Unit,
    onResign: () -> Unit,
    onNewGame: () -> Unit,
    onPlayAgain: () -> Unit = onNewGame,
    onReviewGame: (() -> Unit)? = null
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
        // While the opponent thinks (or the engine warms up on move one),
        // rotate light chatter so a long wait feels alive, not frozen
        var thinkingTick by remember { mutableStateOf(0) }
        LaunchedEffect(state.isAiThinking) {
            thinkingTick = 0
            if (state.isAiThinking) {
                while (true) {
                    delay(CHATTER_INTERVAL_MS)
                    thinkingTick++
                }
            }
        }
        Text(
            text = statusText(state, thinkingTick),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        // The coach gets a word in at game over (Training only)
        if (state.phase == GamePhase.GAME_OVER && state.coachReaction != null) {
            Text(
                text = "Rai: ${state.coachReaction}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Coach line (Training only — Rated shouldn't pay a blank gap for a
        // feature it never shows): a requested hint, else the live move
        // rating and win chances. Fixed-height slot so the board never
        // reflows when text appears or disappears.
        if (state.gameMode == GameMode.TRAINING) {
            // A hint owns the line; otherwise the grade, tappable to swap
            // in the explanation behind it ("Why?") and back
            val whyTappable = state.hintText == null && state.lastMoveWhy != null
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // min, not fixed: at large accessibility font scales the
                    // two lines outgrow the 1.0x-derived slot — growing the
                    // slot (one-time reflow) beats clipping the text
                    .heightIn(min = coachLineHeight)
                    .then(
                        if (whyTappable) Modifier.clickable(onClick = onWhyTapped)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                val coachText = state.hintText?.let { "Hint: $it" }
                    ?: if (state.showWhy && state.lastMoveWhy != null) {
                        state.lastMoveWhy
                    } else {
                        coachStatusLine(state)?.let { line ->
                            if (whyTappable) "$line · Why?" else line
                        }
                    }
                if (coachText != null) {
                    Text(
                        text = coachText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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

        if (state.phase == GamePhase.PLAYING) {
            // Resign is one mis-tap from a recorded loss — confirm it
            var showResignConfirm by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
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
                OutlinedButton(onClick = { showResignConfirm = true }) {
                    Text("Resign")
                }
            }
            if (showResignConfirm) {
                AlertDialog(
                    onDismissRequest = { showResignConfirm = false },
                    title = { Text("Resign?") },
                    text = { Text("This counts as a loss.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showResignConfirm = false
                            onResign()
                        }) { Text("Resign") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResignConfirm = false }) {
                            Text("Keep playing")
                        }
                    }
                )
            }
        } else {
            // Game-over panel: review-while-motivated first, then rematch
            // or home. No review for a game with no stored moves (instant
            // resign) — there'd be nothing to open.
            if (onReviewGame != null && state.moveHistorySan.isNotEmpty()) {
                Button(
                    onClick = onReviewGame,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Review this game")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = onPlayAgain) {
                    Text("Play again")
                }
                OutlinedButton(onClick = onNewGame) {
                    Text("Home")
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

/**
 * A coaching arrow drawn over the board, square ordinal to square ordinal
 * (a1=0..h8=63). Colors come from ChessColors' coach palette — three hues
 * at three luminance steps, so overlapping roles stay distinguishable.
 */
data class BoardArrow(val from: Int, val to: Int, val color: Color)

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
    onSquareTapped: (Int) -> Unit,
    /** Colored square markers (border + tint) keyed by square ordinal. */
    markers: Map<Int, Color> = emptyMap(),
    /** Coaching arrows, drawn in list order (last on top). */
    arrows: List<BoardArrow> = emptyList()
) {
    Box(modifier = Modifier.fillMaxSize()) {
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
                        val onLastMove =
                            lastMove?.let { index == it.from || index == it.to } ?: false
                        BoardSquare(
                            piece = if (index == hiddenSquare) null else squares.getOrNull(index),
                            isLight = (rank + file) % 2 == 1,
                            isSelected = index == selectedSquare,
                            isLegalTarget = index in legalTargets,
                            isCaptureTarget =
                                index in legalTargets && squares.getOrNull(index) != null,
                            isLastMove = onLastMove,
                            isOpponentLastMove = onLastMove && lastMoveByOpponent,
                            isHintHighlight = index in hintHighlights,
                            isCheckedKing = index == checkedKingSquare,
                            markerColor = markers[index],
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
        if (arrows.isNotEmpty()) {
            // Same 2dp inset as the grid (the border band), so arrow
            // geometry lines up with square centers. Canvas has no click
            // handling, so taps fall through to the squares beneath.
            BoardArrowOverlay(
                arrows = arrows,
                flipped = flipped,
                modifier = Modifier.matchParentSize().padding(2.dp)
            )
        }
    }
}

@Composable
private fun BoardArrowOverlay(
    arrows: List<BoardArrow>,
    flipped: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cell = size.width / 8f
        fun center(square: Int): Offset {
            val file = square % 8
            val rank = square / 8
            val col = if (flipped) 7 - file else file
            val row = if (flipped) rank else 7 - rank
            return Offset((col + 0.5f) * cell, (row + 0.5f) * cell)
        }
        arrows.forEach { arrow ->
            val from = center(arrow.from)
            val to = center(arrow.to)
            val delta = to - from
            val length = delta.getDistance()
            if (length <= 0f) return@forEach
            val unit = delta / length
            // Start off-center so the arrow reads as leaving the piece;
            // the head lands on the target square's center
            val start = from + unit * (cell * 0.30f)
            val headLength = cell * 0.38f
            val base = to - unit * headLength
            val color = arrow.color.copy(alpha = 0.85f)
            drawLine(
                color = color,
                start = start,
                end = base,
                strokeWidth = cell * 0.22f,
                cap = StrokeCap.Round
            )
            val perp = Offset(-unit.y, unit.x) * (headLength * 0.62f)
            val head = Path().apply {
                moveTo(to.x, to.y)
                lineTo(base.x + perp.x, base.y + perp.y)
                lineTo(base.x - perp.x, base.y - perp.y)
                close()
            }
            drawPath(head, color)
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
    markerColor: Color? = null,
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
            // Coach amber, not grayscale: hints are the coach pointing
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.5.dp, ChessColors.CoachReveal)
            )
        }
        if (markerColor != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(markerColor.copy(alpha = 0.25f))
                    .border(2.5.dp, markerColor)
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

// Fits maxLines = 2 of bodyMedium (20sp line height ≈ 40dp) with headroom —
// a fixed slot shorter than its own text would overflow into the row below
private val coachLineHeight = 42.dp

/**
 * The live coach readout: last move's grade (with an undo nudge on a
 * blunder) and the player's current winning chances. Null when there's
 * nothing to show (Rated mode, or nothing graded/analyzed yet).
 */
private fun coachStatusLine(state: GameUiState): String? {
    val parts = mutableListOf<String>()
    state.lastMoveRating?.let { rating ->
        val label = when (rating) {
            MoveClassification.BEST -> "Best move!"
            MoveClassification.GOOD -> "Good move"
            MoveClassification.INACCURACY -> "Inaccuracy"
            MoveClassification.MISTAKE -> "Mistake"
            MoveClassification.BLUNDER -> "Blunder"
        }
        parts += if (state.coachWarning) "$label — consider Undo" else label
    }
    state.winPercent?.let { parts += "Win $it%" }
    return if (parts.isEmpty()) null else parts.joinToString("  ·  ")
}

private fun findKingSquare(squares: List<Char?>, playerColor: PlayerColor): Int? {
    val king = if (playerColor == PlayerColor.WHITE) 'K' else 'k'
    val index = squares.indexOf(king)
    return if (index >= 0) index else null
}

private const val CHATTER_INTERVAL_MS = 4000L

/**
 * Rotating small talk for long thinks — the first slot stays the honest
 * "X is thinking…", then generic musings cycle so a slow search (or the
 * engine's first-move warm-up) reads as alive rather than frozen.
 */
private val thinkingChatter = listOf(
    "Hmm, interesting position…",
    "Weighing every capture…",
    "Checking the sharp lines…",
    "Counting the pawns again…",
    "That move made me think…",
    "Looking one move deeper…",
    "Almost decided…"
)

private fun statusText(state: GameUiState, thinkingTick: Int = 0): String {
    val moveNumber = state.moveHistorySan.size / 2 + 1
    return when {
        state.ending != null -> when (state.ending) {
            GameEnding.CHECKMATE_WIN ->
                "Checkmate — you win!" + eloDeltaText(state) + newPeakText(state)
            GameEnding.CHECKMATE_LOSS ->
                "Checkmate — you lose." + eloDeltaText(state) + lossEncouragement()
            GameEnding.DRAW -> "Draw." + eloDeltaText(state)
            GameEnding.RESIGNED ->
                "You resigned." + eloDeltaText(state) + lossEncouragement()
        }
        state.isAiThinking -> {
            val line = if (thinkingTick == 0) {
                "${state.engineLabel} is thinking…"
            } else {
                thinkingChatter[(thinkingTick - 1) % thinkingChatter.size]
            }
            "Move $moveNumber · $line"
        }
        state.isPlayerInCheck -> "Move $moveNumber · Check!"
        state.isPlayerTurn -> "Move $moveNumber · Your move"
        else -> ""
    }
}

private fun eloDeltaText(state: GameUiState): String {
    val delta = state.eloDelta ?: return ""
    val sign = if (delta >= 0) "+" else ""
    val base = " ELO $sign$delta → ${state.playerStats?.currentElo ?: ""}"
    // Itemize the assistance cost so a shrunken gain is explained, not a
    // mystery — and so players can see hints are cheap, not punitive
    val clean = state.eloDeltaClean ?: return base
    val cleanSign = if (clean >= 0) "+" else ""
    val assists = listOfNotNull(
        state.hintCount.takeIf { it > 0 }?.let { "$it hint${if (it == 1) "" else "s"}" },
        state.undoCount.takeIf { it > 0 }?.let { "$it undo${if (it == 1) "" else "s"}" }
    ).joinToString(", ")
    return "$base ($cleanSign$clean without $assists)"
}

/** Celebrate a rating personal best the moment it happens. */
private fun newPeakText(state: GameUiState): String =
    if (state.isNewPeak) " New peak rating!" else ""

/** Losses feed the coaching loop — say so instead of just "you lose". */
private fun lossEncouragement(): String = " This game becomes practice material."
