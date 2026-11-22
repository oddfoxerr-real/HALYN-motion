package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.SystemClock
import kotlin.math.cos
import kotlin.math.sin

/**
 * Curated fluid wallpaper collection; continuously evolving, no landscape shapes.
 *
 * GPU path: a compact procedural AGSL liquid field, rendered on a hardware Canvas.
 * Fallback: animated layered spline ribbons on a software Canvas or unsupported GPU.
 * Both paths are resolution-independent; the wallpaper animates without touch.
 */
class FluidMotionRenderer {
    data class Scene(val name: String, val description: String)
    companion object {
        const val FIRST = 176
        const val COUNT = 6
        const val LAST = FIRST + COUNT - 1
        val SCENES = listOf(
            Scene("Soft Current", "Slow, smooth streams of graphite liquid."),
            Scene("Satin Flow", "Broad silky folds sliding past one another."),
            Scene("Liquid Ink", "Floating layers of soft ink moving through water."),
            Scene("Deep Ripple", "Continuous concentric disturbances in a dark pool."),
            Scene("Quiet Vortex", "Gentle liquid rotation with a shifting centre."),
            Scene("Velvet Tide", "Rounded, rhythmic waves with soft depth.")
        )
        fun isFluid(id: Int): Boolean = id in FIRST..LAST
        fun description(id: Int): String? = if (isFluid(id)) SCENES[id - FIRST].description else null
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val path = Path()
    private var shader: RuntimeShader? = null
    private var shaderFailed = false
    private var effect: Int = -1
    private var effectStartMs: Long = 0L
    private var eventX = .5f
    private var eventY = .5f

    fun reset() {
        shader = null
        shaderFailed = false
    }

    /** Actions 0..5: Ripple, Swirl, Push, Brighten, Slow, Surge. */
    fun trigger(action: Int, x: Float, y: Float) {
        if (action !in 0..5) return
        effect = action
        effectStartMs = SystemClock.uptimeMillis()
        eventX = x.coerceIn(0f, 1f)
        eventY = y.coerceIn(0f, 1f)
    }

    private fun baseColors(state: MotionState): IntArray {
        val colors = intArrayOf(
            Color.rgb(16, 17, 20), Color.rgb(33, 35, 39),
            Color.rgb(61, 64, 69), Color.rgb(103, 107, 114)
        )
        if (state.fourColorsEnabled) {
            for (i in 0..3) {
                val tint = QuadColorSystem.color(state, i)
                colors[i] = Color.rgb(
                    (Color.red(colors[i]) * .50f + Color.red(tint) * .50f).toInt(),
                    (Color.green(colors[i]) * .50f + Color.green(tint) * .50f).toInt(),
                    (Color.blue(colors[i]) * .50f + Color.blue(tint) * .50f).toInt()
                )
            }
        }
        return colors
    }

    private fun renderFallback(canvas: Canvas, state: MotionState, seconds: Float,
                               colors: IntArray, variant: Int, age: Float) {
        val w = canvas.width.toFloat().coerceAtLeast(1f)
        val h = canvas.height.toFloat().coerceAtLeast(1f)
        paint.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(colors[0], colors[1], colors[0]), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        val speed = state.speed.coerceIn(.25f, 2f)
        val strength = state.intensity.coerceIn(.35f, 2.3f)
        val t = seconds * speed
        for (layer in 0 until 5) {
            val phase = t * (.43f + layer * .11f) * (if (variant == 4) -1f else 1f)
            val base = h * (.39f + layer * .13f)
            val amplitude = h * (.075f + layer * .016f) * strength
            path.reset()
            var i = 0
            while (i <= 36) {
                val x = (i / 36f) * w
                val px = x / w
                val y = base + sin(px * (6.5f + variant * .65f) + phase).toFloat() * amplitude +
                    cos(px * (12f - variant * .32f) - phase * .67f).toFloat() * amplitude * .31f +
                    (if (effect == 0 && age < 3f) sin(px * 19f - age * 11f).toFloat() * 17f * (1f - age / 3f) else 0f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                i++
            }
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
            paint.color = colors[(layer % 3) + 1]
            paint.alpha = (95 + layer * 25).coerceAtMost(235)
            canvas.drawPath(path, paint)
        }
        paint.alpha = 255
        paint.shader = null
    }

    fun draw(canvas: Canvas, state: MotionState, seconds: Float) {
        val variant = state.preset - FIRST
        if (variant !in 0 until COUNT) return
        val colors = baseColors(state)
        val age = (SystemClock.uptimeMillis() - effectStartMs) / 1000f
        val active = if (effect in 0..5 && age in 0f..3.2f) effect else -1

        if (canvas.isHardwareAccelerated && !shaderFailed) {
            try {
                val activeShader = shader ?: RuntimeShader(AGSL).also { shader = it }
                activeShader.setFloatUniform("u_resolution",
                    canvas.width.toFloat().coerceAtLeast(1f),
                    canvas.height.toFloat().coerceAtLeast(1f))
                activeShader.setFloatUniform("u_time", seconds)
                activeShader.setFloatUniform("u_variant", variant.toFloat())
                activeShader.setFloatUniform("u_speed", state.speed.coerceIn(.25f, 2f))
                activeShader.setFloatUniform("u_strength", state.intensity.coerceIn(.35f, 2.3f))
                activeShader.setFloatUniform("u_action", active.toFloat())
                activeShader.setFloatUniform("u_age", if (active >= 0) age else 9f)
                activeShader.setFloatUniform("u_point", eventX, eventY)
                val names = arrayOf("u_c0", "u_c1", "u_c2", "u_c3")
                for (i in 0..3) {
                    val c = colors[i]
                    activeShader.setFloatUniform(names[i],
                        Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f)
                }
                paint.shader = activeShader
                paint.alpha = 255
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
                paint.shader = null
                return
            } catch (_: IllegalArgumentException) {
                shaderFailed = true
                shader = null
                paint.shader = null
            } catch (_: RuntimeException) {
                shaderFailed = true
                shader = null
                paint.shader = null
            }
        }
        renderFallback(canvas, state, seconds, colors, variant, age)
    }

    private val AGSL = """
uniform float2 u_resolution;
uniform float u_time;
uniform float u_variant;
uniform float u_speed;
uniform float u_strength;
uniform float u_action;
uniform float u_age;
uniform float2 u_point;
uniform float3 u_c0;
uniform float3 u_c1;
uniform float3 u_c2;
uniform float3 u_c3;

float liquid(float2 p, float t) {
    float a = sin(p.x * 5.4 + sin(p.y * 2.4 + t * .73) + t);
    float b = sin(p.y * 7.1 - cos(p.x * 3.2 - t * .81) - t * .66);
    float c = cos((p.x + p.y) * 10.0 + sin(p.x * 7.5 - t) * 1.3 + t * .46);
    return a * .52 + b * .34 + c * .14;
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord / u_resolution;
    p.y = 1.0 - p.y;
    float aspect = u_resolution.x / u_resolution.y;
    float2 q = float2((p.x - .5) * aspect, p.y - .5);
    float t = u_time * u_speed;
    float warp = u_strength * .26;
    float angle = atan(q.y, q.x);
    float radius = length(q);

    // Six distinct, smooth liquid behaviors. Never a landscape silhouette.
    if (u_variant < .5) {
        q.x += sin(q.y * 7.2 + t * .64) * warp;
        q.y += cos(q.x * 5.5 - t * .53) * warp * .45;
    } else if (u_variant < 1.5) {
        q.x += sin(q.y * 9.7 - t * .45) * warp * .6;
        q.y += sin(q.x * 5.1 + t * .80) * warp;
    } else if (u_variant < 2.5) {
        q += float2(sin(q.y * 8.8 + t * .53), cos(q.x * 8.2 - t * .64)) * warp * .63;
    } else if (u_variant < 3.5) {
        q.x += sin(radius * 35.0 - t * 1.35) * warp * .18;
        q.y += cos(radius * 30.0 - t * 1.10) * warp * .18;
    } else if (u_variant < 4.5) {
        float spin = .32 * sin(t * .16) + radius * .45;
        float cs = cos(spin);
        float sn = sin(spin);
        q = float2(q.x * cs - q.y * sn, q.x * sn + q.y * cs);
        q += float2(sin(angle * 3.0 + t * .57), cos(angle * 3.0 - t * .47)) * warp * .24;
    } else {
        q.y += sin(q.x * 12.0 - t * .76) * warp * .43;
        q.x += sin(q.y * 5.6 + t * .44) * warp * .66;
    }

    float gesturePower = clamp(1.0 - u_age / 3.2, 0.0, 1.0);
    float2 gesture = float2((u_point.x - .5) * aspect, .5 - u_point.y);
    float d = distance(q, gesture);
    if (u_action < .5 && u_action > -.5) {
        float ring = sin(d * 40.0 - u_age * 13.0);
        q += normalize(q - gesture + .0001) * ring * gesturePower * .055;
    } else if (u_action > .5 && u_action < 1.5) {
        float a = gesturePower * .6 * exp(-d * 3.0);
        q += float2(-(q.y - gesture.y), (q.x - gesture.x)) * a;
    } else if (u_action > 1.5 && u_action < 2.5) {
        q.x += gesturePower * .28 * exp(-d * 4.0);
    } else if (u_action > 4.5) {
        q += sin(float2(q.y, q.x) * 16.0 + u_age * 8.0) * .16 * gesturePower;
    }

    float v = liquid(q * 2.1, t * .72);
    float folds = liquid(q * 1.22 + float2(v * .22, -v * .16), -t * .31);
    float bands = sin(q.y * 11.0 + v * 2.6 + folds + t * .23);
    float value = smoothstep(-.80, .88, v * .78 + folds * .25 + bands * .25);

    float3 col = mix(u_c0, u_c1, smoothstep(.05, .56, value));
    col = mix(col, u_c2, smoothstep(.42, .84, value) * .78);
    float softHighlight = pow(smoothstep(.50, .95, value), 2.5);
    col = mix(col, u_c3, softHighlight * .44);

    if (u_action > 2.5 && u_action < 3.5) {
        col = mix(col, u_c3, gesturePower * exp(-d * 2.3) * .52);
    } else if (u_action > 3.5 && u_action < 4.5) {
        col = mix(col, u_c0, gesturePower * .62);
    }

    // Gentle vignette controls OLED glare without killing the flow.
    float edge = smoothstep(.06, .70, length(q * float2(.9, .58)));
    col *= 1.0 - edge * .17;
    return half4(half3(col), 1.0);
}
"""
}
