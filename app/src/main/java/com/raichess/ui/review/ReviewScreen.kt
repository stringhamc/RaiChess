package com.raichess.ui.review

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raichess.ui.game.ChessBoard

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
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = mistake.classificationLabel,
                    style = MaterialTheme.typography.labelMedium,
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
