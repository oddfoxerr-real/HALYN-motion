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


    /**
     * Analytic surface movement, regenerated from elapsed time on each frame.
     * Unlike static line overlays, the complete wave silhouette actually travels.
     */
    private fun swell(canvas: Canvas, y: Float, amplitude: Float, phase: Float,
                      ink: Int, wavelength: Float = 165f) {
        path.reset()
        var x = -90f
        while (x <= 1100f) {
            val yy = y +
                sin(x / wavelength + phase).toFloat() * amplitude +
                sin(x / (wavelength * .43f) - phase * .74f).toFloat() * amplitude * .24f
            if (x == -90f) path.moveTo(x, yy) else path.lineTo(x, yy)
            x += 22f
        }
        path.lineTo(1100f, 2120f)
        path.lineTo(-90f, 2120f)
        path.close()
        color(ink)
        canvas.drawPath(path, fill)
    }

    private fun driftingFog(canvas: Canvas, time: Float, y: Float, velocity: Float,
                            ink: Int, alpha: Int, scale: Float) {
        // Multiple translucent banks form one coherent fog stream.
        val displacement = ((time * velocity) % 1700f + 1700f) % 1700f
        for (i in -1..1) {
            val x = i * 820f + displacement - 300f
            oval(canvas, x, y + i * 16f, 550f * scale, 78f * scale, ink, alpha)
            oval(canvas, x + 155f, y + 20f, 430f * scale, 54f * scale, ink, alpha / 2)
        }
    }

    private fun speckle(canvas: Canvas, count: Int, time: Float,
                        drift: Float, fallSpeed: Float, colorValue: Int,
                        maxSize: Float, maxAlpha: Int, seed: Int) {
        for (i in 0 until count) {
            val layer = .45f + hash(i * 29 + seed) * .90f
            val initialX = hash(i * 19 + seed + 14) * 1110f - 55f
            val x = initialX +
                sin(time * .32f + i * .61f).toFloat() * drift * layer +
                time * drift * .27f * layer
            val wrappedX = ((x % 1110f) + 1110f) % 1110f - 55f
            val y = ((hash(i * 41 + seed + 7) * 2170f +
                time * fallSpeed * layer) % 2170f + 2170f) % 2170f - 90f
            circle(canvas, wrappedX, y,
                (.85f + layer * maxSize).coerceAtMost(maxSize * 1.7f),
                colorValue, (maxAlpha * layer).toInt().coerceIn(0, 255))
        }
    }

    fun draw(canvas: Canvas, state: MotionState, seconds: Float, gyro: ParallaxPose) {
        val index = state.preset - FIRST
        if (index !in 0 until COUNT) return

        val t = seconds.coerceAtLeast(0f) * state.speed.coerceIn(.25f, 2f)
        val strength = state.intensity.coerceIn(.35f, 2.3f)
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
            canvas.translate(gyro.x.coerceIn(-1f, 1f) * 19f, gyro.y.coerceIn(-1f, 1f) * 14f)
        }
        drawNight(canvas, sky, blend(sky, far, .40f))

        when (index) {
            0 -> { // Valley: fog banks travel visibly between distinct depth planes.
                ridge(canvas, far, floatArrayOf(
                    -90f, 1380f, 90f, 1180f, 225f, 1278f, 385f, 990f,
                    505f, 1128f, 660f, 945f, 810f, 1190f, 1090f, 1020f
                ))
                driftingFog(canvas, t, 1230f, 13f * strength, mist, 53, 1.0f)
                driftingFog(canvas, -t, 1420f, 9f * strength, pale, 17, .84f)
                ridge(canvas, near, floatArrayOf(
                    -80f, 1780f, 180f, 1530f, 315f, 1670f,
                    515f, 1450f, 710f, 1580f, 970f, 1435f, 1080f, 1535f
                ))
                driftingFog(canvas, t, 1785f, 21f * strength, mist, 40, .86f)
                cloud(canvas, 530f + sin(t * .22f).toFloat() * 80f * strength,
                    830f, .74f, mist, 61)
            }
            1 -> { // Moonrise: strong atmospheric cloud movement and evolving moon position.
                val rise = sin(t * .18f).toFloat() * 92f * strength
                moon(canvas, 740f, 1020f - rise, 112f, pale)
                cloud(canvas, ((-280f + t * 24f * strength) % 1450f) + 110f,
                    945f, .95f, mist, 110)
                cloud(canvas, 820f - ((t * 12f * strength) % 1600f),
                    1150f, .60f, far, 82)
                ridge(canvas, far, floatArrayOf(
                    -80f, 1500f, 130f, 1390f, 280f, 1460f,
                    470f, 1220f, 575f, 1340f, 780f, 1250f, 1080f, 1440f
                ))
                driftingFog(canvas, t, 1480f, 9f * strength, pale, 27, .80f)
                ridge(canvas, near, floatArrayOf(
                    -80f, 1840f, 200f, 1680f, 360f, 1760f,
                    570f, 1565f, 730f, 1640f, 950f, 1570f, 1080f, 1700f
                ))
            }
            2 -> { // Ocean: animated waves change shape, not only small reflective lines.
                moon(canvas, 773f, 1020f, 83f, pale)
                swell(canvas, 1210f, 24f * strength, t * .55f, far, 160f)
                swell(canvas, 1305f, 31f * strength, -t * .75f + 2f, near, 140f)
                swell(canvas, 1480f, 38f * strength, t * .9f + 2.4f, mist, 190f)
                swell(canvas, 1660f, 41f * strength, -t * .8f, ground, 168f)
                for (n in 0 until 22) {
                    val y = 1215f + n * 33f
                    val waveX = sin(t * .85f + n * .72f).toFloat() * 76f * strength
                    val shimmer = .52f + .48f * abs(sin(t * 1.3f + n * .9f).toFloat())
                    val spread = 18f + n * 4.9f
                    line(canvas, 773f - spread + waveX, y,
                        773f + spread + waveX, y + 2f,
                        pale, 2.3f + shimmer * 1.8f,
                        (105f * shimmer).toInt().coerceIn(22, 106))
                }
            }
            3 -> { // Pine forest: wind moves full canopies and carries valley fog.
                ridge(canvas, far, floatArrayOf(
                    -80f, 1510f, 165f, 1340f, 345f, 1515f,
                    510f, 1290f, 740f, 1450f, 1080f, 1370f
                ))
                for (n in 0 until 16) {
                    val x = -40f + n * 78f
                    val height = 285f + hash(n * 19 + 2) * 220f
                    val lean = sin(t * .38f + n * .31f).toFloat() * 18f * strength
                    pine(canvas, x, 1700f + hash(n * 29) * 55f,
                        height, mist, lean, 210)
                }
                driftingFog(canvas, t, 1570f, 15f * strength, pale, 31, .7f)
                color(ground)
                canvas.drawRect(-80f, 1740f, 1080f, 2120f, fill)
                for (n in 0 until 11) {
                    val x = -40f + n * 108f
                    val height = 350f + hash(n * 29 + 6) * 285f
                    val lean = sin(t * .33f + n * .43f).toFloat() * 30f * strength
                    pine(canvas, x, 1965f + hash(n * 13) * 80f,
                        height, near, lean)
                }
                driftingFog(canvas, -t, 1890f, 9f * strength, far, 25, .58f)
            }
            4 -> { // Rain: clearly falling streaks, moving clouds and expanding water rings.
                cloud(canvas, 150f + ((t * 19f * strength) % 1400f) - 250f,
                    900f, 1.6f, far, 204)
                cloud(canvas, 900f - ((t * 23f * strength) % 1580f),
                    1110f, 1.52f, mist, 202)
                ridge(canvas, near, floatArrayOf(
                    -80f, 1610f, 200f, 1550f, 430f, 1635f,
                    650f, 1540f, 900f, 1590f, 1080f, 1570f
                ))
                color(ground)
                canvas.drawRect(-80f, 1700f, 1080f, 2120f, fill)
                for (n in 0 until 160) {
                    val speed = 390f + hash(n * 31 + 4) * 250f
                    val y = 780f + ((hash(n * 7 + 30) * 1420f +
                        t * speed * strength) % 1420f)
                    val x = hash(n * 17 + 11) * 1100f - 55f +
                        sin(t * .41f + n).toFloat() * 25f * strength
                    val length = 23f + hash(n + 4) * 27f
                    line(canvas, x, y, x - length * .25f, y + length,
                        pale, 1.7f, 60 + (n % 3) * 19)
                }
                for (n in 0 until 9) {
                    val age = ((t * .63f * strength + n * .123f) % 1f)
                    val cx = 75f + n * 119f
                    val rr = 11f + age * 82f
                    detail.color = pale
                    detail.alpha = ((1f - age) * 111f).toInt()
                    detail.strokeWidth = 2.4f
                    bounds.set(cx - rr, 1800f - rr * .22f,
                        cx + rr, 1800f + rr * .22f)
                    canvas.drawOval(bounds, detail)
                }
                detail.alpha = 255
            }
            5 -> { // Snow: multiple depth planes with visible wind and downward drift.
                moon(canvas, 840f, 780f, 67f, mist)
                ridge(canvas, far, floatArrayOf(
                    -80f, 1390f, 125f, 1270f, 275f, 1360f,
                    490f, 1060f, 570f, 1190f, 780f, 1130f, 1080f, 1400f
                ))
                ridge(canvas, near, floatArrayOf(
                    -80f, 1770f, 160f, 1645f, 340f, 1730f,
                    580f, 1490f, 720f, 1610f, 950f, 1525f, 1080f, 1660f
                ))
                speckle(canvas, 105, t, 56f * strength, 73f * strength,
                    pale, 2.1f, 125, 9)
                speckle(canvas, 56, t, 82f * strength, 115f * strength,
                    pale, 3.6f, 175, 38)
                driftingFog(canvas, t, 1680f, 9f * strength, mist, 25, .66f)
            }
            6 -> { // Clouds: independent near/far banks travel across the moon.
                moon(canvas, 742f, 1040f, 117f, pale)
                val scroll = t * 31f * strength
                for (n in 0..7) {
                    val scale = .70f + (n % 3) * .23f
                    val dir = if (n % 2 == 0) 1f else -1f
                    val x = ((n * 330f + scroll * dir * (.65f + n % 3 * .2f) +
                        3000f) % 1850f) - 420f
                    val y = 820f + n * 113f
                    cloud(canvas, x, y, scale,
                        if (n % 2 == 0) far else mist,
                        if (n % 2 == 0) 183 else 225)
                }
                ridge(canvas, near, floatArrayOf(
                    -80f, 1790f, 140f, 1590f, 330f, 1670f,
                    575f, 1500f, 760f, 1600f, 1080f, 1550f
                ))
                driftingFog(canvas, -t, 1640f, 12f * strength, mist, 28, .76f)
            }
            7 -> { // Night sky: stars twinkle, occasional shooting star crosses valley.
                for (n in 0 until 96) {
                    val x = hash(n * 37 + 8) * 1000f
                    val y = 160f + hash(n * 97 + 17) * 1150f
                    val twinkle = .45f + .55f *
                        sin(t * (.42f + hash(n + 21) * .35f) + n).toFloat() *
                        sin(t * (.42f + hash(n + 21) * .35f) + n).toFloat()
                    star(canvas, x, y, if (n % 9 == 0) 2.5f else 1.2f,
                        (45f + 112f * twinkle).toInt(), pale)
                }
                val meteorPeriod = 23f
                val meteorPhase = ((t * strength) % meteorPeriod) / meteorPeriod
                if (meteorPhase < .12f) {
                    val u = meteorPhase / .12f
                    val startX = 850f - u * 520f
                    val startY = 430f + u * 190f
                    line(canvas, startX, startY, startX + 95f, startY - 35f,
                        pale, 3.1f, ((1f - u) * 148f).toInt())
                    circle(canvas, startX, startY, 3.5f, pale, 165)
                }
                ridge(canvas, far, floatArrayOf(
                    -80f, 1325f, 120f, 1125f, 240f, 1320f,
                    390f, 1455f, 580f, 1390f, 795f, 1160f, 1080f, 1320f
                ))
                ridge(canvas, near, floatArrayOf(
                    -80f, 1510f, 170f, 1430f, 355f, 1780f,
                    535f, 1860f, 695f, 1660f, 920f, 1450f, 1080f, 1520f
                ))
                driftingFog(canvas, t, 1880f, 7f * strength, mist, 26, .6f)
            }
        }
        canvas.restore()
    }
}
