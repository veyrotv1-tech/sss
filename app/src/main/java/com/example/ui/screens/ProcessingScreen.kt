package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.MainViewModel
import com.example.ui.components.AudioPreservedBadge
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurfaceCard
import com.example.ui.theme.NavySurfaceCardBorder
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VibrantPurple
import com.example.video.ProcessingProgress
import com.example.video.ProcessingStage
import com.example.video.VideoInfo

@Composable
fun ProcessingScreen(
    viewModel: MainViewModel,
    videoInfo: VideoInfo?,
    progress: ProcessingProgress,
    modifier: Modifier = Modifier
) {
    BackHandler {
        // Prevent accidental back exit during heavy encoding, or prompt cancel
        viewModel.cancelProcessing()
    }

    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Status
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Processing Video",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = progress.statusMessage.ifEmpty { "Generating 3-second slideshow video..." },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }

        // Circular Progress Ring & Percentage
        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(170.dp)
                    .padding(8.dp)
            ) {
                // Background track
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = NavySurfaceCardBorder,
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round
                )

                // Animated active progress
                CircularProgressIndicator(
                    progress = { progress.overallPercentage / 100f },
                    modifier = Modifier.fillMaxSize(),
                    color = NeonCyan,
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${progress.overallPercentage}%",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            fontSize = 38.sp
                        )
                    )
                    Text(
                        text = "PROCESSING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }

        // Audio Preservation Indicator
        item {
            AudioPreservedBadge()
        }

        // Detailed Stats Overview Card
        item {
            Surface(
                color = NavySurfaceCard,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NavySurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "LIVE CONVERSION STATS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        )
                    )

                    // Video Duration
                    StatRow(
                        label = "Video Duration",
                        value = videoInfo?.formattedDuration() ?: "00:00"
                    )

                    // Screenshot Interval
                    StatRow(
                        label = "Screenshot Interval",
                        value = "3 seconds (fixed)"
                    )

                    // Screenshots count
                    StatRow(
                        label = "Screenshots Total",
                        value = "${progress.totalScreenshots} frames"
                    )

                    // Extracting count
                    StatRow(
                        label = "Screenshots Extracted",
                        value = "${progress.extractedScreenshots} / ${progress.totalScreenshots}",
                        valueColor = if (progress.extractedScreenshots == progress.totalScreenshots && progress.totalScreenshots > 0) EmeraldGreen else NeonCyan
                    )

                    // Video creation %
                    StatRow(
                        label = "Video Creation",
                        value = "${progress.videoCreationPercentage}%"
                    )

                    // Audio Status
                    StatRow(
                        label = "Audio Status",
                        value = "Original audio preserved",
                        valueColor = EmeraldGreen
                    )
                }
            }
        }

        // Step by Step Pipeline Stages
        item {
            Surface(
                color = NavySurfaceCard,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NavySurfaceCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "PROCESSING PIPELINE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        )
                    )

                    PipelineStepItem(
                        title = "Analyzing video...",
                        isCompleted = progress.stage.ordinal > ProcessingStage.ANALYZING.ordinal,
                        isActive = progress.stage == ProcessingStage.ANALYZING
                    )

                    PipelineStepItem(
                        title = "Calculating screenshots...",
                        isCompleted = progress.stage.ordinal > ProcessingStage.CALCULATING.ordinal,
                        isActive = progress.stage == ProcessingStage.CALCULATING
                    )

                    PipelineStepItem(
                        title = "Extracting frames...",
                        isCompleted = progress.stage.ordinal > ProcessingStage.EXTRACTING_FRAMES.ordinal,
                        isActive = progress.stage == ProcessingStage.EXTRACTING_FRAMES,
                        detail = if (progress.totalScreenshots > 0) "${progress.extractedScreenshots} / ${progress.totalScreenshots}" else null
                    )

                    PipelineStepItem(
                        title = "Creating photo video...",
                        isCompleted = progress.stage.ordinal > ProcessingStage.CREATING_VIDEO.ordinal,
                        isActive = progress.stage == ProcessingStage.CREATING_VIDEO,
                        detail = "${progress.videoCreationPercentage}%"
                    )

                    PipelineStepItem(
                        title = "Adding original audio...",
                        isCompleted = progress.stage.ordinal > ProcessingStage.ADDING_AUDIO.ordinal,
                        isActive = progress.stage == ProcessingStage.ADDING_AUDIO
                    )

                    PipelineStepItem(
                        title = "Finalizing video...",
                        isCompleted = progress.stage.ordinal >= ProcessingStage.COMPLETED.ordinal,
                        isActive = progress.stage == ProcessingStage.FINALIZING
                    )
                }
            }
        }

        // Live Extracted Thumbnails Strip (if available)
        if (progress.currentThumbnails.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "RECENT EXTRACTED FRAMES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(progress.currentThumbnails) { file ->
                            Box(
                                modifier = Modifier
                                    .size(width = 84.dp, height = 56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, NavySurfaceCardBorder, RoundedCornerShape(8.dp))
                                    .background(NavySurfaceElevated)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(file)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Extracted Frame",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error message if any
        if (progress.error != null) {
            item {
                Surface(
                    color = RoseError.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoseError.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = RoseError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = progress.error,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                        )
                    }
                }
            }
        }

        // Cancel Button
        item {
            OutlinedButton(
                onClick = { viewModel.cancelProcessing() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("cancel_processing_button"),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NavySurfaceCardBorder)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cancel Processing",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        )
    }
}

@Composable
private fun PipelineStepItem(
    title: String,
    isCompleted: Boolean,
    isActive: Boolean,
    detail: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCompleted -> EmeraldGreen
                        isActive -> NeonCyan
                        else -> NavySurfaceElevated
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isCompleted -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                isActive -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(TextMuted)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isActive || isCompleted) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isCompleted -> EmeraldGreen
                    isActive -> NeonCyan
                    else -> TextMuted
                }
            ),
            modifier = Modifier.weight(1f)
        )

        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) NeonCyan else TextMuted
                )
            )
        }
    }
}
