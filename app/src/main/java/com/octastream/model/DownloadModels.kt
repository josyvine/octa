package com.octastream.model

import java.util.UUID

enum class DownloadState {
    EXTRACTING,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    MUXING,
    COMPLETED,
    ERROR
}

enum class StreamCategory(val displayName: String) {
    DASH_VIDEO("High-Res DASH (1080p / 2K / 4K Video + Audio Mux)"),
    PROGRESSIVE("Combined (Progressive Stream)"),
    AUDIO_ONLY("Audio-Only Extraction (M4A / WebM)")
}

data class QualityOption(
    val id: String = UUID.randomUUID().toString(),
    val label: String,               // e.g. "1080p60 Full HD", "720p HD", "160kbps M4A"
    val resolution: String,          // e.g. "1920x1080", "1280x720", "Audio"
    val container: String,           // e.g. "MP4", "WebM", "M4A"
    val codec: String,               // e.g. "avc1.640028 + mp4a.40.2"
    val category: StreamCategory,
    val videoUrl: String?,
    val audioUrl: String?,           // Non-null for DASH_VIDEO or AUDIO_ONLY
    val estimatedSizeBytes: Long,    // -1L if unknown until HEAD preflight
    val isDashMuxRequired: Boolean,
    val bitrateKbps: Int = 0
)

data class StreamInfo(
    val sourceUrl: String,
    val title: String,
    val uploaderName: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val serviceName: String,
    val qualityOptions: List<QualityOption>
)

data class DownloadSegment(
    val index: Int,                  // 1-based part index (Part 1, Part 2, ...)
    val role: String = "MAIN",       // "MAIN", "VIDEO", or "AUDIO"
    val startByte: Long,
    val endByte: Long,               // Inclusive byte offset
    val downloadedBytes: Long = 0L,
    val isCompleted: Boolean = false,
    val activeSpeedBps: Long = 0L
) {
    val totalBytes: Long
        get() = if (endByte >= startByte && endByte > 0L) (endByte - startByte + 1L) else 0L

    val progressFraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else if (isCompleted) {
            1f
        } else {
            0f
        }

    val progressPercent: Int
        get() = (progressFraction * 100f).toInt().coerceIn(0, 100)
}

data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val sourceUrl: String,
    val title: String,
    val thumbnailUrl: String,
    val qualityLabel: String,
    val container: String,
    val videoUrl: String?,
    val audioUrl: String?,
    val isDash: Boolean,
    val threadCount: Int,
    val state: DownloadState = DownloadState.QUEUED,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = -1L,
    val supportsRange: Boolean = true,
    val segments: List<DownloadSegment> = emptyList(),
    val outputFilePath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val overallProgressFraction: Float
        get() = when {
            state == DownloadState.COMPLETED -> 1f
            totalBytes > 0L -> (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            segments.isNotEmpty() -> segments.map { it.progressFraction }.average().toFloat().coerceIn(0f, 1f)
            else -> 0f
        }

    val overallProgressPercent: Int
        get() = (overallProgressFraction * 100f).toInt().coerceIn(0, 100)
}

data class SampleStreamPreset(
    val title: String,
    val subtitle: String,
    val url: String,
    val badge: String
)
