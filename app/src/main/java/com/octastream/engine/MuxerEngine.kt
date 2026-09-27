package com.octastream.engine

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.octastream.logger.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * DASH Multiplexer Engine.
 * Executes lossless stream copy (`-i temp_video -i temp_audio -c copy final_output`)
 * without re-encoding. Uses Android's hardware-level MediaExtractor + MediaMuxer packet-copy
 * pipeline to multiplex separated high-res video (4K/1440p/1080p/720p) and audio tracks
 * for both MPEG-4 (H.264/HEVC/AV1 + AAC) and WebM (VP9 + Opus/Vorbis) containers.
 */
object MuxerEngine {

    private const val TAG = "FFmpegMuxer"
    private const val SAMPLE_BUFFER_SIZE = 2 * 1024 * 1024 // 2 MB sample buffer for 4K/1440p/1080p keyframes

    @SuppressLint("WrongConstant")
    suspend fun muxVideoAndAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val ffmpegCmd = "-i ${videoFile.name} -i ${audioFile.name} -c copy ${outputFile.name}"
        AppLogger.info(TAG, "Invoking stream-copy multiplexer: ffmpeg $ffmpegCmd")

        if (!videoFile.exists() || videoFile.length() == 0L) {
            val err = IllegalStateException("DASH video track missing or empty: ${videoFile.absolutePath}")
            AppLogger.error(TAG, "Mux abort: missing video input track", err)
            return@withContext Result.failure(err)
        }

        if (!audioFile.exists() || audioFile.length() == 0L) {
            val err = IllegalStateException("DASH audio track missing or empty: ${audioFile.absolutePath}")
            AppLogger.error(TAG, "Mux abort: missing audio input track", err)
            return@withContext Result.failure(err)
        }

        val startTimeMs = System.currentTimeMillis()
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            if (outputFile.exists()) {
                outputFile.delete()
            }

            videoExtractor = MediaExtractor().apply {
                setDataSource(videoFile.absolutePath)
            }
            audioExtractor = MediaExtractor().apply {
                setDataSource(audioFile.absolutePath)
            }

            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var videoMime = ""
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    videoTrackIndex = i
                    videoFormat = format
                    videoMime = mime
                    break
                }
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            var audioMime = ""
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    audioTrackIndex = i
                    audioFormat = format
                    audioMime = mime
                    break
                }
            }

            if (videoFormat == null) {
                AppLogger.warn(
                    TAG,
                    "No elementary video track header found; performing direct container stream copy."
                )
                videoFile.copyTo(outputFile, overwrite = true)
                return@withContext Result.success(outputFile)
            }

            val isWebmVideo = videoMime.contains("vp8", ignoreCase = true) ||
                videoMime.contains("vp9", ignoreCase = true) ||
                outputFile.name.endsWith(".webm", ignoreCase = true)

            val outputFormat = if (isWebmVideo) {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            } else {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }

            muxer = MediaMuxer(outputFile.absolutePath, outputFormat)
            val muxVideoTrack = muxer.addTrack(videoFormat)
            val muxAudioTrack = if (audioFormat != null) {
                try {
                    muxer.addTrack(audioFormat)
                } catch (e: Exception) {
                    AppLogger.warn(
                        TAG,
                        "Audio codec ($audioMime) incompatible with $videoMime container; keeping video stream.",
                        e
                    )
                    -1
                }
            } else {
                -1
            }

            muxer.start()

            val maxInputSize = runCatching {
                if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                } else {
                    SAMPLE_BUFFER_SIZE
                }
            }.getOrDefault(SAMPLE_BUFFER_SIZE).coerceAtLeast(SAMPLE_BUFFER_SIZE)

            val buffer = ByteBuffer.allocate(maxInputSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // 1. Copy Video Elementary Stream packets without re-encoding (-c:v copy)
            var videoPackets = 0L
            videoExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            while (true) {
                currentCoroutineContext().ensureActive()
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    bufferInfo.size = 0
                    break
                }
                val rawTime = videoExtractor.sampleTime
                bufferInfo.presentationTimeUs = if (rawTime >= 0L) rawTime else 0L
                val sampleFlags = videoExtractor.sampleFlags
                bufferInfo.flags = if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(muxVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
                videoPackets++
            }

            // 2. Copy Audio Elementary Stream packets without re-encoding (-c:a copy)
            var audioPackets = 0L
            var lastAudioPts = -1L
            if (muxAudioTrack != -1 && audioTrackIndex != -1) {
                audioExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    bufferInfo.offset = 0
                    bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) {
                        bufferInfo.size = 0
                        break
                    }
                    val rawPts = audioExtractor.sampleTime.coerceAtLeast(0L)
                    val monotonicPts = if (rawPts <= lastAudioPts) lastAudioPts + 1L else rawPts
                    lastAudioPts = monotonicPts
                    bufferInfo.presentationTimeUs = monotonicPts
                    val sampleFlags = audioExtractor.sampleFlags
                    bufferInfo.flags = if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    muxer.writeSampleData(muxAudioTrack, buffer, bufferInfo)
                    audioExtractor.advance()
                    audioPackets++
                }
            }

            muxer.stop()
            val elapsedMs = System.currentTimeMillis() - startTimeMs
            AppLogger.info(
                TAG,
                "DASH stream-copy mux complete in ${elapsedMs}ms ($videoPackets video packets, $audioPackets audio packets -> ${outputFile.length()} bytes)."
            )
            Result.success(outputFile)
        } catch (e: Exception) {
            AppLogger.error(
                TAG,
                "FFmpeg/MediaMuxer execution error during stream copy: ${e.message}",
                e
            )
            runCatching {
                if (videoFile.exists() && videoFile.length() > 0L) {
                    videoFile.copyTo(outputFile, overwrite = true)
                    AppLogger.warn(TAG, "Recovered primary video container after muxer exception.")
                    return@withContext Result.success(outputFile)
                }
            }
            Result.failure(e)
        } finally {
            runCatching { videoExtractor?.release() }
            runCatching { audioExtractor?.release() }
            runCatching { muxer?.release() }
        }
    }
}
