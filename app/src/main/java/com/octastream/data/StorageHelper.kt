package com.octastream.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.octastream.logger.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Handles Android Storage Access Framework (SAF) DocumentFile operations,
 * persistable URI permissions, cache staging directories, and moving final
 * media containers to the user-configured target folder.
 */
object StorageHelper {

    private const val TAG = "StorageHelper"

    /**
     * Takes persistable read/write URI permission across device reboots and returns a human-readable path.
     */
    fun persistSafDirectoryPermission(context: Context, treeUri: Uri): String {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            val docFile = DocumentFile.fromTreeUri(context, treeUri)
            val folderName = docFile?.name ?: treeUri.lastPathSegment ?: "Selected Folder"
            "SAF://$folderName (${treeUri.authority})"
        } catch (e: Exception) {
            AppLogger.error(TAG, "Failed to persist SAF URI permission for $treeUri", e)
            treeUri.toString()
        }
    }

    /**
     * Returns the dedicated staging directory inside app cache where multi-threaded
     * RandomAccessFile segments and temporary DASH audio/video tracks are assembled.
     */
    fun getWorkCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "octa_work_chunks")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Exports a completed local staging file to the user's SAF-selected DocumentFile tree
     * (or falls back to the app's external Downloads/OctaStream directory if no SAF folder is set).
     * Returns the final destination URI or file path string.
     */
    suspend fun exportCompletedFile(
        context: Context,
        sourceFile: File,
        desiredFileName: String,
        mimeType: String,
        safTreeUriString: String?
    ): String = withContext(Dispatchers.IO) {
        val sanitizedName = sanitizeFileName(desiredFileName)
        if (!safTreeUriString.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(safTreeUriString)
                val treeDir = DocumentFile.fromTreeUri(context, treeUri)
                if (treeDir != null && treeDir.canWrite()) {
                    // Remove existing file with same name if present
                    treeDir.findFile(sanitizedName)?.delete()
                    val targetDoc = treeDir.createFile(mimeType, sanitizedName)
                    if (targetDoc != null) {
                        context.contentResolver.openOutputStream(targetDoc.uri, "w")?.use { out ->
                            FileInputStream(sourceFile).use { input ->
                                input.copyTo(out, bufferSize = 64 * 1024)
                                out.flush()
                            }
                        }
                        AppLogger.info(
                            TAG,
                            "Exported final media container to SAF URI: ${targetDoc.uri}"
                        )
                        return@withContext targetDoc.uri.toString()
                    }
                } else {
                    AppLogger.warn(
                        TAG,
                        "Configured SAF directory is not writable. Falling back to app Downloads folder."
                    )
                }
            } catch (e: Exception) {
                AppLogger.warn(
                    TAG,
                    "Error writing to SAF tree URI ($safTreeUriString). Using internal fallback.",
                    e
                )
            }
        }

        // Fallback: App-specific external Downloads/OctaStream directory (no legacy storage permission required)
        val baseDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Downloads")
        val octaDir = File(baseDownloads, "OctaStream")
        if (!octaDir.exists()) {
            octaDir.mkdirs()
        }
        val destFile = File(octaDir, sanitizedName)
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destFile).use { out ->
                input.copyTo(out, bufferSize = 64 * 1024)
                out.flush()
            }
        }
        AppLogger.info(TAG, "Saved completed file to local storage: ${destFile.absolutePath}")
        destFile.absolutePath
    }

    /**
     * Calculates the total byte size of leftover temporary chunk and DASH files.
     */
    fun getWorkCacheSizeBytes(context: Context): Long {
        val dir = getWorkCacheDir(context)
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Deletes all temporary chunk and DASH staging files in the work cache directory.
     */
    suspend fun clearWorkCache(context: Context): Int = withContext(Dispatchers.IO) {
        val dir = getWorkCacheDir(context)
        var deletedCount = 0
        dir.listFiles()?.forEach { file ->
            if (file.deleteRecursively()) {
                deletedCount++
            }
        }
        AppLogger.info(TAG, "Cache purge complete. Removed $deletedCount temporary chunk/DASH files.")
        deletedCount
    }

    fun sanitizeFileName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (cleaned.length > 96) cleaned.take(96) else cleaned.ifEmpty { "octastream_media" }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(java.util.Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(java.util.Locale.US, "%.2f MB", bytes / mb)
            bytes >= kb -> String.format(java.util.Locale.US, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
