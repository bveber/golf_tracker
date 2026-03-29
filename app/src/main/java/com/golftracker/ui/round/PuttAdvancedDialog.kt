package com.golftracker.ui.round

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.golftracker.data.entity.DirectionMiss
import com.golftracker.data.entity.PaceMiss
import com.golftracker.data.entity.Putt
import com.golftracker.data.entity.PuttBreak
import com.golftracker.data.entity.PuttSlopeDirection
import com.golftracker.ui.components.ChipSelector

private val PACE_ROWS = listOf(PaceMiss.BIG_LONG, PaceMiss.LONG, PaceMiss.GOOD, PaceMiss.SHORT, PaceMiss.BIG_SHORT)
private val DIR_COLS = listOf(DirectionMiss.BIG_LEFT, DirectionMiss.LEFT, DirectionMiss.STRAIGHT, DirectionMiss.RIGHT, DirectionMiss.BIG_RIGHT)

private fun paceLabel(p: PaceMiss) = when (p) {
    PaceMiss.BIG_SHORT -> "↓↓"
    PaceMiss.SHORT -> "↓"
    PaceMiss.GOOD -> "✓"
    PaceMiss.LONG -> "↑"
    PaceMiss.BIG_LONG -> "↑↑"
}

private fun dirLabel(d: DirectionMiss) = when (d) {
    DirectionMiss.BIG_LEFT -> "←←"
    DirectionMiss.LEFT -> "←"
    DirectionMiss.STRAIGHT -> "·"
    DirectionMiss.RIGHT -> "→"
    DirectionMiss.BIG_RIGHT -> "→→"
}

private fun breakLabel(b: PuttBreak) = when (b) {
    PuttBreak.BIG_LEFT -> "←←"
    PuttBreak.SMALL_LEFT -> "←"
    PuttBreak.STRAIGHT -> "—"
    PuttBreak.SMALL_RIGHT -> "→"
    PuttBreak.BIG_RIGHT -> "→→"
}

private fun slopeLabel(s: PuttSlopeDirection) = when (s) {
    PuttSlopeDirection.STEEP_UPHILL -> "↑↑"
    PuttSlopeDirection.UPHILL -> "↑"
    PuttSlopeDirection.FLAT -> "—"
    PuttSlopeDirection.DOWNHILL -> "↓"
    PuttSlopeDirection.STEEP_DOWNHILL -> "↓↓"
}

/**
 * 5×5 grid for selecting pace miss (rows) and direction miss (columns) simultaneously.
 * Tapping a cell sets both fields; tapping the active cell clears both to null.
 */
@Composable
fun MissGrid(
    selectedPace: PaceMiss?,
    selectedDir: DirectionMiss?,
    onSelect: (PaceMiss?, DirectionMiss?) -> Unit,
    modifier: Modifier = Modifier
) {
    val cellSize = 36.dp
    val rowLabelWidth = 24.dp

    Column(modifier = modifier) {
        // Axis title
        Row {
            Spacer(modifier = Modifier.width(rowLabelWidth))
            Text(
                "Left ←→ Right",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        // Column headers
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(rowLabelWidth))
            DIR_COLS.forEach { dir ->
                Text(
                    dirLabel(dir),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.size(cellSize),
                    textAlign = TextAlign.Center
                )
            }
        }
        // Grid rows
        PACE_ROWS.forEach { pace ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    paceLabel(pace),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(rowLabelWidth),
                    textAlign = TextAlign.Center
                )
                DIR_COLS.forEach { dir ->
                    val isSelected = selectedPace == pace && selectedDir == dir
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                if (isSelected) onSelect(null, null)
                                else onSelect(pace, dir)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text(
                                "✓",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog state for a single putt during editing inside PuttAdvancedDialog.
 */
data class PuttAdvancedState(
    val putt: Putt,
    var breakDirection: PuttBreak?,
    var slopeDirection: PuttSlopeDirection?,
    var paceMiss: PaceMiss?,
    var directionMiss: DirectionMiss?
)

/**
 * Scrollable dialog with one section per putt.
 * Break + slope shown for all putts; miss grid shown only for missed putts.
 */
@Composable
fun PuttAdvancedDialog(
    putts: List<Putt>,
    onDismissRequest: () -> Unit,
    onSave: (List<PuttAdvancedState>) -> Unit
) {
    // Per-field mutable state lists so each chip/grid re-composes independently
    val breakStates = remember(putts) { putts.map { mutableStateOf(it.breakDirection) } }
    val slopeStates = remember(putts) { putts.map { mutableStateOf(it.slopeDirection) } }
    val paceStates = remember(putts) { putts.map { mutableStateOf(it.paceMiss) } }
    val dirStates = remember(putts) { putts.map { mutableStateOf(it.directionMiss) } }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Advanced Putt Details") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                putts.forEachIndexed { idx, putt ->
                    val isMade = putt.made
                    val distStr = putt.distance?.let { "${it.toInt()} ft" } ?: "—"
                    val header = if (isMade) "Putt ${idx + 1}  ($distStr — Made)" else "Putt ${idx + 1}  ($distStr)"

                    item(key = "header_$idx") {
                        if (idx > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(header, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item(key = "break_$idx") {
                        var breakVal by breakStates[idx]
                        Text("Break", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ChipSelector(
                            options = PuttBreak.values().toList(),
                            selectedOption = breakVal,
                            onOptionSelected = { breakVal = if (breakVal == it) null else it },
                            labelMapper = { breakLabel(it) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item(key = "slope_$idx") {
                        var slopeVal by slopeStates[idx]
                        Text("Slope", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ChipSelector(
                            options = PuttSlopeDirection.values().toList(),
                            selectedOption = slopeVal,
                            onOptionSelected = { slopeVal = if (slopeVal == it) null else it },
                            labelMapper = { slopeLabel(it) }
                        )
                    }

                    if (!isMade) {
                        item(key = "miss_$idx") {
                            var paceVal by paceStates[idx]
                            var dirVal by dirStates[idx]
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Miss", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            MissGrid(
                                selectedPace = paceVal,
                                selectedDir = dirVal,
                                onSelect = { p, d ->
                                    paceVal = p
                                    dirVal = d
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = putts.mapIndexed { idx, putt ->
                    PuttAdvancedState(
                        putt = putt,
                        breakDirection = breakStates[idx].value,
                        slopeDirection = slopeStates[idx].value,
                        paceMiss = if (putt.made) null else paceStates[idx].value,
                        directionMiss = if (putt.made) null else dirStates[idx].value
                    )
                }
                onSave(result)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        }
    )
}
