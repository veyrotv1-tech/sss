package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object VideoSlideshowCreator {
    private const val TAG = "VideoSlideshowCreator"
    private const val INTERVAL_SECONDS = 3L
    private const val INTERVAL_MS = INTERVAL_SECONDS * 1000L

    /**
     * Inspects a video Uri and returns its duration, dimensions, and audio details.
     */
    suspend fun inspectVideo(context: Context, videoUri: Uri): VideoInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val hasAudio = hasAudioStr?.equals("yes", ignoreCase = true) == true || AudioTrackCopier.findAudioTrack(context, videoUri) != null

            var name = "video_${System.currentTimeMillis()}"
            try {
                context.contentResolver.query(videoUri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIdx)
                    }
                }
            } catch (_: Exception) {}

            var fileSizeBytes = 0L
            try {
                context.contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                    fileSizeBytes = pfd.statSize
                }
            } catch (_: Exception) {}

            VideoInfo(
                uri = videoUri,
                name = name,
                durationMs = durationMs,
                width = width,
                height = height,
                rotation = rotation,
                fileSizeBytes = fileSizeBytes,
                hasAudio = hasAudio,
                audioFormatDetails = if (hasAudio) "Original Audio Track (Preserved)" else "No Audio"
            )
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Full conversion pipeline:
     * 1. Analyze
     * 2. Calculate screenshots (duration ÷ 3)
     * 3. Extract frames at 3s intervals
     * 4. Encode photo video with each frame lasting 3s
     * 5. Copy original audio track losslessly
     * 6. Clean up temporary extraction frames to preserve disk space
     */
    suspend fun createVideoFromScreenshots(
        context: Context,
        videoInfo: VideoInfo,
        quality: OutputQuality,
        onProgress: (ProcessingProgress) -> Unit
    ): SlideshowResult = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "v2p_proc_${System.currentTimeMillis()}").apply { mkdirs() }
        val framesDir = File(workDir, "frames").apply { mkdirs() }
        val previewThumbsDir = File(workDir, "thumbs").apply { mkdirs() }
        val encodingFrameFiles = mutableListOf<File>()
        val previewThumbnailFiles = mutableListOf<File>()

        try {
            // STEP 1: Analyzing video
            onProgress(
                ProcessingProgress(
                    stage = ProcessingStage.ANALYZING,
                    overallPercentage = 5,
                    statusMessage = "Analyzing video stream and audio tracks..."
                )
            )
            coroutineContext.ensureActive()

            // STEP 2: Calculating screenshots
            val totalDurationMs = max(1000L, videoInfo.durationMs)
            val durationSec = totalDurationMs / 1000L
            // Formula: Number of screenshots = Video duration in seconds ÷ 3
            val screenshotCount = max(1, ceil(durationSec / 3.0).toInt())
            val remainderSec = durationSec % INTERVAL_SECONDS

            onProgress(
                ProcessingProgress(
                    stage = ProcessingStage.CALCULATING,
                    overallPercentage = 10,
                    totalScreenshots = screenshotCount,
                    extractedScreenshots = 0,
                    statusMessage = "Calculated $screenshotCount screenshots ($INTERVAL_SECONDS-sec interval)"
                )
            )
            coroutineContext.ensureActive()

            // Determine output resolution maintaining portrait/landscape aspect ratio
            val isPortrait = if (videoInfo.rotation == 90 || videoInfo.rotation == 270) {
                videoInfo.width > videoInfo.height
            } else {
                videoInfo.height > videoInfo.width
            }

            val targetWidth = if (isPortrait) min(quality.width, quality.height) else max(quality.width, quality.height)
            val targetHeight = if (isPortrait) max(quality.width, quality.height) else min(quality.width, quality.height)
            // Ensure dimensions are multiples of 16 for H.264 encoder compatibility
            val encWidth = targetWidth - (targetWidth % 16)
            val encHeight = targetHeight - (targetHeight % 16)

            // STEP 3: Extract frames every 3 seconds
            var retriever: MediaMetadataRetriever? = MediaMetadataRetriever().apply {
                setDataSource(context, videoInfo.uri)
            }

            val rotationMatrix = if (videoInfo.rotation != 0) {
                Matrix().apply { postRotate(videoInfo.rotation.toFloat()) }
            } else null

            for (i in 0 until screenshotCount) {
                coroutineContext.ensureActive()

                // Periodic retriever recreation for very long videos to prevent native memory bloat
                if (i > 0 && i % 60 == 0) {
                    try { retriever?.release() } catch (_: Exception) {}
                    retriever = MediaMetadataRetriever().apply {
                        setDataSource(context, videoInfo.uri)
                    }
                }

                val targetTimestampUs = min(i * INTERVAL_SECONDS * 1_000_000L, max(0L, (totalDurationMs - 100L) * 1000L))

                var rawBitmap = try {
                    retriever?.getFrameAtTime(targetTimestampUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (_: Exception) { null }

                if (rawBitmap == null) {
                    rawBitmap = try {
                        retriever?.getFrameAtTime(targetTimestampUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    } catch (_: Exception) { null }
                }
                if (rawBitmap == null) {
                    rawBitmap = Bitmap.createBitmap(encWidth, encHeight, Bitmap.Config.ARGB_8888)
                }

                // Rotate if required
                val orientedBitmap = if (rotationMatrix != null && rawBitmap.width > 0) {
                    Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, rotationMatrix, true)
                } else rawBitmap

                // Scale to fit canvas with centered letterbox/pillarbox
                val scaledBitmap = fitAndCenterBitmap(orientedBitmap, encWidth, encHeight)
                if (orientedBitmap != rawBitmap) orientedBitmap.recycle()
                rawBitmap.recycle()

                // Save full-res encoding frame
                val frameFile = File(framesDir, String.format(Locale.US, "frame_%04d.jpg", i))
                FileOutputStream(frameFile).use { out ->
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                encodingFrameFiles.add(frameFile)

                // Save lightweight mini preview thumbnail (160x90, ~4KB) for smooth timeline navigation
                val thumbFile = File(previewThumbsDir, String.format(Locale.US, "thumb_%04d.jpg", i))
                val miniThumb = Bitmap.createScaledBitmap(scaledBitmap, 160, (160 * encHeight / encWidth).coerceAtLeast(90), true)
                FileOutputStream(thumbFile).use { out ->
                    miniThumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                miniThumb.recycle()
                previewThumbnailFiles.add(thumbFile)

                scaledBitmap.recycle()

                // Update real-time progress
                val extractPct = 10 + ((i + 1).toFloat() / screenshotCount * 40).toInt()
                onProgress(
                    ProcessingProgress(
                        stage = ProcessingStage.EXTRACTING_FRAMES,
                        overallPercentage = extractPct,
                        totalScreenshots = screenshotCount,
                        extractedScreenshots = i + 1,
                        currentThumbnails = previewThumbnailFiles.takeLast(6),
                        statusMessage = "Extracting frame ${i + 1} of $screenshotCount..."
                    )
                )
            }
            try { retriever?.release() } catch (_: Exception) {}

            // Save persistent thumbnail for project list
            val projectThumbFile = File(context.filesDir, "thumb_${System.currentTimeMillis()}.jpg")
            if (previewThumbnailFiles.isNotEmpty()) {
                previewThumbnailFiles.first().copyTo(projectThumbFile, overwrite = true)
            }

            // STEP 4 & 5: Encode Photo Video & Mux Original Audio
            onProgress(
                ProcessingProgress(
                    stage = ProcessingStage.CREATING_VIDEO,
                    overallPercentage = 55,
                    totalScreenshots = screenshotCount,
                    extractedScreenshots = screenshotCount,
                    videoCreationPercentage = 10,
                    statusMessage = "Creating photo video (3s per screenshot)..."
                )
            )

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outputVideoFile = File(context.filesDir, "Video2Photo_$timeStamp.mp4")

            // Encode video and mux audio
            encodeSlideshowVideo(
                context = context,
                videoInfo = videoInfo,
                frames = encodingFrameFiles,
                width = encWidth,
                height = encHeight,
                quality = quality,
                totalDurationMs = totalDurationMs,
                remainderSec = remainderSec,
                outputFile = outputVideoFile,
                onProgress = { videoProg, stage ->
                    val overall = 55 + (videoProg * 0.40f).toInt()
                    onProgress(
                        ProcessingProgress(
                            stage = stage,
                            overallPercentage = overall,
                            totalScreenshots = screenshotCount,
                            extractedScreenshots = screenshotCount,
                            videoCreationPercentage = (videoProg * 100).toInt(),
                            statusMessage = if (stage == ProcessingStage.ADDING_AUDIO)
                                "Adding original audio track..." else "Encoding video frames (${(videoProg * 100).toInt()}%)..."
                        )
                    )
                }
            )

            // STEP 6: Finalizing
            onProgress(
                ProcessingProgress(
                    stage = ProcessingStage.FINALIZING,
                    overallPercentage = 98,
                    totalScreenshots = screenshotCount,
                    extractedScreenshots = screenshotCount,
                    videoCreationPercentage = 100,
                    statusMessage = "Finalizing video file..."
                )
            )

            val finalResult = SlideshowResult(
                outputFile = outputVideoFile,
                durationMs = totalDurationMs,
                screenshotCount = screenshotCount,
                thumbnailFile = projectThumbFile,
                screenshots = previewThumbnailFiles.toList(),
                fileSizeBytes = outputVideoFile.length()
            )

            onProgress(
                ProcessingProgress(
                    stage = ProcessingStage.COMPLETED,
                    overallPercentage = 100,
                    totalScreenshots = screenshotCount,
                    extractedScreenshots = screenshotCount,
                    videoCreationPercentage = 100,
                    statusMessage = "Video created successfully!"
                )
            )

            finalResult
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error in createVideoFromScreenshots", e)
            onProgress(
                ProcessingProgress(
                    stage = ProcessingStage.ERROR,
                    error = e.localizedMessage ?: "Processing failed"
                )
            )
            throw e
        } finally {
            // Clean up heavy full-size encoding frame files to save user storage!
            try {
                framesDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }

    /**
     * Fits source bitmap into target dimensions with background letterboxing.
     */
    private fun fitAndCenterBitmap(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(android.graphics.Color.BLACK)

        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()
        val scale = min(targetW / srcW, targetH / srcH)

        val destW = srcW * scale
        val destH = srcH * scale
        val left = (targetW - destW) / 2f
        val top = (targetH - destH) / 2f

        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(left, top)
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(src, matrix, paint)
        return output
    }

    /**
     * Encodes the sequence of images with each lasting 3 seconds (and last frame handling remainder),
     * and copies the original audio track into the container.
     */
    private suspend fun encodeSlideshowVideo(
        context: Context,
        videoInfo: VideoInfo,
        frames: List<File>,
        width: Int,
        height: Int,
        quality: OutputQuality,
        totalDurationMs: Long,
        remainderSec: Long,
        outputFile: File,
        onProgress: (Float, ProcessingStage) -> Unit
    ) = withContext(Dispatchers.IO) {
        val frameRate = 10 // 10 fps is smooth for slideshow and ultra-fast to encode
        val frameDurationUs = (1_000_000L / frameRate)

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            setInteger(MediaFormat.KEY_BIT_RATE, quality.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false

        // Check audio track from source
        val audioInfo = if (videoInfo.hasAudio) {
            AudioTrackCopier.findAudioTrack(context, videoInfo.uri)
        } else null

        val nv12Buffer = ByteArray(width * height * 3 / 2)
        val vBufferInfo = MediaCodec.BufferInfo()

        var currentPtsUs = 0L
        val totalScreenshots = frames.size

        try {
            for (i in 0 until totalScreenshots) {
                coroutineContext.ensureActive()
                val frameFile = frames[i]
                val bitmap = BitmapFactory.decodeFile(frameFile.absolutePath) ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                YuvConverter.bitmapToNv12(bitmap, width, height, nv12Buffer)
                bitmap.recycle()

                // Calculate duration of this screenshot:
                // Normal screenshot = 3 seconds (30 frames at 10 fps)
                // If it's the last screenshot and total duration has a remainder:
                val isLast = (i == totalScreenshots - 1)
                val durationSec = if (isLast && remainderSec > 0) {
                    // Handled remaining duration automatically to preserve exact timeline!
                    remainderSec
                } else {
                    INTERVAL_SECONDS
                }

                val framesForThisScreenshot = (durationSec * frameRate).toInt().coerceAtLeast(1)

                for (f in 0 until framesForThisScreenshot) {
                    coroutineContext.ensureActive()
                    val isEos = isLast && (f == framesForThisScreenshot - 1)

                    var queued = false
                    while (!queued) {
                        val inputIdx = encoder.dequeueInputBuffer(10_000)
                        if (inputIdx >= 0) {
                            val inBuf = encoder.getInputBuffer(inputIdx)!!
                            inBuf.clear()
                            inBuf.put(nv12Buffer)
                            val flags = if (isEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                            encoder.queueInputBuffer(inputIdx, 0, nv12Buffer.size, currentPtsUs, flags)
                            currentPtsUs += frameDurationUs
                            queued = true
                        }

                        // Drain encoder
                        var outIdx = encoder.dequeueOutputBuffer(vBufferInfo, 5_000)
                        while (outIdx >= 0 || outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                val encOutFormat = encoder.outputFormat
                                videoTrackIndex = muxer.addTrack(encOutFormat)
                                if (audioInfo != null) {
                                    try {
                                        audioTrackIndex = muxer.addTrack(audioInfo.format)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Audio track could not be added to muxer: ${e.message}")
                                        audioTrackIndex = -1
                                    }
                                }
                                muxer.start()
                                muxerStarted = true
                            } else if (outIdx >= 0) {
                                if (muxerStarted && (vBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && vBufferInfo.size > 0) {
                                    val outBuf = encoder.getOutputBuffer(outIdx)!!
                                    outBuf.position(vBufferInfo.offset)
                                    outBuf.limit(vBufferInfo.offset + vBufferInfo.size)
                                    muxer.writeSampleData(videoTrackIndex, outBuf, vBufferInfo)
                                }
                                encoder.releaseOutputBuffer(outIdx, false)
                            }
                            outIdx = encoder.dequeueOutputBuffer(vBufferInfo, 0)
                        }
                    }
                }

                val progressFrac = (i + 1).toFloat() / totalScreenshots * 0.7f
                onProgress(progressFrac, ProcessingStage.CREATING_VIDEO)
            }

            // Drain remaining encoder output until EOS
            var eosReached = false
            while (!eosReached) {
                val outIdx = encoder.dequeueOutputBuffer(vBufferInfo, 10_000)
                if (outIdx >= 0) {
                    if (muxerStarted && (vBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && vBufferInfo.size > 0) {
                        val outBuf = encoder.getOutputBuffer(outIdx)!!
                        outBuf.position(vBufferInfo.offset)
                        outBuf.limit(vBufferInfo.offset + vBufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, outBuf, vBufferInfo)
                    }
                    if ((vBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        eosReached = true
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    eosReached = true
                }
            }

            // STEP 5: Copy original audio track losslessly if available
            if (audioInfo != null && audioTrackIndex >= 0 && muxerStarted) {
                onProgress(0.75f, ProcessingStage.ADDING_AUDIO)
                AudioTrackCopier.copyAudioSamples(
                    context = context,
                    videoUri = videoInfo.uri,
                    audioTrackIndex = audioInfo.trackIndex,
                    muxer = muxer,
                    muxerAudioTrackIndex = audioTrackIndex,
                    maxDurationUs = totalDurationMs * 1000L,
                    onProgress = { audioProg ->
                        onProgress(0.75f + audioProg * 0.23f, ProcessingStage.ADDING_AUDIO)
                    }
                )
            }
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            if (muxerStarted) {
                try { muxer.stop() } catch (_: Exception) {}
            }
            try { muxer.release() } catch (_: Exception) {}
        }
    }
}
