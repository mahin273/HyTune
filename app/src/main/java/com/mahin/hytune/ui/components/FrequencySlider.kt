package com.mahin.hytune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.mahin.hytune.ui.common.accessibleTouchTarget
import com.mahin.hytune.ui.theme.MonospaceCodeStyle
import kotlin.math.roundToInt

@Composable
fun FrequencySlider(
    label: String,
    currentFreqKHz: Long,
    availableFrequenciesKHz: List<Long>,
    onFreqChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableFrequenciesKHz.isEmpty()) return

    val sortedFreqs = availableFrequenciesKHz.sorted()
    val currentIndex = sortedFreqs.indexOf(currentFreqKHz).coerceAtLeast(0)
    val maxIndex = (sortedFreqs.size - 1).coerceAtLeast(1)
    val freqMhz = currentFreqKHz / 1000f
    val freqDisplay = if (freqMhz >= 1000f) "%.2f GHz".format(freqMhz / 1000f) else "%.0f MHz".format(freqMhz)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = freqDisplay,
                style = MonospaceCodeStyle.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
            )
        }

        if (sortedFreqs.size > 1) {
            Slider(
                value = currentIndex.toFloat(),
                onValueChange = { floatIndex ->
                    val snappedIndex = floatIndex.roundToInt().coerceIn(0, sortedFreqs.size - 1)
                    val snappedFreq = sortedFreqs[snappedIndex]
                    if (snappedFreq != currentFreqKHz) {
                        onFreqChanged(snappedFreq)
                    }
                },
                valueRange = 0f..maxIndex.toFloat(),
                steps = (sortedFreqs.size - 2).coerceAtLeast(0),
                modifier = Modifier
                    .fillMaxWidth()
                    .accessibleTouchTarget()
                    .semantics {
                        this.contentDescription = "$label slider"
                        this.stateDescription = freqDisplay
                    }
            )
        }
    }
}
