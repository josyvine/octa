package com.octastream.engine

import android.content.Context
import com.octastream.data.PreferenceManager
import com.octastream.data.StorageHelper
import com.octastream.data.TaskDatabase
import com.octastream.data.TaskRepository
import com.octastream.extractor.MediaExtractor
import com.octastream.logger.AppLogger
import com.octastream.model.DownloadSegment
import com.octastream.model.DownloadState
import com.octastream.model.DownloadTask
import com.octastream.model.QualityOption
import com.octastream.model.StreamInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-threaded Parallel Segmented Download Coordinator (IDM Core).
 * Manages HTTP HEAD pre-flight inspection, byte-range segmentation, RandomAccessFile pre-allocation,
 * concurrent worker dispatch, DASH video+audio multiplexing, and true Pause/Resume state persistence.
 */
class DownloadEngine private constructor(private val appContext: Context) {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = TaskRepository(TaskDatabase.getInstance(appContext).taskDao())
    private val preferenceManager = PreferenceManager.getInstance(appContext)
    private val httpClient = MediaExtractor.sharedHttpClient

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val lastPersistenceMap = ConcurrentHashMap<String, Long>()

    init {
        engineScope.launch {
            val saved = repository.getAllOnce().map { task ->
                if (task.state == DownloadState.DOWNLOADING ||
                    task.state == DownloadState.EXTRACTING ||
                    task.state == DownloadState.MUXING ||
                    task.state == DownloadState.QUEUED
                ) {
                    val paused = task.copy(
                        state = DownloadState.PAUSED,
                        speedBytesPerSec = 0L,
                        etaSeconds = -1L
                    )
                    repository.upsert(paused)
                    paused
                } else {
                    task
                }
            }
            _tasks.value = saved
            if (saved.isNotEmpty()) {
                AppLogger.info(
                    TAG,
                    "Restored ${saved.size} persisted task(s) from Room database."
                )
            }
        }
    }

    fun enqueueDownload(
        streamInfo: StreamInfo,
        option: QualityOption
    ): String {
        val settings = preferenceManager.settings.value
        val threadCount = settings.threadCount.coerceIn(1, 16)

        val newTask = DownloadTask(
            sourceUrl = streamInfo.sourceUrl,
            title = streamInfo.title,
            thumbnailUrl = streamInfo.thumbnailUrl,
            qualityLabel = option.label,
            container = option.container,
            videoUrl = option.videoUrl,
            audioUrl = option.audioUrl,
            isDash = option.isDashMuxRequired,
            threadCount = threadCount,
            state = DownloadState.QUEUED,
            totalBytes = option.estimatedSizeBytes.coerceAtLeast(0L)
        )

        _tasks.update { listOf(newTask) + it }
        engineScope.launch {
            repository.upsert(newTask)
        }

        AppLogger.info(
            TAG,
            "Enqueued task '${newTask.title}' [${newTask.qualityLabel}] with $threadCount threads (DASH=${newTask.isDash})."
        )

        DownloadService.startOrUpdateService(appContext)
        startTaskInternal(newTask.id)
        return newTask.id
    }

    fun pauseTask(taskId: String) {
        val job = activeJobs.remove(taskId)
        job?.cancel()
        updateTaskState(taskId, forcePersist = true) { task ->
            task.copy(
                state = DownloadState.PAUSED,
                speedBytesPerSec = 0L,
                etaSeconds = -1L,
                segments = task.segments.map { it.copy(activeSpeedBps = 0L) }
            )
        }
        AppLogger.info(TAG, "Paused task [$taskId]. Byte offsets preserved for instant resume.")
    }

