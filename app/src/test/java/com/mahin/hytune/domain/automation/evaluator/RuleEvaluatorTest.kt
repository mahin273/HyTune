package com.mahin.hytune.domain.automation.evaluator

import com.mahin.hytune.domain.automation.model.AutomationRule
import com.mahin.hytune.domain.automation.model.RuleActionType
import com.mahin.hytune.domain.automation.model.RuleTriggerType
import com.mahin.hytune.domain.automation.model.SystemStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuleEvaluatorTest {

    private lateinit var evaluator: RuleEvaluator

    @Before
    fun setUp() {
        evaluator = RuleEvaluator()
    }

    @Test
    fun evaluateWinningRules_withBatteryPercentBelow_triggersRule() {
        val rule = AutomationRule(
            id = 1L,
            name = "Low Battery Saver",
            isEnabled = true,
            priority = 50,
            triggerType = RuleTriggerType.BATTERY_PERCENT_BELOW,
            triggerValue = "20",
            actionType = RuleActionType.APPLY_PROFILE,
            actionValue = "powersave"
        )

        val prevState = SystemStateSnapshot(batteryPercent = 25)
        val currState = SystemStateSnapshot(batteryPercent = 19)

        val winning = evaluator.evaluateWinningRules(listOf(rule), prevState, currState)
        assertEquals(1, winning.size)
        assertEquals(1L, winning.first().id)
    }

    @Test
    fun evaluateWinningRules_whenOnCooldown_skipsRule() {
        val rule = AutomationRule(
            id = 1L,
            name = "Low Battery Saver",
            isEnabled = true,
            priority = 50,
            triggerType = RuleTriggerType.BATTERY_PERCENT_BELOW,
            triggerValue = "20",
            actionType = RuleActionType.APPLY_PROFILE,
            actionValue = "powersave",
            cooldownSeconds = 60,
            lastTriggeredEpochMs = 100000L
        )

        val prevState = SystemStateSnapshot(batteryPercent = 25)
        val currState = SystemStateSnapshot(batteryPercent = 18)

        // Only 10 seconds elapsed, cooldown is 60s
        val winning = evaluator.evaluateWinningRules(
            listOf(rule),
            prevState,
            currState,
            currentTimeMs = 110000L
        )
        assertTrue(winning.isEmpty())

        // 70 seconds elapsed, cooldown expired
        val winningAfterCooldown = evaluator.evaluateWinningRules(
            listOf(rule),
            prevState,
            currState,
            currentTimeMs = 170000L
        )
        assertEquals(1, winningAfterCooldown.size)
    }

    @Test
    fun evaluateWinningRules_withConflictingActions_highestPriorityWins() {
        val lowPriorityRule = AutomationRule(
            id = 1L,
            name = "Normal Game Boost",
            isEnabled = true,
            priority = 40,
            triggerType = RuleTriggerType.FOREGROUND_APP_OPENED,
            triggerValue = "com.test.game",
            actionType = RuleActionType.APPLY_PROFILE,
            actionValue = "performance"
        )

        val highPriorityRule = AutomationRule(
            id = 2L,
            name = "Emergency Thermal Throttle",
            isEnabled = true,
            priority = 90,
            triggerType = RuleTriggerType.TEMPERATURE_ABOVE,
            triggerValue = "45",
            actionType = RuleActionType.APPLY_PROFILE,
            actionValue = "powersave"
        )

        val prevState = SystemStateSnapshot(
            temperatureC = 42f,
            foregroundPackage = "com.android.launcher"
        )
        val currState = SystemStateSnapshot(
            temperatureC = 46f,
            foregroundPackage = "com.test.game"
        )

        // Both triggers match, but highPriorityRule (priority 90) must override lowPriorityRule (priority 40)
        val winning = evaluator.evaluateWinningRules(
            listOf(lowPriorityRule, highPriorityRule),
            prevState,
            currState
        )

        assertEquals(1, winning.size)
        assertEquals(2L, winning.first().id)
        assertEquals("powersave", winning.first().actionValue)
    }

    @Test
    fun evaluateWinningRules_withChargerAndScreenEvents_detectsTransitions() {
        val chargerRule = AutomationRule(
            id = 1L,
            name = "On AC Plugged",
            isEnabled = true,
            priority = 50,
            triggerType = RuleTriggerType.CHARGER_CONNECTED,
            actionType = RuleActionType.APPLY_PROFILE,
            actionValue = "balance"
        )

        val screenOffRule = AutomationRule(
            id = 2L,
            name = "On Screen Off",
            isEnabled = true,
            priority = 60,
            triggerType = RuleTriggerType.SCREEN_OFF,
            actionType = RuleActionType.FREEZE_BACKGROUND_APPS
        )

        val s1 = SystemStateSnapshot(isCharging = false, screenOn = true)
        val s2 = SystemStateSnapshot(isCharging = true, screenOn = false)

        val winning = evaluator.evaluateWinningRules(listOf(chargerRule, screenOffRule), s1, s2)
        // Two distinct action types -> both can win simultaneously
        assertEquals(2, winning.size)
    }
}
