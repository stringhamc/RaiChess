package com.raichess.ui.review

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raichess.ui.game.ChessBoard
import com.raichess.ui.game.LastMove
import kotlin.math.sqrt

/**
 * Post-game review: steps through the player's graded mistakes from the
 * last analyzed game — the played move highlighted on the board, the
 * engine's best move in hint styling, and the tagged reason in prose.
 */
@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Game Review",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))

        when {
            state.loading -> Text(
                "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            state.noGames -> ReviewMessage(
                "Play a game and it'll be analyzed for review here."
            )
            state.analysisPending -> ReviewMessage(
                "Your last game is still being analyzed — check back in a moment."
            )
            state.cleanGame -> {
                Headline(state.headline)
                Spacer(modifier = Modifier.height(24.dp))
                ReviewMessage("No graded mistakes in this game — clean play!")
            }
            else -> {
                val mistake = state.mistakes[state.index]
                Headline(state.headline)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Mistake ${state.index + 1} of ${state.mistakes.size}" +
                        " · move ${mistake.moveNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                // Fixed-height detail slot: no board reflow while stepping
                Box(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mistake.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 3
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                    ChessBoard(
                        squares = mistake.squares,
                        selectedSquare = null,
                        legalTargets = emptySet(),
                        hintHighlights = mistake.bestHighlights,
                        lastMove = mistake.playedMove,
                        lastMoveByOpponent = false,
                        checkedKingSquare = null,
                        hiddenSquare = null,
                        flipped = !state.playerIsWhite,
                        onSquareTapped = {}
                    )
                    MoveArrowOverlay(
                        played = mistake.playedMove,
                        best = mistake.bestMove,
                        flipped = !state.playerIsWhite,
                        // Same 2dp inset as ChessBoard's border/padding, so
                        // cell centers line up with the real squares
                        modifier = Modifier.matchParentSize().padding(2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = mistake.classificationLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "✕ your move   ○ engine's best",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            if (state.mistakes.size > 1) {
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = state.index > 0
                ) { Text("Prev") }
                Button(
                    onClick = onNext,
                    enabled = state.index < state.mistakes.size - 1
                ) { Text("Next") }
            }
        }
    }
}

/**
 * Arrows over the review board: the player's move in mid-gray ending in a
 * ✕, the engine's best in white ending in a ○. Shape carries the meaning
 * (not just luminance), keeping the monochrome style colorblind-friendly;
 * a dark halo under every stroke keeps both readable on light and dark
 * squares alike.
 */
@Composable
private fun MoveArrowOverlay(
    played: LastMove?,
    best: LastMove?,
    flipped: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cell = size.width / 8f
        val halo = Color(0xAA000000)
        val playedColor = Color(0xFFB8B8B8)
        val bestColor = Color.White

        fun center(square: Int): Offset {
            val col = square % 8
            val row = square / 8
            val x = if (flipped) 7 - col else col
            val y = if (flipped) row else 7 - row
            return Offset((x + 0.5f) * cell, (y + 0.5f) * cell)
        }

        fun arrow(move: LastMove, color: Color) {
            val from = center(move.from)
            val to = center(move.to)
            val dx = to.x - from.x
            val dy = to.y - from.y
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1f) return
            val ux = dx / len
            val uy = dy / len
            // Shaft stops short of the destination marker
            val start = Offset(from.x + ux * cell * 0.28f, from.y + uy * cell * 0.28f)
            val end = Offset(to.x - ux * cell * 0.52f, to.y - uy * cell * 0.52f)
            drawLine(halo, start, end, strokeWidth = cell * 0.20f, cap = StrokeCap.Round)
            drawLine(color, start, end, strokeWidth = cell * 0.12f, cap = StrokeCap.Round)
            // Head pointing into the marker
            val tip = Offset(to.x - ux * cell * 0.32f, to.y - uy * cell * 0.32f)
            val base = Offset(to.x - ux * cell * 0.54f, to.y - uy * cell * 0.54f)
            val head = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(base.x - uy * cell * 0.18f, base.y + ux * cell * 0.18f)
                lineTo(base.x + uy * cell * 0.18f, base.y - ux * cell * 0.18f)
                close()
            }
            drawPath(head, halo, style = Stroke(width = cell * 0.08f))
            drawPath(head, color)
        }

        played?.let { move ->
            arrow(move, playedColor)
            val c = center(move.to)
            val r = cell * 0.20f
            listOf(
                Offset(c.x - r, c.y - r) to Offset(c.x + r, c.y + r),
                Offset(c.x - r, c.y + r) to Offset(c.x + r, c.y - r)
            ).forEach { (a, b) ->
                drawLine(halo, a, b, strokeWidth = cell * 0.17f, cap = StrokeCap.Round)
                drawLine(playedColor, a, b, strokeWidth = cell * 0.10f, cap = StrokeCap.Round)
            }
        }
        best?.let { move ->
            arrow(move, bestColor)
            val c = center(move.to)
            drawCircle(halo, radius = cell * 0.25f, center = c, style = Stroke(width = cell * 0.17f))
            drawCircle(bestColor, radius = cell * 0.25f, center = c, style = Stroke(width = cell * 0.10f))
        }
    }
}

@Composable
private fun Headline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
private fun ReviewMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 24.dp)
    )
}