    fun resumeTask(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.state == DownloadState.DOWNLOADING || task.state == DownloadState.MUXING) return
        AppLogger.info(TAG, "Resuming task '${task.title}' from saved segment byte offsets...")
        DownloadService.startOrUpdateService(appContext)
        startTaskInternal(taskId)
    }

    fun cancelAndDeleteTask(taskId: String) {
        activeJobs.remove(taskId)?.cancel()
        val task = _tasks.value.find { it.id == taskId }
        _tasks.update { list -> list.filterNot { it.id == taskId } }
        engineScope.launch {
            repository.deleteById(taskId)
            val cacheDir = StorageHelper.getWorkCacheDir(appContext)
            File(cacheDir, "task_${taskId}_main.tmp").delete()
            File(cacheDir, "task_${taskId}_video.mp4").delete()
            File(cacheDir, "task_${taskId}_audio.m4a").delete()
            File(cacheDir, "task_${taskId}_muxed.mp4").delete()
            AppLogger.info(
                TAG,
                "Cancelled and removed task '${task?.title ?: taskId}' and its staging files."
            )
        }
    }

    fun clearCompletedTasks() {
        val completedCount = _tasks.value.count { it.state == DownloadState.COMPLETED }
        _tasks.update { list -> list.filterNot { it.state == DownloadState.COMPLETED } }
        engineScope.launch {
            repository.clearCompleted()
            AppLogger.info(TAG, "Cleared $completedCount completed task(s) from queue.")
        }
    }

    private fun startTaskInternal(taskId: String) {
        if (activeJobs.containsKey(taskId)) return

        val job = engineScope.launch {
            try {
                executeTaskPipeline(taskId)
            } catch (ce: CancellationException) {
                AppLogger.info(TAG, "Task [${taskId.take(6)}] coroutine suspended/paused.")
            } catch (e: Exception) {
                AppLogger.error(TAG, "Task [${taskId.take(6)}] failed with fatal error: ${e.message}", e)
                updateTaskState(taskId, forcePersist = true) { current ->
                    current.copy(
                        state = DownloadState.ERROR,
                        speedBytesPerSec = 0L,
                        etaSeconds = -1L,
                        errorMessage = e.message ?: "Network or I/O transfer failure"
                    )
                }
            } finally {
                activeJobs.remove(taskId)
            }
        }
        activeJobs[taskId] = job
    }

    private suspend fun executeTaskPipeline(taskId: String) {
        val initialTask = _tasks.value.find { it.id == taskId } ?: return
        val settings = preferenceManager.settings.value
        val cacheDir = StorageHelper.getWorkCacheDir(appContext)

        if (initialTask.isDash && !initialTask.videoUrl.isNullOrBlank() && !initialTask.audioUrl.isNullOrBlank()) {
            executeDashPipeline(initialTask, settings.threadCount, settings.singleStreamFallback, cacheDir)
        } else {
            val targetUrl = initialTask.videoUrl ?: initialTask.audioUrl
                ?: throw IOException("No stream URL available for task")
            executeSingleContainerPipeline(
                initialTask,
                targetUrl,
                settings.threadCount,
                settings.singleStreamFallback,
                cacheDir
            )
        }
    }

    private suspend fun executeSingleContainerPipeline(
        initialTask: DownloadTask,
        streamUrl: String,
        configuredThreads: Int,
        allowSingleFallback: Boolean,
        cacheDir: File
    ) {
        val taskId = initialTask.id
        val stagingFile = File(cacheDir, "task_${taskId}_main.tmp")

        // 1. Pre-flight check if segments haven't been initialized yet
        var currentTask = _tasks.value.find { it.id == taskId } ?: return
        if (currentTask.segments.isEmpty()) {
            val preflight = performPreflightHead(streamUrl)
            AppLogger.network(
                TAG,
                "Pre-flight [${currentTask.title.take(24)}]: Content-Length=${preflight.contentLength}B, Accept-Ranges=${preflight.acceptRanges}"
            )

            if (!preflight.acceptRanges && !allowSingleFallback) {
                throw IOException(
                    "Server does not support HTTP Range requests and Single-Stream Fallback is disabled in Settings."
                )
            }

            val useRange = preflight.acceptRanges && preflight.contentLength > 0L
            val actualThreads = if (useRange) configuredThreads.coerceAtLeast(1) else 1
            val totalSize = if (preflight.contentLength > 0L) preflight.contentLength else currentTask.totalBytes

            if (useRange && totalSize > 0L) {
                RandomAccessFile(stagingFile, "rw").use { raf ->
                    raf.setLength(totalSize)
                }
                AppLogger.info(
                    TAG,
                    "Pre-allocated RandomAccessFile (${StorageHelper.formatBytes(totalSize)}) across $actualThreads segments."
                )
            }

            val builtSegments = buildSegments(
                totalBytes = totalSize,
                threadCount = actualThreads,
                role = "MAIN",
                startIndexOffset = 1,
                useRange = useRange
            )

            currentTask = updateTaskState(taskId, forcePersist = true) {
                it.copy(
                    state = DownloadState.DOWNLOADING,
                    totalBytes = totalSize,
                    supportsRange = useRange,
                    segments = builtSegments,
                    errorMessage = null
                )
            } ?: return
        } else {
            currentTask = updateTaskState(taskId, forcePersist = true) {
                it.copy(state = DownloadState.DOWNLOADING, errorMessage = null)
            } ?: return
        }

        // 2. Dispatch all N segments in parallel
        val useRange = currentTask.supportsRange && currentTask.totalBytes > 0L
        coroutineScope {
            currentTask.segments.map { seg ->
                async(Dispatchers.IO) {
                    SegmentWorker(
                        httpClient = httpClient,
                        taskId = taskId,
                        url = streamUrl,
                        targetFile = stagingFile,
                        initialSegment = seg,
                        useRangeHeader = useRange && seg.endByte > 0L,
                        onSegmentProgress = { index, downloaded, speedBps, completed ->
                            onWorkerProgress(taskId, index, downloaded, speedBps, completed)
                        }
                    ).execute()
                }
            }.awaitAll()
        }

        // 3. Export completed file to user's SAF directory (or fallback Downloads)
        val ext = currentTask.container.lowercase()
        val mime = when (ext) {
            "m4a" -> "audio/mp4"
            "webm" -> "video/webm"
            else -> "video/mp4"
        }
        val desiredName = "${StorageHelper.sanitizeFileName(currentTask.title)}_${currentTask.qualityLabel.substringBefore(" ")}.$ext"
        val finalPath = StorageHelper.exportCompletedFile(
            context = appContext,
            sourceFile = stagingFile,
            desiredFileName = desiredName,
            mimeType = mime,
            safTreeUriString = preferenceManager.settings.value.safDirectoryUri
        )

        stagingFile.delete()

        updateTaskState(taskId, forcePersist = true) { task ->
            val finalSize = if (task.totalBytes > 0L) task.totalBytes else task.downloadedBytes
            task.copy(
                state = DownloadState.COMPLETED,
                totalBytes = finalSize,
                downloadedBytes = finalSize,
                speedBytesPerSec = 0L,
                etaSeconds = 0L,
                outputFilePath = finalPath,
                segments = task.segments.map {
                    it.copy(
                        downloadedBytes = if (it.totalBytes > 0L) it.totalBytes else it.downloadedBytes,
                        isCompleted = true,
                        activeSpeedBps = 0L
                    )
                }
            )
        }
        AppLogger.info(TAG, "Task '${currentTask.title}' completed -> $finalPath")
    }

    private suspend fun executeDashPipeline(
        initialTask: DownloadTask,
        configuredThreads: Int,
        allowSingleFallback: Boolean,
        cacheDir: File
    ) {
        val taskId = initialTask.id
        val videoUrl = initialTask.videoUrl ?: throw IOException("Missing DASH video URL")
        val audioUrl = initialTask.audioUrl ?: throw IOException("Missing DASH audio URL")

        val videoTempFile = File(cacheDir, "task_${taskId}_video.mp4")
        val audioTempFile = File(cacheDir, "task_${taskId}_audio.m4a")
        val muxedTempFile = File(cacheDir, "task_${taskId}_muxed.mp4")

        var currentTask = _tasks.value.find { it.id == taskId } ?: return
        if (currentTask.segments.isEmpty()) {
            val videoPreflight = performPreflightHead(videoUrl)
            val audioPreflight = performPreflightHead(audioUrl)

            AppLogger.network(
                TAG,
                "DASH Pre-flight: Video=${videoPreflight.contentLength}B (Range=${videoPreflight.acceptRanges}), Audio=${audioPreflight.contentLength}B (Range=${audioPreflight.acceptRanges})"
            )

            if ((!videoPreflight.acceptRanges || !audioPreflight.acceptRanges) && !allowSingleFallback) {
                throw IOException("Upstream server rejected HTTP Range for DASH tracks and fallback is disabled.")
            }

            val useVideoRange = videoPreflight.acceptRanges && videoPreflight.contentLength > 0L
            val useAudioRange = audioPreflight.acceptRanges && audioPreflight.contentLength > 0L

            val videoThreads = if (useVideoRange) configuredThreads.coerceAtLeast(2) else 1
            val audioThreads = if (useAudioRange) (configuredThreads / 2).coerceIn(1, 2) else 1

            if (useVideoRange && videoPreflight.contentLength > 0L) {
                RandomAccessFile(videoTempFile, "rw").use { it.setLength(videoPreflight.contentLength) }
            }
            if (useAudioRange && audioPreflight.contentLength > 0L) {
                RandomAccessFile(audioTempFile, "rw").use { it.setLength(audioPreflight.contentLength) }
            }

            val videoSegments = buildSegments(
                totalBytes = videoPreflight.contentLength,
                threadCount = videoThreads,
                role = "VIDEO",
                startIndexOffset = 1,
                useRange = useVideoRange
            )
            val audioSegments = buildSegments(
                totalBytes = audioPreflight.contentLength,
                threadCount = audioThreads,
                role = "AUDIO",
                startIndexOffset = videoSegments.size + 1,
                useRange = useAudioRange
            )

            val combinedSegments = videoSegments + audioSegments
            val combinedTotalBytes =
                (videoPreflight.contentLength.coerceAtLeast(0L)) + (audioPreflight.contentLength.coerceAtLeast(0L))

            currentTask = updateTaskState(taskId, forcePersist = true) {
                it.copy(
                    state = DownloadState.DOWNLOADING,
                    totalBytes = combinedTotalBytes,
                    supportsRange = useVideoRange || useAudioRange,
                    segments = combinedSegments,
                    errorMessage = null
                )
            } ?: return
        } else {
            currentTask = updateTaskState(taskId, forcePersist = true) {
                it.copy(state = DownloadState.DOWNLOADING, errorMessage = null)
            } ?: return
        }

        // Download both video and audio track segments concurrently
        coroutineScope {
            currentTask.segments.map { seg ->
                async(Dispatchers.IO) {
                    val isAudio = seg.role == "AUDIO"
                    val targetFile = if (isAudio) audioTempFile else videoTempFile
                    val targetUrl = if (isAudio) audioUrl else videoUrl
                    val useRange = seg.endByte > 0L
                    SegmentWorker(
                        httpClient = httpClient,
                        taskId = taskId,
                        url = targetUrl,
                        targetFile = targetFile,
                        initialSegment = seg,
                        useRangeHeader = useRange,
                        onSegmentProgress = { index, downloaded, speedBps, completed ->
                            onWorkerProgress(taskId, index, downloaded, speedBps, completed)
                        }
                    ).execute()
                }
            }.awaitAll()
        }

        // Transition to MUXING state and stitch DASH tracks without re-encoding
        updateTaskState(taskId, forcePersist = true) {
            it.copy(
                state = DownloadState.MUXING,
                speedBytesPerSec = 0L,
                etaSeconds = 1L
            )
        }

        val muxResult = MuxerEngine.muxVideoAndAudio(
            videoFile = videoTempFile,
            audioFile = audioTempFile,
            outputFile = muxedTempFile
        )
        val finalContainerFile = muxResult.getOrThrow()

        val desiredName = "${StorageHelper.sanitizeFileName(currentTask.title)}_${currentTask.qualityLabel.substringBefore(" ")}_DASH.mp4"
        val exportedUri = StorageHelper.exportCompletedFile(
            context = appContext,
            sourceFile = finalContainerFile,
            desiredFileName = desiredName,
            mimeType = "video/mp4",
            safTreeUriString = preferenceManager.settings.value.safDirectoryUri
        )

        // Cleanup temporary DASH cache files
        videoTempFile.delete()
        audioTempFile.delete()
        muxedTempFile.delete()

        updateTaskState(taskId, forcePersist = true) { task ->
            val finalBytes = if (task.totalBytes > 0L) task.totalBytes else task.downloadedBytes
            task.copy(
                state = DownloadState.COMPLETED,
                totalBytes = finalBytes,
                downloadedBytes = finalBytes,
                speedBytesPerSec = 0L,
                etaSeconds = 0L,
                outputFilePath = exportedUri
            )
        }
        AppLogger.info(TAG, "DASH task '${currentTask.title}' muxed and saved to $exportedUri")
    }

    private fun onWorkerProgress(
        taskId: String,
        segmentIndex: Int,
        downloadedBytes: Long,
        speedBps: Long,
        completed: Boolean
    ) {
        val now = System.currentTimeMillis()
        val lastSaved = lastPersistenceMap[taskId] ?: 0L
        val shouldPersist = completed || (now - lastSaved >= 1200L)
        if (shouldPersist) {
            lastPersistenceMap[taskId] = now
        }

        updateTaskState(taskId, forcePersist = shouldPersist) { task ->
            val updatedSegments = task.segments.map { seg ->
                if (seg.index == segmentIndex) {
                    seg.copy(
                        downloadedBytes = downloadedBytes,
                        activeSpeedBps = if (completed) 0L else speedBps,
                        isCompleted = completed
                    )
                } else {
                    seg
                }
            }
            val totalDownloaded = updatedSegments.sumOf { it.downloadedBytes }
            val aggregateSpeed = updatedSegments.sumOf { it.activeSpeedBps }
            val remainingBytes = (task.totalBytes - totalDownloaded).coerceAtLeast(0L)
            val eta = if (aggregateSpeed > 0L && remainingBytes > 0L) {
                remainingBytes / aggregateSpeed
            } else {
                -1L
            }

            task.copy(
                downloadedBytes = totalDownloaded,
                speedBytesPerSec = aggregateSpeed,
                etaSeconds = eta,
                segments = updatedSegments
            )
        }
    }

    private fun updateTaskState(
        taskId: String,
        forcePersist: Boolean = false,
        transform: (DownloadTask) -> DownloadTask
    ): DownloadTask? {
        var updatedTask: DownloadTask? = null
        _tasks.update { list ->
            list.map { item ->
                if (item.id == taskId) {
                    transform(item).also { updatedTask = it }
                } else {
                    item
                }
            }
        }
        if (forcePersist && updatedTask != null) {
            val snapshot = updatedTask!!
            engineScope.launch {
                repository.upsert(snapshot)
            }
        }
        return updatedTask
    }

    private data class PreflightInfo(
        val contentLength: Long,
        val acceptRanges: Boolean
    )

    private suspend fun performPreflightHead(url: String): PreflightInfo = withContext(Dispatchers.IO) {
        // 1. Check if URL itself carries a signed `clen=` parameter (e.g., YouTube googlevideo.com streams)
        val urlParamClen = MediaExtractor.extractContentLengthFromUrlParam(url)
        if (urlParamClen > 0L && url.contains("googlevideo.com", ignoreCase = true)) {
            return@withContext PreflightInfo(
                contentLength = urlParamClen,
                acceptRanges = true
            )
        }

        val userAgent = MediaExtractor.resolveUserAgentForUrl(url)
        var length = urlParamClen
        var acceptsRange = false

        // 2. Try HTTP HEAD check (only read headers if response is 2xx successful)
        runCatching {
            val headReq = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", userAgent)
                .header("Accept-Encoding", "identity")
                .build()
            httpClient.newCall(headReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val headerLen = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    if (headerLen > 0L) length = headerLen
                    acceptsRange = resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                }
            }
        }

        // 3. Fallback Range: bytes=0-0 probe if length or range capability still unknown
        if (length <= 0L || !acceptsRange) {
            runCatching {
                val rangeReq = Request.Builder()
                    .url(url)
                    .get()
                    .header("Range", "bytes=0-0")
                    .header("User-Agent", userAgent)
                    .header("Accept-Encoding", "identity")
                    .build()
                httpClient.newCall(rangeReq).execute().use { resp ->
                    if (resp.code == 206) {
                        acceptsRange = true
                        val cr = resp.header("Content-Range")
                        if (cr != null && cr.contains("/")) {
                            val totalFromRange = cr.substringAfter("/").toLongOrNull() ?: -1L
                            if (totalFromRange > 0L) length = totalFromRange
                        }
                    } else if (resp.isSuccessful && length <= 0L) {
                        val headerLen = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                        if (headerLen > 0L) length = headerLen
                    }
                }
            }
        }

        PreflightInfo(contentLength = length, acceptRanges = acceptsRange)
    }

    private fun buildSegments(
        totalBytes: Long,
        threadCount: Int,
        role: String,
        startIndexOffset: Int,
        useRange: Boolean
    ): List<DownloadSegment> {
        if (!useRange || totalBytes <= 0L) {
            return listOf(
                DownloadSegment(
                    index = startIndexOffset,
                    role = role,
                    startByte = 0L,
                    endByte = -1L,
                    downloadedBytes = 0L,
                    isCompleted = false
                )
            )
        }

        if (threadCount <= 1) {
            return listOf(
                DownloadSegment(
                    index = startIndexOffset,
                    role = role,
                    startByte = 0L,
                    endByte = totalBytes - 1L,
                    downloadedBytes = 0L,
                    isCompleted = false
                )
            )
        }

        val chunkSize = totalBytes / threadCount
        return (0 until threadCount).map { i ->
            val start = i * chunkSize
            val end = if (i == threadCount - 1) totalBytes - 1L else (start + chunkSize - 1L)
            DownloadSegment(
                index = startIndexOffset + i,
                role = role,
                startByte = start,
                endByte = end,
                downloadedBytes = 0L,
                isCompleted = false
            )
        }
    }

    companion object {
        private const val TAG = "DownloadEngine"

        @Volatile
        private var INSTANCE: DownloadEngine? = null

        fun getInstance(context: Context): DownloadEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
