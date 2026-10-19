package com.mahin.hytune.domain.sysfs.model

data class SysfsNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val isReadable: Boolean = true,
    val isWritable: Boolean = false,
    val sizeBytes: Long = 0,
    val value: String? = null
)
