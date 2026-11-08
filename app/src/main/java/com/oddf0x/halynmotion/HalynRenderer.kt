package com.oddf0x.halynmotion

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color

/**
 * Settings shared between the live wallpaper service and the studio.
 * All values are sanitized when read so old preferences cannot break the shader.
 */
data class MotionState(
    val preset: Int = SmoothWallpaperRenderer.FIRST,
    val palette: Int = 0,
    val customColor: Int = Color.rgb(185, 174, 255),
    val intensity: Float = 1.0f,
    val speed: Float = 1.0f,
    val fps: Int = 30,
    val shaderQuality: Int = 1,
    val shadesDepth: Float = 1.0f,
    val shadesGloss: Float = 1.0f,
    val shadesGlow: Float = 1.0f,
    val touch: Boolean = true,
    val starTapTravel: Boolean = false,
    val parallax: Boolean = true,
    val gyroMode: Int = 1,
    val gyroStrength: Float = 1f,
    val mountainDepth: Float = 1f,
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
    val skyFixTime: Long = 0L,
    val clockFont: Int = 0,
    val clockFontRevision: Int = 0,
    val clockCustomPlacement: Boolean = false,
    val clockX: Float = .5f,
    val clockY: Float = .68f,
    val photoRevision: Int = 0
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
            preset = sanitizePreset(preferences.getInt("preset", SmoothWallpaperRenderer.FIRST)),
            palette = preferences.getInt("palette", 0).coerceIn(0, 5),
            customColor = preferences.getInt("custom_color", Color.rgb(185, 174, 255)) or -0x1000000,
            intensity = float("intensity", 1f, .35f, 2.3f),
            speed = float("speed", 1f, .25f, 2f),
            fps = when (preferences.getInt("fps", 30)) {
                60 -> 60
                45 -> 45
                else -> 30
            },
            shaderQuality = preferences.getInt("shader_quality", 1).coerceIn(0, 2),
            shadesDepth = float("shades_depth", 1f, .4f, 2.0f),
            shadesGloss = float("shades_gloss", 1f, .1f, 2.0f),
            shadesGlow = float("shades_glow", 1f, 0f, 2.0f),
            touch = preferences.getBoolean("touch", true),
            starTapTravel = preferences.getBoolean("star_tap_travel", false),
            parallax = preferences.getBoolean("parallax", true),
            gyroMode = preferences.getInt("gyro_mode", 1).coerceIn(0, 3),
            gyroStrength = float("gyro_strength", 1f, .5f, 1.8f),
            mountainDepth = float("mountain_depth", 1f, .5f, 1.6f),
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
            skyFixTime = preferences.getLong("sky_fix_time", 0L),
            clockFont = preferences.getInt("clock_font", 0).coerceIn(0, 6),
            clockFontRevision = preferences.getInt("clock_font_revision", 0),
            clockCustomPlacement = preferences.getBoolean("clock_custom_placement", false),
            clockX = float("clock_x", .5f, .08f, .92f),
            clockY = float("clock_y", .68f, .14f, .88f),
            photoRevision = preferences.getInt("photo_revision", 0)
        )
    }

    private fun sanitizePreset(value: Int): Int = when {
        value == StarWindowRenderer.SCENE_INDEX || value in 17..20 ||
            value == PhotoWallpaperStore.SCENE_INDEX ||
            SmoothWallpaperRenderer.isSmooth(value) -> value
        else -> SmoothWallpaperRenderer.FIRST
    }

    fun setPreset(value: Int) = preferences.edit().putInt("preset", sanitizePreset(value)).apply()
    fun photoImported() = preferences.edit()
        .putInt("photo_revision", preferences.getInt("photo_revision", 0) + 1)
        .putInt("preset", PhotoWallpaperStore.SCENE_INDEX).apply()
    fun setPalette(value: Int) = preferences.edit().putInt("palette", value.coerceIn(0, 5)).apply()
    fun setCustomColor(color: Int) = preferences.edit()
        .putInt("custom_color", color or -0x1000000)
        .putInt("palette", 5)
        .apply()
    fun setIntensity(value: Float) = preferences.edit().putFloat("intensity", value.coerceIn(.35f, 2.3f)).apply()
    fun setSpeed(value: Float) = preferences.edit().putFloat("speed", value.coerceIn(.25f, 2f)).apply()
    fun setFps(value: Int) = preferences.edit().putInt("fps", value).apply()
    fun setShaderQuality(value: Int) = preferences.edit().putInt("shader_quality", value.coerceIn(0, 2)).apply()
    fun setShadesDepth(value: Float) = preferences.edit().putFloat("shades_depth", value.coerceIn(.4f, 2f)).apply()
    fun setShadesGloss(value: Float) = preferences.edit().putFloat("shades_gloss", value.coerceIn(.1f, 2f)).apply()
    fun setShadesGlow(value: Float) = preferences.edit().putFloat("shades_glow", value.coerceIn(0f, 2f)).apply()
    fun setTouch(value: Boolean) = preferences.edit().putBoolean("touch", value).apply()
    fun setStarTapTravel(value: Boolean) = preferences.edit().putBoolean("star_tap_travel", value).apply()
    fun setParallax(value: Boolean) = preferences.edit().putBoolean("parallax", value).apply()
    fun setGyroMode(value: Int) = preferences.edit().putInt("gyro_mode", value.coerceIn(0, 3)).apply()
    fun setGyroStrength(value: Float) = preferences.edit().putFloat("gyro_strength", value.coerceIn(.5f, 1.8f)).apply()
    fun setMountainDepth(value: Float) = preferences.edit().putFloat("mountain_depth", value.coerceIn(.5f, 1.6f)).apply()
    fun setClockStyle(value: Int) = preferences.edit().putInt("clock_style", value.coerceIn(0, 5)).apply()
    fun setClockPosition(value: Int) = preferences.edit()
        .putInt("clock_position", value.coerceIn(0, 2))
        .putBoolean("clock_custom_placement", false).apply()
    fun setClockFont(value: Int) = preferences.edit().putInt("clock_font", value.coerceIn(0, 6)).apply()
    fun importedClockFont() = preferences.edit()
        .putInt("clock_font", 6)
        .putInt("clock_font_revision", preferences.getInt("clock_font_revision", 0) + 1).apply()
    fun setClockPlacement(x: Float, y: Float) = preferences.edit()
        .putFloat("clock_x", x.coerceIn(.08f, .92f))
        .putFloat("clock_y", y.coerceIn(.14f, .88f))
        .putBoolean("clock_custom_placement", true).apply()
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
 * One reliable Canvas/AGSL rendering pipeline. Legacy flat-colour shader
 * packs and SHADES sources are retired, not hidden behind a menu.
 */
