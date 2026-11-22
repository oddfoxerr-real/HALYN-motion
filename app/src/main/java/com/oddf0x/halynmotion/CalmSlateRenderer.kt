package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Eight recognizable, continuously animated nighttime environments.
 *
 * No abstract/generated filler artwork, glass, stickers or text. The atmosphere
 * uses restrained graphite values and keeps its strongest contrast below the
 * clock area. All movement is deterministic and time-based, so scene previews,
 * live wallpaper playback and resumed surfaces behave consistently.
 *
 * Designed for Canvas on Android 13+; no network calls or bitmap allocations.
 */
class CalmSlateRenderer {
    data class Scene(val name: String, val description: String)

    companion object {
        // Stable indices: preserve existing wallpaper selections and per-scene colours.
        const val FIRST = 168
        const val COUNT = 8
        const val LAST = FIRST + COUNT - 1

        val SCENES = listOf(
            Scene("Mist Valley", "Layered mountain ridges with drifting valley fog."),
            Scene("Moonrise", "A soft rising moon and quiet distant peaks."),
            Scene("Midnight Sea", "Slow ocean swell and moving moonlit reflections."),
            Scene("Pine Silence", "A deep forest whose treetops sway in the breeze."),
            Scene("Rainfall", "Passing rain clouds, falling rain and pond ripples."),
            Scene("First Snow", "A snowy alpine valley with gently falling flakes."),
            Scene("Cloud Passage", "Moonlight spilling through slowly drifting clouds."),
            Scene("Star Valley", "A twinkling night sky over an alpine silhouette.")
        )

        fun isCalm(preset: Int): Boolean = preset in FIRST..LAST
        fun description(preset: Int): String? =
            if (isCalm(preset)) SCENES[preset - FIRST].description else null
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val detail = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val bounds = RectF()

    private fun blend(a: Int, b: Int, strength: Float): Int {
        val t = strength.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt().coerceIn(0, 255),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt().coerceIn(0, 255),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt().coerceIn(0, 255)
        )
    }

    private fun palette(state: MotionState, index: Int, neutral: Int): Int {
        return if (state.fourColorsEnabled) {
            blend(neutral, QuadColorSystem.color(state, index), .40f)
        } else {
            neutral
        }
    }

    private fun color(color: Int, alpha: Int = 255) {
        fill.shader = null
        fill.style = Paint.Style.FILL
        fill.color = color
        fill.alpha = alpha.coerceIn(0, 255)
    }

    private fun circle(canvas: Canvas, x: Float, y: Float, radius: Float, ink: Int, alpha: Int = 255) {
        color(ink, alpha)
        canvas.drawCircle(x, y, radius, fill)
    }

