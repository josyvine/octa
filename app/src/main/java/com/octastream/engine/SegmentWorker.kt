package com.octastream.engine

import com.octastream.extractor.MediaExtractor
import com.octastream.logger.AppLogger
import com.octastream.model.DownloadSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
 * using RandomAccessFile("rw"). For Google Video CDN streams, applies server-level query
 * range parameterization (`&range=`) while omitting duplicate HTTP headers to prevent HTTP 416.
 */
class SegmentWorker(
    private val httpClient: OkHttpClient,
    private val taskId: String,
    private val url: String,
    private val targetFile: File,
    private val initialSegment: DownloadSegment,
    private val useRangeHeader: Boolean,
    private val userAgent: String? = null,
    private val customHeaders: Map<String, String> = emptyMap(),
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

        val effectiveUa = userAgent ?: MediaExtractor.resolveUserAgentForUrl(url)
        val isGoogleVideo = url.contains("googlevideo.com", ignoreCase = true)

        // For Google Video CDN streams, bind &range=start-end to the query string
        val effectiveUrl = if (useRangeHeader && endByte > 0L && isGoogleVideo) {
            val cleanUrl = url.replace(Regex("&range=[^&]*"), "")
            if (cleanUrl.contains("?")) {
                "$cleanUrl&range=$resumeStartByte-$endByte"
            } else {
                "$cleanUrl?range=$resumeStartByte-$endByte"
            }
        } else {
            url
        }

        val maxAttempts = 3
        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxAttempts) {
            attempt++
            currentCoroutineContext().ensureActive()

            val requestBuilder = Request.Builder()
                .url(effectiveUrl)
                .header("User-Agent", effectiveUa)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")

            customHeaders.forEach { (k, v) ->
                if (!k.equals("User-Agent", ignoreCase = true)) {
                    requestBuilder.header(k, v)
                }
            }

            if (useRangeHeader && endByte > 0L) {
                if (isGoogleVideo) {
                    // Google Video handles the slice via &range= in effectiveUrl.
                    // Omit the HTTP Range header here to prevent duplicate range evaluation and HTTP 416.
                    if (attempt == 1) {
                        AppLogger.network(
                            "SegWorker-${initialSegment.index}",
                            "Task[${taskId.take(6)}] Part #${initialSegment.index} (${initialSegment.role}) requesting GoogleVideo range $resumeStartByte-$endByte"
                        )
                    }
                } else {
                    // Standard HTTP servers use the Range header
                    val rangeHeader = "bytes=$resumeStartByte-$endByte"
                    requestBuilder.header("Range", rangeHeader)
                    if (attempt == 1) {
                        AppLogger.network(
                            "SegWorker-${initialSegment.index}",
                            "Task[${taskId.take(6)}] Part #${initialSegment.index} (${initialSegment.role}) requesting HTTP $rangeHeader"
                        )
                    }
                }
            } else if (attempt == 1) {
                AppLogger.network(
                    "SegWorker-${initialSegment.index}",
                    "Task[${taskId.take(6)}] Part #${initialSegment.index} starting single-stream GET (offset=0)"
                )
            }

            val activeCall = httpClient.newCall(requestBuilder.build())

            try {
                val response: Response = activeCall.execute()

                response.use { resp ->
                    val isSuccess = resp.isSuccessful || resp.code == 206
                    if (!isSuccess) {
                        throw IOException("Part #${initialSegment.index} HTTP ${resp.code} (${resp.message})")
                    }

                    val body = resp.body
                        ?: throw IOException("Empty HTTP response body on Part #${initialSegment.index}")

                    RandomAccessFile(targetFile, "rw").use { raf ->
                        val serverHonoredRange = useRangeHeader && (
                            resp.code == 206 ||
                                (isGoogleVideo && effectiveUrl.contains("range=")) ||
                                resp.header("Content-Range") != null
                            )
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
                lastException = e
                AppLogger.warn(
                    "SegWorker-${initialSegment.index}",
                    "Part #${initialSegment.index} attempt $attempt/$maxAttempts failed: ${e.message}"
                )
                if (attempt < maxAttempts) {
                    delay(350L * attempt)
                }
            }
        }

        AppLogger.error(
            "SegWorker-${initialSegment.index}",
            "Part #${initialSegment.index} permanently failed after $maxAttempts attempts: ${lastException?.message}",
            lastException
        )
        throw (lastException ?: IOException("Part #${initialSegment.index} failed"))
    }
}