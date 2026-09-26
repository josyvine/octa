package com.octastream.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OctaAmber
import com.example.ui.theme.OctaCoralRed
import com.example.ui.theme.OctaCyan
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalErrorRed
import com.example.ui.theme.TerminalHeaderSurface
import com.example.ui.theme.TerminalInfoGreen
import com.example.ui.theme.TerminalTextMuted
import com.example.ui.theme.TerminalWarnYellow
import com.octastream.logger.AppLogger
import com.octastream.model.LogEntry
import com.octastream.model.LogLevel
import kotlin.math.roundToInt

/**
 * Global Floating Diagnostic Log Bubble & Fullscreen Terminal Overlay.
 * Tracks drag gestures across the entire root Box and expands into an edge-to-edge
 * #121212 syntax-highlighted diagnostic console on tap.
 */
@Composable
fun FloatingLogBubbleOverlay(
    logs: List<LogEntry>,
    unreadAlertCount: Int,
    hasRecentError: Boolean,
    onMarkAlertsRead: () -> Unit,
    onClearLogs: () -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val bubbleSizePx = with(density) { 60.dp.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }

        var offsetX by remember {
            mutableFloatStateOf((maxWidthPx - bubbleSizePx - marginPx).coerceAtLeast(marginPx))
        }
        var offsetY by remember {
            mutableFloatStateOf((maxHeightPx * 0.72f).coerceAtLeast(marginPx))
        }

        // Keep bubble within bounds when orientation or window size changes
        LaunchedEffect(maxWidthPx, maxHeightPx) {
            offsetX = offsetX.coerceIn(
                marginPx,
                (maxWidthPx - bubbleSizePx - marginPx).coerceAtLeast(marginPx)
            )
            offsetY = offsetY.coerceIn(
                marginPx,
                (maxHeightPx - bubbleSizePx - marginPx * 5f).coerceAtLeast(marginPx)
            )
        }

        // 1. Draggable Floating Diagnostic Bubble (when collapsed)
        AnimatedVisibility(
            visible = !isExpanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (unreadAlertCount > 0) 1.12f else 1.03f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = if (hasRecentError) 550 else 1100,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bubblePulse"
            )

            val borderColor = when {
                hasRecentError -> OctaCoralRed
                unreadAlertCount > 0 -> OctaAmber
                else -> OctaCyan
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(60.dp)
                    .scale(pulseScale)
                    .shadow(14.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF141C2B),
                                Color(0xFF0B0F19)
                            )
                        )
                    )
                    .border(2.dp, borderColor, CircleShape)
                    .pointerInput(maxWidthPx, maxHeightPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(
                                8f,
                                (maxWidthPx - bubbleSizePx - 8f).coerceAtLeast(8f)
                            )
                            offsetY = (offsetY + dragAmount.y).coerceIn(
                                48f,
                                (maxHeightPx - bubbleSizePx - 96f).coerceAtLeast(48f)
                            )
                        }
                    }
                    .clickable {
                        onMarkAlertsRead()
                        isExpanded = true
                    }
                    .testTag("floating_diagnostic_bubble"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (hasRecentError) {
                            Icons.Default.ErrorOutline
                        } else if (unreadAlertCount > 0) {
                            Icons.Default.WarningAmber
                        } else {
                            Icons.Default.Terminal
                        },
                        contentDescription = "Open Diagnostic Terminal",
                        tint = borderColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "LOG",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = Color.White
                    )
                }

                if (unreadAlertCount > 0) {
                    Badge(
                        containerColor = if (hasRecentError) OctaCoralRed else OctaAmber,
                        contentColor = Color.Black,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .testTag("diagnostic_alert_badge")
                    ) {
                        Text(
                            text = if (unreadAlertCount > 99) "99+" else unreadAlertCount.toString(),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        // 2. Fullscreen Diagnostic Console Modal (#121212 dark terminal theme)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.94f, animationSpec = tween(200)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180))
        ) {
            BackHandler(enabled = isExpanded) {
                isExpanded = false
            }
            FullscreenDiagnosticConsole(
                logs = logs,
                onMinimize = { isExpanded = false },
                onClearBuffer = onClearLogs
            )
        }
    }
}

