package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object SampleVideoHelper {
    private const val TAG = "SampleVideoHelper"

    data class PendingPacket(
        val isVideo: Boolean,
        val data: ByteArray,
        val info: MediaCodec.BufferInfo
    )

    /**
     * Creates a 15-second demo MP4 video with countdown graphics and a synchronized audio melody.
     * Useful for immediate testing on devices or emulators that don't have video files in storage.
     */
    fun createSampleVideo(context: Context): Uri? {
        val sampleFile = File(context.cacheDir, "sample_video2photo_demo.mp4")
        if (sampleFile.exists() && sampleFile.length() > 50_000) {
            return Uri.fromFile(sampleFile)
        }

        try {
            val width = 640
            val height = 360
            val frameRate = 15
            val durationSec = 15
            val totalFrames = durationSec * frameRate

            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                setInteger(MediaFormat.KEY_BIT_RATE, 1_200_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            videoEncoder.start()

            // Audio Encoder (AAC)
            val sampleRate = 44100
            val channelCount = 1
            val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            val muxer = MediaMuxer(sampleFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerStarted = false
            val pendingPackets = mutableListOf<PendingPacket>()

            // Draw frames
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
            val nv12 = ByteArray(width * height * 3 / 2)

            val colors = listOf(
                Color.parseColor("#4338CA"),
                Color.parseColor("#7C3AED"),
                Color.parseColor("#2563EB"),
                Color.parseColor("#059669"),
                Color.parseColor("#D97706")
            )

            val vBufferInfo = MediaCodec.BufferInfo()
            val aBufferInfo = MediaCodec.BufferInfo()

            var frameIdx = 0
            var audioSampleIndex = 0
            val totalAudioSamples = durationSec * sampleRate
            val audioChunkSize = 2048

            while (frameIdx < totalFrames || audioSampleIndex < totalAudioSamples) {
                // Feed video frame if needed
                if (frameIdx < totalFrames) {
                    val currentSecond = frameIdx / frameRate
                    val color = colors[currentSecond % colors.size]
                    canvas.drawColor(color)

                    paint.color = Color.WHITE
                    paint.textSize = 42f
                    canvas.drawText("Video2Photo Demo", width / 2f, height / 2f - 40f, paint)

                    paint.textSize = 28f
                    paint.color = Color.parseColor("#E0E7FF")
                    canvas.drawText("Timestamp: 00:${String.format("%02d", currentSecond)} / 00:15", width / 2f, height / 2f + 20f, paint)

                    paint.textSize = 22f
                    paint.color = Color.parseColor("#38BDF8")
                    canvas.drawText("Original Audio Track Synchronized", width / 2f, height / 2f + 65f, paint)

                    YuvConverter.bitmapToNv12(bitmap, width, height, nv12)

                    val inputBufIdx = videoEncoder.dequeueInputBuffer(5_000)
                    if (inputBufIdx >= 0) {
                        val inputBuf = videoEncoder.getInputBuffer(inputBufIdx)!!
                        inputBuf.clear()
                        inputBuf.put(nv12)
                        val ptsUs = (frameIdx * 1_000_000L) / frameRate
                        val flags = if (frameIdx == totalFrames - 1) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        videoEncoder.queueInputBuffer(inputBufIdx, 0, nv12.size, ptsUs, flags)
                        frameIdx++
                    }
                }

                // Feed audio samples in parallel
                if (audioSampleIndex < totalAudioSamples) {
                    val aInIdx = audioEncoder.dequeueInputBuffer(5_000)
                    if (aInIdx >= 0) {
                        val aInBuf = audioEncoder.getInputBuffer(aInIdx)!!
                        aInBuf.clear()
                        val samplesToFeed = audioChunkSize.coerceAtMost(totalAudioSamples - audioSampleIndex)
                        val pcm = ShortArray(samplesToFeed)

                        val freq = 320.0 + (audioSampleIndex / (sampleRate * 3)) * 140.0
                        for (s in 0 until samplesToFeed) {
                            val t = (audioSampleIndex + s).toDouble() / sampleRate
                            val sample = (sin(2.0 * Math.PI * freq * t) * 16000).toInt().toShort()
                            pcm[s] = sample
                        }

                        val byteBuffer = ByteBuffer.allocate(samplesToFeed * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (s in pcm) byteBuffer.putShort(s)
                        aInBuf.put(byteBuffer.array())

                        val aPtsUs = (audioSampleIndex * 1_000_000L) / sampleRate
                        val aFlags = if (audioSampleIndex + samplesToFeed >= totalAudioSamples) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        audioEncoder.queueInputBuffer(aInIdx, 0, samplesToFeed * 2, aPtsUs, aFlags)
                        audioSampleIndex += samplesToFeed
                    }
                }

                // Drain video encoder
                drainSampleEncoder(
                    encoder = videoEncoder,
                    muxer = muxer,
                    bufferInfo = vBufferInfo,
                    isVideo = true,
                    onFormatChanged = {
                        videoTrackIndex = muxer.addTrack(videoEncoder.outputFormat)
                        if (!muxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                            muxer.start()
                            muxerStarted = true
                            flushPendingPackets(muxer, pendingPackets, videoTrackIndex, audioTrackIndex)
                        }
                    },
                    pendingPackets = pendingPackets,
                    isMuxerStarted = { muxerStarted },
                    trackIndex = { videoTrackIndex }
                )

                // Drain audio encoder
                drainSampleEncoder(
                    encoder = audioEncoder,
                    muxer = muxer,
                    bufferInfo = aBufferInfo,
                    isVideo = false,
                    onFormatChanged = {
                        audioTrackIndex = muxer.addTrack(audioEncoder.outputFormat)
                        if (!muxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                            muxer.start()
                            muxerStarted = true
                            flushPendingPackets(muxer, pendingPackets, videoTrackIndex, audioTrackIndex)
                        }
                    },
                    pendingPackets = pendingPackets,
                    isMuxerStarted = { muxerStarted },
                    trackIndex = { audioTrackIndex }
                )
            }

            // Finish encoding
            try { videoEncoder.stop() } catch (_: Exception) {}
            try { videoEncoder.release() } catch (_: Exception) {}
            try { audioEncoder.stop() } catch (_: Exception) {}
            try { audioEncoder.release() } catch (_: Exception) {}

            if (muxerStarted) {
                try { muxer.stop() } catch (_: Exception) {}
            }
            try { muxer.release() } catch (_: Exception) {}

            return Uri.fromFile(sampleFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating sample video", e)
            return null
        }
    }

    private fun flushPendingPackets(
        muxer: MediaMuxer,
        pendingPackets: MutableList<PendingPacket>,
        videoTrackIndex: Int,
        audioTrackIndex: Int
    ) {
        for (packet in pendingPackets) {
            val track = if (packet.isVideo) videoTrackIndex else audioTrackIndex
            val buf = ByteBuffer.wrap(packet.data)
            muxer.writeSampleData(track, buf, packet.info)
        }
        pendingPackets.clear()
    }

    private fun drainSampleEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        isVideo: Boolean,
        onFormatChanged: () -> Unit,
        pendingPackets: MutableList<PendingPacket>,
        isMuxerStarted: () -> Boolean,
        trackIndex: () -> Int
    ) {
        var outIdx = encoder.dequeueOutputBuffer(bufferInfo, 2_000)
        while (outIdx >= 0 || outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                onFormatChanged()
            } else if (outIdx >= 0) {
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0) {
                    val encodedData = encoder.getOutputBuffer(outIdx)!!
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)

                    if (isMuxerStarted()) {
                        muxer.writeSampleData(trackIndex(), encodedData, bufferInfo)
                    } else {
                        // Buffer packet until muxer starts
                        val copyBytes = ByteArray(bufferInfo.size)
                        encodedData.get(copyBytes)
                        val copyInfo = MediaCodec.BufferInfo().apply {
                            set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                        }
                        pendingPackets.add(PendingPacket(isVideo, copyBytes, copyInfo))
                    }
                }
                encoder.releaseOutputBuffer(outIdx, false)
            }
            outIdx = encoder.dequeueOutputBuffer(bufferInfo, 0)
        }
    }
}
