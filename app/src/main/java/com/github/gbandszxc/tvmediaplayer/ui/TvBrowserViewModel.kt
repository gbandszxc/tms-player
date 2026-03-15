package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.gbandszxc.tvmediaplayer.data.repo.JcifsSmbRepository
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbConfigStore
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbFailureMapper
import com.github.gbandszxc.tvmediaplayer.domain.model.SavedSmbConnection
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.domain.repo.SmbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvBrowserState(
    val config: SmbConfig = SmbConfig.Empty,
    val savedConnections: List<SavedSmbConnection> = emptyList(),
    val activeConnectionId: String? = null,
    val currentPath: String = "",
    val entries: List<SmbEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val toast: String? = null
)

class TvBrowserViewModel(
    private val repository: SmbRepository,
    private val configStore: SmbConfigStore
) : ViewModel() {

    private val _state = MutableStateFlow(TvBrowserState())
    val state: StateFlow<TvBrowserState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = configStore.loadState()
            _state.update {
                it.copy(
                    config = loaded.activeConfig,
                    currentPath = loaded.activeConfig.normalizedPath(),
                    savedConnections = loaded.savedConnections,
                    activeConnectionId = loaded.activeConnectionId
                )
            }
            if (loaded.activeConfig.host.isNotBlank()) {
                loadCurrentPath()
            }
        }
    }

    fun saveConfig(config: SmbConfig, name: String, saveAsNew: Boolean = false) {
        val id = if (saveAsNew) configStore.newConnectionId() else (_state.value.activeConnectionId ?: configStore.newConnectionId())
        val actualName = name.ifBlank { defaultConnectionName(config) }
        val saved = SavedSmbConnection(id = id, name = actualName, config = config)

        _state.update {
            val mutable = it.savedConnections.toMutableList()
            val index = mutable.indexOfFirst { c -> c.id == id }
            if (index >= 0) mutable[index] = saved else mutable.add(saved)
            it.copy(
                config = config,
                currentPath = config.normalizedPath(),
                activeConnectionId = id,
                savedConnections = mutable,
                error = null
            )
        }

        viewModelScope.launch {
            configStore.saveConnection(saved, activate = true)
        }
        loadCurrentPath()
    }

    fun switchConnection(connectionId: String) {
        val target = _state.value.savedConnections.firstOrNull { it.id == connectionId } ?: return
        _state.update {
            it.copy(
                config = target.config,
                currentPath = target.config.normalizedPath(),
                activeConnectionId = target.id,
                error = null
            )
        }
        viewModelScope.launch {
            configStore.setActiveConnection(connectionId)
        }
        loadCurrentPath()
    }

    fun loadCurrentPath() {
        val snapshot = _state.value
        if (snapshot.config.host.isBlank()) {
            _state.update { it.copy(error = "SMB 主机地址不能为空") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, toast = null) }
            runCatching {
                repository.list(snapshot.config, snapshot.currentPath)
            }.onSuccess { list ->
                _state.update { it.copy(entries = list, loading = false) }
            }.onFailure { ex ->
                val message = SmbFailureMapper.toUserMessage(SmbFailureMapper.map(ex))
                _state.update { it.copy(loading = false, error = message) }
            }
        }
    }

    fun enterDirectory(entry: SmbEntry) {
        if (!entry.isDirectory) {
            _state.update { it.copy(toast = "待实现：播放 ${entry.name}") }
            return
        }
        val current = _state.value.currentPath
        val nextPath = if (entry.name == "..") current.substringBeforeLast('/', "") else entry.fullPath
        _state.update { it.copy(currentPath = nextPath) }
        loadCurrentPath()
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun defaultConnectionName(config: SmbConfig): String {
        val share = config.share.ifBlank { "全部共享" }
        return "${config.host} / $share"
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                return TvBrowserViewModel(
                    repository = JcifsSmbRepository(),
                    configStore = SmbConfigStore(appContext)
                ) as T
            }
        }
    }
}
