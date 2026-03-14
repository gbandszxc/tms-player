package com.example.tvmediaplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvmediaplayer.data.repo.FakeSmbRepository
import com.example.tvmediaplayer.domain.model.SmbConfig
import com.example.tvmediaplayer.domain.model.SmbEntry
import com.example.tvmediaplayer.domain.repo.SmbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvBrowserState(
    val config: SmbConfig = SmbConfig.Empty,
    val currentPath: String = "",
    val entries: List<SmbEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val toast: String? = null
)

class TvBrowserViewModel(
    private val repository: SmbRepository = FakeSmbRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(TvBrowserState())
    val state: StateFlow<TvBrowserState> = _state.asStateFlow()

    fun saveConfig(config: SmbConfig) {
        _state.update { it.copy(config = config, currentPath = config.normalizedPath(), error = null) }
        loadCurrentPath()
    }

    fun loadCurrentPath() {
        val snapshot = _state.value
        if (snapshot.config.host.isBlank() || snapshot.config.share.isBlank()) {
            _state.update { it.copy(error = "SMB 主机地址和共享名不能为空") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, toast = null) }
            runCatching {
                repository.list(snapshot.config, snapshot.currentPath)
            }.onSuccess { list ->
                _state.update { it.copy(entries = list, loading = false) }
            }.onFailure { ex ->
                _state.update { it.copy(loading = false, error = ex.message ?: "SMB 浏览失败") }
            }
        }
    }

    fun enterDirectory(entry: SmbEntry) {
        if (!entry.isDirectory) {
            _state.update { it.copy(toast = "TODO：播放 ${entry.name}") }
            return
        }
        val current = _state.value.currentPath
        val nextPath = when {
            entry.name == ".." -> current.substringBeforeLast('/', "")
            else -> entry.fullPath
        }
        _state.update { it.copy(currentPath = nextPath) }
        loadCurrentPath()
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }
}
