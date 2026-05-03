package com.kingmc.flexmusic.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kingmc.flexmusic.data.repository.MusicRepository
import com.kingmc.flexmusic.player.Media3PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val songCount: Int = 0,
    val isLoading: Boolean = false,
    val scanMessage: String? = null,
    val appVersion: String = "1.0.0"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val appSettingsManager: AppSettingsManager,
    private val playerController: Media3PlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val appSettings: StateFlow<AppSettings> = appSettingsManager.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    init {
        viewModelScope.launch {
            repository.observeSongs().collect { songs ->
                _uiState.update { it.copy(songCount = songs.size) }
            }
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, scanMessage = null) }
            val result = repository.refreshLocalLibrary()
            _uiState.update {
                if (result.isSuccess) {
                    val count = result.getOrDefault(0)
                    val message = if (count == 0) {
                        "未发现可播放音频，请检查音频目录"
                    } else {
                        "已扫描 $count 首歌曲"
                    }
                    it.copy(isLoading = false, scanMessage = message)
                } else {
                    val error = result.exceptionOrNull()
                    val message = if (error is SecurityException) {
                        "缺少媒体读取权限，请先授权"
                    } else {
                        "扫描失败，请稍后重试"
                    }
                    it.copy(isLoading = false, scanMessage = message)
                }
            }
        }
    }

    fun updateSmartLyricsMatch(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.updateSmartLyricsMatch(enabled)
        }
    }

    fun updateAutoPlay(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.updateAutoPlay(enabled)
        }
    }

    fun updateShowNotification(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.updateShowNotification(enabled)
        }
    }

    fun updateRememberProgress(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsManager.updateRememberProgress(enabled)
        }
    }

    fun clearScanMessage() {
        _uiState.update { it.copy(scanMessage = null) }
    }
}
