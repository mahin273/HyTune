package com.mahin.hytune.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahin.hytune.data.local.db.entity.ProfileEntity
import com.mahin.hytune.ui.common.profileSemantics

@Composable
fun ProfileModeSelector(
    profiles: List<ProfileEntity>,
    activeProfileId: String,
    onProfileSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        profiles.forEach { profile ->
            val isSelected = profile.id == activeProfileId
            FilterChip(
                selected = isSelected,
                onClick = { onProfileSelected(profile.id) },
                label = { Text(text = profile.name) },
                modifier = Modifier.profileSemantics(profile.name, isSelected),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}
