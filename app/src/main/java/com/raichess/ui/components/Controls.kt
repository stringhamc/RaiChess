package com.raichess.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raichess.ui.theme.ChessColors

/**
 * Shared scaffold for the setup-style screens (play setup, coach,
 * settings, games): consistent padding, a title (or custom [titleRow]),
 * the content, and the bottom Back button — one place instead of five
 * hand-rolled copies. Window insets are handled globally in MainActivity.
 *
 * [scrollable] = false is for screens whose content manages its own
 * scrolling (e.g. a weighted list); their content can use ColumnScope
 * weights, which a scrolling column can't offer.
 */
@Composable
internal fun RaiScreen(
    title: String,
    onBack: () -> Unit,
    scrollable: Boolean = true,
    titleRow: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
            )
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (titleRow != null) {
            titleRow()
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        content()
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

/**
 * Shared form controls for the setup-style screens (home, play setup,
 * settings): eyebrow-labelled sections, the segmented control, and the
 * framed record row. Extracted from HomeScreen when it became a launcher.
 */

/** A labelled section: an uppercase eyebrow above its content. */
@Composable
internal fun Section(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(text = label, modifier = Modifier.padding(bottom = 11.dp))
        content()
    }
}

/** Uppercase, wide-tracked eyebrow label. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
        letterSpacing = 2.sp,
        modifier = modifier
    )
}

@Composable
internal fun TickLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary
    )
}

/** Two-or-more option segmented control; the selected cell is filled. */
@Composable
internal fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .border(1.dp, ChessColors.SquareBorder.copy(alpha = 0.35f), shape)
    ) {
        options.forEachIndexed { index, label ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(ChessColors.SquareBorder.copy(alpha = 0.35f))
                )
            }
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) ChessColors.ControlActive else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) Color.Black else MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/** Framed Won / Drew / Lost record. */
@Composable
internal fun RecordRow(
    wins: Int,
    draws: Int,
    losses: Int,
    modifier: Modifier = Modifier
) {
    val line = ChessColors.SquareBorder.copy(alpha = 0.35f)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .border(1.dp, line, shape)
    ) {
        RecordCell(label = "Won", value = wins, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(line)
        )
        RecordCell(label = "Drew", value = draws, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(line)
        )
        RecordCell(label = "Lost", value = losses, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RecordCell(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$value",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}
