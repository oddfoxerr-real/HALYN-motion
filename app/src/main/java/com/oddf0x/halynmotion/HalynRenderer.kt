package com.oddf0x.halynmotion

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build

/**
 * Settings shared between the live wallpaper service and the studio.
 * All values are sanitized when read so old preferences cannot break the shader.
 */
data class MotionState(
    val preset: Int = 0,
    val palette: Int = 0,
    val customColor: Int = Color.rgb(185, 174, 255),
    val intensity: Float = 1.0f,
    val speed: Float = 1.0f,
    val fps: Int = 30,
    val touch: Boolean = true,
    val parallax: Boolean = true,
    val clockStyle: Int = 0,
    val clockPosition: Int = 1,
    val clock24h: Boolean = true,
    val clockDate: Boolean = true,
    val clockSeconds: Boolean = true,
    val clockAccent: Boolean = false,
    val starSensitivity: Float = .55f,
    val starDensity: Int = 1,
    val starLines: Boolean = true,
    val starEffects: Int = 2,
    val skyAligned: Boolean = false,
    val skyLatitude: Double = 0.0,
    val skyLongitude: Double = 0.0,
    val skyFixTime: Long = 0L
)

class MotionPreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("halyn_motion_settings", Context.MODE_PRIVATE)

    val raw: SharedPreferences get() = preferences

    fun get(): MotionState {
        fun float(key: String, default: Float, min: Float, max: Float): Float {
            val result = preferences.getFloat(key, default)
            return if (result.isFinite()) result.coerceIn(min, max) else default
        }
        return MotionState(
            preset = preferences.getInt("preset", 0).coerceIn(0, 12),
            palette = preferences.getInt("palette", 0).coerceIn(0, 5),
            customColor = preferences.getInt("custom_color", Color.rgb(185, 174, 255)) or -0x1000000,
            intensity = float("intensity", 1f, .35f, 2.3f),
            speed = float("speed", 1f, .25f, 2f),
            fps = when (preferences.getInt("fps", 30)) {
                60 -> 60
                45 -> 45
                else -> 30
            },
            touch = preferences.getBoolean("touch", true),
            parallax = preferences.getBoolean("parallax", true),
            clockStyle = preferences.getInt("clock_style", 0).coerceIn(0, 5),
            clockPosition = preferences.getInt("clock_position", 1).coerceIn(0, 2),
            clock24h = preferences.getBoolean("clock_24h", true),
            clockDate = preferences.getBoolean("clock_date", true),
            clockSeconds = preferences.getBoolean("clock_seconds", true),
            clockAccent = preferences.getBoolean("clock_accent", false),
            starSensitivity = float("star_sensitivity", .55f, .15f, 1.4f),
            starDensity = preferences.getInt("star_density", 1).coerceIn(0, 2),
            starLines = preferences.getBoolean("star_lines", true),
            starEffects = preferences.getInt("star_effects", 2).coerceIn(0, 2),
            skyAligned = preferences.getBoolean("sky_aligned", false) && preferences.contains("sky_latitude"),
            skyLatitude = Double.fromBits(preferences.getLong("sky_latitude", 0L)).takeIf { it.isFinite() && it in -90.0..90.0 } ?: 0.0,
            skyLongitude = Double.fromBits(preferences.getLong("sky_longitude", 0L)).takeIf { it.isFinite() && it in -180.0..180.0 } ?: 0.0,
            skyFixTime = preferences.getLong("sky_fix_time", 0L)
        )
    }

    fun setPreset(value: Int) = preferences.edit().putInt("preset", value.coerceIn(0, 12)).apply()
    fun setPalette(value: Int) = preferences.edit().putInt("palette", value.coerceIn(0, 5)).apply()
    fun setCustomColor(color: Int) = preferences.edit()
        .putInt("custom_color", color or -0x1000000)
        .putInt("palette", 5)
        .apply()
    fun setIntensity(value: Float) = preferences.edit().putFloat("intensity", value.coerceIn(.35f, 2.3f)).apply()
    fun setSpeed(value: Float) = preferences.edit().putFloat("speed", value.coerceIn(.25f, 2f)).apply()
    fun setFps(value: Int) = preferences.edit().putInt("fps", value).apply()
    fun setTouch(value: Boolean) = preferences.edit().putBoolean("touch", value).apply()
    fun setParallax(value: Boolean) = preferences.edit().putBoolean("parallax", value).apply()
    fun setClockStyle(value: Int) = preferences.edit().putInt("clock_style", value.coerceIn(0, 5)).apply()
    fun setClockPosition(value: Int) = preferences.edit().putInt("clock_position", value.coerceIn(0, 2)).apply()
    fun setClock24h(value: Boolean) = preferences.edit().putBoolean("clock_24h", value).apply()
    fun setClockDate(value: Boolean) = preferences.edit().putBoolean("clock_date", value).apply()
    fun setClockSeconds(value: Boolean) = preferences.edit().putBoolean("clock_seconds", value).apply()
    fun setClockAccent(value: Boolean) = preferences.edit().putBoolean("clock_accent", value).apply()
    fun setStarSensitivity(value: Float) = preferences.edit().putFloat("star_sensitivity", value.coerceIn(.15f, 1.4f)).apply()
    fun setStarDensity(value: Int) = preferences.edit().putInt("star_density", value.coerceIn(0, 2)).apply()
    fun setStarLines(value: Boolean) = preferences.edit().putBoolean("star_lines", value).apply()
    fun setStarEffects(value: Int) = preferences.edit().putInt("star_effects", value.coerceIn(0, 2)).apply()
    fun setSkyAligned(value: Boolean) = preferences.edit().putBoolean("sky_aligned", value).apply()
    fun saveSkyFix(latitude: Double, longitude: Double, timestampMs: Long) {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        preferences.edit()
            .putLong("sky_latitude", latitude.toBits())
            .putLong("sky_longitude", longitude.toBits())
            .putLong("sky_fix_time", timestampMs)
            .putBoolean("sky_aligned", true).apply()
    }
    fun clearSkyFix() = preferences.edit()
        .putBoolean("sky_aligned", false)
        .remove("sky_latitude").remove("sky_longitude").remove("sky_fix_time").apply()
    fun requestWarp() = requestWarpToward(.5f, .5f, .46f)
    fun requestWarpToward(nx: Float, ny: Float, aspect: Float) {
        val next = preferences.getInt("star_warp_token", 0) + 1
        preferences.edit()
            .putFloat("star_warp_x", nx.coerceIn(0f, 1f))
            .putFloat("star_warp_y", ny.coerceIn(0f, 1f))
            .putFloat("star_warp_aspect", aspect.coerceIn(.2f, 2.0f))
            .putInt("star_warp_token", next).apply()
    }
    fun warpCoordinates(): FloatArray = floatArrayOf(
        preferences.getFloat("star_warp_x", .5f),
        preferences.getFloat("star_warp_y", .5f),
        preferences.getFloat("star_warp_aspect", .46f)
    )
    fun requestDestination(name: String) {
        if (name.isBlank()) return
        val next = preferences.getInt("star_destination_token", 0) + 1
        preferences.edit()
            .putString("star_destination_name", name)
            .putInt("star_destination_token", next).apply()
    }
    fun destinationName(): String = preferences.getString("star_destination_name", "") ?: ""

    fun recenterStarWindow() {
        val token = preferences.getInt("star_recenter_token", 0)
        preferences.edit().putInt("star_recenter_token", token + 1).apply()
    }
}

