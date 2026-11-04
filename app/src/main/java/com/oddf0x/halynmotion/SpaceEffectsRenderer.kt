package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Cinematic layers distinct from the factual star positions. "Survey" uses no
 * fictional objects; "Nebula" adds procedural atmospheric light; "Expedition"
 * adds a clearly artistic destination world and richer ambient effects.
 *
 * The warp is deliberately visible even without a working RuntimeShader:
 * the streak tunnel, travel HUD and arrival pulse use Android Canvas.
 */
class SpaceEffectsRenderer {
    // Lazy creation is essential: the shader source property is initialized
    // later in the class. Reading it eagerly would produce a null source.
    private val clouds: RuntimeShader? by lazy {
        try { RuntimeShader(NEBULA_SHADER) }
        catch (_: RuntimeException) { null }
    }
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val typePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        setShadowLayer(5f, 0f, 2f, Color.BLACK)
    }
    private val stars = Array(96) { i ->
        val a = fract(sin(i * 127.71) * 43758.54)
        val b = fract(sin((i + 3) * 91.51) * 27391.51)
        val c = fract(sin((i + 19) * 53.29) * 16231.79)
        floatArrayOf(a * 2f * PI.toFloat(), .15f + b * .75f, c)
    }
    private var lastFinishedLy = 0.0
    private var arrivalAtMs = 0L
    private var activeWarp = false
    // A defective GPU driver must not blank the live wallpaper forever.
    private var shaderDisabled = false

    fun drawBackground(canvas: Canvas, state: MotionState, elapsedSeconds: Float, pose: StarPose) {
        val w = canvas.width.coerceAtLeast(1).toFloat()
        val h = canvas.height.coerceAtLeast(1).toFloat()
        val accent = palette(state)
        if (state.starEffects == 0 || pose.skyAligned) {
            canvas.drawColor(Color.rgb(3, 6, 14))
            return
        }
        val shader = if (shaderDisabled || !canvas.isHardwareAccelerated) null else clouds
        var rendered = false
        if (shader != null) {
            try {
                shader.setFloatUniform("u_resolution", w, h)
                shader.setFloatUniform("u_time", elapsedSeconds)
                shader.setFloatUniform("u_mode", state.starEffects.toFloat())
                shader.setFloatUniform("u_warp", 1f - pose.warpProgress)
                shader.setFloatUniform("u_color",
                    Color.red(accent) / 255f,
                    Color.green(accent) / 255f,
                    Color.blue(accent) / 255f)
                cloudPaint.shader = shader
                canvas.drawRect(0f, 0f, w, h, cloudPaint)
                rendered = true
            } catch (_: RuntimeException) {
                // Some vendor AGSL implementations accept a shader but fail
                // while binding uniforms or drawing. Use a stable fallback.
                shaderDisabled = true
            } finally {
                cloudPaint.shader = null
            }
        }
        if (!rendered) {
            cloudPaint.shader = LinearGradient(0f, 0f, w, h,
                intArrayOf(Color.rgb(8, 17, 34), Color.rgb(18, 9, 34), Color.rgb(2, 4, 14)),
                null, Shader.TileMode.CLAMP)
            try {
                canvas.drawRect(0f, 0f, w, h, cloudPaint)
            } finally {
                cloudPaint.shader = null
            }
        }
        // Fictional planetary systems are rendered exclusively in the actual
        // catalog-star coordinate system by StarWindowRenderer. A screen-fixed
        // planet here would falsely appear to exist at every destination.
    }

    fun drawOverlay(canvas: Canvas, state: MotionState, pose: StarPose) {
        val w = canvas.width.toFloat().coerceAtLeast(1f)
        val h = canvas.height.toFloat().coerceAtLeast(1f)
        val size = min(w, h)
        val progress = pose.warpProgress.coerceIn(0f, 1f)
        val now = SystemClock.uptimeMillis()
        val accent = palette(state)

        if (progress < 1f) {
            activeWarp = true
            val strength = (.14f + .86f * sin(progress * PI.toFloat()).coerceAtLeast(0f).pow(.57f))
                .coerceIn(0f, 1f)
            val centerX = w * .5f
            val centerY = h * .5f
            val radius = max(w, h) * 1.15f
            // Every line has a stable seed; only the phase advances. This
            // avoids creating a new random pattern at each animation frame.
            for (i in stars.indices) {
                val s = stars[i]
                val angle = s[0]
                val speed = .19f + s[2] * .42f
                val phase = fract((s[1] + progress * (1.3f + speed)).toDouble())
                val inner = (phase * phase) * radius
                val outer = inner + radius * (.10f + phase * .23f) * strength
                val dx = cos(angle)
                val dy = sin(angle)
                val alpha = (strength * (75f + 155f * phase)).toInt().coerceIn(0, 235)
                stroke.color = if (i % 7 == 0) Color.WHITE else accent
                stroke.alpha = alpha
                stroke.strokeWidth = max(1.25f, size * (.001f + phase * .0025f))
                stroke.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(centerX + dx * inner, centerY + dy * inner,
                    centerX + dx * outer, centerY + dy * outer, stroke)
            }
            // Focus ring and central portal keep movement visually readable
            // when the background starfield barely changes at real distances.
            stroke.color = accent
            stroke.alpha = (strength * 165f).toInt().coerceIn(0, 200)
            stroke.strokeWidth = max(1.4f, size * .003f)
            val ringRadius = size * (.08f + progress * .27f)
            canvas.drawCircle(centerX, centerY, ringRadius, stroke)

            typePaint.textSize = max(13f, size * .036f)
            typePaint.color = Color.WHITE
            typePaint.alpha = (strength * 230).toInt().coerceIn(0, 255)
            val caption = if (pose.destinationName.isNotEmpty() &&
                pose.destinationName != "Deep space")
                "APPROACHING  ·  ${pose.destinationName.uppercase(java.util.Locale.US)}"
            else "HYPERSPACE  ·  1 LIGHT-YEAR"
            canvas.drawText(caption, centerX, h * .19f, typePaint)
        } else {
            if (activeWarp) {
                activeWarp = false
                arrivalAtMs = now
                lastFinishedLy = pose.travelLy
            }
            val age = (now - arrivalAtMs) / 1000f
            if (arrivalAtMs != 0L && age in 0f..2.2f) {
                val opacity = (1f - age / 2.2f).coerceIn(0f, 1f)
                stroke.color = accent
                stroke.alpha = (130 * opacity).toInt().coerceIn(0, 180)
                stroke.strokeWidth = max(1f, size * .002f)
                canvas.drawCircle(w * .5f, h * .5f,
                    size * (.04f + .24f * (age / 2.2f)), stroke)
                typePaint.textSize = max(14f, size * .039f)
                typePaint.color = Color.WHITE
                typePaint.alpha = (230 * opacity).toInt().coerceIn(0, 255)
                canvas.drawText("WARP COMPLETE", w * .5f, h * .22f, typePaint)
            }
        }

        // HUD is only for the space-exploration scene; no location is shared.
        if (state.starEffects >= 1 && !pose.skyAligned) {
            typePaint.textSize = max(12f, size * .031f)
            typePaint.color = Color.rgb(215, 221, 237)
            typePaint.alpha = 180
            val ly = String.format(java.util.Locale.US, "%.2f ly", pose.travelLy)
            val status = if (pose.destinationName.isNotBlank() &&
                pose.destinationName != "Deep space")
                "DESTINATION: ${pose.destinationName.uppercase(java.util.Locale.US)}  ·  $ly FROM ORIGIN"
            else "DISTANCE FROM ORIGIN  ·  $ly"
            canvas.drawText(status, w * .5f, h * .88f, typePaint)
        }
    }

    private fun drawFictionalWorld(canvas: Canvas, state: MotionState, time: Float, pose: StarPose) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val size = min(w, h)
        val shift = sin(time * .065f + pose.travelLy.toFloat() * .3f)
        val cx = w * (.69f + .026f * shift)
        val cy = h * .43f
        val radius = size * .205f
        val accent = palette(state)
        val shadow = Color.rgb(10, 16, 36)
        val illuminated = Color.rgb(
            ((Color.red(accent) * .68f + 40f).toInt()).coerceIn(0, 255),
            ((Color.green(accent) * .72f + 26f).toInt()).coerceIn(0, 255),
            ((Color.blue(accent) * .8f + 45f).toInt()).coerceIn(0, 255)
        )

        fxPaint.shader = RadialGradient(cx - radius * .28f, cy - radius * .33f,
            radius * 3.0f, intArrayOf(
                Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.argb(10, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT
            ), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 3f, fxPaint)
        fxPaint.shader = RadialGradient(cx - radius * .36f, cy - radius * .47f,
            radius * 1.65f,
            intArrayOf(illuminated, Color.rgb(28, 50, 88), shadow),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, fxPaint)
        fxPaint.shader = null

        stroke.color = accent
        stroke.alpha = 85
        stroke.strokeWidth = max(1f, radius * .018f)
        canvas.drawCircle(cx, cy, radius * 1.03f, stroke)

        canvas.save()
        canvas.rotate(-23f, cx, cy)
        stroke.color = Color.rgb(177, 186, 219)
        stroke.alpha = 64
        stroke.strokeWidth = radius * .09f
        canvas.drawOval(cx - radius * 1.47f, cy - radius * .46f,
            cx + radius * 1.47f, cy + radius * .46f, stroke)
        stroke.alpha = 148
        stroke.strokeWidth = max(1.5f, radius * .025f)
        canvas.drawOval(cx - radius * 1.59f, cy - radius * .55f,
            cx + radius * 1.59f, cy + radius * .55f, stroke)
        canvas.restore()

        typePaint.textSize = max(10f, size * .024f)
        typePaint.color = Color.rgb(222, 231, 250)
        typePaint.alpha = 210
        canvas.drawText("PROCEDURAL WORLD  ·  ARTISTIC", cx, cy + radius * 1.5f, typePaint)
    }

    private fun fract(value: Double): Float = (value - kotlin.math.floor(value)).toFloat()
    private fun palette(state: MotionState): Int = if (state.palette == 5) state.customColor
        else HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0, 4)]

    private val NEBULA_SHADER = """
uniform float2 u_resolution;
uniform float u_time;
uniform float u_mode;
uniform float u_warp;
uniform float3 u_color;
float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.54);
}
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 smoothF = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1,0)), smoothF.x),
        mix(hash(i + float2(0,1)), hash(i + float2(1,1)), smoothF.x), smoothF.y);
}
half4 main(float2 frag) {
    float2 uv = frag / u_resolution;
    float aspect = u_resolution.x / u_resolution.y;
    float2 p = float2((uv.x - .5) * aspect, uv.y - .5);
    float t = u_time * .022;
    float2 flow = p * 2.5 + float2(t, -t * .35);
    float n = noise(flow) * .55 + noise(flow * 2.15 + float2(7.4, -1.9)) * .3
        + noise(flow * 4.8 - float2(3.1, 5.6)) * .15;
    float cloud = pow(max(0.0, n * 1.8 - .47), 2.1);
    float ribbon = exp(-pow((p.y - .16 * sin(p.x * 3.1 + t)) * 3.0, 2.0));
    float3 base = float3(.005, .008, .022);
    float3 cold = float3(.12, .20, .41);
    float strength = (.34 + u_mode * .25);
    float3 col = base + cold * cloud * strength
        + u_color * (cloud * ribbon * .42 + ribbon * .018) * strength;
    float blue = exp(-length(p - float2(-.38, -.15)) * 3.0) * .027;
    col += float3(.16, .34, .53) * blue;
    col += u_color * u_warp * .12;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
""".trimIndent()
}
