package com.mahin.hytune.ui.model

data class AppItemUiModel(
    val packageName: String,
    val appLabel: String,
    val isGame: Boolean,
    val configuredProfileId: String? = null,
    val targetRefreshRate: Int? = null,
    val dndEnabled: Boolean = false
)
