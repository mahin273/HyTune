package com.mahin.hytune.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahin.hytune.domain.sysfs.SysfsRepository
import com.mahin.hytune.domain.sysfs.model.SysfsNode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SysfsUiState(
    val currentPath: String = "/sys",
    val nodes: List<SysfsNode> = emptyList(),
    val selectedNode: SysfsNode? = null,
    val selectedNodeContent: String? = null,
    val isLoading: Boolean = false,
    val isWriting: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class SysfsViewModel @Inject constructor(
    private val sysfsRepository: SysfsRepository
) : ViewModel() {

    private val _currentPath = MutableStateFlow("/sys")
    private val _nodes = MutableStateFlow<List<SysfsNode>>(emptyList())
    private val _selectedNode = MutableStateFlow<SysfsNode?>(null)
    private val _selectedNodeContent = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _isWriting = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SysfsUiState> = combine(
        _currentPath,
        _nodes,
        _selectedNode,
        _selectedNodeContent,
        combine(_isLoading, _isWriting, _statusMessage, _errorMessage) { loading, writing, status, error ->
            Quad(loading, writing, status, error)
        }
    ) { path, nodes, selectedNode, content, quad ->
        SysfsUiState(
            currentPath = path,
            nodes = nodes,
            selectedNode = selectedNode,
            selectedNodeContent = content,
            isLoading = quad.first,
            isWriting = quad.second,
            statusMessage = quad.third,
            errorMessage = quad.fourth
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SysfsUiState()
    )

    init {
        navigateTo("/sys")
    }

    fun navigateTo(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _statusMessage.value = null

            val result = sysfsRepository.listDirectory(path)
            result.onSuccess { list ->
                _currentPath.value = path
                _nodes.value = list
            }.onFailure { ex ->
                _errorMessage.value = ex.message ?: "Failed to list directory: $path"
            }

            _isLoading.value = false
        }
    }

    fun navigateUp() {
        val curr = _currentPath.value
        if (curr == "/sys" || curr == "/proc" || curr == "/") return

        val parent = curr.substringBeforeLast("/")
        val target = if (parent.isEmpty()) "/" else parent
        if (target == "/sys" || target.startsWith("/sys/") || target == "/proc" || target.startsWith("/proc/")) {
            navigateTo(target)
        } else {
            navigateTo("/sys")
        }
    }

    fun selectNode(node: SysfsNode) {
        if (node.isDirectory) {
            navigateTo(node.path)
        } else {
            viewModelScope.launch {
                _selectedNode.value = node
                _selectedNodeContent.value = null
                _errorMessage.value = null

                val result = sysfsRepository.readNode(node.path)
                result.onSuccess { content ->
                    _selectedNodeContent.value = content
                }.onFailure { ex ->
                    _errorMessage.value = ex.message ?: "Failed to read node"
                }
            }
        }
    }

    fun writeNodeValue(path: String, value: String) {
        viewModelScope.launch {
            _isWriting.value = true
            _errorMessage.value = null
            _statusMessage.value = null

            val result = sysfsRepository.writeNode(path, value)
            result.onSuccess {
                _statusMessage.value = "Successfully written to $path"
                // Refresh node content
                val readBack = sysfsRepository.readNode(path)
                readBack.onSuccess { updated ->
                    _selectedNodeContent.value = updated
                }
            }.onFailure { ex ->
                _errorMessage.value = ex.message ?: "Failed to write node"
            }

            _isWriting.value = false
        }
    }

    fun clearSelectedNode() {
        _selectedNode.value = null
        _selectedNodeContent.value = null
    }

    fun clearMessages() {
        _errorMessage.value = null
        _statusMessage.value = null
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
