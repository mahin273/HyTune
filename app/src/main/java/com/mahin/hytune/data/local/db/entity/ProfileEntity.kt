package com.mahin.hytune.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val isBuiltIn: Boolean = false,
    val cpuGovernor: String? = null,
    val cpuMinFreqKHz: Long? = null,
    val cpuMaxFreqKHz: Long? = null,
    val gpuGovernor: String? = null,
    val gpuMinFreqKHz: Long? = null,
    val gpuMaxFreqKHz: Long? = null,
    val zramEnabled: Boolean? = null,
    val ioScheduler: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
