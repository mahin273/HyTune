package com.mahin.hytune.core.privilege

enum class Capability {
    SYSFS_READ,
    SYSFS_WRITE,
    APP_FREEZE,
    DUMPSYS,
    PROCESS_CONTROL,
    SETTINGS_WRITE
}
