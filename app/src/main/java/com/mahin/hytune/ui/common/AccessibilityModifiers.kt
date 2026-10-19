package com.mahin.hytune.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.accessibleTouchTarget(minSize: Dp = 48.dp): Modifier = this.defaultMinSize(
    minWidth = minSize,
    minHeight = minSize
)

fun Modifier.talkBackAction(
    label: String,
    role: Role? = Role.Button,
    onClick: () -> Unit
): Modifier = this
    .accessibleTouchTarget()
    .semantics {
        if (role != null) {
            this.role = role
        }
        this.contentDescription = label
    }
    .clickable(onClick = onClick)

fun Modifier.telemetryAnnouncement(label: String, value: String): Modifier = this.semantics(mergeDescendants = true) {
    this.contentDescription = "$label: $value"
}

fun Modifier.profileSemantics(
    profileName: String,
    isSelected: Boolean
): Modifier = this
    .accessibleTouchTarget()
    .semantics {
        this.role = Role.RadioButton
        this.contentDescription = "$profileName profile"
        this.stateDescription = if (isSelected) "Active profile" else "Available profile"
    }

fun Modifier.profileSelectionSemantics(
    profileName: String,
    isSelected: Boolean,
    onClick: () -> Unit
): Modifier = this
    .profileSemantics(profileName, isSelected)
    .clickable(onClick = onClick)

fun Modifier.toggleSemantics(
    label: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
): Modifier = this
    .accessibleTouchTarget()
    .semantics {
        this.role = Role.Switch
        this.contentDescription = label
        this.stateDescription = if (isEnabled) "Enabled" else "Disabled"
    }
    .clickable { onToggle(!isEnabled) }

fun Modifier.loadingSemantics(description: String = "Loading hardware telemetry"): Modifier = this.semantics {
    this.contentDescription = description
}
