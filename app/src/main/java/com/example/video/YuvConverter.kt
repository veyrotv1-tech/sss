package com.example.video

import android.graphics.Bitmap

object YuvConverter {

    /**
     * Converts an ARGB Bitmap into NV12 (YUV420SemiPlanar: Y plane, then UV interleaved).
     * Output size is width * height * 3 / 2.
     */
    fun bitmapToNv12(bitmap: Bitmap, width: Int, height: Int, outNv12: ByteArray) {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize

        var argbIndex = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val color = argb[argbIndex++]
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff

                // RGB to YUV formula
                var y = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
                var u = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
                var v = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128

                y = y.coerceIn(16, 235)
                u = u.coerceIn(16, 240)
                v = v.coerceIn(16, 240)

                outNv12[yIndex++] = y.toByte()

                // Subsample U and V 2x2 for NV12 (interleaved UV)
                if (j % 2 == 0 && i % 2 == 0) {
                    outNv12[uvIndex++] = u.toByte()
                    outNv12[uvIndex++] = v.toByte()
                }
            }
        }
    }

    /**
     * Converts an ARGB Bitmap into I420 (YUV420Planar: Y plane, then U plane, then V plane).
     */
    fun bitmapToI420(bitmap: Bitmap, width: Int, height: Int, outI420: ByteArray) {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val frameSize = width * height
        val qFrameSize = frameSize / 4

        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + qFrameSize

        var argbIndex = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val color = argb[argbIndex++]
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff

                var y = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
                var u = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
                var v = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128

                y = y.coerceIn(16, 235)
                u = u.coerceIn(16, 240)
                v = v.coerceIn(16, 240)

                outI420[yIndex++] = y.toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    outI420[uIndex++] = u.toByte()
                    outI420[vIndex++] = v.toByte()
                }
            }
        }
    }
}
