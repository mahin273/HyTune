package com.mahin.hytune.core.privilege

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivilegeManager @Inject constructor(
    private val rootShellRunner: RootShellRunner,
    private val shizukuShellRunner: ShizukuShellRunner,
    private val rootBackend: PrivilegeBackend,
    private val shizukuBackend: PrivilegeBackend,
    private val unprivilegedBackend: PrivilegeBackend
) {

    private var manualOverride: PrivilegeBackend? = null

    private val _activeBackend = MutableStateFlow(resolveBackend())
    val activeBackend: StateFlow<PrivilegeBackend> = _activeBackend.asStateFlow()

    fun refresh() {
        _activeBackend.value = resolveBackend()
    }

    fun setOverride(backend: PrivilegeBackend?) {
        manualOverride = backend
        _activeBackend.value = resolveBackend()
    }

    fun resolveBackend(): PrivilegeBackend {
        manualOverride?.let { return it }

        if (rootShellRunner.isRootAvailable()) {
            return rootBackend
        }

        if (shizukuShellRunner.isShizukuAvailable()) {
            return shizukuBackend
        }

        return unprivilegedBackend
    }
}
