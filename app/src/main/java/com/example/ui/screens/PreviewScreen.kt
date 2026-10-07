package com.example.ui.screens

import android.widget.VideoView
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.SavedProject
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.components.AudioPreservedBadge
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.components.GradientActionButton
import com.example.ui.components.Video2PhotoTopBar
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurfaceCard
import com.example.ui.theme.NavySurfaceCardBorder
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VibrantPurple
import com.example.video.SlideshowResult
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun PreviewScreen(
    viewModel: MainViewModel,
    slideshowResult: SlideshowResult?,
    previewProject: SavedProject?,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateTo(AppScreen.HOME)
    }

    val context = LocalContext.current
    val videoFile = remember(slideshowResult, previewProject) {
        slideshowResult?.outputFile ?: previewProject?.let { File(it.videoPath) }
    }

    val thumbnailFiles = remember(slideshowResult, previewProject) {
        slideshowResult?.screenshots ?: emptyList()
    }

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember {
        mutableIntStateOf(
            slideshowResult?.durationMs?.toInt() ?: ((previewProject?.durationSeconds?.toInt() ?: 10) * 1000)
        )
    }

    // Polling player timeline
    LaunchedEffect(videoViewRef, isPlaying) {
        while (true) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    currentPositionMs = vv.currentPosition
                    if (vv.duration > 0) durationMs = vv.duration
                    isPlaying = true
                } else if (isPlaying) {
                    isPlaying = false
                }
            }
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoViewRef?.stopPlayback()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
    ) {
        Video2PhotoTopBar(
            title = "Final Video Preview",
            subtitle = "Screenshots Slideshow • Synchronized Audio",
            onBackClick = { viewModel.navigateTo(AppScreen.HOME) },
            actions = {
                IconButton(
                    onClick = { viewModel.shareCurrentVideo() },
                    modifier = Modifier.testTag("preview_top_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = NeonCyan
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Video Player Container
            item {
                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, NavySurfaceCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (videoFile != null && videoFile.exists()) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        setVideoPath(videoFile.absolutePath)
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                            durationMs = mp.duration
                                            start()
                                            isPlaying = true
                                        }
                                        setOnCompletionListener {
                                            isPlaying = false
                                        }
                                        videoViewRef = this
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        videoViewRef?.let { vv ->
                                            if (vv.isPlaying) {
                                                vv.pause()
                                                isPlaying = false
                                            } else {
                                                vv.start()
                                                isPlaying = true
                                            }
                                        }
                                    }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Video file not found", color = TextSecondary)
                            }
                        }

                        // Play/Pause center overlay button
                        if (!isPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = {
                                        videoViewRef?.start()
                                        isPlaying = true
                                    },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(AccentGradient)
                                        .shadow(16.dp, CircleShape, spotColor = NeonCyan)
                                        .testTag("preview_play_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Timeline Scrubbing & Playback Controls Card
            item {
                Surface(
                    color = NavySurfaceCard,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NavySurfaceCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        // Slider
                        val maxDuration = if (durationMs > 0) durationMs.toFloat() else 1000f
                        val currentPos = currentPositionMs.toFloat().coerceIn(0f, maxDuration)

                        Slider(
                            value = currentPos,
                            onValueChange = { newPos ->
                                currentPositionMs = newPos.toInt()
                                videoViewRef?.seekTo(newPos.toInt())
                            },
                            valueRange = 0f..maxDuration,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = ElectricIndigo,
                                inactiveTrackColor = NavySurfaceCardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("preview_timeline_slider")
                        )

                        // Timestamps row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatMs(currentPositionMs),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = formatMs(durationMs),
                                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // -5s
                            IconButton(
                                onClick = {
                                    val newPos = (currentPositionMs - 5000).coerceAtLeast(0)
                                    videoViewRef?.seekTo(newPos)
                                    currentPositionMs = newPos
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastRewind,
                                    contentDescription = "-5s",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Play / Pause
                            IconButton(
                                onClick = {
                                    videoViewRef?.let { vv ->
                                        if (vv.isPlaying) {
                                            vv.pause()
                                            isPlaying = false
                                        } else {
                                            vv.start()
                                            isPlaying = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(AccentGradient)
                                    .testTag("preview_toggle_play_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // +5s
                            IconButton(
                                onClick = {
                                    val newPos = (currentPositionMs + 5000).coerceAtMost(durationMs)
                                    videoViewRef?.seekTo(newPos)
                                    currentPositionMs = newPos
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = "+5s",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Audio Waveform & Status Banner
            item {
                Surface(
                    color = NavySurfaceCard,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AudioPreservedBadge()
                            Text(
                                text = if (isPlaying) "Playing Audio" else "Audio Ready",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isPlaying) NeonCyan else TextMuted,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        AudioWaveformVisualizer(
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        )
                    }
                }
            }

            // Screenshot Thumbnails Strip (Every 3 seconds)
            if (thumbnailFiles.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SCREENSHOTS (EVERY 3 SECONDS)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = "${thumbnailFiles.size} frames",
                                style = MaterialTheme.typography.labelSmall.copy(color = NeonCyan)
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(thumbnailFiles) { index, file ->
                                val timestampSec = index * 3
                                val isSelected = currentPositionMs / 3000 == index

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        val seekMs = timestampSec * 1000
                                        videoViewRef?.seekTo(seekMs)
                                        currentPositionMs = seekMs
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 88.dp, height = 58.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) NeonCyan else NavySurfaceCardBorder,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .background(NavySurfaceElevated)
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(file)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Frame #$index",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatSeconds(timestampSec),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isSelected) NeonCyan else TextMuted,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action Buttons: Save to Gallery, Share, Create Again
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Save to Gallery
                    GradientActionButton(
                        text = "Save to Gallery",
                        icon = Icons.Default.Download,
                        testTag = "save_to_gallery_button",
                        onClick = { viewModel.saveCurrentVideoToGallery() }
                    )

                    // Share Video
                    OutlinedButton(
                        onClick = { viewModel.shareCurrentVideo() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("share_video_button"),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, VibrantPurple)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = VibrantPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Share Video",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Create Again
                    OutlinedButton(
                        onClick = { viewModel.navigateTo(AppScreen.HOME) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("create_again_button"),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NavySurfaceCardBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create Again",
                            color = TextSecondary,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

private fun formatSeconds(sec: Int): String {
    val min = sec / 60
    val s = sec % 60
    return String.format("%02d:%02d", min, s)
}
