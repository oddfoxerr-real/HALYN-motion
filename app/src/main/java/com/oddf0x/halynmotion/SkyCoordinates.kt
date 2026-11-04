package com.oddf0x.halynmotion

import android.hardware.GeomagneticField
import kotlin.math.cos
import kotlin.math.sin

/**
 * Geographic sky alignment from standard sidereal-time and J2000 coordinates.
 *
 * Android TYPE_ROTATION_VECTOR uses a magnetic East-North-Up world basis.
 * A declination correction maps the device's magnetic axes to true ENU.
 * The resulting celestial axes use the same J2000 ecliptic orientation as
 * the Yale/Hipparcos stars and the JPL approximate planetary model.
 *
 * This is an approximate sky view; atmospheric refraction, precise Earth
 * nutation/precession, horizon obstructions and local magnetic interference
 * are not modeled. A cached foreground location fix is sufficient; GPS is
 * not sampled continuously while the wallpaper runs.
 */
object SkyCoordinates {
    data class LocalAxes(
        val east: SpacePoint,
        val north: SpacePoint,
        val zenith: SpacePoint,
        val magneticDeclinationRad: Double
    )

    fun axes(latitudeDeg: Double, longitudeDeg: Double, nowMs: Long): LocalAxes {
        val lat = Math.toRadians(latitudeDeg.coerceIn(-90.0, 90.0))
        val jd = nowMs / 86_400_000.0 + 2_440_587.5
        val t = (jd - 2_451_545.0) / 36525.0
        val gmstDeg = 280.46061837 + 360.98564736629 * (jd - 2_451_545.0) +
            0.000387933 * t * t - t * t * t / 38_710_000.0
        val sidereal = Math.toRadians(((gmstDeg + longitudeDeg) % 360.0 + 360.0) % 360.0)

        fun equatorialToEcliptic(x: Double, y: Double, z: Double): SpacePoint {
            val tilt = Math.toRadians(23.43929111)
            return SpacePoint(x, y * cos(tilt) + z * sin(tilt),
                -y * sin(tilt) + z * cos(tilt))
        }

        val east = equatorialToEcliptic(-sin(sidereal), cos(sidereal), 0.0)
        val north = equatorialToEcliptic(
            -sin(lat) * cos(sidereal), -sin(lat) * sin(sidereal), cos(lat))
        val zenith = equatorialToEcliptic(
            cos(lat) * cos(sidereal), cos(lat) * sin(sidereal), sin(lat))

        val declination = try {
            Math.toRadians(GeomagneticField(
                latitudeDeg.toFloat(), longitudeDeg.toFloat(), 0f, nowMs
            ).declination.toDouble())
        } catch (_: Exception) {
            0.0
        }

        return LocalAxes(east, north, zenith, declination)
    }

    /**
     * Convert an Android device right/up/outward basis from magnetic ENU into
     * J2000 ecliptic. The matrix is row-major; each column is a device axis
     * expressed in world ENU before the transformation.
     */
    fun cameraMatrix(deviceToMagneticEnu: FloatArray, axes: LocalAxes): FloatArray {
        val result = FloatArray(9)
        val cd = cos(axes.magneticDeclinationRad)
        val sd = sin(axes.magneticDeclinationRad)
        for (c in 0..2) {
            val magneticEast = deviceToMagneticEnu[c].toDouble()
            val magneticNorth = deviceToMagneticEnu[3 + c].toDouble()
            val up = deviceToMagneticEnu[6 + c].toDouble()
            val trueEast = magneticEast * cd + magneticNorth * sd
            val trueNorth = -magneticEast * sd + magneticNorth * cd
            val v = axes.east * trueEast + axes.north * trueNorth + axes.zenith * up
            result[c] = v.x.toFloat()
            result[3 + c] = v.y.toFloat()
            result[6 + c] = v.z.toFloat()
        }
        return result
    }
}
