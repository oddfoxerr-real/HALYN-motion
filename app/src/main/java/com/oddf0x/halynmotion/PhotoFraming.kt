package com.oddf0x.halynmotion

import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Camera-style framing for an Android live wallpaper.
 *
 * Some launchers request a wallpaper surface wider than the actual display so
 * they can scroll home-screen pages. Scaling a photo to that entire surface
 * severely zooms the image seen in a single viewport. Always fit to the
 * physical DISPLAY viewport, never the off-screen wallpaper surface.
 *
 * Every layer (background, clock, foreground) shares this viewport.
 */
object PhotoFraming {
    const val FIT = 0
    const val FILL = 1
    const val AUTO = 2

    data class Frame(val source: Rect, val destination: RectF)

    fun viewport(
        surfaceWidth: Int, surfaceHeight: Int, visibleDisplayWidth: Int, offsetX: Float
    ): RectF {
        val sw = surfaceWidth.coerceAtLeast(1).toFloat()
        val sh = surfaceHeight.coerceAtLeast(1).toFloat()
        val vw = visibleDisplayWidth.coerceIn(1, surfaceWidth.coerceAtLeast(1)).toFloat()
        val extra = (sw - vw).coerceAtLeast(0f)
        val page = if (offsetX.isFinite()) offsetX.coerceIn(0f, 1f) else .5f
        val left = extra * page
        return RectF(left, 0f, left + vw, sh)
    }

    fun calculate(
        imageWidth: Int, imageHeight: Int, viewport: RectF,
        mode: Int = FIT, tiltX: Float = 0f, tiltY: Float = 0f
    ): Frame {
        val bw = imageWidth.coerceAtLeast(1).toFloat()
        val bh = imageHeight.coerceAtLeast(1).toFloat()
        val w = viewport.width().coerceAtLeast(1f)
        val h = viewport.height().coerceAtLeast(1f)
        val pictureAspect = bw / bh
        val displayAspect = w / h
        // Auto preserves the full composition when the aspect difference
        // exceeds 6%; otherwise it fills without perceptible cropping.
        val fit = mode == FIT || (mode == AUTO &&
            abs(pictureAspect / displayAspect - 1f) > .06f)

        if (fit) {
            val scale = min(w / bw, h / bh)
            val dw = bw * scale
            val dh = bh * scale
            val cx = viewport.centerX()
            val cy = viewport.centerY()
            return Frame(
                Rect(0, 0, imageWidth.coerceAtLeast(1), imageHeight.coerceAtLeast(1)),
                RectF(cx - dw * .5f, cy - dh * .5f, cx + dw * .5f, cy + dh * .5f)
            )
        }

        val scale = max(w / bw, h / bh)
        val sourceWidth = min(bw, w / scale).coerceAtLeast(1f)
        val sourceHeight = min(bh, h / scale).coerceAtLeast(1f)
        val spareX = (bw - sourceWidth).coerceAtLeast(0f)
        val spareY = (bh - sourceHeight).coerceAtLeast(0f)
        val panX = if (tiltX.isFinite()) tiltX.coerceIn(-1f, 1f) else 0f
        val panY = if (tiltY.isFinite()) tiltY.coerceIn(-1f, 1f) else 0f
        val left = (spareX * (.5f + panX * .32f)).coerceIn(0f, spareX)
        val top = (spareY * (.5f + panY * .32f)).coerceIn(0f, spareY)
        return Frame(
            Rect(
                left.roundToInt(), top.roundToInt(),
                (left + sourceWidth).roundToInt().coerceAtMost(imageWidth.coerceAtLeast(1)),
                (top + sourceHeight).roundToInt().coerceAtMost(imageHeight.coerceAtLeast(1))
            ),
            RectF(viewport)
        )
    }
}