class HalynRenderer(private val context: Context) {
    private val starRenderer by lazy { StarWindowRenderer(context) }
    private val mountainRenderer = MountainSceneRenderer()
    private val photoRenderer = PhotoWallpaperStore(context.applicationContext)
    private val smooth = SmoothWallpaperRenderer()

    fun resetHardwareState() { smooth.resetHardwareState() }

    fun draw(
        canvas: Canvas, state: MotionState, elapsedSeconds: Float,
        touchX: Float = .5f, touchY: Float = .5f, offsetX: Float = .5f,
        starPose: StarPose? = null, gyro: ParallaxPose = ParallaxPose()
    ) {
        when {
            state.preset == StarWindowRenderer.SCENE_INDEX ->
                starRenderer.draw(canvas, state, elapsedSeconds, starPose ?: StarPose())
            MountainSceneRenderer.isMountain(state.preset) ->
                mountainRenderer.drawBackground(canvas, state, elapsedSeconds,
                    mountainPose(state, gyro, offsetX))
            state.preset == PhotoWallpaperStore.SCENE_INDEX ->
                photoRenderer.draw(canvas, gyro)
            else -> smooth.draw(canvas, state, elapsedSeconds,
                touchX, touchY, offsetX, gyro)
        }
    }

    fun drawForeground(canvas: Canvas, state: MotionState,
        gyro: ParallaxPose = ParallaxPose(), offsetX: Float = .5f) {
        if (MountainSceneRenderer.isMountain(state.preset)) {
            mountainRenderer.drawForeground(canvas, state, mountainPose(state, gyro, offsetX))
        }
    }

    private fun mountainPose(state: MotionState, gyro: ParallaxPose, offsetX: Float): ParallaxPose {
        val swipe = if (state.parallax) (offsetX - .5f) * .4f else 0f
        return ParallaxPose((gyro.x + swipe).coerceIn(-1f, 1f), gyro.y)
    }

    companion object {
        val PRESETS: List<String> = MutableList(SmoothWallpaperRenderer.LAST + 1) {
            "Retired wallpaper"
        }.apply {
            this[StarWindowRenderer.SCENE_INDEX] = "Star Window"
            listOf("Twilight Peaks", "Moonlit Ridge", "Alpine Mist", "Ember Valley")
                .forEachIndexed { index, name -> this[17 + index] = name }
            this[PhotoWallpaperStore.SCENE_INDEX] = "My Image"
            SmoothWallpaperRenderer.SCENES.forEachIndexed { index, spec ->
                this[SmoothWallpaperRenderer.FIRST + index] = spec.name
            }
        }
        val PALETTES = listOf("Arctic", "Ultraviolet", "Ember", "Rose", "Verdant")
        val PALETTE_COLORS = intArrayOf(
            Color.rgb(100, 235, 248), Color.rgb(182, 144, 255),
            Color.rgb(255, 169, 97), Color.rgb(255, 133, 188),
            Color.rgb(120, 244, 172)
        )
    }
}
