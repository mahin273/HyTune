package com.mahin.hytune.domain.automation.model

enum class RuleTriggerType {
    BATTERY_PERCENT_BELOW,
    BATTERY_PERCENT_ABOVE,
    TEMPERATURE_ABOVE,
    CHARGER_CONNECTED,
    CHARGER_DISCONNECTED,
    SCREEN_ON,
    SCREEN_OFF,
    FOREGROUND_APP_OPENED
}
