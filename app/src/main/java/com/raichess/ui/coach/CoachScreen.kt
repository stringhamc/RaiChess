package com.raichess.ui.coach

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raichess.domain.usecase.CoachAdvisor
import com.raichess.ui.components.RaiLogo
import com.raichess.ui.components.RaiScreen
import com.raichess.ui.components.SectionLabel
import com.raichess.ui.theme.ChessColors

/**
 * The coach's corner: personal talking points from CoachAdvisor (what
 * we're working on and why), one suggested next action, and the lesson
 * plan with progress.
 */
@Composable
fun CoachScreen(
    state: CoachUiState,
    onAction: (CoachAdvisor.Action) -> Unit,
    onBack: () -> Unit
) {
    RaiScreen(
        title = "Coach",
        onBack = onBack,
        titleRow = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RaiLogo(size = 36.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Coach",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    ) {
        if (state.loading) {
            Text(
                "Thinking…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            // The coach's message
            Text(
                text = state.headline,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = state.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )

            if (state.focuses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.focuses.forEach { focus ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = focus,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onAction(state.action) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(state.actionLabel)
            }

            if (state.planRows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                SectionLabel(
                    text = "Your plan",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                )
                val shape = RoundedCornerShape(12.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .border(1.dp, ChessColors.SquareBorder.copy(alpha = 0.35f), shape)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    state.planRows.forEach { row ->
                        PlanRow(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanRow(row: CoachPlanRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.active) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = row.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = when {
                row.done -> "✓"
                row.active -> "▸ ${row.progressText}"
                else -> row.progressText
            },
            fontSize = 13.sp,
            color = if (row.done) {
                ChessColors.ControlActive
            } else {
                MaterialTheme.colorScheme.secondary
            },
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}
