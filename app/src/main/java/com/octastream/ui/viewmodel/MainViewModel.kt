package com.octastream.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.octastream.data.EngineSettings
import com.octastream.data.PreferenceManager
import com.octastream.data.StorageHelper
import com.octastream.engine.DownloadEngine
import com.octastream.extractor.MediaExtractor
import com.octastream.logger.AppLogger
import com.octastream.model.DownloadTask
import com.octastream.model.LogEntry
import com.octastream.model.QualityOption
import com.octastream.model.SampleStreamPreset
import com.octastream.model.StreamInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OctaTab {
    LANDING,
    FETCH,
    DOWNLOADS,
    SETTINGS
}

sealed class ExtractionUiState {
    data object Idle : ExtractionUiState()
    data class Resolving(val url: String) : ExtractionUiState()
    data class Success(val streamInfo: StreamInfo) : ExtractionUiState()
    data class Error(val message: String) : ExtractionUiState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val downloadEngine = DownloadEngine.getInstance(appContext)
    private val preferenceManager = PreferenceManager.getInstance(appContext)

    private val _selectedTab = MutableStateFlow(OctaTab.FETCH)
    val selectedTab: StateFlow<OctaTab> = _selectedTab.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _extractionState = MutableStateFlow<ExtractionUiState>(ExtractionUiState.Idle)
    val extractionState: StateFlow<ExtractionUiState> = _extractionState.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val tasks: StateFlow<List<DownloadTask>> = downloadEngine.tasks
    val settings: StateFlow<EngineSettings> = preferenceManager.settings
    val logs: StateFlow<List<LogEntry>> = AppLogger.logs
    val unreadAlertCount: StateFlow<Int> = AppLogger.unreadAlertCount
    val hasRecentError: StateFlow<Boolean> = AppLogger.hasRecentError
    val presets: List<SampleStreamPreset> = MediaExtractor.verifiedPresets

    init {
        refreshCacheSize()
    }

    fun selectTab(tab: OctaTab) {
        _selectedTab.value = tab
        if (tab == OctaTab.SETTINGS) {
            refreshCacheSize()
        }
    }

    fun onUrlInputChanged(newUrl: String) {
        _urlInput.value = newUrl
        if (_extractionState.value is ExtractionUiState.Error) {
            _extractionState.value = ExtractionUiState.Idle
        }
    }

    fun clearUrlInput() {
        _urlInput.value = ""
        _extractionState.value = ExtractionUiState.Idle
    }

    fun fetchStreamManifest(overrideUrl: String? = null) {
        val targetUrl = (overrideUrl ?: _urlInput.value).trim()
        if (overrideUrl != null) {
            _urlInput.value = overrideUrl
        }
        if (targetUrl.isEmpty()) {
            _extractionState.value = ExtractionUiState.Error("Please enter or paste a valid media stream URL.")
            AppLogger.warn("UI-Extract", "User triggered extraction with an empty URL field.")
            return
        }

        _extractionState.value = ExtractionUiState.Resolving(targetUrl)
        viewModelScope.launch {
            val result = MediaExtractor.extractStreamInfo(targetUrl)
            result.fold(
                onSuccess = { info ->
                    _extractionState.value = ExtractionUiState.Success(info)
                },
                onFailure = { err ->
                    _extractionState.value = ExtractionUiState.Error(
                        err.message ?: "Failed to extract stream manifest."
                    )
                }
            )
        }
    }

    fun dismissQualityPicker() {
        if (_extractionState.value is ExtractionUiState.Success ||
            _extractionState.value is ExtractionUiState.Error
        ) {
            _extractionState.value = ExtractionUiState.Idle
        }
    }

    fun startDownload(streamInfo: StreamInfo, option: QualityOption) {
        downloadEngine.enqueueDownload(streamInfo, option)
        _extractionState.value = ExtractionUiState.Idle
        // Automatically redirect user to Tab 2 (Download Manager / Queue)
        _selectedTab.value = OctaTab.DOWNLOADS
        _snackbarMessage.value = "Queued '${streamInfo.title}' (${option.resolution})"
    }

    fun pauseTask(taskId: String) {
        downloadEngine.pauseTask(taskId)
    }

    fun resumeTask(taskId: String) {
        downloadEngine.resumeTask(taskId)
    }

    fun deleteTask(taskId: String) {
        downloadEngine.cancelAndDeleteTask(taskId)
        refreshCacheSize()
    }

    fun clearFinishedTasks() {
        downloadEngine.clearCompletedTasks()
        _snackbarMessage.value = "Cleared finished tasks"
    }

    fun setThreadCount(threads: Int) {
        preferenceManager.setThreadCount(threads)
    }

    fun setSingleStreamFallback(enabled: Boolean) {
        preferenceManager.setSingleStreamFallback(enabled)
    }

    fun onSafDirectoryPicked(treeUri: Uri) {
        val display = StorageHelper.persistSafDirectoryPermission(appContext, treeUri)
        preferenceManager.setSafDirectory(treeUri, display)
        _snackbarMessage.value = "Storage folder updated: $display"
    }

    fun resetSafDirectory() {
        preferenceManager.setSafDirectory(null, "Default (Internal Downloads/OctaStream)")
        _snackbarMessage.value = "Reset to default internal Downloads directory"
    }

    fun clearCache() {
        viewModelScope.launch {
            val count = StorageHelper.clearWorkCache(appContext)
            refreshCacheSize()
            _snackbarMessage.value = "Purged $count temporary chunk/DASH file(s)"
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheSizeBytes.value = StorageHelper.getWorkCacheSizeBytes(appContext)
        }
    }

    fun markDiagnosticAlertsRead() {
        AppLogger.markAlertsRead()
    }

    fun clearDiagnosticLogs() {
        AppLogger.clearBuffer()
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }
}