    private fun oval(canvas: Canvas, x: Float, y: Float, rx: Float, ry: Float, ink: Int, alpha: Int = 255) {
        color(ink, alpha)
        bounds.set(x - rx, y - ry, x + rx, y + ry)
        canvas.drawOval(bounds, fill)
    }

    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float,
                     ink: Int, width: Float, alpha: Int = 255) {
        detail.color = ink
        detail.alpha = alpha.coerceIn(0, 255)
        detail.strokeWidth = width
        canvas.drawLine(x1, y1, x2, y2, detail)
        detail.alpha = 255
    }

    private fun ridge(canvas: Canvas, ink: Int, points: FloatArray, xShift: Float = 0f) {
        path.reset()
        path.moveTo(-80f, 2120f)
        path.lineTo(points[0] + xShift, points[1])
        var i = 2
        while (i < points.size) {
            path.lineTo(points[i] + xShift, points[i + 1])
            i += 2
        }
        path.lineTo(1080f, 2120f)
        path.close()
        color(ink)
        canvas.drawPath(path, fill)
    }

    private fun cloud(canvas: Canvas, x: Float, y: Float, scale: Float, ink: Int, alpha: Int) {
        // A coherent cloud silhouette made from connected domes, not random circles.
        oval(canvas, x, y + 20f * scale, 190f * scale, 45f * scale, ink, alpha)
        oval(canvas, x - 90f * scale, y, 77f * scale, 49f * scale, ink, alpha)
        oval(canvas, x, y - 32f * scale, 107f * scale, 73f * scale, ink, alpha)
        oval(canvas, x + 97f * scale, y - 6f * scale, 79f * scale, 52f * scale, ink, alpha)
    }

    private fun star(canvas: Canvas, x: Float, y: Float, r: Float, alpha: Int, ink: Int) {
        circle(canvas, x, y, r, ink, alpha)
    }

    // Stable pseudo-random positions; nothing jumps when a frame is redrawn.
    private fun hash(n: Int): Float {
        val mixed = n * 1103515245 + 12345
        return ((mixed ushr 8) and 0xFFFF) / 65535f
    }

    private fun moon(canvas: Canvas, x: Float, y: Float, radius: Float, ink: Int) {
        circle(canvas, x, y, radius * 1.60f, ink, 8)
        circle(canvas, x, y, radius * 1.29f, ink, 15)
        circle(canvas, x, y, radius, ink, 185)
        circle(canvas, x - radius * .23f, y - radius * .12f,
            radius * .17f, Color.rgb(100, 103, 108), 33)
        circle(canvas, x + radius * .29f, y + radius * .28f,
            radius * .11f, Color.rgb(100, 103, 108), 29)
    }

    private fun pine(canvas: Canvas, x: Float, bottom: Float, height: Float,
                     ink: Int, lean: Float, alpha: Int = 255) {
        val tipX = x + lean
        val tipY = bottom - height
        color(ink, alpha)
        path.reset()
        path.moveTo(x - height * .035f, bottom)
        path.lineTo(x + height * .035f, bottom)
        path.lineTo(tipX + height * .015f, tipY + height * .13f)
        path.lineTo(tipX - height * .015f, tipY + height * .13f)
        path.close()
        canvas.drawPath(path, fill)
        for (tier in 0..3) {
            val part = tier / 4f
            val top = tipY + height * (.08f + part * .19f)
            val bottomTier = top + height * .32f
            val span = height * (.14f + part * .09f)
            path.reset()
            path.moveTo(tipX, top)
            path.lineTo(x + span, bottomTier)
            path.lineTo(x - span, bottomTier)
            path.close()
            canvas.drawPath(path, fill)
        }
        fill.alpha = 255
    }

    private fun drawNight(canvas: Canvas, top: Int, bottom: Int) {
        color(top)
        fill.shader = LinearGradient(0f, 0f, 0f, 2010f,
            intArrayOf(top, blend(top, bottom, .40f), bottom),
            floatArrayOf(0f, .57f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(-80f, -80f, 1080f, 2120f, fill)
        fill.shader = null
    }

    fun draw(canvas: Canvas, state: MotionState, seconds: Float, gyro: ParallaxPose) {
        val index = state.preset - FIRST
        if (index !in 0 until COUNT) return

        val t = seconds.coerceAtLeast(0f) * state.speed.coerceIn(.25f, 2f)
        val sky = palette(state, 0, Color.rgb(22, 23, 25))
        val mist = palette(state, 1, Color.rgb(45, 47, 50))
        val far = palette(state, 1, Color.rgb(58, 60, 64))
        val near = palette(state, 2, Color.rgb(38, 40, 43))
        val ground = palette(state, 0, Color.rgb(29, 30, 33))
        val pale = palette(state, 3, Color.rgb(164, 165, 169))

        val w = canvas.width.toFloat().coerceAtLeast(1f)
        val h = canvas.height.toFloat().coerceAtLeast(1f)
        canvas.save()
        canvas.scale(w / 1000f, h / 2000f)
        if (state.parallax) {
            canvas.translate(gyro.x.coerceIn(-1f, 1f) * 9f, gyro.y.coerceIn(-1f, 1f) * 7f)
        }
        drawNight(canvas, sky, blend(sky, far, .40f))

        when (index) {
            0 -> { // Mountain valley, atmospheric haze moving through two ridgelines.
                ridge(canvas, far, floatArrayOf(
                    -90f, 1380f, 90f, 1180f, 225f, 1278f, 385f, 990f,
                    505f, 1128f, 660f, 945f, 810f, 1190f, 1090f, 1020f
                ))
                val drift = sin(t * .12f).toFloat() * 46f
                for (layer in 0..3) {
                    val y = 1280f + layer * 165f
                    oval(canvas, 290f + drift + layer * 75f, y, 480f, 48f, mist, 38 + layer * 9)
                    oval(canvas, 770f + drift * .6f, y + 38f, 450f, 39f, pale, 9)
                }
                ridge(canvas, near, floatArrayOf(
                    -80f, 1780f, 180f, 1530f, 315f, 1670f,
                    515f, 1450f, 710f, 1580f, 970f, 1435f, 1080f, 1535f
                ))
                oval(canvas, 460f + drift * .9f, 1840f, 600f, 40f, mist, 32)
            }

            1 -> { // Moonrise is continuous, not a random decorative circle.
                val rise = sin(t * .025f).toFloat() * 80f
                moon(canvas, 760f, 1020f - rise, 113f, pale)
                ridge(canvas, far, floatArrayOf(
                    -80f, 1500f, 130f, 1390f, 280f, 1460f,
                    470f, 1220f, 575f, 1340f, 780f, 1250f, 1080f, 1440f
                ))
                ridge(canvas, near, floatArrayOf(
                    -80f, 1840f, 200f, 1680f, 360f, 1760f,
                    570f, 1565f, 730f, 1640f, 950f, 1570f, 1080f, 1700f
                ))
                oval(canvas, 570f, 1545f, 500f, 36f, mist, 42)
            }

            2 -> { // Ocean surface: layered swells and moving broken moon reflection.
                moon(canvas, 773f, 1020f, 83f, pale)
                color(far, 195)
                canvas.drawRect(-80f, 1240f, 1080f, 2120f, fill)
                for (n in 0 until 25) {
                    val y = 1250f + n * 33f
                    val phase = t * (.14f + n * .007f)
                    val waveX = sin(phase + n * .85f).toFloat() * 50f
                    val width = 150f + (n % 5) * 50f
                    val x = 260f + (n * 79 % 590) + waveX
                    line(canvas, x - width * .5f, y, x + width * .5f, y + 1f,
                        if (n % 3 == 0) mist else near, 2.6f, 130)
                }
                for (n in 0 until 12) {
                    val y = 1280f + n * 54f
                    val shimmer = sin(t * .52f + n * 1.3f).toFloat() * 32f
                    val spread = n * 4.7f + 11f
                    line(canvas, 770f - spread + shimmer, y, 770f + spread + shimmer, y,
                        pale, 2.8f, (80 - n * 4).coerceAtLeast(20))
                }
            }

            3 -> { // Two depth planes of conifers, wind-driven canopy sway.
                ridge(canvas, far, floatArrayOf(
                    -80f, 1510f, 165f, 1340f, 345f, 1515f,
                    510f, 1290f, 740f, 1450f, 1080f, 1370f
                ))
                for (n in 0 until 14) {
                    val x = -40f + n * 83f
                    val ht = 285f + hash(n * 19 + 2) * 220f
                    val lean = sin(t * .31f + n * .68f).toFloat() * 4.0f
                    pine(canvas, x, 1700f + hash(n * 29) * 55f, ht, mist, lean, 210)
                }
                color(ground)
                canvas.drawRect(-80f, 1675f, 1080f, 2120f, fill)
                for (n in 0 until 10) {
                    val x = -40f + n * 115f
                    val ht = 350f + hash(n * 29 + 6) * 285f
                    val lean = sin(t * .20f + n * .52f).toFloat() * 6.5f
                    pine(canvas, x, 1955f + hash(n * 13) * 80f, ht, near, lean)
                }
                oval(canvas, 550f + sin(t * .09f).toFloat() * 25f,
                    1530f, 530f, 43f, far, 21)
            }

            4 -> { // Cloud front, downward rainfall, and concentric pond ripples.
                cloud(canvas, 230f + sin(t * .05f).toFloat() * 40f, 940f, 1.65f, far, 170)
                cloud(canvas, 840f - t * .45f % 300f, 1050f, 1.5f, mist, 180)
                ridge(canvas, near, floatArrayOf(
                    -80f, 1610f, 200f, 1550f, 430f, 1635f,
                    650f, 1540f, 900f, 1590f, 1080f, 1570f
                ))
                color(ground)
                canvas.drawRect(-80f, 1700f, 1080f, 2120f, fill)
                for (n in 0 until 80) {
                    val speed = .17f + hash(n * 31 + 4) * .13f
                    val y = 900f + ((hash(n * 7 + 30) * 1180f + t * speed * 115f) % 1200f)
                    val x = hash(n * 17 + 11) * 1060f - 25f + y * .018f
                    line(canvas, x, y, x - 6f, y + 19f, pale, 1.3f, 55)
                }
                for (n in 0..4) {
                    val age = ((t * .28f + n * .213f) % 1f)
                    val cx = 130f + n * 180f
                    val rr = 15f + age * 64f
                    detail.color = pale
                    detail.alpha = ((1f - age) * 65f).toInt()
                    detail.strokeWidth = 1.7f
                    bounds.set(cx - rr, 1800f - rr * .23f, cx + rr, 1800f + rr * .23f)
                    canvas.drawOval(bounds, detail)
                }
                detail.alpha = 255
            }

            5 -> { // Alpine snowfall, gentle deterministic flakes at two speeds.
                moon(canvas, 840f, 780f, 67f, mist)
                ridge(canvas, far, floatArrayOf(
                    -80f, 1390f, 125f, 1270f, 275f, 1360f,
                    490f, 1060f, 570f, 1190f, 780f, 1130f, 1080f, 1400f
                ))
                ridge(canvas, near, floatArrayOf(
                    -80f, 1770f, 160f, 1645f, 340f, 1730f,
                    580f, 1490f, 720f, 1610f, 950f, 1525f, 1080f, 1660f
                ))
                for (n in 0 until 70) {
                    val parallax = if (n % 3 == 0) .54f else .27f
                    val x = hash(n * 17 + 4) * 1080f - 40f +
                        sin(t * .19f + n * .4f).toFloat() * (8f + n % 7)
                    val y = (hash(n * 41 + 9) * 2170f + t * (20f + n % 8) * parallax) % 2200f
                    star(canvas, x, y, if (n % 3 == 0) 2.5f else 1.5f,
                        if (n % 3 == 0) 130 else 82, pale)
                }
            }

            6 -> { // Wind-driven cloud banks crossing a naturally lit moon.
                moon(canvas, 742f, 1040f, 117f, pale)
                val scroll = t * .45f
                for (n in 0..5) {
                    val scale = .8f + (n % 3) * .25f
                    val x = ((n * 290f + 100f + scroll * (1f + n % 2)) % 1700f) - 350f
                    val y = 930f + n * 100f
                    cloud(canvas, x, y, scale, if (n % 2 == 0) far else mist, 183)
                }
                ridge(canvas, near, floatArrayOf(
                    -80f, 1790f, 140f, 1590f, 330f, 1670f,
                    575f, 1500f, 760f, 1600f, 1080f, 1550f
                ))
                oval(canvas, 500f, 1605f, 530f, 30f, mist, 30)
            }

            7 -> { // Starfield twinkling over a physically recognizable V-shaped valley.
                for (n in 0 until 85) {
                    val x = hash(n * 37 + 8) * 1000f
                    val y = 160f + hash(n * 97 + 17) * 1150f
                    val twinkle = .60f + .40f * sin(t * (.28f + hash(n + 21) * .3f) + n).toFloat()
                    star(canvas, x, y, if (n % 9 == 0) 2.5f else 1.2f,
                        (42f + 105f * twinkle).toInt(), pale)
                }
                ridge(canvas, far, floatArrayOf(
                    -80f, 1325f, 120f, 1125f, 240f, 1320f,
                    390f, 1455f, 580f, 1390f, 795f, 1160f, 1080f, 1320f
                ))
                ridge(canvas, near, floatArrayOf(
                    -80f, 1510f, 170f, 1430f, 355f, 1780f,
                    535f, 1860f, 695f, 1660f, 920f, 1450f, 1080f, 1520f
                ))
                oval(canvas, 520f, 1915f, 300f, 45f, mist, 32)
            }
        }
        canvas.restore()
    }
}
