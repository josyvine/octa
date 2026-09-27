package com.octastream.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.octastream.model.DownloadSegment
import com.octastream.model.DownloadState
import com.octastream.model.DownloadTask
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "download_tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val sourceUrl: String,
    val title: String,
    val thumbnailUrl: String,
    val qualityLabel: String,
    val container: String,
    val videoUrl: String?,
    val audioUrl: String?,
    val isDash: Boolean,
    val threadCount: Int,
    val stateName: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val supportsRange: Boolean,
    val segmentsJson: String,
    val outputFilePath: String?,
    val errorMessage: String?,
    val createdAt: Long
) {
    fun toDomain(
        liveSpeedBps: Long = 0L,
        liveEtaSec: Long = -1L
    ): DownloadTask {
        val parsedState = runCatching { DownloadState.valueOf(stateName) }
            .getOrDefault(DownloadState.PAUSED)
        return DownloadTask(
            id = id,
            sourceUrl = sourceUrl,
            title = title,
            thumbnailUrl = thumbnailUrl,
            qualityLabel = qualityLabel,
            container = container,
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            isDash = isDash,
            threadCount = threadCount,
            state = parsedState,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            speedBytesPerSec = liveSpeedBps,
            etaSeconds = liveEtaSec,
            supportsRange = supportsRange,
            segments = decodeSegments(segmentsJson),
            outputFilePath = outputFilePath,
            errorMessage = errorMessage,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(task: DownloadTask): TaskEntity {
            return TaskEntity(
                id = task.id,
                sourceUrl = task.sourceUrl,
                title = task.title,
                thumbnailUrl = task.thumbnailUrl,
                qualityLabel = task.qualityLabel,
                container = task.container,
                videoUrl = task.videoUrl,
                audioUrl = task.audioUrl,
                isDash = task.isDash,
                threadCount = task.threadCount,
                stateName = task.state.name,
                totalBytes = task.totalBytes,
                downloadedBytes = task.downloadedBytes,
                supportsRange = task.supportsRange,
                segmentsJson = encodeSegments(task.segments),
                outputFilePath = task.outputFilePath,
                errorMessage = task.errorMessage,
                createdAt = task.createdAt
            )
        }

        fun encodeSegments(segments: List<DownloadSegment>): String {
            val arr = JSONArray()
            segments.forEach { seg ->
                val obj = JSONObject().apply {
                    put("index", seg.index)
                    put("role", seg.role)
                    put("startByte", seg.startByte)
                    put("endByte", seg.endByte)
                    put("downloadedBytes", seg.downloadedBytes)
                    put("isCompleted", seg.isCompleted)
                }
                arr.put(obj)
            }
            return arr.toString()
        }

        fun decodeSegments(json: String): List<DownloadSegment> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(
                            DownloadSegment(
                                index = obj.optInt("index", i + 1),
                                role = obj.optString("role", "MAIN"),
                                startByte = obj.optLong("startByte", 0L),
                                endByte = obj.optLong("endByte", 0L),
                                downloadedBytes = obj.optLong("downloadedBytes", 0L),
                                isCompleted = obj.optBoolean("isCompleted", false),
                                activeSpeedBps = 0L
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    suspend fun getAllTasksOnce(): List<TaskEntity>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(entity: TaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM download_tasks WHERE stateName = 'COMPLETED'")
    suspend fun deleteCompletedTasks()
}

@Database(entities = [TaskEntity::class], version = 1, exportSchema = false)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        fun getInstance(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "octastream_tasks.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}

class TaskRepository(private val taskDao: TaskDao) {
    val allTasksFlow: Flow<List<TaskEntity>> = taskDao.observeAllTasks()

    suspend fun getAllOnce(): List<DownloadTask> =
        taskDao.getAllTasksOnce().map { it.toDomain() }

    suspend fun upsert(task: DownloadTask) {
        taskDao.upsertTask(TaskEntity.fromDomain(task))
    }

    suspend fun deleteById(taskId: String) {
        taskDao.deleteTaskById(taskId)
    }

    suspend fun clearCompleted() {
        taskDao.deleteCompletedTasks()
    }
}
