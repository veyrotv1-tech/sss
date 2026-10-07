package com.example.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import java.nio.ByteBuffer
import kotlin.math.max

object AudioTrackCopier {
    private const val TAG = "AudioTrackCopier"
    private const val BUFFER_SIZE = 512 * 1024

    data class ExtractedAudioInfo(
        val trackIndex: Int,
        val format: MediaFormat,
        val durationUs: Long,
        val mime: String
    )

    /**
     * Inspects the audio track in the source video.
     */
    fun findAudioTrack(context: Context, videoUri: Uri): ExtractedAudioInfo? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, videoUri, null)
            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        format.getLong(MediaFormat.KEY_DURATION)
                    } else 0L
                    return ExtractedAudioInfo(i, format, durationUs, mime)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding audio track", e)
            null
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /**
     * Copies all audio samples from the source video directly into the muxer.
     * This preserves the exact original audio stream, codec, timestamps, and quality.
     * Guarantees monotonic, non-negative presentation timestamps to prevent MediaMuxer crashes.
     */
    fun copyAudioSamples(
        context: Context,
        videoUri: Uri,
        audioTrackIndex: Int,
        muxer: MediaMuxer,
        muxerAudioTrackIndex: Int,
        maxDurationUs: Long? = null,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, videoUri, null)
            extractor.selectTrack(audioTrackIndex)

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val bufferInfo = MediaCodec.BufferInfo()

            val format = extractor.getTrackFormat(audioTrackIndex)
            val totalDurationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else (maxDurationUs ?: 1_000_000L)

            var sampleCount = 0
            var lastAudioPts = -1L

            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    break
                }

                val rawSampleTime = extractor.sampleTime
                if (maxDurationUs != null && rawSampleTime > maxDurationUs) {
                    break
                }

                // Guarantee strictly non-negative and monotonically increasing PTS
                val safePts = if (rawSampleTime < 0) {
                    max(0L, lastAudioPts + 1000L)
                } else if (rawSampleTime <= lastAudioPts) {
                    lastAudioPts + 1000L
                } else {
                    rawSampleTime
                }
                lastAudioPts = safePts

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = safePts
                bufferInfo.flags = extractor.sampleFlags

                try {
                    muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)
                    sampleCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed writing audio sample at $safePts: ${e.message}")
                }

                if (sampleCount % 50 == 0 && totalDurationUs > 0) {
                    val progress = (safePts.toFloat() / totalDurationUs).coerceIn(0f, 1f)
                    onProgress?.invoke(progress)
                }

                extractor.advance()
            }
            Log.d(TAG, "Successfully copied $sampleCount audio samples to muxer")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying audio samples", e)
            false
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}
