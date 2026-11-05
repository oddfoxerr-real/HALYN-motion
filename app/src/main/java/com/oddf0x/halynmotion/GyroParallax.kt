package com.oddf0x.halynmotion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/** Bounded, normalized motion relative to the device position at activation. */
data class ParallaxPose(val x: Float = 0f, val y: Float = 0f)

/**
 * Stable orientation-relative parallax for procedural wallpapers.
 *
 * Unlike accelerometer integration, this uses a fused 3D rotation vector and
 * never accumulates position/velocity. A neutral pose is captured each time
 * the wallpaper becomes visible, so moving the phone does not permanently
 * offset the scene. The camera basis adapts to portrait/landscape rotations.
 *
 * Registered only while an eligible scene is visible and gyro mode is enabled.
 * Star Window retains its existing independent SpatialTracker.
 */
class GyroParallax(context: Context) : SensorEventListener {
    private val app = context.applicationContext
    private val sensors = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windows = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotation = FloatArray(9)
    private val screen = FloatArray(9)
    private val basis = FloatArray(9)
    private var active = false
    private var calibrated = false
    private var targetX = 0f
    private var targetY = 0f
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var lastFrameNs = 0L

    val available: Boolean get() = rotationSensor != null

    fun start() {
        if (active || rotationSensor == null) return
        recenter()
        active = sensors.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun recenter() {
        calibrated = false
        targetX = 0f
        targetY = 0f
        smoothedX = 0f
        smoothedY = 0f
        lastFrameNs = 0L
    }

    fun stop() {
        if (active) sensors.unregisterListener(this)
        active = false
        recenter()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (!active || rotationSensor == null || event.sensor.type != rotationSensor.type) return
        if (event.values.size < 3 || event.values.any { !it.isFinite() }) return

        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val display = try { windows.defaultDisplay.rotation }
        catch (_: RuntimeException) { Surface.ROTATION_0 }
        val axes = when (display) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        if (!SensorManager.remapCoordinateSystem(rotation, axes.first, axes.second, screen)) return

        if (!calibrated) {
            screen.copyInto(basis)
            calibrated = true
            return
        }

        // Relative phone normal projected onto original horizontal/vertical
        // axes. Unlike Euler yaw/pitch, this remains stable near gimbal poles.
        val dx = screen[2] * basis[0] + screen[5] * basis[3] + screen[8] * basis[6]
        val dy = screen[2] * basis[1] + screen[5] * basis[4] + screen[8] * basis[7]
        if (!dx.isFinite() || !dy.isFinite()) return
        targetX = deadzone(dx).coerceIn(-1f, 1f)
        targetY = deadzone(-dy).coerceIn(-1f, 1f)
    }

    private fun deadzone(value: Float): Float {
        val magnitude = abs(value)
        val threshold = .024f
        if (magnitude <= threshold) return 0f
        // Roughly 26 degrees reaches full displacement. Clamp prevents
        // landscape gaps and excessive motion for fast hand movements.
        return sign(value) * ((magnitude - threshold) / .42f)
    }

    fun pose(state: MotionState): ParallaxPose {
        if (!active || !calibrated || state.gyroMode == 0) return ParallaxPose()
        val now = SystemClock.elapsedRealtimeNanos()
        val dt = if (lastFrameNs == 0L) 1f / 60f
            else ((now - lastFrameNs) * 1e-9f).coerceIn(0f, .075f)
        lastFrameNs = now

        val multiplier = when (state.gyroMode) {
            1 -> .38f      // Soft
            2 -> .72f      // Dynamic
            else -> 1f     // Space
        } * state.gyroStrength
        val goalX = (targetX * multiplier).coerceIn(-1f, 1f)
        val goalY = (targetY * multiplier).coerceIn(-1f, 1f)
        val timeConstant = when (state.gyroMode) {
            1 -> .22f
            2 -> .14f
            else -> .11f
        }
        val alpha = (1f - exp((-dt / timeConstant).toDouble()).toFloat()).coerceIn(0f, 1f)
        smoothedX += (goalX - smoothedX) * alpha
        smoothedY += (goalY - smoothedY) * alpha
        if (abs(smoothedX) < .0002f && abs(goalX) < .0002f) smoothedX = 0f
        if (abs(smoothedY) < .0002f && abs(goalY) < .0002f) smoothedY = 0f
        return ParallaxPose(smoothedX, smoothedY)
    }
}
