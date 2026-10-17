package com.mahin.hytune.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mahin.hytune.domain.cpu.model.CpuCoreState
import com.mahin.hytune.ui.theme.MintSecondaryDark
import com.mahin.hytune.ui.theme.MonospaceCodeStyle

@Composable
fun CpuCoreGrid(
    coreStates: List<CpuCoreState>,
    liveFrequenciesKHz: Map<Int, Long>,
    onCoreToggled: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CPU Cores",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val onlineCount = coreStates.count { it.isOnline }
                Text(
                    text = "$onlineCount / ${coreStates.size} Online",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                coreStates.forEach { core ->
                    val isOnline = core.isOnline
                    val curFreq = liveFrequenciesKHz[core.coreId]
                    val freqText = if (isOnline && curFreq != null && curFreq > 0) {
                        "%.1fG".format(curFreq / 1_000_000f)
                    } else if (isOnline) {
                        "ON"
                    } else {
                        "OFF"
                    }

                    val containerColor = if (isOnline) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    }

                    val statusDotColor = if (isOnline) MintSecondaryDark else Color.Gray

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(containerColor)
                            .clickable(enabled = core.isConfigurable) {
                                onCoreToggled(core.coreId, !isOnline)
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(statusDotColor)
                                )
                                Text(
                                    text = "C${core.coreId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = freqText,
                                style = MonospaceCodeStyle.copy(
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    color = if (isOnline) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
