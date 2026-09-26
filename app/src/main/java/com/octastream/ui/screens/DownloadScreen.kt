package com.octastream.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OctaAmber
import com.example.ui.theme.OctaCobalt
import com.example.ui.theme.OctaCoralRed
import com.example.ui.theme.OctaCyan
import com.example.ui.theme.OctaEmerald
import com.octastream.data.StorageHelper
import com.octastream.model.DownloadSegment
import com.octastream.model.DownloadState
import com.octastream.model.DownloadTask
import java.util.Locale

@Composable
fun DownloadScreen(
    tasks: List<DownloadTask>,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearFinishedTasks: () -> Unit,
    onNavigateToExtract: () -> Unit
) {
    val activeCount = tasks.count {
        it.state == DownloadState.DOWNLOADING ||
            it.state == DownloadState.MUXING ||
            it.state == DownloadState.QUEUED ||
            it.state == DownloadState.EXTRACTING
    }
    val completedCount = tasks.count { it.state == DownloadState.COMPLETED }
    val totalLiveSpeed = tasks.sumOf { it.speedBytesPerSec }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Top Bar with Aggregate Telemetry & Global "Clear Finished Tasks" Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Parallel Download Manager",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "$activeCount Active • $completedCount Completed • ${StorageHelper.formatBytes(totalLiveSpeed)}/s",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = OctaCyan
                    )
                }

                OutlinedButton(
                    onClick = onClearFinishedTasks,
                    enabled = completedCount > 0,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("clear_finished_tasks_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_clear_finished),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (tasks.isEmpty()) {
                EmptyDownloadsView(onNavigateToExtract = onNavigateToExtract)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        DownloadTaskCard(
                            task = task,
                            onPause = { onPauseTask(task.id) },
                            onResume = { onResumeTask(task.id) },
                            onDelete = { onDeleteTask(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val stateColor = when (task.state) {
        DownloadState.EXTRACTING -> OctaAmber
        DownloadState.QUEUED -> OctaAmber
        DownloadState.DOWNLOADING -> OctaCyan
        DownloadState.PAUSED -> OctaAmber
        DownloadState.MUXING -> Color(0xFFB388FF)
        DownloadState.COMPLETED -> OctaEmerald
        DownloadState.ERROR -> OctaCoralRed
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = stateColor.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.large
            )
            .testTag("task_item_card_${task.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header: Title, State Badge, and Overall Progress Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = stateColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = task.state.name,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = stateColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (task.isDash) "DASH MUX • ${task.container}" else task.container,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = task.qualityLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Big Monospace Percentage Readout
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${task.overallProgressPercent}%",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = stateColor
                    )
                    Text(
                        text = "${task.segments.size.coerceAtLeast(task.threadCount)} Threads",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Master Progress Bar
            if (task.state == DownloadState.MUXING) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = stateColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else {
                LinearProgressIndicator(
                    progress = { task.overallProgressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = stateColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }

            // 3. Speed, Bytes & ETA Telemetry Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val downloadedStr = StorageHelper.formatBytes(task.downloadedBytes)
                val totalStr = if (task.totalBytes > 0L) {
                    StorageHelper.formatBytes(task.totalBytes)
                } else {
                    "Streaming"
                }
                Text(
                    text = "$downloadedStr / $totalStr",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = OctaCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${StorageHelper.formatBytes(task.speedBytesPerSec)}/s",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = OctaCyan
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatEta(task.etaSeconds, task.state),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 4. IDM-Style N-Segment Parallel Visualization Matrix
            if (task.segments.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = OctaCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "IDM PARALLEL BYTE-RANGE SEGMENTS (${task.segments.size})",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (task.supportsRange) "HTTP 206 Range: bytes" else "Single-Stream",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 9.sp,
                                color = if (task.supportsRange) OctaEmerald else OctaAmber
                            )
                        }

                        // Render each parallel connection bar (Part 1, Part 2, ..., Part N)
                        task.segments.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEach { seg ->
                                    IdmSegmentMiniBar(
                                        segment = seg,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 5. Error or Output Path Banner
            AnimatedVisibility(visible = !task.errorMessage.isNullOrBlank()) {
                Surface(
                    color = OctaCoralRed.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = OctaCoralRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = task.errorMessage ?: "",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = OctaCoralRed
                        )
                    }
                }
            }

            AnimatedVisibility(visible = task.state == DownloadState.COMPLETED && !task.outputFilePath.isNullOrBlank()) {
                Surface(
                    color = OctaEmerald.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = OctaEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Saved: ${task.outputFilePath}",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = OctaEmerald,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            // 6. Action Controls per Card: Pause, Resume, and Delete/Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val canPause = task.state == DownloadState.DOWNLOADING || task.state == DownloadState.QUEUED
                val canResume = task.state == DownloadState.PAUSED || task.state == DownloadState.ERROR

                FilledTonalButton(
                    onClick = onPause,
                    enabled = canPause,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("pause_task_button_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause Download",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Pause", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = onResume,
                    enabled = canResume,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = OctaCyan.copy(alpha = 0.2f),
                        contentColor = OctaCyan
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("resume_task_button_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume Download",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Resume", style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = OctaCoralRed
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("delete_task_button_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete or Cancel Task",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (task.state == DownloadState.COMPLETED) "Delete" else "Cancel",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun IdmSegmentMiniBar(
    segment: DownloadSegment,
    modifier: Modifier = Modifier
) {
    val barColor = when {
        segment.isCompleted -> OctaEmerald
        segment.role == "AUDIO" -> OctaAmber
        else -> OctaCyan
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF101622))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (segment.role == "MAIN") {
                    "Part ${segment.index}"
                } else {
                    "Part ${segment.index} (${segment.role.take(3)})"
                },
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = Color(0xFFD5E1F2)
            )
            Text(
                text = "${segment.progressPercent}%",
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = barColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { segment.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = Color(0xFF232F46),
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun EmptyDownloadsView(onNavigateToExtract: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudQueue,
                contentDescription = null,
                tint = OctaCyan,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No Active or Queued Transfers",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Extract a stream from the Home screen to watch N parallel HTTP Range workers and DASH stream-copy multiplexing in real time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onNavigateToExtract,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OctaCyan,
                    contentColor = Color(0xFF002227)
                ),
                modifier = Modifier.testTag("empty_go_to_extract_button")
            ) {
                Text(
                    text = "Extract Media Stream",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private fun formatEta(etaSeconds: Long, state: DownloadState): String {
    return when {
        state == DownloadState.COMPLETED -> "Done"
        state == DownloadState.MUXING -> "Muxing…"
        state == DownloadState.PAUSED -> "Paused"
        etaSeconds < 0L -> "--:--"
        etaSeconds >= 3600L -> String.format(
            Locale.US,
            "%dh %02dm",
            etaSeconds / 3600,
            (etaSeconds % 3600) / 60
        )
        else -> String.format(Locale.US, "%02d:%02d", etaSeconds / 60, etaSeconds % 60)
    }
}
