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
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Rigid camera pose in the J2000-ecliptic-aligned *virtual scene*.
 *
 * Rotation is relative to the device's initial orientation. Camera origin is
 * in astronomical units; Double precision survives light-year warp distances.
 * This is an approximate inertial estimate, not surveyed 6DoF tracking.
 */
data class StarPose(
    val right: FloatArray = floatArrayOf(1f, 0f, 0f),
    val up: FloatArray = floatArrayOf(0f, 1f, 0f),
    val outward: FloatArray = floatArrayOf(0f, 0f, 1f),
    val originAu: SpacePoint = SpacePoint(0.0, 0.0, 2.5),
    val warpProgress: Float = 1f,
    val travelLy: Double = 0.0,
    val skyAligned: Boolean = false
)

/**
 * Slow, floating astronaut navigation built from linear acceleration +
 * Android's fused rotation vector. No ARCore or camera is needed.
 *
 * Stabilization strategy:
 * - Reject impossible single-sample angular jumps and acceleration impulses.
 * - Adaptive one-euro-like exponential orientation smoothing (~115 ms).
 * - Predict small short-lived (<=85ms) sensor delivery gaps, never long ones.
 * - Integrate acceleration with heavy speed clamp and gentle momentum decay.
 * - Smooth translation at render cadence, independent of sensor events.
 * - Smoothstep an intentional 1-ly camera warp over 2.6 seconds.
 * - Stop sensors entirely whenever wallpaper is not visible.
 */
class SpatialTracker(context: Context) : SensorEventListener {
    private val app = context.applicationContext
    private val sensorManager = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val windowManager = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val absoluteNorthAvailable = rotationSensor?.type == Sensor.TYPE_ROTATION_VECTOR

    private val identity = floatArrayOf(1f,0f,0f, 0f,1f,0f, 0f,0f,1f)
    private val raw = identity.copyOf()
    private val screen = identity.copyOf()
    private val baseline = identity.copyOf()
    private val target = identity.copyOf()
    private val previousTarget = identity.copyOf()
    private val smoothed = identity.copyOf()
    private val velocity = FloatArray(3)
    private val position = FloatArray(3)
    private val smoothedPosition = FloatArray(3)
    private val filteredAcceleration = FloatArray(3)
    private var useLocalSky = false
    private var latitude = 0.0
    private var longitude = 0.0
    private var skyBucket = Long.MIN_VALUE
    private var skyAxes: SkyCoordinates.LocalAxes? = null
    private var baselineReady = false
    private var active = false
    private var sensitivity = .55f
    private var lastMotionNs = 0L
    private var lastRotationNs = 0L
    private var previousRotationNs = 0L
    private var lastRenderNs = 0L
    private var visualClockNs = 0L

    private var completedWarpAu = SpacePoint(0.0, 0.0, 0.0)
    private var warpFromAu = SpacePoint(0.0, 0.0, 0.0)
    private var warpToAu = SpacePoint(0.0, 0.0, 0.0)
    private var warpStartNs = 0L
    private val warpDurationNs = 2_600_000_000L

    val hasTrackingSensors: Boolean get() = rotationSensor != null && motionSensor != null
    val hasCompassHeading: Boolean get() = absoluteNorthAvailable

    fun configureSky(state: MotionState) {
        val want = state.skyAligned && absoluteNorthAvailable
        if (want != useLocalSky || state.skyLatitude != latitude || state.skyLongitude != longitude) {
            useLocalSky = want
            latitude = state.skyLatitude
            longitude = state.skyLongitude
            skyBucket = Long.MIN_VALUE
            // Never instant-snap the camera when the user changes modes.
            // The existing render filter smoothly blends to the new axes.
        }
    }

