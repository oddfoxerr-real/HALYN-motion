package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader

/**
 * Per-wallpaper four-color customization.
 * Each choice is an opaque RGB swatch persisted against the wallpaper preset ID.
 * Existing wallpapers retain their original palette until explicitly enabled.
 */
object QuadColorSystem {
    val DEFAULTS = intArrayOf(
        Color.rgb(156, 197, 255),
        Color.rgb(190, 164, 250),
        Color.rgb(255, 189, 189),
        Color.rgb(136, 215, 195)
    )

    fun colors(state: MotionState): IntArray =
        intArrayOf(state.colorOne, state.colorTwo, state.colorThree, state.colorFour)

    fun color(state: MotionState, index: Int): Int = when (index) {
        0 -> state.colorOne
        1 -> state.colorTwo
        2 -> state.colorThree
        else -> state.colorFour
    }

    fun setShaderUniforms(shader: RuntimeShader, state: MotionState) {
        shader.setFloatUniform("u_quadEnabled", if (state.fourColorsEnabled) 1f else 0f)
        for (slot in 0..3) {
            val color = color(state, slot)
            val key = when (slot) {
                0 -> "u_quadOne"
                1 -> "u_quadTwo"
                2 -> "u_quadThree"
                else -> "u_quadFour"
            }
            shader.setFloatUniform(
                key,
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f
            )
        }
    }

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private var cachedShader: LinearGradient? = null
    private var lastWidth = -1
    private var lastHeight = -1
    private var lastColors: IntArray? = null

    /**
     * Used only for non-AGSL wallpapers such as imported photographs,
     * Star Window and layered mountains. The overlay is optional and subtle,
     * so the original artwork remains recognizable.
     */
    fun drawOverlay(canvas: Canvas, state: MotionState, opacity: Float) {
        if (!state.fourColorsEnabled) return
        val width = canvas.width.coerceAtLeast(1)
        val height = canvas.height.coerceAtLeast(1)
        val chosen = colors(state)
        if (cachedShader == null || width != lastWidth || height != lastHeight ||
            !chosen.contentEquals(lastColors)) {
            cachedShader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                chosen,
                floatArrayOf(0f, .33f, .66f, 1f),
                Shader.TileMode.CLAMP
            )
            lastWidth = width
            lastHeight = height
            lastColors = chosen
        }
        overlayPaint.shader = cachedShader
        overlayPaint.alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        overlayPaint.shader = null
        overlayPaint.alpha = 255
    }
}
