package com.octastream.ui.components

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Global Floating Diagnostic Log Bubble & Fullscreen Terminal Overlay.
 * Tracks drag gestures across the entire root Box and expands into an edge-to-edge
 * #121212 syntax-highlighted console on tap.
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
                        }
                        ,
                        contentDescription = "Open Console",
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

        // 2. Fullscreen Console Modal (#121212 dark terminal theme)
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
    val view = LocalView.current
    val listState = rememberLazyListState()
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }
    var copiedAllFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(copiedAllFeedback) {
        if (copiedAllFeedback) {
            delay(1500)
            copiedAllFeedback = false
        }
    }

    val filteredLogs = remember(logs, filterLevel) {
        if (filterLevel == null) logs else logs.filter { it.level == filterLevel }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.lastIndex)
        }
    }

    val copyEntryToClipboard: (LogEntry) -> Unit = { entry ->
        val formatted = buildString {
            append("${entry.formattedTimestamp} [${entry.level.label}] ${entry.tag}: ${entry.message}")
            if (!entry.stackTrace.isNullOrBlank()) {
                append("\n")
                append(entry.stackTrace)
            }
        }
        clipboardManager.setText(AnnotatedString(formatted))
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        Toast.makeText(context, "Copied log entry", Toast.LENGTH_SHORT).show()
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
            // Clean Console Header Bar: "Console" on left + Copy, Clear, Minimize icon buttons on right
            Surface(
                color = TerminalHeaderSurface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = OctaCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Console",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Visual Copy Icon Button (Icon Only)
                            IconButton(
                                onClick = {
                                    val textToCopy = if (filterLevel == null) {
                                        AppLogger.exportFormattedLogs()
                                    } else {
                                        filteredLogs.joinToString("\n\n") { entry ->
                                            buildString {
                                                append("${entry.formattedTimestamp} [${entry.level.label}] ${entry.tag}: ${entry.message}")
                                                if (!entry.stackTrace.isNullOrBlank()) {
                                                    append("\n")
                                                    append(entry.stackTrace)
                                                }
                                            }
                                        }
                                    }
                                    clipboardManager.setText(AnnotatedString(textToCopy))
                                    copiedAllFeedback = true
                                    Toast.makeText(
                                        context,
                                        "Copied ${filteredLogs.size} log entries",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(
                                        width = 1.dp,
                                        color = if (copiedAllFeedback) TerminalInfoGreen else OctaCyan.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .testTag("copy_logs_button")
                            ) {
                                Icon(
                                    imageVector = if (copiedAllFeedback) {
                                        Icons.Default.Check
                                    } else {
                                        Icons.Default.ContentCopy
                                    },
                                    contentDescription = stringResource(R.string.action_copy_logs),
                                    tint = if (copiedAllFeedback) TerminalInfoGreen else OctaCyan,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // 2. Clear Log Buffer Icon Button (Icon Only)
                            IconButton(
                                onClick = {
                                    onClearBuffer()
                                    Toast.makeText(context, "Cleared log buffer", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2A1C20))
                                    .border(
                                        width = 1.dp,
                                        color = TerminalErrorRed.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .testTag("clear_log_buffer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = stringResource(R.string.action_clear_logs),
                                    tint = TerminalErrorRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // 3. Minimize Console Icon Button (Google Material CloseFullscreen Icon)
                            IconButton(
                                onClick = onMinimize,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF242B38))
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFF3B4454),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .testTag("minimize_console_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloseFullscreen,
                                    contentDescription = stringResource(R.string.action_minimize_logs),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
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
                        text = "$ No logs_",
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
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        TerminalLogRow(
                            entry = entry,
                            onCopyEntry = { copyEntryToClipboard(entry) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TerminalLogRow(
    entry: LogEntry,
    onCopyEntry: () -> Unit
) {
    var showStackTrace by remember { mutableStateOf(true) }
    var copiedThisRow by remember { mutableStateOf(false) }

    LaunchedEffect(copiedThisRow) {
        if (copiedThisRow) {
            delay(1400)
            copiedThisRow = false
        }
    }

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
            .combinedClickable(
                onClick = {
                    if (!entry.stackTrace.isNullOrBlank()) {
                        showStackTrace = !showStackTrace
                    }
                },
                onLongClick = {
                    copiedThisRow = true
                    onCopyEntry()
                }
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
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

            // Per-entry visual copy icon
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF222731))
                    .clickable {
                        copiedThisRow = true
                        onCopyEntry()
                    }
                    .testTag("copy_log_entry_${entry.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (copiedThisRow) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy log entry",
                    tint = if (copiedThisRow) TerminalInfoGreen else TerminalTextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
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
