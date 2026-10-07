package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.components.AudioPreservedBadge
import com.example.ui.components.GradientActionButton
import com.example.ui.components.Video2PhotoTopBar
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceCard
import com.example.ui.theme.NavySurfaceCardBorder
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VibrantPurple
import com.example.video.OutputQuality
import com.example.video.VideoInfo

@Composable
fun VideoSettingsScreen(
    viewModel: MainViewModel,
    videoInfo: VideoInfo?,
    selectedQuality: OutputQuality,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateTo(AppScreen.HOME)
    }

    if (videoInfo == null) {
        Box(
            modifier = modifier.fillMaxSize().background(NavyBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No video selected", color = TextSecondary)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
    ) {
        Video2PhotoTopBar(
            title = "Video Settings",
            subtitle = videoInfo.name,
            onBackClick = { viewModel.navigateTo(AppScreen.HOME) }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Selected Video Header Card
            item {
                Surface(
                    color = NavySurfaceCard,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NavySurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VibrantPurple.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoFile,
                                contentDescription = null,
                                tint = VibrantPurple,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = videoInfo.name,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${videoInfo.width}x${videoInfo.height} • ${videoInfo.formattedDuration()}",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }
                }
            }

            // Core Settings Grid
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "CONVERSION PARAMETERS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    // Video Duration Card
                    SettingsParameterCard(
                        title = "Video Duration",
                        value = videoInfo.formattedDuration(),
                        subtext = "${videoInfo.durationSeconds} total seconds",
                        icon = Icons.Default.Schedule,
                        highlightColor = NeonCyan
                    )

                    // Screenshot Interval Card (Fixed 3s)
                    SettingsParameterCard(
                        title = "Screenshot Interval",
                        value = "3 seconds",
                        subtext = "Fixed interval • One frame captured every 3s",
                        icon = Icons.Default.Sync,
                        highlightColor = VibrantPurple
                    )

                    // Estimated Screenshots Card (Duration ÷ 3)
                    SettingsParameterCard(
                        title = "Estimated Screenshots",
                        value = "${videoInfo.estimatedScreenshots} Screenshots",
                        subtext = "Formula: ${videoInfo.durationSeconds}s ÷ 3 = ${videoInfo.estimatedScreenshots} frames",
                        icon = Icons.Default.HighQuality,
                        highlightColor = ElectricIndigo
                    )

                    // Original Audio Status Card
                    Surface(
                        color = NavySurfaceCard,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(EmeraldGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Original Audio",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = if (videoInfo.hasAudio) "ON • 100% losslessly synchronized" else "No audio stream detected",
                                    style = MaterialTheme.typography.bodySmall.copy(color = EmeraldGreen)
                                )
                            }
                            Switch(
                                checked = true,
                                onCheckedChange = { /* Audio preservation is strictly enforced */ },
                                enabled = false,
                                colors = SwitchDefaults.colors(
                                    disabledCheckedTrackColor = EmeraldGreen,
                                    disabledCheckedThumbColor = Color.White
                                )
                            )
                        }
                    }

                    // Edge Case Notice: if duration is not divisible by 3 seconds
                    if (videoInfo.remainderSeconds > 0) {
                        Surface(
                            color = NavySurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Edge case handled: Remaining ${videoInfo.remainderSeconds}s portion will automatically extend final frame so audio timeline matches original duration.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFBAE6FD),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Output Quality Selector
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "OUTPUT QUALITY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutputQuality.values().forEach { quality ->
                            val isSelected = selectedQuality == quality
                            QualityOptionChip(
                                quality = quality,
                                isSelected = isSelected,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setQuality(quality) }
                            )
                        }
                    }
                }
            }

            // Primary "Create Video" Button
            item {
                Spacer(modifier = Modifier.height(10.dp))
                GradientActionButton(
                    text = "Create Video",
                    icon = Icons.Default.PlayArrow,
                    testTag = "create_video_button",
                    onClick = { viewModel.startProcessing() }
                )
            }
        }
    }
}

@Composable
private fun SettingsParameterCard(
    title: String,
    value: String,
    subtext: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlightColor: Color
) {
    Surface(
        color = NavySurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NavySurfaceCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(highlightColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = highlightColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(color = TextMuted)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun QualityOptionChip(
    quality: OutputQuality,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) ElectricIndigo.copy(alpha = 0.25f) else NavySurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) NeonCyan else NavySurfaceCardBorder
        ),
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("quality_option_${quality.name}")
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = quality.label.split(" ").first(),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else TextPrimary
                )
            )
            Text(
                text = quality.label.split(" ").getOrElse(1) { "" },
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isSelected) NeonCyan else TextMuted,
                    fontSize = 10.sp
                )
            )
        }
    }
}
