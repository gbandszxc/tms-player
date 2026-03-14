package com.example.tvmediaplayer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tvmediaplayer.data.repo.JcifsSmbRepository
import com.example.tvmediaplayer.data.repo.SmbConfigStore
import com.example.tvmediaplayer.data.repo.SmbFailureMapper
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
    private val repository: SmbRepository,
    private val configStore: SmbConfigStore
) : ViewModel() {

    private val _state = MutableStateFlow(TvBrowserState())
    val state: StateFlow<TvBrowserState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val config = configStore.load()
            _state.update { it.copy(config = config, currentPath = config.normalizedPath()) }
            if (config.host.isNotBlank() && config.share.isNotBlank()) {
                loadCurrentPath()
            }
        }
    }

    fun saveConfig(config: SmbConfig) {
        _state.update { it.copy(config = config, currentPath = config.normalizedPath(), error = null) }
        viewModelScope.launch {
            configStore.save(config)
        }
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
                val message = SmbFailureMapper.toUserMessage(SmbFailureMapper.map(ex))
                _state.update { it.copy(loading = false, error = message) }
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
