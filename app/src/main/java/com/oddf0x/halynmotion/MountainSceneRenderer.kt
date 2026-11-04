package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Four intentionally restrained layered landscapes. Distant ridges are drawn
 * behind the clock and the nearest silhouette is drawn AFTER the clock.
 *
 * This is real Canvas occlusion rather than an opaque clock sticker. The clock
 * can be dragged through the ridge and naturally disappears behind terrain.
 */
class MountainSceneRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val path = Path()

    private data class Theme(
        val skyTop: Int, val skyMid: Int, val skyBottom: Int,
        val distant: Int, val middle: Int, val front: Int,
        val celestial: Int, val mist: Int
    )

    private val themes = arrayOf(
        Theme(0xFF081426.toInt(), 0xFF3A456A.toInt(), 0xFFBA8891.toInt(),
            0xFF5C6383.toInt(), 0xFF333D5A.toInt(), 0xFF121D32.toInt(),
            0xFFFFE0C6.toInt(), 0xFFB5A2C7.toInt()),
        Theme(0xFF020916.toInt(), 0xFF152C47.toInt(), 0xFF4B6173.toInt(),
            0xFF435A70.toInt(), 0xFF263E56.toInt(), 0xFF0A1D2E.toInt(),
            0xFFDBEEFF.toInt(), 0xFF7EADB7.toInt()),
        Theme(0xFF71888B.toInt(), 0xFFB3BAB1.toInt(), 0xFFE2D7BD.toInt(),
            0xFF9DADA3.toInt(), 0xFF637D74.toInt(), 0xFF29483F.toInt(),
            0xFFFFF1D1.toInt(), 0xFFE5E9E0.toInt()),
        Theme(0xFF251522.toInt(), 0xFF8E4C57.toInt(), 0xFFFFB078.toInt(),
            0xFFC58175.toInt(), 0xFF704A59.toInt(), 0xFF251E32.toInt(),
            0xFFFFD2A0.toInt(), 0xFFEDB1AA.toInt())
    )

    private fun theme(state: MotionState) = themes[(state.preset - FIRST).coerceIn(0, 3)]

    fun drawBackground(canvas: Canvas, state: MotionState, seconds: Float) {
        val w = canvas.width.toFloat().coerceAtLeast(1f)
        val h = canvas.height.toFloat().coerceAtLeast(1f)
        val scene = (state.preset - FIRST).coerceIn(0, 3)
        val t = theme(state)
        paint.shader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(t.skyTop, t.skyMid, t.skyBottom),
            floatArrayOf(0f, .49f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        if (scene == 1 || scene == 0) {
            paint.style = Paint.Style.FILL
            for (i in 0 until 52) {
                val x = ((i * 137 + 53) % 1000) / 1000f * w
                val y = ((i * 211 + 31) % 570) / 1000f * h
                val shimmer = .63f + .37f * sin(seconds * .12f + i * 1.73f)
                paint.color = Color.argb((36 + shimmer * 100).toInt().coerceIn(0, 255),
                    235, 244, 255)
                canvas.drawCircle(x, y, if (i % 11 == 0) 1.5f else .7f, paint)
            }
        }

        val orbX = w * when (scene) { 0 -> .72f; 1 -> .74f; 2 -> .29f; else -> .76f }
        val orbY = h * when (scene) { 0 -> .33f; 1 -> .26f; 2 -> .31f; else -> .37f }
        val orbRadius = min(w, h) * when (scene) { 1 -> .091f; else -> .11f }
        val glowColor = t.celestial
        paint.shader = RadialGradient(orbX, orbY, orbRadius * 3.2f,
            intArrayOf(Color.argb(56, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                Color.argb(18, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                Color.TRANSPARENT),
            floatArrayOf(0f, .50f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(orbX, orbY, orbRadius * 3.2f, paint)
        paint.shader = null
        paint.color = glowColor
        paint.alpha = if (scene == 2) 204 else 235
        canvas.drawCircle(orbX, orbY, orbRadius, paint)
        paint.alpha = 255

        // Distant landscape, always behind clock typography.
        ridge(canvas, w, h, t.distant,
            if (scene == 2) floatArrayOf(.0f,.77f,.17f,.64f,.34f,.74f,.53f,.61f,.76f,.77f,1f,.66f)
            else floatArrayOf(.0f,.73f,.17f,.59f,.31f,.71f,.49f,.56f,.65f,.68f,.84f,.59f,1f,.70f))
        ridge(canvas, w, h, t.middle,
            if (scene == 3) floatArrayOf(.0f,.87f,.19f,.72f,.35f,.80f,.53f,.68f,.7f,.81f,.86f,.67f,1f,.78f)
            else floatArrayOf(.0f,.86f,.19f,.74f,.35f,.82f,.54f,.68f,.72f,.83f,.88f,.71f,1f,.82f))

        val mistY = h * (.72f + .012f * sin(seconds * .11f))
        paint.shader = LinearGradient(0f, mistY - h * .12f, 0f, mistY + h * .17f,
            intArrayOf(Color.TRANSPARENT,
                Color.argb(28, Color.red(t.mist), Color.green(t.mist), Color.blue(t.mist)),
                Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, mistY - h * .12f, w, mistY + h * .17f, paint)
        paint.shader = null
    }

    fun drawForeground(canvas: Canvas, state: MotionState) {
        val w = canvas.width.toFloat().coerceAtLeast(1f)
        val h = canvas.height.toFloat().coerceAtLeast(1f)
        val scene = (state.preset - FIRST).coerceIn(0, 3)
        val t = theme(state)
        // Draw this AFTER the clock: mountain peaks occlude clock glyphs.
        val peaks = when (scene) {
            0 -> floatArrayOf(.0f,.85f,.12f,.79f,.24f,.66f,.35f,.77f,.48f,.70f,.62f,.82f,.76f,.64f,.89f,.78f,1f,.71f)
            1 -> floatArrayOf(.0f,.84f,.14f,.72f,.26f,.79f,.39f,.66f,.55f,.82f,.72f,.73f,.85f,.61f,1f,.77f)
            2 -> floatArrayOf(.0f,.83f,.11f,.77f,.23f,.69f,.34f,.76f,.45f,.74f,.59f,.63f,.73f,.81f,.86f,.69f,1f,.77f)
            else -> floatArrayOf(.0f,.86f,.12f,.78f,.26f,.70f,.4f,.80f,.54f,.67f,.7f,.79f,.82f,.66f,1f,.79f)
        }
        ridge(canvas, w, h, t.front, peaks)

        // Sparse geometric slope facets keep the silhouette polished, not noisy.
        paint.color = Color.argb(20, 244, 246, 255)
        path.reset()
        path.moveTo(w * .24f, h * peaks[5])
        path.lineTo(w * .30f, h * .83f)
        path.lineTo(w * .36f, h)
        path.lineTo(w * .27f, h)
        path.close()
        canvas.drawPath(path, paint)
        path.reset()
    }

    private fun ridge(canvas: Canvas, w: Float, h: Float, color: Int, pairs: FloatArray) {
        path.reset()
        path.moveTo(pairs[0] * w, pairs[1] * h)
        var i = 2
        while (i + 1 < pairs.size) {
            path.lineTo(pairs[i] * w, pairs[i + 1] * h)
            i += 2
        }
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        canvas.drawPath(path, paint)
        path.reset()
    }

    companion object {
        const val FIRST = 17
        const val LAST = 20
        fun isMountain(preset: Int): Boolean = preset in FIRST..LAST
    }
}
