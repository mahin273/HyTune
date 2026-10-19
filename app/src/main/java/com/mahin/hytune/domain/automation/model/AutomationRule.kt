package com.mahin.hytune.domain.automation.model

data class AutomationRule(
    val id: Long = 0L,
    val name: String,
    val isEnabled: Boolean = true,
    val priority: Int = 50,
    val triggerType: RuleTriggerType,
    val triggerValue: String = "",
    val conditionJson: String = "[]",
    val actionType: RuleActionType,
    val actionValue: String = "",
    val cooldownSeconds: Int = 30,
    val lastTriggeredEpochMs: Long = 0L
)
