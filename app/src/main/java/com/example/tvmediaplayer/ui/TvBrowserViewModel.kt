package com.example.tvmediaplayer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tvmediaplayer.data.repo.JcifsSmbRepository
import com.example.tvmediaplayer.data.repo.SmbConfigStore
import com.example.tvmediaplayer.data.repo.SmbFailureMapper
import com.example.tvmediaplayer.domain.model.SavedSmbConnection
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

    fun saveConfig(config: SmbConfig, name: String) {
        val id = _state.value.activeConnectionId ?: configStore.newConnectionId()
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
            _state.update { it.copy(error = "SMB host is required") }
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
            _state.update { it.copy(toast = "TODO: play ${entry.name}") }
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
        val share = config.share.ifBlank { "all-shares" }
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
