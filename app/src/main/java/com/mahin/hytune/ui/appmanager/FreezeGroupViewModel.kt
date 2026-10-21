package com.mahin.hytune.ui.appmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.domain.appmanager.FreezeGroupRepository
import com.mahin.hytune.domain.appmanager.FreezeShortcutManager
import com.mahin.hytune.domain.appmanager.model.FreezeGroup
import com.mahin.hytune.domain.appmanager.model.GroupOperationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FreezeGroupUiState(
    val groups: List<FreezeGroup> = emptyList(),
    val operatingGroupId: Long? = null,
    val statusMessage: String? = null,
    val isCreatingOrEditing: Boolean = false,
    val selectedGroup: FreezeGroup? = null
)

@HiltViewModel
class FreezeGroupViewModel @Inject constructor(
    private val repository: FreezeGroupRepository,
    private val shortcutManager: FreezeShortcutManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _operatingGroupId = MutableStateFlow<Long?>(null)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _isCreatingOrEditing = MutableStateFlow(false)
    private val _selectedGroup = MutableStateFlow<FreezeGroup?>(null)

    val groups: StateFlow<List<FreezeGroup>> = repository.getAllGroups()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val operatingGroupId: StateFlow<Long?> = _operatingGroupId.asStateFlow()
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()
    val isCreatingOrEditing: StateFlow<Boolean> = _isCreatingOrEditing.asStateFlow()
    val selectedGroup: StateFlow<FreezeGroup?> = _selectedGroup.asStateFlow()

    fun openCreateDialog() {
        _selectedGroup.value = null
        _isCreatingOrEditing.value = true
    }

    fun openEditDialog(group: FreezeGroup) {
        _selectedGroup.value = group
        _isCreatingOrEditing.value = true
    }

    fun dismissDialog() {
        _selectedGroup.value = null
        _isCreatingOrEditing.value = false
    }

    fun saveGroup(
        id: Long = 0,
        name: String,
        description: String = "",
        autoFreeze: Boolean = false,
        delaySeconds: Int = 30,
        packages: List<String> = emptyList()
    ) {
        viewModelScope.launch(ioDispatcher) {
            val group = FreezeGroup(
                id = id,
                name = name,
                description = description,
                autoFreezeOnScreenOff = autoFreeze,
                autoFreezeDelaySeconds = delaySeconds,
                packages = packages
            )
            repository.saveGroup(group)
            _statusMessage.value = "Group '${name.trim()}' saved"
            dismissDialog()
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch(ioDispatcher) {
            repository.deleteGroup(groupId)
            _statusMessage.value = "Group deleted"
        }
    }

    fun addPackageToGroup(groupId: Long, packageName: String) {
        viewModelScope.launch(ioDispatcher) {
            repository.addPackageToGroup(groupId, packageName)
            _statusMessage.value = "Added $packageName to group"
        }
    }

    fun removePackageFromGroup(groupId: Long, packageName: String) {
        viewModelScope.launch(ioDispatcher) {
            repository.removePackageFromGroup(groupId, packageName)
            _statusMessage.value = "Removed $packageName from group"
        }
    }

    fun togglePackageInGroup(groupId: Long, packageName: String, add: Boolean) {
        if (add) {
            addPackageToGroup(groupId, packageName)
        } else {
            removePackageFromGroup(groupId, packageName)
        }
    }

    fun freezeGroup(groupId: Long) {
        viewModelScope.launch(ioDispatcher) {
            _operatingGroupId.value = groupId
            val result = repository.freezeGroup(groupId)
            result.onSuccess { op ->
                _statusMessage.value = "Frozen ${op.successCount}/${op.totalPackages} apps in ${op.groupName}"
            }.onFailure { err ->
                _statusMessage.value = "Failed to freeze group: ${err.message}"
            }
            _operatingGroupId.value = null
        }
    }

    fun unfreezeGroup(groupId: Long) {
        viewModelScope.launch(ioDispatcher) {
            _operatingGroupId.value = groupId
            val result = repository.unfreezeGroup(groupId)
            result.onSuccess { op ->
                _statusMessage.value = "Unfrozen ${op.successCount}/${op.totalPackages} apps in ${op.groupName}"
            }.onFailure { err ->
                _statusMessage.value = "Failed to unfreeze group: ${err.message}"
            }
            _operatingGroupId.value = null
        }
    }

    fun pinShortcut(group: FreezeGroup): Boolean {
        val success = shortcutManager.requestPinGroupShortcut(group)
        if (success) {
            _statusMessage.value = "Shortcut requested for ${group.name}"
        } else {
            _statusMessage.value = "Launcher does not support pinned shortcuts"
        }
        return success
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
