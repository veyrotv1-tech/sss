package com.example

import android.net.Uri
import com.example.video.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleUnitTest {

    @Test
    fun test20MinuteVideoCalculation() {
        val uri = Uri.EMPTY
        // 20 minutes = 1200 seconds = 1,200,000 ms
        val video = VideoInfo(
            uri = uri,
            name = "test_20min.mp4",
            durationMs = 1_200_000L,
            width = 1920,
            height = 1080,
            rotation = 0,
            fileSizeBytes = 50_000_000L,
            hasAudio = true
        )

        assertEquals(1200L, video.durationSeconds)
        // Formula: 1200 / 3 = 400 screenshots
        assertEquals(400, video.estimatedScreenshots)
        assertEquals(0L, video.remainderSeconds)
        assertEquals("20:00", video.formattedDuration())
    }

    @Test
    fun testEdgeCaseNonDivisibleDuration() {
        val uri = Uri.EMPTY
        // 20 minutes + 1 second = 1201 seconds
        val video = VideoInfo(
            uri = uri,
            name = "test_20min_1sec.mp4",
            durationMs = 1_201_000L,
            width = 1920,
            height = 1080,
            rotation = 0,
            fileSizeBytes = 50_000_000L,
            hasAudio = true
        )

        assertEquals(1201L, video.durationSeconds)
        // 1201 / 3 = 400.33 -> 401 screenshots
        assertEquals(401, video.estimatedScreenshots)
        assertEquals(1L, video.remainderSeconds)
        assertEquals("20:01", video.formattedDuration())
    }

    @Test
    fun testShortVideoCalculations() {
        val uri = Uri.EMPTY
        // 15 seconds
        val video = VideoInfo(
            uri = uri,
            name = "demo_15s.mp4",
            durationMs = 15_000L,
            width = 1280,
            height = 720,
            rotation = 0,
            fileSizeBytes = 2_000_000L,
            hasAudio = true
        )

        assertEquals(15L, video.durationSeconds)
        assertEquals(5, video.estimatedScreenshots) // 15 / 3 = 5 screenshots
        assertEquals(0L, video.remainderSeconds)
        assertEquals("00:15", video.formattedDuration())
    }
}