/**
 * GPU-only procedural wallpaper; there is no video decoder, network dependency,
 * microphone, media-projection permission or continuously running background worker.
 *
 * Android 13+ AGSL produces a shader directly on a hardware-accelerated Canvas.
 * All six scenes have separate silhouettes and motion formulas.
 */
class HalynRenderer(private val context: Context) {
    private val starRenderer by lazy { StarWindowRenderer(context) }
    private val shader: RuntimeShader? = try {
        if (Build.VERSION.SDK_INT >= 33) RuntimeShader(AGSL) else null
    } catch (_: IllegalArgumentException) {
        null
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
        if (this@HalynRenderer.shader != null) this.shader = this@HalynRenderer.shader else color = Color.rgb(9, 12, 22)
    }

    fun draw(
        canvas: Canvas,
        state: MotionState,
        elapsedSeconds: Float,
        touchX: Float = .5f,
        touchY: Float = .5f,
        offsetX: Float = .5f,
        starPose: StarPose? = null
    ) {
        if (state.preset == StarWindowRenderer.SCENE_INDEX) {
            starRenderer.draw(canvas, state, elapsedSeconds, starPose ?: StarPose())
            return
        }
        val width = canvas.width.coerceAtLeast(1).toFloat()
        val height = canvas.height.coerceAtLeast(1).toFloat()
        canvas.drawColor(Color.rgb(5, 7, 13))
        val current = shader
        if (current != null && canvas.isHardwareAccelerated) {
            current.setFloatUniform("u_resolution", width, height)
            current.setFloatUniform("u_time", elapsedSeconds)
            current.setFloatUniform("u_variant", state.preset.toFloat())
            current.setFloatUniform("u_palette", state.palette.toFloat())
            current.setFloatUniform("u_customColor",
                Color.red(state.customColor) / 255f,
                Color.green(state.customColor) / 255f,
                Color.blue(state.customColor) / 255f
            )
            current.setFloatUniform("u_intensity", state.intensity)
            current.setFloatUniform("u_speed", state.speed)
            current.setFloatUniform(
                "u_touch",
                if (state.touch) touchX.coerceIn(0f, 1f) else .5f,
                if (state.touch) touchY.coerceIn(0f, 1f) else .5f
            )
            current.setFloatUniform("u_offset", if (state.parallax) offsetX else .5f)
        }
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    companion object {
        val PRESETS = listOf(
            "Ion Veil", "Liquid Chrome", "Nebula Drift",
            "Electric Silk", "Solar Bloom", "Prism Tide",
            "Silk Reactor", "Glass Orbit", "Magnetic Dust",
            "Midnight Horizon", "Chromatic Flow", "Circuit Pulse",
            "Star Window"
        )
        val PALETTES = listOf(
            "Arctic", "Ultraviolet", "Ember", "Rose", "Verdant"
        )
        val PALETTE_COLORS = intArrayOf(
            Color.rgb(100, 235, 248),
            Color.rgb(182, 144, 255),
            Color.rgb(255, 169, 97),
            Color.rgb(255, 133, 188),
            Color.rgb(120, 244, 172)
        )

        private val AGSL = """
uniform float2 u_resolution;
uniform float2 u_touch;
uniform float u_time;
uniform float u_variant;
uniform float u_palette;
uniform float3 u_customColor;
uniform float u_intensity;
uniform float u_speed;
uniform float u_offset;

float noise21(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}
half4 main(float2 frag) {
    float2 uv = frag / u_resolution;
    float aspect = u_resolution.x / u_resolution.y;
    float2 p = float2((uv.x - 0.5) * aspect, uv.y - 0.5);
    p.x -= (u_offset - 0.5) * 0.10;
    float2 touch = float2((u_touch.x - 0.5) * aspect, u_touch.y - 0.5);
    float nearTouch = exp(-13.0 * length(p - touch));
    float t = u_time * (0.35 + u_speed * 0.9);
    float k = u_intensity;
    float3 accent = float3(0.32, 0.91, 1.0);
    if (u_palette > 0.5 && u_palette < 1.5) {
        accent = float3(0.68, 0.42, 1.0);
    } else if (u_palette > 1.5 && u_palette < 2.5) {
        accent = float3(1.0, 0.48, 0.22);
    } else if (u_palette > 2.5 && u_palette < 3.5) {
        accent = float3(1.0, 0.35, 0.66);
    } else if (u_palette > 3.5) {
        accent = float3(0.30, 0.96, 0.64);
    }
    if (u_palette > 4.5) {
        accent = u_customColor;
    }
    float3 companion = accent.zxy * 0.74 + accent * 0.26;
    float3 col = float3(0.014, 0.018, 0.038);
    float r = length(p);
    float v = 0.0;
    float glow = 0.0;
    if (u_variant < 0.5) {
        // Ion Veil: tall, translucent aurora ribbons with varying drift.
        float a = p.x * 5.8 + sin(p.y * 3.3 + t * 0.28) * 1.5;
        float ribbon1 = sin(a + sin(p.y * 6.0 - t * 0.5) * 1.3 + t * .7);
        float ribbon2 = cos(a * .75 - t * .42 + p.y * 3.2);
        float spine = p.x - sin(p.y * 3.8 + t * .55) * .23;
        v = exp(-abs(spine - ribbon1 * .095) * 9.0) * (.58 + .42 * sin(p.y * 5.0 + t * .35));
        glow = exp(-abs(spine - ribbon2 * .13 - .14) * 5.0) * .66;
    } else if (u_variant < 1.5) {
        // Liquid Chrome: molten reflective contour lines.
        float2 q = p;
        q.x += sin(q.y * 6.0 + t * .6) * .12;
        q.y += cos(q.x * 7.0 - t * .5) * .1;
        float wave = sin(19.0 * length(q + float2(.04, -.06)) - t * 1.8
            + sin(q.x * 11.0 + t) * 1.7);
        v = pow(abs(wave), 15.0) * .95;
        glow = exp(-abs(wave) * 1.9) * .38 + exp(-6.0 * length(q)) * .2;
    } else if (u_variant < 2.5) {
        // Nebula Drift: clouds and floating stars, no visible hard circles.
        float2 q = p * 3.5;
        float n = sin(q.x * 2.3 + t * .3 + sin(q.y * 1.3))
            * cos(q.y * 2.8 - t * .23 + cos(q.x * 1.7));
        v = pow(max(0.0, n * .5 + .5), 3.8) * .78;
        float stars = noise21(floor(uv * float2(115.0, 220.0)));
        glow = smoothstep(.991, 1.0, stars) * (.28 + .72 * sin(t * .8 + stars * 70.0));
        glow += exp(-length(q - float2(sin(t * .2), cos(t * .14))) * .55) * .2;
    } else if (u_variant < 3.5) {
        // Electric Silk: separate folded strands, vivid at their centers.
        float bend = p.x * 11.0 + sin(p.y * 6.0 - t * .8) * 2.3;
        float filament = sin(bend + cos(p.y * 13.0 + t * .4) * 1.3);
        float center = abs(filament);
        v = pow(center, 20.0) * .9;
        glow = pow(center, 3.0) * .38;
        v *= (1.0 - smoothstep(.08, .48, abs(p.x)));
    } else if (u_variant < 4.5) {
        // Solar Bloom: a radiating luminous core and moving petals.
        float angle = atan(p.y, p.x);
        float petals = sin(angle * 9.0 + sin(r * 18.0 - t) * 1.4 + t * .3);
        float core = exp(-11.0 * r);
        float rays = pow(max(0.0, petals), 7.0) * exp(-2.8 * r);
        v = core * 1.3 + rays * .75;
        glow = exp(-3.0 * r) * .33;
    } else if (u_variant < 5.5) {
        // Prism Tide: layered interference and drifting chromatic bands.
        float flow = p.y * 12.0 + sin(p.x * 6.0 + t * .36) * 2.1
            + sin(p.x * 15.0 - t * .55) * .5;
        float line = sin(flow + t * .8);
        v = pow(max(0.0, line), 12.0) * .87;
        glow = pow(abs(line), 3.0) * .36;
        v += .16 * (sin(p.x * 18.0 + p.y * 7.0 - t * .6) * .5 + .5);
    } else if (u_variant < 6.5) {
        // Silk Reactor: polished, folded optical fabric; multiple reflective folds.
        float2 q = p;
        q.x += .15 * sin(q.y * 9.0 + t * .7) + .07 * sin(q.y * 17.0 - t * .32);
        float fold = sin(q.x * 27.0 + sin(q.y * 7.5 - t * .5) * 2.8);
        float shimmer = pow(abs(fold), 20.0);
        float secondaryFold = sin(q.x * 13.0 - q.y * 10.0 + t * .9);
        float envelope = exp(-4.2 * abs(q.x + .1 * sin(q.y * 4.0 + t)));
        v = (shimmer * 1.05 + .18 * pow(abs(secondaryFold), 11.0)) * envelope;
        glow = envelope * (.25 + .3 * pow(abs(fold), 3.0));
    } else if (u_variant < 7.5) {
        // Glass Orbit: translucent gyroscopic glass rings with sharp edge highlights.
        float2 q = p;
        q.x += .045 * sin(t * .42);
        float angle = atan(q.y, q.x);
        float radius = length(q);
        float ring1 = abs(radius - (.265 + .055 * sin(2.0 * angle - t * .45)));
        float ring2 = abs(radius - (.345 + .040 * cos(3.0 * angle + t * .35)));
        float rim1 = exp(-85.0 * ring1);
        float rim2 = exp(-100.0 * ring2);
        float specular = pow(max(0.0, cos(angle * 2.5 + t * .8)), 15.0);
        v = (rim1 * 1.1 + rim2 * .66) * (.55 + .65 * specular);
        glow = exp(-14.0 * ring1) * .21 + exp(-14.0 * ring2) * .20
            + exp(-8.0 * radius) * .12;
    } else if (u_variant < 8.5) {
        // Magnetic Dust: sparse points orbiting curved invisible field-lines.
        float2 q = p * 25.0;
        q.x += 2.0 * sin(q.y * .11 + t * .38);
        q.y += 1.5 * sin(q.x * .09 - t * .28);
        float2 cell = floor(q);
        float2 local = fract(q) - .5;
        float seed = noise21(cell);
        float2 offset = float2(noise21(cell + float2(9.0, 3.0)),
            noise21(cell + float2(1.0, 21.0))) - .5;
        float dotDistance = length(local - offset * .65);
        float spark = (1.0 - smoothstep(.08, .19, dotDistance))
            * smoothstep(.67, .99, seed);
        float field = sin(12.0 * length(p) - t * .8 + sin(atan(p.y, p.x) * 3.0));
        v = spark * (.4 + .6 * max(0.0, field));
        glow = exp(-abs(field) * 8.0) * exp(-3.8 * length(p)) * .17;
    } else if (u_variant < 9.5) {
        // Midnight Horizon: luminous celestial body, distant moving cloud bands.
        float horizon = .125 + .024 * sin(p.x * 7.5 + t * .12)
            + .010 * sin(p.x * 19.0 - t * .18);
        float atmosphere = exp(-18.0 * abs(p.y - horizon));
        float2 moon = p - float2(.16 + .025 * sin(t * .12), -.165);
        float moonDistance = length(moon);
        float moonDisc = 1.0 - smoothstep(.085, .095, moonDistance);
        float cloud = sin(p.x * 10.0 + p.y * 2.2 + t * .13)
            * sin(p.y * 25.0 + t * .15);
        float mist = exp(-13.0 * abs(p.y - horizon - .085))
            * (.55 + .45 * cloud);
        float star = noise21(floor(float2(p.x * 210.0, p.y * 320.0)));
        float starfield = smoothstep(.996, 1.0, star)
            * (1.0 - smoothstep(-.15, .1, p.y));
        v = moonDisc * .75 + starfield * .7;
        glow = atmosphere * .33 + exp(-11.0 * moonDistance) * .25
            + max(0.0, mist) * .22;
    } else if (u_variant < 10.5) {
        // Chromatic Flow: slow coherent flowing contours, not a fluid solver.
        float2 q = p * 3.8;
        q.x += .65 * sin(q.y * 1.4 + t * .28)
            + .3 * sin(q.y * 3.1 - t * .19);
        q.y += .43 * cos(q.x * 1.8 - t * .25);
        float contour = sin(q.x * 3.5 + q.y * 2.1 + .7 * sin(q.y * 3.3 + t));
        float contrast = pow(max(0.0, contour), 6.0);
        float bloom = sin(q.x * .7 - q.y * .9 + t * .14);
        v = contrast * .72 + pow(max(0.0, bloom), 8.0) * .16;
        glow = pow(max(0.0, contour), 2.0) * .36
            * (1.0 - smoothstep(.25, .85, length(p)));
    } else {
        // Circuit Pulse: clean technical trace geometry, rhythmic crossings.
        float2 q = p * 11.0;
        q.x += .12 * sin(t * .5 + floor(q.y) * 3.0);
        float2 g = abs(fract(q) - .5);
        float trace = 1.0 - smoothstep(.015, .06, min(g.x, g.y));
        float pulse = .4 + .6 * pow(max(0.0, sin(q.x * .38 + q.y * .41 - t)), 5.0);
        float dot = exp(-18.0 * length(g));
        float window = exp(-3.8 * length(p));
        v = (trace * .31 + dot * .8) * pulse * window;
        glow = trace * window * .075 + exp(-7.0 * length(p)) * .09;
    }
    v = max(0.0, v) * k;
    glow = max(0.0, glow) * k;
    float depth = .07 * (1.0 - smoothstep(0.0, .8, r));
    col += accent * (v * .8 + glow * .24 + depth);
    col += companion * (v * v * .28 + glow * .18);
    col += accent * nearTouch * .095 * k;
    col += float3(.02, .025, .045) * (1.0 - smoothstep(.15, .9, r));
    col = col / (1.0 + col * .45);
    col = pow(max(col, float3(0.0)), float3(.9));
    return half4(half3(col), 1.0);
}
""".trimIndent()
    }
}
