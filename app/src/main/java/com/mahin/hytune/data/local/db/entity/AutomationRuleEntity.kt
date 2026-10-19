package com.mahin.hytune.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mahin.hytune.domain.automation.model.AutomationRule
import com.mahin.hytune.domain.automation.model.RuleActionType
import com.mahin.hytune.domain.automation.model.RuleTriggerType

@Entity(
    tableName = "automation_rules",
    indices = [
        Index("isEnabled"),
        Index("priority")
    ]
)
data class AutomationRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val isEnabled: Boolean = true,
    val priority: Int = 50,
    val triggerType: String,
    val triggerValue: String = "",
    val conditionJson: String = "[]",
    val actionType: String,
    val actionValue: String = "",
    val cooldownSeconds: Int = 30,
    val lastTriggeredEpochMs: Long = 0L
) {
    fun toDomain(): AutomationRule {
        val trigger = try {
            RuleTriggerType.valueOf(triggerType)
        } catch (_: Exception) {
            RuleTriggerType.BATTERY_PERCENT_BELOW
        }
        val action = try {
            RuleActionType.valueOf(actionType)
        } catch (_: Exception) {
            RuleActionType.APPLY_PROFILE
        }
        return AutomationRule(
            id = id,
            name = name,
            isEnabled = isEnabled,
            priority = priority,
            triggerType = trigger,
            triggerValue = triggerValue,
            conditionJson = conditionJson,
            actionType = action,
            actionValue = actionValue,
            cooldownSeconds = cooldownSeconds,
            lastTriggeredEpochMs = lastTriggeredEpochMs
        )
    }

    companion object {
        fun fromDomain(rule: AutomationRule): AutomationRuleEntity {
            return AutomationRuleEntity(
                id = rule.id,
                name = rule.name,
                isEnabled = rule.isEnabled,
                priority = rule.priority,
                triggerType = rule.triggerType.name,
                triggerValue = rule.triggerValue,
                conditionJson = rule.conditionJson,
                actionType = rule.actionType.name,
                actionValue = rule.actionValue,
                cooldownSeconds = rule.cooldownSeconds,
                lastTriggeredEpochMs = rule.lastTriggeredEpochMs
            )
        }
    }
}
