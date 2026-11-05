package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.min
import kotlin.math.sin

/**
 * Layered minimalist landscapes sharing one normalized camera pose.
 *
 * Canvas order is: sky -> celestial/stars -> far mountain -> middle mountain
 * -> mist -> clock (drawn by caller) -> foreground mountain.
 * Every polygon is oversized past the viewport to prevent edge seams on tilt.
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

    // Geometry is created once, not on every 60 Hz frame.
    private val far = arrayOf(
        floatArrayOf(0f,.73f,.17f,.59f,.31f,.71f,.49f,.56f,.65f,.68f,.84f,.59f,1f,.70f),
        floatArrayOf(0f,.73f,.17f,.59f,.31f,.71f,.49f,.56f,.65f,.68f,.84f,.59f,1f,.70f),
        floatArrayOf(0f,.77f,.17f,.64f,.34f,.74f,.53f,.61f,.76f,.77f,1f,.66f),
        floatArrayOf(0f,.73f,.17f,.59f,.31f,.71f,.49f,.56f,.65f,.68f,.84f,.59f,1f,.70f)
    )
    private val middle = arrayOf(
        floatArrayOf(0f,.86f,.19f,.74f,.35f,.82f,.54f,.68f,.72f,.83f,.88f,.71f,1f,.82f),
        floatArrayOf(0f,.86f,.19f,.74f,.35f,.82f,.54f,.68f,.72f,.83f,.88f,.71f,1f,.82f),
        floatArrayOf(0f,.86f,.19f,.74f,.35f,.82f,.54f,.68f,.72f,.83f,.88f,.71f,1f,.82f),
        floatArrayOf(0f,.87f,.19f,.72f,.35f,.80f,.53f,.68f,.70f,.81f,.86f,.67f,1f,.78f)
    )
    private val foreground = arrayOf(
        floatArrayOf(0f,.85f,.12f,.79f,.24f,.66f,.35f,.77f,.48f,.70f,.62f,.82f,.76f,.64f,.89f,.78f,1f,.71f),
        floatArrayOf(0f,.84f,.14f,.72f,.26f,.79f,.39f,.66f,.55f,.82f,.72f,.73f,.85f,.61f,1f,.77f),
        floatArrayOf(0f,.83f,.11f,.77f,.23f,.69f,.34f,.76f,.45f,.74f,.59f,.63f,.73f,.81f,.86f,.69f,1f,.77f),
        floatArrayOf(0f,.86f,.12f,.78f,.26f,.70f,.4f,.80f,.54f,.67f,.70f,.79f,.82f,.66f,1f,.79f)
    )

    private var cachedScene = -1
    private var cachedWidth = -1f
    private var cachedHeight = -1f
    private var skyGradient: Shader? = null
    private var glowGradient: Shader? = null
    private var mistGradient: Shader? = null

    private fun ensureShaders(scene: Int, w: Float, h: Float) {
        if (cachedScene == scene && cachedWidth == w && cachedHeight == h) return
        cachedScene = scene
        cachedWidth = w
        cachedHeight = h
        val t = themes[scene]
        skyGradient = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(t.skyTop, t.skyMid, t.skyBottom),
            floatArrayOf(0f, .49f, 1f), Shader.TileMode.CLAMP)
        val orbRadius = min(w, h) * if (scene == 1) .091f else .11f
        val c = t.celestial
        glowGradient = RadialGradient(0f, 0f, orbRadius * 3.2f,
            intArrayOf(
                Color.argb(58, Color.red(c), Color.green(c), Color.blue(c)),
                Color.argb(16, Color.red(c), Color.green(c), Color.blue(c)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
        mistGradient = LinearGradient(0f, h * .60f, 0f, h * .91f,
            intArrayOf(Color.TRANSPARENT,
                Color.argb(32, Color.red(t.mist), Color.green(t.mist), Color.blue(t.mist)),
                Color.TRANSPARENT),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
    }

    fun drawBackground(
        canvas: Canvas, state: MotionState, seconds: Float,
        pose: ParallaxPose = ParallaxPose()
    ) {
        val w = canvas.width.coerceAtLeast(1).toFloat()
        val h = canvas.height.coerceAtLeast(1).toFloat()
        val scene = (state.preset - FIRST).coerceIn(0, 3)
        ensureShaders(scene, w, h)
        val t = themes[scene]
        val depth = state.mountainDepth

        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = skyGradient
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Existing colour controls previously had no effect on mountains.
        // Use a light atmosphere tint while preserving the scene palette.
        val accent = if (state.palette == 5) state.customColor else
            HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0, 4)]
        paint.color = accent
        paint.alpha = if (state.palette == 5) 27 else 13
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.alpha = 255

        // Stars + orb shift less than the foreground. The sky remains full bleed.
        if (scene == 0 || scene == 1) {
            for (i in 0 until 52) {
                val x = ((i * 137 + 53) % 1000) / 1000f * w + pose.x * w * .006f * depth
                val y = ((i * 211 + 31) % 570) / 1000f * h + pose.y * h * .004f * depth
                val shimmer = .63f + .37f * sin(seconds * state.speed * .12f + i * 1.73f)
                paint.color = Color.argb(((40f + shimmer * 96f) * state.intensity).toInt().coerceIn(0, 255),
                    235, 244, 255)
                canvas.drawCircle(x, y, if (i % 11 == 0) 1.5f else .7f, paint)
            }
        }

        val orbX = w * when (scene) { 0 -> .72f; 1 -> .74f; 2 -> .29f; else -> .76f } +
            pose.x * w * .012f * depth
        val orbY = h * when (scene) { 0 -> .33f; 1 -> .26f; 2 -> .31f; else -> .37f } +
            pose.y * h * .009f * depth
        val orbRadius = min(w, h) * if (scene == 1) .091f else .11f
        canvas.save()
        canvas.translate(orbX, orbY)
        paint.shader = glowGradient
        paint.alpha = (180f * state.intensity).toInt().coerceIn(85, 255)
        canvas.drawCircle(0f, 0f, orbRadius * 3.2f, paint)
        paint.shader = null
        paint.color = t.celestial
        paint.alpha = ((if (scene == 2) 204 else 235) * state.intensity).toInt().coerceIn(96, 255)
        canvas.drawCircle(0f, 0f, orbRadius, paint)
        paint.alpha = 255
        canvas.restore()

        ridge(canvas, w, h, t.distant, far[scene],
            pose.x * w * .012f * depth, pose.y * h * .006f * depth)
        ridge(canvas, w, h, t.middle, middle[scene],
            pose.x * w * .027f * depth, pose.y * h * .013f * depth)

        paint.shader = mistGradient
        val drift = .008f * sin(seconds * state.speed * .09f)
        canvas.save()
        canvas.translate(pose.x * w * .016f * depth,
            (pose.y * .012f * depth + drift) * h)
        canvas.drawRect(-w * .16f, h * .60f, w * 1.16f, h * .91f, paint)
        canvas.restore()
        paint.shader = null
    }

    fun drawForeground(
        canvas: Canvas, state: MotionState,
        pose: ParallaxPose = ParallaxPose()
    ) {
        val w = canvas.width.coerceAtLeast(1).toFloat()
        val h = canvas.height.coerceAtLeast(1).toFloat()
        val scene = (state.preset - FIRST).coerceIn(0, 3)
        paint.shader = null
        paint.alpha = 255
        ridge(canvas, w, h, themes[scene].front, foreground[scene],
            pose.x * w * .052f * state.mountainDepth,
            pose.y * h * .026f * state.mountainDepth)
        // No hand-painted facets above the silhouette: these used to cut
        // across the ridge and made the clock overlap look broken.
    }

    private fun ridge(
        canvas: Canvas, w: Float, h: Float,
        color: Int, pairs: FloatArray, dx: Float, dy: Float
    ) {
        // Extend both ends past the viewport. Translating a closed polygon
        // that ends at x=w previously revealed sky-coloured edge seams.
        val pad = w * .18f
        val firstY = pairs[1] * h + dy
        val lastY = pairs[pairs.lastIndex] * h + dy
        path.reset()
        path.moveTo(-pad, firstY)
        var i = 0
        while (i < pairs.size - 1) {
            path.lineTo(pairs[i] * w + dx, pairs[i + 1] * h + dy)
            i += 2
        }
        path.lineTo(w + pad, lastY)
        path.lineTo(w + pad, h + pad)
        path.lineTo(-pad, h + pad)
        path.close()
        paint.shader = null
        paint.color = color
        paint.alpha = 255
        canvas.drawPath(path, paint)
    }

    companion object {
        const val FIRST = 17
        const val LAST = 20
        fun isMountain(preset: Int): Boolean = preset in FIRST..LAST
    }
}
