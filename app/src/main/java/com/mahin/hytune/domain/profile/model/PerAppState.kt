package com.mahin.hytune.domain.profile.model

data class PerAppState(
    val packageName: String,
    val profileId: String,
    val isGame: Boolean,
    val targetRefreshRate: Int? = null,
    val dndEnabled: Boolean = false,
    val baseProfileId: String
)