    fun start(level: Float) {
        setSensitivity(level)
        if (active) return
        active = true
        lastRenderNs = 0L
        lastMotionNs = 0L
        if (rotationSensor != null)
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        if (motionSensor != null)
            sensorManager.registerListener(this, motionSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (active) sensorManager.unregisterListener(this)
        active = false
        lastMotionNs = 0L
        lastRenderNs = 0L
        velocity.fill(0f)
        filteredAcceleration.fill(0f)
    }

    fun setSensitivity(level: Float) {
        sensitivity = level.coerceIn(.15f, 1.4f)
    }

    fun recenter() {
        position.fill(0f)
        smoothedPosition.fill(0f)
        velocity.fill(0f)
        filteredAcceleration.fill(0f)
        completedWarpAu = SpacePoint(0.0, 0.0, 0.0)
        warpStartNs = 0L
        lastMotionNs = 0L
        // Calibrate forward again using the most recently accepted device pose.
        for (i in 0..8) baseline[i] = screen[i]
        for (i in 0..8) {
            target[i] = identity[i]
            previousTarget[i] = identity[i]
            smoothed[i] = identity[i]
        }
        baselineReady = lastRotationNs != 0L
        lastRenderNs = 0L
        visualClockNs = 0L
    }

    fun warpForward() {
        val now = SystemClock.elapsedRealtimeNanos()
        val pose = pose(now)
        val frameTime = visualClockNs
        val current = currentWarpOffset(frameTime)
        val outward = pose.outward
        // A single deliberate tap moves ONE LIGHT YEAR along the view ray.
        val destination = SpacePoint(
            current.x - outward[0].toDouble() * AstronomyCatalog.AU_PER_LY,
            current.y - outward[1].toDouble() * AstronomyCatalog.AU_PER_LY,
            current.z - outward[2].toDouble() * AstronomyCatalog.AU_PER_LY
        )
        warpFromAu = current
        warpToAu = destination
        warpStartNs = frameTime
    }

    fun pose(nowNs: Long = SystemClock.elapsedRealtimeNanos()): StarPose {
        if (lastRenderNs == 0L) {
            for (i in 0..8) smoothed[i] = target[i]
            for (i in 0..2) smoothedPosition[i] = position[i]
            lastRenderNs = nowNs
            visualClockNs = nowNs
        }
        // A delayed UI/GPU frame advances visual time by only a bounded frame
        // interval. The following frames smoothly catch up to sensor input.
        val stepNs = (nowNs - lastRenderNs).coerceIn(0L, 52_000_000L)
        visualClockNs += stepNs
        val dt = stepNs * 1e-9f
        lastRenderNs = nowNs

        val estimated = target.copyOf()
        val gapNs = nowNs - lastRotationNs
        val previousInterval = lastRotationNs - previousRotationNs
        if (lastRotationNs > 0L && previousInterval > 0L &&
            gapNs > 8_000_000L && gapNs < 85_000_000L &&
            previousInterval in 5_000_000L..70_000_000L) {
            // Small bounded continuation of accepted angular motion bridges a
            // delayed or dropped sensor packet; never predict large rotations.
            val ratio = min(.65f, gapNs.toFloat() / previousInterval.toFloat())
            for (i in 0..8) {
                estimated[i] = target[i] + (target[i] - previousTarget[i])
                    .coerceIn(-.10f, .10f) * ratio
            }
        }

        // Render-time filters avoid a visible teleport even if an event arrives
        // late. A short gap simply continues the previous trajectory.
        val alpha = (1.0 - exp(-dt / .115f)).toFloat()
        for (i in 0..8) smoothed[i] += (estimated[i] - smoothed[i]) * alpha
        normalizeBasis(smoothed)

        val posAlpha = (1.0 - exp(-dt / .24f)).toFloat()
        for (i in 0..2) {
            smoothedPosition[i] += (position[i] - smoothedPosition[i]) * posAlpha
        }

        val warp = currentWarpOffset(visualClockNs)
        val originBase = if (useLocalSky) {
            SolarEphemeris.positions(System.currentTimeMillis())
                .firstOrNull { it.name == "Earth" }?.location ?: SpacePoint(0.0, 0.0, 2.5)
        } else SpacePoint(0.0, 0.0, 2.5)
        val progress = if (warpStartNs == 0L) 1f
            else ((visualClockNs - warpStartNs).toDouble() / warpDurationNs).coerceIn(0.0, 1.0).toFloat()
        return StarPose(
            right = floatArrayOf(smoothed[0], smoothed[3], smoothed[6]),
            up = floatArrayOf(smoothed[1], smoothed[4], smoothed[7]),
            outward = floatArrayOf(smoothed[2], smoothed[5], smoothed[8]),
            originAu = SpacePoint(
                originBase.x + warp.x + smoothedPosition[0],
                originBase.y + warp.y + smoothedPosition[1],
                originBase.z + warp.z + smoothedPosition[2]
            ),
            warpProgress = progress,
            travelLy = warp.length() / AstronomyCatalog.AU_PER_LY,
            skyAligned = useLocalSky
        )
    }

    private fun currentWarpOffset(nowNs: Long): SpacePoint {
        if (warpStartNs == 0L) return completedWarpAu
        val elapsed = (nowNs - warpStartNs).toDouble() / warpDurationNs.toDouble()
        if (elapsed >= 1.0) {
            completedWarpAu = warpToAu
            warpStartNs = 0L
            return completedWarpAu
        }
        val t = elapsed.coerceIn(0.0, 1.0)
        val eased = t * t * (3.0 - 2.0 * t)
        return warpFromAu + (warpToAu - warpFromAu) * eased
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (!active) return
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> acceptRotation(event)
            Sensor.TYPE_LINEAR_ACCELERATION -> integrateAcceleration(event)
        }
    }

