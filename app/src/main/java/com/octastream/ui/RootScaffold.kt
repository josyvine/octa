package com.octastream.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OctaAmber
import com.example.ui.theme.OctaCyan
import com.example.ui.theme.OctaEmerald
import com.example.ui.theme.SpaceGroteskFamily
import com.octastream.data.StorageHelper
import com.octastream.model.DownloadState
import com.octastream.ui.components.FloatingLogBubbleOverlay
import com.octastream.ui.screens.DownloadScreen
import com.octastream.ui.screens.FetchScreen
import com.octastream.ui.screens.SettingsScreen
import com.octastream.ui.viewmodel.MainViewModel
import com.octastream.ui.viewmodel.OctaTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScaffold(
    viewModel: MainViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val extractionState by viewModel.extractionState.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val unreadAlertCount by viewModel.unreadAlertCount.collectAsStateWithLifecycle()
    val hasRecentError by viewModel.hasRecentError.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    // Step-wise Back Navigation: Secondary tabs -> FETCH -> LANDING
    BackHandler(enabled = selectedTab != OctaTab.LANDING) {
        if (selectedTab == OctaTab.FETCH) {
            viewModel.selectTab(OctaTab.LANDING)
        } else {
            viewModel.selectTab(OctaTab.FETCH)
        }
    }

    val activeTaskCount = tasks.count {
        it.state == DownloadState.DOWNLOADING ||
            it.state == DownloadState.MUXING ||
            it.state == DownloadState.QUEUED ||
            it.state == DownloadState.EXTRACTING
    }
    val liveSpeedBps = tasks.sumOf { it.speedBytesPerSec }

    // Root Box overlay containing Scaffold + Draggable Log Console Bubble
    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedScreen = maxWidth >= 720.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.selectTab(OctaTab.LANDING) }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(OctaCyan.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Hub,
                                        contentDescription = "Landing",
                                        tint = OctaCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "OCTASTREAM",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            color = OctaCyan.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "IDM CORE",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                color = OctaCyan,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${settings.threadCount}-Thread HTTP Range • DASH Muxer",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            if (liveSpeedBps > 0L || activeTaskCount > 0) {
                                Surface(
                                    color = OctaEmerald.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(end = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${StorageHelper.formatBytes(liveSpeedBps)}/s",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = OctaEmerald
                                        )
                                    }
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!isExpandedScreen) {
                        OctaBottomNavigationBar(
                            selectedTab = selectedTab,
                            totalTasksCount = tasks.size,
                            activeTaskCount = activeTaskCount,
                            onSelectTab = viewModel::selectTab
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpandedScreen) {
                        OctaSideNavigationRail(
                            selectedTab = selectedTab,
                            totalTasksCount = tasks.size,
                            activeTaskCount = activeTaskCount,
                            onSelectTab = viewModel::selectTab
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            OctaTab.LANDING -> LandingScreen(
                                configuredThreads = settings.threadCount,
                                onLaunchExtractor = { viewModel.selectTab(OctaTab.FETCH) }
                            )

                            OctaTab.FETCH -> FetchScreen(
                                urlInput = urlInput,
                                extractionState = extractionState,
                                configuredThreads = settings.threadCount,
                                onUrlChanged = viewModel::onUrlInputChanged,
                                onClearUrl = viewModel::clearUrlInput,
                                onFetchClicked = viewModel::fetchStreamManifest,
                                onDismissQualityPicker = viewModel::dismissQualityPicker,
                                onStartDownload = viewModel::startDownload
                            )

                            OctaTab.DOWNLOADS -> DownloadScreen(
                                tasks = tasks,
                                onPauseTask = viewModel::pauseTask,
                                onResumeTask = viewModel::resumeTask,
                                onDeleteTask = viewModel::deleteTask,
                                onClearFinishedTasks = viewModel::clearFinishedTasks,
                                onNavigateToExtract = { viewModel.selectTab(OctaTab.FETCH) }
                            )

                            OctaTab.SETTINGS -> SettingsScreen(
                                settings = settings,
                                cacheSizeBytes = cacheSizeBytes,
                                onSelectSafDirectory = viewModel::onSafDirectoryPicked,
                                onResetSafDirectory = viewModel::resetSafDirectory,
                                onThreadCountSelected = viewModel::setThreadCount,
                                onSingleStreamFallbackChanged = viewModel::setSingleStreamFallback,
                                onClearCache = viewModel::clearCache
                            )
                        }
                    }
                }
            }
        }

        // Global Floating Diagnostic Log Bubble & Terminal Console Overlay
        FloatingLogBubbleOverlay(
            logs = logs,
            unreadAlertCount = unreadAlertCount,
            hasRecentError = hasRecentError,
            onMarkAlertsRead = viewModel::markDiagnosticAlertsRead,
            onClearLogs = viewModel::clearDiagnosticLogs
        )
    }
}

