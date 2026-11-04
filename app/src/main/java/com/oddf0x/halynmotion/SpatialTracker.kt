package com.oddf0x.halynmotion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Virtual inertial camera: linear acceleration contributes bounded positional
 * displacement while the rotation vector keeps the starfield direction fixed.
 *
 * IMPORTANT: This is deliberately not marketed as accurate 6DoF odometry.
 * Accelerometer integration drifts. Velocity damping, dead zones, bounded
 * displacement and explicit recentering make it usable for a wallpaper.
 * No camera, ARCore, location, network or background service is used.
 */
data class StarPose(
    val right: FloatArray = floatArrayOf(1f, 0f, 0f),
    val up: FloatArray = floatArrayOf(0f, 1f, 0f),
    val outward: FloatArray = floatArrayOf(0f, 0f, 1f),
    val origin: FloatArray = floatArrayOf(0f, 0f, 0f)
)

class SpatialTracker(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val orientation = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val acceleration = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val rawRotation = FloatArray(9).apply { this[0] = 1f; this[4] = 1f; this[8] = 1f }
    private val screenRotation = FloatArray(9).apply { this[0] = 1f; this[4] = 1f; this[8] = 1f }
    private val velocity = FloatArray(3)
    private val position = FloatArray(3)
    private val smoothedAcceleration = FloatArray(3)

    private var active = false
    private var orientationReceived = false
    private var lastTimestampNs = 0L
    private var sensitivity = 1f

    val hasTrackingSensors: Boolean get() = orientation != null && acceleration != null

    fun start(level: Float) {
        sensitivity = level.coerceIn(.35f, 2.2f)
        if (active) return
        active = true
        lastTimestampNs = 0L
        if (orientation != null) manager.registerListener(this, orientation, SensorManager.SENSOR_DELAY_GAME)
        if (acceleration != null) manager.registerListener(this, acceleration, SensorManager.SENSOR_DELAY_GAME)
    }

    fun setSensitivity(level: Float) {
        sensitivity = level.coerceIn(.35f, 2.2f)
    }

    fun stop() {
        if (active) manager.unregisterListener(this)
        active = false
        lastTimestampNs = 0L
        velocity.fill(0f)
        smoothedAcceleration.fill(0f)
    }

    fun recenter() {
        position.fill(0f)
        velocity.fill(0f)
        smoothedAcceleration.fill(0f)
        lastTimestampNs = 0L
    }

    fun pose(): StarPose = StarPose(
        right = floatArrayOf(screenRotation[0], screenRotation[3], screenRotation[6]),
        up = floatArrayOf(screenRotation[1], screenRotation[4], screenRotation[7]),
        outward = floatArrayOf(screenRotation[2], screenRotation[5], screenRotation[8]),
        origin = position.copyOf()
    )

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (!active) return
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rawRotation, event.values)
                val rotation = try { windowManager.defaultDisplay.rotation } catch (_: Exception) { Surface.ROTATION_0 }
                val axes = when (rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rawRotation, axes.first, axes.second, screenRotation)
                orientationReceived = true
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                if (!orientationReceived) return
                val timestamp = event.timestamp
                if (lastTimestampNs == 0L) {
                    lastTimestampNs = timestamp
                    return
                }
                val dt = ((timestamp - lastTimestampNs) * 1e-9f).coerceIn(0f, .05f)
                lastTimestampNs = timestamp
                if (dt <= 0f) return

                // Transform from the phone's native axes to stable world axes.
                val e = event.values
                for (i in 0 until 3) {
                    val a = rawRotation[i * 3] * e[0] +
                        rawRotation[i * 3 + 1] * e[1] +
                        rawRotation[i * 3 + 2] * e[2]
                    smoothedAcceleration[i] += (a.coerceIn(-6f, 6f) - smoothedAcceleration[i]) * .26f
                }

                // The sensor estimates linear acceleration, not position. A
                // dead zone and damping are intentional to control drift.
                val friction = exp(-2.8f * dt)
                for (i in 0 until 3) {
                    val signal = smoothedAcceleration[i]
                    val useful = if (abs(signal) < .21f) 0f
                        else (abs(signal) - .21f) * if (signal < 0f) -1f else 1f
                    velocity[i] = (velocity[i] * friction + useful * dt * sensitivity * 1.3f)
                        .coerceIn(-1.8f, 1.8f)
                    position[i] += velocity[i] * dt
                }
                // At large drift, keep the camera inside its virtual field.
                val mag = sqrt(position[0] * position[0] +
                    position[1] * position[1] + position[2] * position[2])
                if (mag > 2.25f) {
                    val scale = 2.25f / mag
                    for (i in 0 until 3) {
                        position[i] *= scale
                        velocity[i] *= .35f
                    }
                }
            }
        }
    }
}
