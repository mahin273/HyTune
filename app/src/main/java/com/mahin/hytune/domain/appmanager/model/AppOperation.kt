package com.mahin.hytune.domain.appmanager.model

enum class AppOperation(val displayName: String, val isDestructive: Boolean) {
    FREEZE("Freeze", true),
    UNFREEZE("Unfreeze", false),
    SUSPEND("Suspend", true),
    UNSUSPEND("Unsuspend", false),
    HIDE("Hide", true),
    UNHIDE("Unhide", false),
    FORCE_STOP("Force Stop", false),
    CLEAR_DATA("Clear Data", true),
    UNINSTALL_USER_0("Uninstall (User 0)", true),
    REINSTALL_EXISTING("Reinstall Existing", false)
}

data class OperationResult(
    val packageName: String,
    val operation: AppOperation,
    val success: Boolean,
    val message: String? = null,
    val commandExecuted: String = ""
)
