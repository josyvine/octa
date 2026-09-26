package com.octastream.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OctaAmber
import com.example.ui.theme.OctaCobalt
import com.example.ui.theme.OctaCoralRed
import com.example.ui.theme.OctaCyan
import com.example.ui.theme.OctaEmerald
import com.octastream.data.StorageHelper
import com.octastream.model.QualityOption
import com.octastream.model.SampleStreamPreset
import com.octastream.model.StreamCategory
import com.octastream.model.StreamInfo
import com.octastream.ui.viewmodel.ExtractionUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FetchScreen(
    urlInput: String,
    extractionState: ExtractionUiState,
    configuredThreads: Int,
    presets: List<SampleStreamPreset>,
    onUrlChanged: (String) -> Unit,
    onClearUrl: () -> Unit,
    onFetchClicked: (String?) -> Unit,
    onDismissQualityPicker: () -> Unit,
    onStartDownload: (StreamInfo, QualityOption) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val isResolving = extractionState is ExtractionUiState.Resolving

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Banner Card
            item {
                HeroHeaderBanner(configuredThreads = configuredThreads)
            }

            // 2. Primary Extraction Card (URL Input + Paste + Clear + Fetch Action)
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            MaterialTheme.shapes.large
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = OctaCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Direct Stream Extractor",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                color = OctaCyan.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "NewPipe + HTTP Range",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OctaCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = onUrlChanged,
                            enabled = !isResolving,
                            label = { Text(stringResource(R.string.url_input_label)) },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.url_input_placeholder),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OctaCyan,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (urlInput.isNotEmpty()) {
                                        IconButton(
                                            onClick = onClearUrl,
                                            modifier = Modifier.testTag("clear_url_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = stringResource(R.string.action_clear_url),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            val clipText = clipboardManager.getText()?.text
                                            if (!clipText.isNullOrBlank()) {
                                                onUrlChanged(clipText.trim())
                                            }
                                        },
                                        modifier = Modifier.testTag("paste_clipboard_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = stringResource(R.string.action_paste_clipboard),
                                            tint = OctaCyan
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("url_input_field")
                        )

                        // Error Banner if extraction failed
                        AnimatedVisibility(visible = extractionState is ExtractionUiState.Error) {
                            val errMsg = (extractionState as? ExtractionUiState.Error)?.message ?: ""
                            Surface(
                                color = OctaCoralRed.copy(alpha = 0.14f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = OctaCoralRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = errMsg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OctaCoralRed
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { onFetchClicked(null) },
                            enabled = !isResolving,
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OctaCyan,
                                contentColor = Color(0xFF002227)
                            ),
                            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("fetch_streams_button")
                        ) {
                            if (isResolving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp,
                                    color = Color(0xFF002227)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.action_resolving_streams),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.action_fetch_streams),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            // 3. Architecture Pipeline Telemetry Pills
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PipelineMetricCard(
                        title = "$configuredThreads Threads",
                        subtitle = "HTTP Byte-Range",
                        accent = OctaCyan,
                        modifier = Modifier.weight(1f)
                    )
                    PipelineMetricCard(
                        title = "DASH Muxer",
                        subtitle = "-c copy Zero-Encode",
                        accent = OctaEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    PipelineMetricCard(
                        title = "RandomAccess",
                        subtitle = "True Pause/Resume",
                        accent = OctaAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4. Verified Benchmark & Open-Movie Multi-Stream Presets
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "VERIFIED MULTI-THREAD & DASH TEST STREAMS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap any stream below to immediately test N-segment Range downloading or 1080p/4K DASH audio+video multiplexing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            items(presets, key = { it.url }) { preset ->
                PresetStreamCard(
                    preset = preset,
                    enabled = !isResolving,
                    onSelect = {
                        onFetchClicked(preset.url)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        // 5. Quality Picker ModalBottomSheet when extraction completes
        if (extractionState is ExtractionUiState.Success) {
            QualityPickerBottomSheet(
                streamInfo = extractionState.streamInfo,
                configuredThreads = configuredThreads,
                onDismiss = onDismissQualityPicker,
                onStartDownload = { selectedOption ->
                    onStartDownload(extractionState.streamInfo, selectedOption)
                }
            )
        }
    }
}

@Composable
private fun HeroHeaderBanner(configuredThreads: Int) {
    Card(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp)
            .border(
                1.dp,
                OctaCyan.copy(alpha = 0.35f),
                MaterialTheme.shapes.large
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner_1790464806368),
                contentDescription = "OctaStream Parallel Engine Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xEE0B0F19),
                                Color(0xBB0B0F19),
                                Color(0x660B0F19)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = OctaCyan.copy(alpha = 0.2f),
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
                        color = OctaEmerald.copy(alpha = 0.2f),
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

                Column {
                    Text(
                        text = "OctaStream",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Direct signature decoding, multi-segment HTTP Range acceleration & zero-encode DASH multiplexing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD0DCF0),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineMetricCard(
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.border(
            1.dp,
            accent.copy(alpha = 0.3f),
            MaterialTheme.shapes.medium
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = title,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = accent
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PresetStreamCard(
    preset: SampleStreamPreset,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onSelect)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                MaterialTheme.shapes.medium
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OctaCyan.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = null,
                        tint = OctaCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = preset.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = preset.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = OctaCobalt.copy(alpha = 0.22f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = preset.badge,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Color(0xFF9FB3FF),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityPickerBottomSheet(
    streamInfo: StreamInfo,
    configuredThreads: Int,
    onDismiss: () -> Unit,
    onStartDownload: (QualityOption) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedOption by remember(streamInfo) {
        mutableStateOf(
            streamInfo.qualityOptions.firstOrNull { it.category == StreamCategory.DASH_VIDEO }
                ?: streamInfo.qualityOptions.first()
        )
    }

    val groupedOptions = remember(streamInfo) {
        streamInfo.qualityOptions.groupBy { it.category }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("quality_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            // Media Header: Thumbnail, Title, Duration, Service Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = streamInfo.thumbnailUrl,
                    contentDescription = streamInfo.title,
                    placeholder = painterResource(id = R.drawable.img_hero_banner_1790464806368),
                    error = painterResource(id = R.drawable.img_hero_banner_1790464806368),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 112.dp, height = 68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, OctaCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = OctaCyan.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = streamInfo.serviceName.uppercase(),
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = OctaCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = formatDuration(streamInfo.durationSeconds),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = streamInfo.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = streamInfo.uploaderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Scrollable Stream Categories
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreamCategory.entries.forEach { category ->
                    val listForCategory = groupedOptions[category].orEmpty()
                    if (listForCategory.isNotEmpty()) {
                        item(key = "header_${category.name}") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            ) {
                                Icon(
                                    imageVector = when (category) {
                                        StreamCategory.PROGRESSIVE -> Icons.Default.HighQuality
                                        StreamCategory.DASH_VIDEO -> Icons.Default.MovieFilter
                                        StreamCategory.AUDIO_ONLY -> Icons.Default.AudioFile
                                    },
                                    contentDescription = null,
                                    tint = when (category) {
                                        StreamCategory.PROGRESSIVE -> OctaCyan
                                        StreamCategory.DASH_VIDEO -> OctaEmerald
                                        StreamCategory.AUDIO_ONLY -> OctaAmber
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = category.displayName.uppercase(),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(listForCategory, key = { it.id }) { option ->
                            val isSelected = selectedOption.id == option.id
                            QualityOptionRow(
                                option = option,
                                selected = isSelected,
                                onClick = { selectedOption = option }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Start Download Action Button
            Button(
                onClick = { onStartDownload(selectedOption) },
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OctaCyan,
                    contentColor = Color(0xFF002227)
                ),
                contentPadding = PaddingValues(vertical = 15.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_download_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (selectedOption.isDashMuxRequired) {
                        "Start $configuredThreads-Thread DASH Download & Mux (${selectedOption.resolution})"
                    } else {
                        "Start $configuredThreads-Thread Parallel Download (${selectedOption.resolution})"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun QualityOptionRow(
    option: QualityOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) OctaCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val containerColor = if (selected) {
        OctaCyan.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) OctaCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${option.container} • ${option.codec}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            if (option.estimatedSizeBytes > 0L) {
                Text(
                    text = StorageHelper.formatBytes(option.estimatedSizeBytes),
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = OctaCyan
                )
            }
            if (option.isDashMuxRequired) {
                Text(
                    text = "FFMPEG MUX",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = OctaEmerald
                )
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return "LIVE / STREAM"
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
