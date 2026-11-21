package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin

/**
 * Eight calm, original, resolution-independent matte wallpapers.
 *
 * Artwork is painted directly at display resolution: no stretched PNGs, faux glass,
 * raster banding, texture noise, or lettering behind launcher widgets. The upper
 * half deliberately retains negative space for oversized home-screen clocks.
 * Animation is slow and bounded to avoid distracting motion.
 */
class CalmSlateRenderer {
    data class Scene(val name: String, val description: String)

    companion object {
        const val FIRST = 168
        const val COUNT = 8
        const val LAST = FIRST + COUNT - 1
        val SCENES = listOf(
            Scene("Still Horizon", "Soft layered horizons matched to your charcoal home screen."),
            Scene("Stonefold", "Three broad paper-like sheets in muted graphite."),
            Scene("Quiet Dunes", "Relaxed sculpted dunes and unbroken negative space."),
            Scene("Halo Outline", "A single matte orbit with extremely restrained contrast."),
            Scene("Folded Night", "Architectural paper folds without glass reflections."),
            Scene("Drift Contour", "Wide flowing contour and a fine, quiet accent line."),
            Scene("Monolith", "A grounded rounded monolith and low shadowed landscape."),
            Scene("Low Tide", "Minimal overlapping shores and soft circular forms.")
        )
        fun isCalm(preset: Int): Boolean = preset in FIRST..LAST
        fun description(preset: Int): String? =
            if (isCalm(preset)) SCENES[preset - FIRST].description else null
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shape = Path()

    private fun mix(a: Int, b: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) * (1f - f) + Color.red(b) * f).toInt().coerceIn(0, 255),
            (Color.green(a) * (1f - f) + Color.green(b) * f).toInt().coerceIn(0, 255),
            (Color.blue(a) * (1f - f) + Color.blue(b) * f).toInt().coerceIn(0, 255)
        )
    }

    private fun ink(state: MotionState, index: Int, fallback: Int): Int =
        if (state.fourColorsEnabled) mix(fallback, QuadColorSystem.color(state, index), .52f)
        else fallback

    private fun solid(canvas: Canvas, color: Int, alpha: Int = 255, path: Path) {
        fill.shader = null
        fill.color = color
        fill.alpha = alpha
        canvas.drawPath(path, fill)
        fill.alpha = 255
    }

    private fun wave(
        canvas: Canvas, color: Int,
        leftY: Float, c1y: Float, c2y: Float, rightY: Float
    ) {
        shape.reset()
        shape.moveTo(-50f, leftY)
        shape.cubicTo(280f, c1y, 715f, c2y, 1050f, rightY)
        shape.lineTo(1050f, 2050f)
        shape.lineTo(-50f, 2050f)
        shape.close()
        solid(canvas, color, path = shape)
    }

    private fun ellipse(canvas: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int) {
        fill.shader = null
        fill.color = color
        canvas.drawOval(RectF(x - rx, y - ry, x + rx, y + ry), fill)
    }

    private fun line(canvas: Canvas, color: Int, width: Float, points: Path) {
        stroke.color = color
        stroke.strokeWidth = width
        canvas.drawPath(points, stroke)
    }

    fun draw(canvas: Canvas, state: MotionState, seconds: Float, gyro: ParallaxPose) {
        val w = canvas.width.toFloat().coerceAtLeast(1f)
        val h = canvas.height.toFloat().coerceAtLeast(1f)
        val i = state.preset - FIRST
        if (i !in 0 until COUNT) return

        val base = ink(state, 0, Color.rgb(27, 27, 29))
        val sheet = ink(state, 1, Color.rgb(42, 42, 44))
        val middle = ink(state, 2, Color.rgb(49, 49, 51))
        val highlight = ink(state, 3, Color.rgb(61, 61, 63))
        val muted = mix(sheet, base, .46f)
        val depth = mix(base, Color.BLACK, .22f)
        val motion = sin(seconds * .085f).toFloat() * 8f * state.speed.coerceIn(.25f, 2f)

        canvas.save()
        canvas.scale(w / 1000f, h / 2000f)
        if (state.parallax) canvas.translate(gyro.x * 7f, gyro.y * 5f)

        fill.shader = LinearGradient(0f, 0f, 850f, 2000f,
            intArrayOf(base, mix(base, sheet, .24f), base),
            floatArrayOf(0f, .58f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(-30f, -30f, 1030f, 2030f, fill)
        fill.shader = null

        when (i) {
            0 -> {
                wave(canvas, sheet, 545f, 640f, 450f, 555f)
                ellipse(canvas, 758f, 850f, 122f, 93f, middle)
                wave(canvas, middle, 1215f, 1355f, 1270f, 1170f)
                wave(canvas, muted, 1605f, 1730f, 1690f, 1580f)
            }
            1 -> {
                wave(canvas, sheet, 755f, 610f + motion, 790f, 705f)
                wave(canvas, middle, 1040f, 1210f, 950f - motion, 1110f)
                wave(canvas, muted, 1450f, 1360f, 1690f, 1560f)
                wave(canvas, depth, 1900f, 1760f, 1800f, 1810f)
            }
            2 -> {
                wave(canvas, muted, 810f, 1020f, 500f, 770f)
                wave(canvas, sheet, 1160f, 980f, 1290f, 1150f)
                wave(canvas, middle, 1670f, 1500f, 1440f, 1630f)
                ellipse(canvas, 818f, 1330f, 90f, 92f, highlight)
            }
            3 -> {
                stroke.color = middle
                stroke.strokeWidth = 64f
                canvas.drawCircle(655f, 1050f + motion, 390f, stroke)
                stroke.color = highlight
                stroke.strokeWidth = 2f
                canvas.drawArc(RectF(264f, 660f, 1046f, 1442f), 205f, 95f, false, stroke)
                wave(canvas, sheet, 1590f, 1660f, 1410f, 1560f)
            }
            4 -> {
                shape.reset()
                shape.moveTo(-50f, 970f)
                shape.lineTo(1050f, 690f)
                shape.lineTo(1050f, 2050f)
                shape.lineTo(-50f, 2050f)
                shape.close()
                solid(canvas, sheet, path = shape)
                shape.reset()
                shape.moveTo(1050f, 970f)
                shape.lineTo(320f, 1520f)
                shape.lineTo(1050f, 2050f)
                shape.close()
                solid(canvas, middle, path = shape)
                wave(canvas, muted, 1620f, 1710f, 1450f, 1560f)
            }
            5 -> {
                wave(canvas, sheet, 700f, 890f, 650f, 830f)
                shape.reset()
                shape.moveTo(-60f, 710f)
                shape.cubicTo(260f, 900f, 770f, 670f, 1060f, 840f)
                line(canvas, highlight, 4f, shape)
                wave(canvas, muted, 1420f, 1700f, 1260f, 1450f)
                wave(canvas, middle, 1760f, 1620f, 1840f, 1730f)
            }
            6 -> {
                fill.color = sheet
                canvas.drawRoundRect(RectF(565f, 620f, 890f, 1550f), 160f, 160f, fill)
                fill.color = middle
                canvas.drawRoundRect(RectF(635f, 665f, 846f, 1460f), 105f, 105f, fill)
                wave(canvas, muted, 1320f, 1490f, 1280f, 1450f)
                wave(canvas, sheet, 1670f, 1600f, 1630f, 1540f)
            }
            7 -> {
                ellipse(canvas, 790f, 910f + motion, 165f, 165f, middle)
                ellipse(canvas, 792f, 910f + motion, 130f, 130f, muted)
                wave(canvas, sheet, 1050f, 1100f, 1230f, 1000f)
                wave(canvas, middle, 1320f, 1480f, 1220f, 1430f)
                wave(canvas, muted, 1690f, 1610f, 1810f, 1740f)
            }
        }
        canvas.restore()
    }
}
