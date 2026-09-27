package com.octastream.engine

import com.octastream.extractor.MediaExtractor
import com.octastream.logger.AppLogger
import com.octastream.model.DownloadSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Executes a single parallel byte-range worker thread against a pre-allocated target file
 * using RandomAccessFile("rw") and HTTP Range headers (with automatic `&range=` query parameter
 * fallback for YouTube googlevideo.com adaptive DASH streams).
 */
class SegmentWorker(
    private val httpClient: OkHttpClient,
    private val taskId: String,
    private val url: String,
    private val targetFile: File,
    private val initialSegment: DownloadSegment,
    private val useRangeHeader: Boolean,
    private val onSegmentProgress: (segmentIndex: Int, downloadedBytes: Long, speedBps: Long, completed: Boolean) -> Unit
) {

    suspend fun execute(): DownloadSegment = withContext(Dispatchers.IO) {
        if (initialSegment.isCompleted) {
            return@withContext initialSegment
        }

        val currentDownloaded = initialSegment.downloadedBytes.coerceAtLeast(0L)
        val resumeStartByte = initialSegment.startByte + currentDownloaded
        val endByte = initialSegment.endByte

        if (useRangeHeader && endByte > 0L && resumeStartByte > endByte) {
            onSegmentProgress(initialSegment.index, initialSegment.totalBytes, 0L, true)
            return@withContext initialSegment.copy(
                downloadedBytes = initialSegment.totalBytes,
                isCompleted = true,
                activeSpeedBps = 0L
            )
        }

        val userAgent = MediaExtractor.resolveUserAgentForUrl(url)
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")

        if (useRangeHeader && endByte > 0L) {
            val rangeHeader = "bytes=$resumeStartByte-$endByte"
            requestBuilder.header("Range", rangeHeader)
            AppLogger.network(
                "SegWorker-${initialSegment.index}",
                "Task[${taskId.take(6)}] Part #${initialSegment.index} (${initialSegment.role}) requesting HTTP $rangeHeader"
            )
        } else {
            AppLogger.network(
                "SegWorker-${initialSegment.index}",
                "Task[${taskId.take(6)}] Part #${initialSegment.index} starting single-stream GET (offset=0)"
            )
        }

        var activeCall = httpClient.newCall(requestBuilder.build())
        try {
            var response: Response = activeCall.execute()

            // If googlevideo.com adaptive stream requires `&range=start-end` query parameter instead of Range header
            if (!response.isSuccessful && response.code != 206 &&
                useRangeHeader && endByte > 0L && url.contains("googlevideo.com", ignoreCase = true)
            ) {
                response.close()
                val rangeParamUrl = if (url.contains("?")) {
                    "$url&range=$resumeStartByte-$endByte"
                } else {
                    "$url?range=$resumeStartByte-$endByte"
                }
                val fallbackReq = Request.Builder()
                    .url(rangeParamUrl)
                    .header("User-Agent", userAgent)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity")
                    .build()
                activeCall = httpClient.newCall(fallbackReq)
                response = activeCall.execute()
            }

            response.use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    throw IOException(
                        "Part #${initialSegment.index} HTTP ${resp.code} (${resp.message})"
                    )
                }

                val body = resp.body
                    ?: throw IOException("Empty HTTP response body on Part #${initialSegment.index}")

                RandomAccessFile(targetFile, "rw").use { raf ->
                    val serverHonoredRange = useRangeHeader && (resp.code == 206 || resp.request.url.toString().contains("range="))
                    val seekOffset = if (serverHonoredRange) resumeStartByte else 0L
                    raf.seek(seekOffset)

                    val buffer = ByteArray(32 * 1024)
                    var segmentAccumulated = if (serverHonoredRange) currentDownloaded else 0L
                    var windowBytes = 0L
                    var lastReportTimeMs = System.currentTimeMillis()
                    val expectedTotal = initialSegment.totalBytes

                    body.byteStream().use { inputStream ->
                        while (true) {
                            currentCoroutineContext().ensureActive()

                            val maxToRead = if (serverHonoredRange && expectedTotal > 0L) {
                                val remaining = expectedTotal - segmentAccumulated
                                if (remaining <= 0L) break
                                minOf(buffer.size.toLong(), remaining).toInt()
                            } else {
                                buffer.size
                            }

                            val read = inputStream.read(buffer, 0, maxToRead)
                            if (read == -1) break

                            raf.write(buffer, 0, read)
                            segmentAccumulated += read
                            windowBytes += read

                            val now = System.currentTimeMillis()
                            val elapsedMs = now - lastReportTimeMs
                            if (elapsedMs >= 160L) {
                                val speedBps = (windowBytes * 1000L) / elapsedMs.coerceAtLeast(1L)
                                onSegmentProgress(
                                    initialSegment.index,
                                    segmentAccumulated,
                                    speedBps,
                                    false
                                )
                                windowBytes = 0L
                                lastReportTimeMs = now
                            }
                        }
                    }

                    onSegmentProgress(initialSegment.index, segmentAccumulated, 0L, true)
                    AppLogger.info(
                        "SegWorker-${initialSegment.index}",
                        "Task[${taskId.take(6)}] Part #${initialSegment.index} (${initialSegment.role}) completed (${segmentAccumulated} bytes written)."
                    )

                    return@withContext initialSegment.copy(
                        downloadedBytes = segmentAccumulated,
                        isCompleted = true,
                        activeSpeedBps = 0L
                    )
                }
            }
        } catch (ce: CancellationException) {
            activeCall.cancel()
            throw ce
        } catch (e: Exception) {
            activeCall.cancel()
            AppLogger.error(
                "SegWorker-${initialSegment.index}",
                "Part #${initialSegment.index} failed: ${e.message}",
                e
            )
            throw e
        }
    }
}