    private fun acceptRotation(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(raw, event.values)
        val displayRotation = try { windowManager.defaultDisplay.rotation }
            catch (_: Exception) { Surface.ROTATION_0 }
        val axes = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(raw, axes.first, axes.second, screen)

        if (!baselineReady) {
            for (i in 0..8) baseline[i] = screen[i]
            baselineReady = true
        }
        val relative = FloatArray(9)
        // R_relative = transpose(R_calibration) * R_current
        for (r in 0..2) {
            for (c in 0..2) {
                relative[r * 3 + c] = baseline[r] * screen[c] +
                    baseline[3 + r] * screen[3 + c] +
                    baseline[6 + r] * screen[6 + c]
            }
        }
        val now = System.currentTimeMillis()
        val bucket = now / 60_000L
        if (useLocalSky && (skyAxes == null || bucket != skyBucket)) {
            skyAxes = SkyCoordinates.axes(latitude, longitude, now)
            skyBucket = bucket
        }
        val desired = if (useLocalSky && skyAxes != null) {
            SkyCoordinates.cameraMatrix(screen, skyAxes!!)
        } else relative
        val delta = if (lastRotationNs > 0L) {
            val dot = ((target[0] * desired[0] + target[3] * desired[3] + target[6] * desired[6]) +
                (target[1] * desired[1] + target[4] * desired[4] + target[7] * desired[7]) +
                (target[2] * desired[2] + target[5] * desired[5] + target[8] * desired[8]))
            acos(((dot - 1f) * .5f).coerceIn(-1f, 1f))
        } else 0f
        val intervalNs = event.timestamp - lastRotationNs
        // More than ~55 degrees inside <60ms is almost certainly a sensor
        // glitch. Keep the preceding pose and let the renderer interpolate.
        // A deliberate switch to geographic alignment may be a large rotation.
        // Accept that switch and interpolate it instead of treating it as noise.
        if (!useLocalSky && intervalNs in 1L..60_000_000L && delta > .96f) return

        for (i in 0..8) previousTarget[i] = target[i]
        for (i in 0..8) target[i] = desired[i]
        previousRotationNs = lastRotationNs
        lastRotationNs = event.timestamp
    }

    private fun integrateAcceleration(event: SensorEvent) {
        if (!baselineReady) return
        if (lastMotionNs == 0L) {
            lastMotionNs = event.timestamp
            return
        }
        val dt = ((event.timestamp - lastMotionNs) * 1e-9f).coerceIn(0f, .045f)
        lastMotionNs = event.timestamp
        if (dt <= 0f) return

        val e = event.values
        val magnitudeSquared = e[0] * e[0] + e[1] * e[1] + e[2] * e[2]
        // Sporadic sensor explosions are not human walking acceleration.
        if (magnitudeSquared > 180f) return
        val world = FloatArray(3)
        for (r in 0..2) {
            val inertial = raw[r * 3] * e[0] + raw[r * 3 + 1] * e[1] + raw[r * 3 + 2] * e[2]
            world[r] = inertial
        }
        val calibrated = FloatArray(3)
        for (i in 0..2) {
            calibrated[i] = baseline[i] * world[0] +
                baseline[3 + i] * world[1] + baseline[6 + i] * world[2]
        }

        // Quiet, weightless acceleration. High-frequency spikes cause no
        // sudden position jump; momentum gradually slows after movement.
        val response = .55f
        val drag = exp(-.84f * dt)
        for (i in 0..2) {
            val input = calibrated[i].coerceIn(-5f, 5f)
            filteredAcceleration[i] += (input - filteredAcceleration[i]) * response
            val noiseGate = filteredAcceleration[i]
            val useful = if (abs(noiseGate) < .22f) 0f
                else (abs(noiseGate) - .22f) * if (noiseGate < 0f) -1f else 1f
            val impulse = useful * dt * sensitivity * .014f
            velocity[i] = (velocity[i] * drag + impulse).coerceIn(-.048f, .048f)
            position[i] += velocity[i] * dt
        }
        val norm = sqrt(position.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 2.5f) {
            val scale = 2.5f / max(.0001f, norm)
            for (i in 0..2) {
                position[i] *= scale
                velocity[i] *= .6f
            }
        }
    }

    private fun normalizeBasis(r: FloatArray) {
        fun norm(x: Float, y: Float, z: Float): Float =
            max(.00001f, sqrt(x * x + y * y + z * z))
        val a = norm(r[0], r[3], r[6])
        r[0] /= a; r[3] /= a; r[6] /= a
        val projection = r[0] * r[1] + r[3] * r[4] + r[6] * r[7]
        r[1] -= r[0] * projection
        r[4] -= r[3] * projection
        r[7] -= r[6] * projection
        val b = norm(r[1], r[4], r[7])
        r[1] /= b; r[4] /= b; r[7] /= b
        // Reconstruct an exactly orthogonal forward vector.
        r[2] = r[3] * r[7] - r[6] * r[4]
        r[5] = r[6] * r[1] - r[0] * r[7]
        r[8] = r[0] * r[4] - r[3] * r[1]
    }
}
