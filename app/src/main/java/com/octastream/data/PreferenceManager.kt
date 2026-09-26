package com.octastream.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.octastream.logger.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EngineSettings(
    val threadCount: Int = 4,
    val singleStreamFallback: Boolean = true,
    val safDirectoryUri: String? = null,
    val safDirectoryDisplayPath: String = "Default (Internal Downloads/OctaStream)"
)

/**
 * Manages persisted SAF tree URIs, parallel connection thread counts (2, 4, 8),
 * and single-stream HTTP fallback configuration.
 */
class PreferenceManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<EngineSettings> = _settings.asStateFlow()

    private fun loadSettings(): EngineSettings {
        val threads = prefs.getInt(KEY_THREAD_COUNT, 4).coerceIn(1, 16)
        val fallback = prefs.getBoolean(KEY_SINGLE_STREAM_FALLBACK, true)
        val safUri = prefs.getString(KEY_SAF_TREE_URI, null)
        val display = prefs.getString(
            KEY_SAF_DISPLAY_PATH,
            "Default (Internal Downloads/OctaStream)"
        ) ?: "Default (Internal Downloads/OctaStream)"

        return EngineSettings(
            threadCount = threads,
            singleStreamFallback = fallback,
            safDirectoryUri = safUri,
            safDirectoryDisplayPath = display
        )
    }

    fun setThreadCount(count: Int) {
        val valid = count.coerceIn(1, 16)
        prefs.edit().putInt(KEY_THREAD_COUNT, valid).apply()
        _settings.value = _settings.value.copy(threadCount = valid)
        AppLogger.info("Preferences", "Parallel segment thread count updated to $valid connections/file.")
    }

    fun setSingleStreamFallback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SINGLE_STREAM_FALLBACK, enabled).apply()
        _settings.value = _settings.value.copy(singleStreamFallback = enabled)
        AppLogger.info(
            "Preferences",
            "Single-stream fallback if HTTP Range unsupported set to: $enabled"
        )
    }

    fun setSafDirectory(uri: Uri?, displayPath: String) {
        if (uri == null) {
            prefs.edit()
                .remove(KEY_SAF_TREE_URI)
                .putString(KEY_SAF_DISPLAY_PATH, "Default (Internal Downloads/OctaStream)")
                .apply()
            _settings.value = _settings.value.copy(
                safDirectoryUri = null,
                safDirectoryDisplayPath = "Default (Internal Downloads/OctaStream)"
            )
            AppLogger.info("Preferences", "Reset storage target to default internal directory.")
        } else {
            val uriString = uri.toString()
            prefs.edit()
                .putString(KEY_SAF_TREE_URI, uriString)
                .putString(KEY_SAF_DISPLAY_PATH, displayPath)
                .apply()
            _settings.value = _settings.value.copy(
                safDirectoryUri = uriString,
                safDirectoryDisplayPath = displayPath
            )
            AppLogger.info("Preferences", "Persisted SAF Tree URI: $uriString ($displayPath)")
        }
    }

    companion object {
        private const val PREFS_NAME = "octastream_engine_prefs"
        private const val KEY_THREAD_COUNT = "key_thread_count"
        private const val KEY_SINGLE_STREAM_FALLBACK = "key_single_stream_fallback"
        private const val KEY_SAF_TREE_URI = "key_saf_tree_uri"
        private const val KEY_SAF_DISPLAY_PATH = "key_saf_display_path"

        @Volatile
        private var INSTANCE: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferenceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
