package com.mahin.hytune.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mahin.hytune.domain.script.model.CustomScript

@Entity(
    tableName = "custom_scripts",
    indices = [
        Index(value = ["runAtBoot"])
    ]
)
data class CustomScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val scriptContent: String,
    val runAtBoot: Boolean = false,
    val runAsRoot: Boolean = true,
    val lastExecutedEpochMs: Long = 0,
    val lastExitCode: Int? = null,
    val lastOutput: String? = null,
    val lastDurationMs: Long = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    fun toDomain(): CustomScript = CustomScript(
        id = id,
        name = name,
        scriptContent = scriptContent,
        runAtBoot = runAtBoot,
        runAsRoot = runAsRoot,
        lastExecutedEpochMs = lastExecutedEpochMs,
        lastExitCode = lastExitCode,
        lastOutput = lastOutput,
        lastDurationMs = lastDurationMs,
        createdAtEpochMs = createdAtEpochMs
    )

    companion object {
        fun fromDomain(domain: CustomScript): CustomScriptEntity = CustomScriptEntity(
            id = domain.id,
            name = domain.name,
            scriptContent = domain.scriptContent,
            runAtBoot = domain.runAtBoot,
            runAsRoot = domain.runAsRoot,
            lastExecutedEpochMs = domain.lastExecutedEpochMs,
            lastExitCode = domain.lastExitCode,
            lastOutput = domain.lastOutput,
            lastDurationMs = domain.lastDurationMs,
            createdAtEpochMs = domain.createdAtEpochMs
        )
    }
}
