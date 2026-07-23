package com.raichess.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.raichess.domain.usecase.DrillSelector
import com.raichess.ui.game.ChessBoard

/**
 * Practice screen (Phase D): drills from the player's own analyzed
 * mistakes and from the bundled Lichess-format puzzle set, selected and
 * ordered by DrillSelector (weakness themes + spaced repetition).
 */
@Composable
fun PracticeScreen(
    state: PracticeUiState,
    onSquareTapped: (Int) -> Unit,
    onSourceChanged: (DrillSelector.Source) -> Unit,
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
            text = "Practice",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Source selector: Mixed / Mistakes / Puzzles / Lesson
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SourceChip("Mixed", state.source == DrillSelector.Source.MIXED) {
                onSourceChanged(DrillSelector.Source.MIXED)
            }
            SourceChip("Mistakes", state.source == DrillSelector.Source.MISTAKES) {
                onSourceChanged(DrillSelector.Source.MISTAKES)
            }
            SourceChip("Puzzles", state.source == DrillSelector.Source.PUZZLES) {
                onSourceChanged(DrillSelector.Source.PUZZLES)
            }
            SourceChip("Lesson", state.source == DrillSelector.Source.LESSON) {
                onSourceChanged(DrillSelector.Source.LESSON)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        when {
            state.loading -> {
                Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Loading…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            state.queueEmpty -> {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = when {
                        state.lessonComplete ->
                            "Lesson plan complete! New lessons grow as you play more games."
                        state.source == DrillSelector.Source.MISTAKES ->
                            "No analyzed mistakes to drill yet — play some Training games first."
                        else -> "Nothing to practice right now."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                // Lesson header: the unit being worked plus plan progress
                state.lessonTitle?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    state.lessonProgressText?.let { progressText ->
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = state.sourceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                // Fixed-height prompt slot: no board reflow on feedback
                Box(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.prompt,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                    ChessBoard(
                        squares = state.squares,
                        selectedSquare = state.selectedSquare,
                        legalTargets = state.legalTargets,
                        hintHighlights = state.revealHighlights,
                        lastMove = null,
                        lastMoveByOpponent = false,
                        checkedKingSquare = null,
                        hiddenSquare = null,
                        flipped = !state.playerIsWhite,
                        onSquareTapped = onSquareTapped
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                // Practice rating rises with solves, so progress is visible
                // and the difficulty window follows demonstrated skill
                val ratingSuffix = state.practiceRating?.let { " · Puzzle rating $it" } ?: ""
                Text(
                    text = "Solved ${state.solvedCount} of ${state.attemptedCount}$ratingSuffix",
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
            if (!state.loading && !state.queueEmpty) {
                Button(onClick = onNext) {
                    Text(if (state.phase == DrillPhase.SOLVING) "Skip" else "Next")
                }
            }
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}
