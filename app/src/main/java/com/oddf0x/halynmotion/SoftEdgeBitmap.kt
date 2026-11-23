package com.oddf0x.halynmotion

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Small, pre-blurred copy used strictly behind letterboxed wallpapers.
 * No RenderScript, software blur of a full 4K canvas, or per-frame allocations.
 * Produces a quiet extension of the original artwork rather than black bars.
 */
object SoftEdgeBitmap {
    fun create(source: Bitmap): Bitmap {
        require(!source.isRecycled)
        val longest = max(source.width, source.height).coerceAtLeast(1)
        val scale = 144f / longest
        val width = (source.width * scale).roundToInt().coerceAtLeast(8)
        val height = (source.height * scale).roundToInt().coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(source, width, height, true)
        val total = width * height
        var pixels = IntArray(total)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        val tmp = IntArray(total)
        repeat(3) {
            blurHorizontal(pixels, tmp, width, height, 9)
            blurVertical(tmp, pixels, width, height, 9)
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== source && !small.isRecycled) small.recycle()
        return out
    }

    private fun blurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        for (y in 0 until h) {
            val start = y * w
            for (x in 0 until w) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dx in -radius..radius) {
                    val c = src[start + (x + dx).coerceIn(0, w - 1)]
                    r += c ushr 16 and 255
                    g += c ushr 8 and 255
                    b += c and 255
                    count++
                }
                dst[start + x] = -0x1000000 or
                    ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
    }

    private fun blurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dy in -radius..radius) {
                    val c = src[(y + dy).coerceIn(0, h - 1) * w + x]
                    r += c ushr 16 and 255
                    g += c ushr 8 and 255
                    b += c and 255
                    count++
                }
                dst[y * w + x] = -0x1000000 or
                    ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
    }
}