@Composable
private fun LandingScreen(
    configuredThreads: Int,
    onLaunchExtractor: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Image Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, OctaCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_banner_1790464806368),
                    contentDescription = "OctaStream Hero",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xCC0A0E14),
                                    Color(0xF00A0E14)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = OctaCyan.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "IDM CORE ENGINE",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = OctaCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            color = OctaEmerald.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "$configuredThreads PARALLEL CONNECTIONS",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = OctaEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "OctaStream",
                        fontFamily = SpaceGroteskFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "Direct stream decoding, multi-segment HTTP Range acceleration & hardware lossless DASH multiplexing.",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = Color(0xFFB0BEC5),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Action CTA Button
        Button(
            onClick = onLaunchExtractor,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OctaCyan)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xFF002227)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "OPEN STREAM EXTRACTOR",
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF002227)
            )
        }

        // Architecture Features Overview
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "CORE CAPABILITIES",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = OctaCyan,
                letterSpacing = 1.sp
            )

            FeatureCard(
                icon = Icons.Default.Speed,
                title = "N-Way HTTP Range Slicing",
                subtitle = "Concurrently pulls segmented byte ranges through dynamic thread pools with RandomAccess true resume support.",
                accentColor = OctaCyan
            )

            FeatureCard(
                icon = Icons.Default.Transform,
                title = "Lossless Hardware DASH Muxer",
                subtitle = "Synchronously multiplexes isolated video and audio streams at hardware level with zero re-encoding.",
                accentColor = OctaEmerald
            )

            FeatureCard(
                icon = Icons.Default.Security,
                title = "Client Identity Preservation",
                subtitle = "Binds User-Agents and query range fallback parameters to bypass HTTP 403 Forbidden errors.",
                accentColor = OctaAmber
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun FeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontFamily = SpaceGroteskFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun OctaBottomNavigationBar(
    selectedTab: OctaTab,
    totalTasksCount: Int,
    activeTaskCount: Int,
    onSelectTab: (OctaTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == OctaTab.FETCH || selectedTab == OctaTab.LANDING,
            onClick = { onSelectTab(OctaTab.FETCH) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == OctaTab.FETCH || selectedTab == OctaTab.LANDING) Icons.Filled.Bolt else Icons.Outlined.Bolt,
                    contentDescription = stringResource(R.string.tab_extract)
                )
            },
            label = { Text(stringResource(R.string.tab_extract)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF002227),
                selectedTextColor = OctaCyan,
                indicatorColor = OctaCyan
            ),
            modifier = Modifier.testTag("nav_tab_extract")
        )

        NavigationBarItem(
            selected = selectedTab == OctaTab.DOWNLOADS,
            onClick = { onSelectTab(OctaTab.DOWNLOADS) },
            icon = {
                BadgedBox(
                    badge = {
                        if (totalTasksCount > 0) {
                            Badge(
                                containerColor = if (activeTaskCount > 0) OctaEmerald else OctaCyan,
                                contentColor = Color.Black
                            ) {
                                Text(
                                    text = totalTasksCount.toString(),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (selectedTab == OctaTab.DOWNLOADS) {
                            Icons.Filled.CloudDownload
                        } else {
                            Icons.Outlined.CloudDownload
                        },
                        contentDescription = stringResource(R.string.tab_downloads)
                    )
                }
            },
            label = { Text(stringResource(R.string.tab_downloads)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF002227),
                selectedTextColor = OctaCyan,
                indicatorColor = OctaCyan
            ),
            modifier = Modifier.testTag("nav_tab_downloads")
        )

        NavigationBarItem(
            selected = selectedTab == OctaTab.SETTINGS,
            onClick = { onSelectTab(OctaTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == OctaTab.SETTINGS) {
                        Icons.Filled.Settings
                    } else {
                        Icons.Outlined.Settings
                    },
                    contentDescription = stringResource(R.string.tab_settings)
                )
            },
            label = { Text(stringResource(R.string.tab_settings)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF002227),
                selectedTextColor = OctaCyan,
                indicatorColor = OctaCyan
            ),
            modifier = Modifier.testTag("nav_tab_settings")
        )
    }
}

@Composable
private fun OctaSideNavigationRail(
    selectedTab: OctaTab,
    totalTasksCount: Int,
    activeTaskCount: Int,
    onSelectTab: (OctaTab) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight()
    ) {
        Spacer(modifier = Modifier.weight(1f))
        NavigationRailItem(
            selected = selectedTab == OctaTab.FETCH || selectedTab == OctaTab.LANDING,
            onClick = { onSelectTab(OctaTab.FETCH) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == OctaTab.FETCH || selectedTab == OctaTab.LANDING) Icons.Filled.Bolt else Icons.Outlined.Bolt,
                    contentDescription = stringResource(R.string.tab_extract)
                )
            },
            label = { Text(stringResource(R.string.tab_extract)) },
            modifier = Modifier.testTag("nav_tab_extract")
        )
        NavigationRailItem(
            selected = selectedTab == OctaTab.DOWNLOADS,
            onClick = { onSelectTab(OctaTab.DOWNLOADS) },
            icon = {
                BadgedBox(
                    badge = {
                        if (totalTasksCount > 0) {
                            Badge(
                                containerColor = if (activeTaskCount > 0) OctaEmerald else OctaCyan,
                                contentColor = Color.Black
                            ) {
                                Text(text = totalTasksCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (selectedTab == OctaTab.DOWNLOADS) {
                            Icons.Filled.CloudDownload
                        } else {
                            Icons.Outlined.CloudDownload
                        },
                        contentDescription = stringResource(R.string.tab_downloads)
                    )
                }
            },
            label = { Text(stringResource(R.string.tab_downloads)) },
            modifier = Modifier.testTag("nav_tab_downloads")
        )
        NavigationRailItem(
            selected = selectedTab == OctaTab.SETTINGS,
            onClick = { onSelectTab(OctaTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == OctaTab.SETTINGS) {
                        Icons.Filled.Settings
                    } else {
                        Icons.Outlined.Settings
                    },
                    contentDescription = stringResource(R.string.tab_settings)
                )
            },
            label = { Text(stringResource(R.string.tab_settings)) },
            modifier = Modifier.testTag("nav_tab_settings")
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}