@Composable
private fun FullscreenDiagnosticConsole(
    logs: List<LogEntry>,
    onMinimize: () -> Unit,
    onClearBuffer: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }

    val filteredLogs = remember(logs, filterLevel) {
        if (filterLevel == null) logs else logs.filter { it.level == filterLevel }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.lastIndex)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("fullscreen_diagnostic_console"),
        color = TerminalBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // Terminal Top Bar
            Surface(
                color = TerminalHeaderSurface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = TerminalInfoGreen,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "OCTASTREAM DIAGNOSTIC CONSOLE",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Ring Buffer: ${logs.size}/500 lines • Live Telemetry",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = TerminalTextMuted
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val rawDump = AppLogger.exportFormattedLogs()
                                    clipboardManager.setText(AnnotatedString(rawDump))
                                    Toast.makeText(
                                        context,
                                        "Copied ${logs.size} log entries to clipboard",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF263245),
                                    contentColor = OctaCyan
                                ),
                                modifier = Modifier.testTag("copy_logs_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy to Clipboard",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Copy",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = onMinimize,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2B3548))
                                    .testTag("minimize_console_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloseFullscreen,
                                    contentDescription = "Minimize Console",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Level Filter Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TerminalFilterChip(
                            label = "ALL (${logs.size})",
                            selected = filterLevel == null,
                            accentColor = OctaCyan,
                            onClick = { filterLevel = null }
                        )
                        TerminalFilterChip(
                            label = "NET (${logs.count { it.level == LogLevel.NETWORK }})",
                            selected = filterLevel == LogLevel.NETWORK,
                            accentColor = TerminalInfoGreen,
                            onClick = { filterLevel = LogLevel.NETWORK }
                        )
                        TerminalFilterChip(
                            label = "WARN (${logs.count { it.level == LogLevel.WARN }})",
                            selected = filterLevel == LogLevel.WARN,
                            accentColor = TerminalWarnYellow,
                            onClick = { filterLevel = LogLevel.WARN }
                        )
                        TerminalFilterChip(
                            label = "ERR (${logs.count { it.level == LogLevel.ERROR }})",
                            selected = filterLevel == LogLevel.ERROR,
                            accentColor = TerminalErrorRed,
                            onClick = { filterLevel = LogLevel.ERROR }
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF2A2F3A))

            // Log Stream Body
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$ No diagnostic entries matching current filter_",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 13.sp,
                        color = TerminalTextMuted
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        TerminalLogRow(entry = entry)
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF2A2F3A))

            // Bottom Action Bar: Clear Log Buffer
            Surface(
                color = TerminalHeaderSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SYNTAX: GREEN=INFO/NET • YELLOW=WARN • RED=ERR",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = TerminalTextMuted,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedButton(
                        onClick = onClearBuffer,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TerminalErrorRed
                        ),
                        modifier = Modifier.testTag("clear_log_buffer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Log Buffer",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Clear Log Buffer",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalFilterChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val bgColor = if (selected) accentColor.copy(alpha = 0.2f) else Color(0xFF1F242D)
    val borderColor = if (selected) accentColor else Color(0xFF343B48)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) accentColor else TerminalTextMuted
        )
    }
}

@Composable
private fun TerminalLogRow(entry: LogEntry) {
    var showStackTrace by remember { mutableStateOf(true) }
    val levelColor = when (entry.level) {
        LogLevel.ERROR -> TerminalErrorRed
        LogLevel.WARN -> TerminalWarnYellow
        LogLevel.NETWORK -> TerminalInfoGreen
        LogLevel.INFO -> TerminalInfoGreen
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF181B20))
            .border(
                width = 1.dp,
                color = if (entry.level == LogLevel.ERROR) {
                    TerminalErrorRed.copy(alpha = 0.45f)
                } else if (entry.level == LogLevel.WARN) {
                    TerminalWarnYellow.copy(alpha = 0.35f)
                } else {
                    Color(0xFF232830)
                },
                shape = RoundedCornerShape(6.dp)
            )
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = entry.formattedTimestamp,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = TerminalTextMuted
            )
            Text(
                text = "[${entry.level.label}]",
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = levelColor
            )
            Text(
                text = "${entry.tag}:",
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = OctaCyan
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = entry.message,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = levelColor
        )

        if (!entry.stackTrace.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (showStackTrace) "▼ Stack Trace (tap to collapse)" else "▶ Stack Trace (tap to expand)",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalWarnYellow,
                modifier = Modifier
                    .clickable { showStackTrace = !showStackTrace }
                    .padding(vertical = 2.dp)
            )
            if (showStackTrace) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0E1014))
                        .padding(8.dp)
                ) {
                    Text(
                        text = entry.stackTrace,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = TerminalErrorRed.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}
