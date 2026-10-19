package com.mahin.hytune.domain.automation.evaluator

import com.mahin.hytune.domain.automation.model.AutomationRule
import com.mahin.hytune.domain.automation.model.RuleTriggerType
import com.mahin.hytune.domain.automation.model.SystemStateSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleEvaluator @Inject constructor() {

    fun evaluateWinningRules(
        rules: List<AutomationRule>,
        prevState: SystemStateSnapshot,
        currState: SystemStateSnapshot,
        currentTimeMs: Long = System.currentTimeMillis()
    ): List<AutomationRule> {
        val matchingRules = mutableListOf<AutomationRule>()

        // Rules are expected to be ordered by priority descending
        val sortedRules = rules.filter { it.isEnabled }.sortedByDescending { it.priority }

        for (rule in sortedRules) {
            if (isCooldownActive(rule, currentTimeMs)) {
                continue
            }

            if (isTriggerMatched(rule, prevState, currState)) {
                matchingRules.add(rule)
            }
        }

        // Conflict resolution: only highest priority rule per actionType executes
        return matchingRules.distinctBy { it.actionType }
    }

    fun isCooldownActive(rule: AutomationRule, currentTimeMs: Long): Boolean {
        if (rule.lastTriggeredEpochMs <= 0L || rule.cooldownSeconds <= 0) {
            return false
        }
        val elapsedMs = currentTimeMs - rule.lastTriggeredEpochMs
        return elapsedMs < (rule.cooldownSeconds * 1000L)
    }

    fun isTriggerMatched(
        rule: AutomationRule,
        prevState: SystemStateSnapshot,
        currState: SystemStateSnapshot
    ): Boolean {
        return when (rule.triggerType) {
            RuleTriggerType.BATTERY_PERCENT_BELOW -> {
                val threshold = rule.triggerValue.toIntOrNull() ?: 20
                (prevState.batteryPercent >= threshold && currState.batteryPercent < threshold) ||
                        (currState.batteryPercent < threshold && (prevState.timestampEpochMs == 0L || prevState.batteryPercent < threshold))
            }
            RuleTriggerType.BATTERY_PERCENT_ABOVE -> {
                val threshold = rule.triggerValue.toIntOrNull() ?: 80
                (prevState.batteryPercent <= threshold && currState.batteryPercent > threshold) ||
                        (currState.batteryPercent > threshold && (prevState.timestampEpochMs == 0L || prevState.batteryPercent > threshold))
            }
            RuleTriggerType.TEMPERATURE_ABOVE -> {
                val threshold = rule.triggerValue.toFloatOrNull() ?: 45.0f
                (prevState.temperatureC < threshold && currState.temperatureC >= threshold) ||
                        (currState.temperatureC >= threshold && (prevState.timestampEpochMs == 0L || prevState.temperatureC >= threshold))
            }
            RuleTriggerType.CHARGER_CONNECTED -> {
                !prevState.isCharging && currState.isCharging
            }
            RuleTriggerType.CHARGER_DISCONNECTED -> {
                prevState.isCharging && !currState.isCharging
            }
            RuleTriggerType.SCREEN_ON -> {
                !prevState.screenOn && currState.screenOn
            }
            RuleTriggerType.SCREEN_OFF -> {
                prevState.screenOn && !currState.screenOn
            }
            RuleTriggerType.FOREGROUND_APP_OPENED -> {
                val targetPkg = rule.triggerValue.trim()
                targetPkg.isNotEmpty() &&
                        currState.foregroundPackage == targetPkg &&
                        prevState.foregroundPackage != targetPkg
            }
        }
    }
}
