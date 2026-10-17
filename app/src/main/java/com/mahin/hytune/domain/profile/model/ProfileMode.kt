package com.mahin.hytune.domain.profile.model

enum class ProfileMode(val id: String, val displayName: String) {
    POWERSAVE("powersave", "Powersave"),
    BALANCE("balance", "Balance"),
    PERFORMANCE("performance", "Performance"),
    FAST("fast", "Fast"),
    CUSTOM("custom", "Custom");

    companion object {
        fun fromId(id: String): ProfileMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BALANCE
        }
    }
}
