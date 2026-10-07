package com.example.video

import android.net.Uri
import java.io.File

data class VideoInfo(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val fileSizeBytes: Long,
    val hasAudio: Boolean,
    val audioFormatDetails: String = ""
) {
    val durationSeconds: Long get() = durationMs / 1000L

    /**
     * Number of screenshots = Video duration in seconds ÷ 3
     * Handles ceiling for videos with remainder seconds.
     */
    val estimatedScreenshots: Int
        get() {
            val sec = durationSeconds
            return if (sec <= 0) 1 else kotlin.math.max(1, kotlin.math.ceil(sec / 3.0).toInt())
        }

    val remainderSeconds: Long
        get() = durationSeconds % 3L

    fun formattedDuration(): String {
        val totalSec = durationSeconds
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }
}

enum class OutputQuality(val label: String, val width: Int, val height: Int, val bitrate: Int) {
    QUALITY_720P("720p HD", 1280, 720, 2_500_000),
    QUALITY_1080P("1080p FHD", 1920, 1080, 5_000_000),
    QUALITY_4K("4K UHD", 3840, 2160, 12_000_000)
}

enum class ProcessingStage(val displayText: String) {
    IDLE("Ready"),
    ANALYZING("Analyzing video..."),
    CALCULATING("Calculating screenshots..."),
    EXTRACTING_FRAMES("Extracting frames..."),
    CREATING_VIDEO("Creating photo video..."),
    ADDING_AUDIO("Adding original audio..."),
    FINALIZING("Finalizing video..."),
    COMPLETED("Completed"),
    ERROR("Error")
}

data class ProcessingProgress(
    val stage: ProcessingStage = ProcessingStage.IDLE,
    val overallPercentage: Int = 0,
    val totalScreenshots: Int = 0,
    val extractedScreenshots: Int = 0,
    val videoCreationPercentage: Int = 0,
    val audioPreserved: Boolean = true,
    val currentThumbnails: List<File> = emptyList(),
    val statusMessage: String = "",
    val error: String? = null
)

data class SlideshowResult(
    val outputFile: File,
    val durationMs: Long,
    val screenshotCount: Int,
    val thumbnailFile: File,
    val screenshots: List<File>,
    val fileSizeBytes: Long
)
