package com.oddf0x.halynmotion

import android.content.Context
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** J2000 heliocentric ecliptic coordinates. Distances are astronomical units. */
data class SpacePoint(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: SpacePoint): SpacePoint =
        SpacePoint(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: SpacePoint): SpacePoint =
        SpacePoint(x - other.x, y - other.y, z - other.z)
    operator fun times(n: Double): SpacePoint = SpacePoint(x * n, y * n, z * n)
    fun length(): Double = sqrt(x * x + y * y + z * z)
}

data class CatalogStar(
    val location: SpacePoint,
    val magnitude: Float,
    val bv: Float,
    val name: String
)

/**
 * Yale Bright Star Catalogue (J2000 RA/Dec, visual magnitude, B-V),
 * Hipparcos distance in parsecs where robustly determined, IAU proper names.
 * Actual catalog facts, not procedural/randomly invented stars.
 *
 * Original factual compilation:
 * https://github.com/isene/starmap/blob/master/data/stars.csv
 * https://github.com/isene/starmap/blob/master/data/README.md
 *
 * The source is intentionally sorted by magnitude, brightest first.
 * A star without a robust parallax is placed at a very distant direction
 * only, never presented with a fabricated named distance.
 */
object AstronomyCatalog {
    const val AU_PER_LY = 63241.07708426628
    private const val AU_PER_PC = 206264.806247096
    private val tilt = Math.toRadians(23.43929111)
    private var cached: List<CatalogStar>? = null

    @Synchronized
    fun stars(context: Context): List<CatalogStar> {
        cached?.let { return it }
        val result = ArrayList<CatalogStar>(9100)
        context.applicationContext.assets.open("yale_bright_stars.csv").bufferedReader().useLines { lines ->
            lines.forEach { row ->
                if (row.isBlank() || row.startsWith("#")) return@forEach
                val values = row.split(',', limit = 6)
                if (values.size < 6) return@forEach
                val ra = values[0].toDoubleOrNull() ?: return@forEach
                val dec = values[1].toDoubleOrNull() ?: return@forEach
                val mag = values[2].toFloatOrNull() ?: return@forEach
                val bv = values[3].toFloatOrNull() ?: 0.50f
                val parsedPc = values[4].toDoubleOrNull()
                // Unknown distance = fixed on the remote celestial sphere.
                val distanceAu = if (parsedPc != null && parsedPc > 0)
                    parsedPc * AU_PER_PC else 1.0e12
                val raRad = Math.toRadians(ra)
                val decRad = Math.toRadians(dec)
                val c = cos(decRad)
                val equX = c * cos(raRad)
                val equY = c * sin(raRad)
                val equZ = sin(decRad)
                val eclX = equX
                val eclY = equY * cos(tilt) + equZ * sin(tilt)
                val eclZ = -equY * sin(tilt) + equZ * cos(tilt)
                result.add(CatalogStar(
                    SpacePoint(eclX * distanceAu, eclY * distanceAu, eclZ * distanceAu),
                    mag, bv, values[5].trim()
                ))
            }
        }
        cached = result
        return result
    }
}

data class PlanetPosition(
    val name: String,
    val location: SpacePoint,
    val tint: Int
)

/**
 * NASA/JPL SSD "Approximate Positions of the Planets", Table 1, 1800–2050.
 *
 * Uses each planet's six J2000 orbital elements and rates per Julian century,
 * Kepler's equation and J2000 ecliptic transformation. Elements labelled
 * "EM Bary" represent Earth/Moon barycenter, approximating Earth's centre.
 * This is a published low-accuracy ephemeris approximation, NOT Horizons.
 *
 * Source: https://ssd.jpl.nasa.gov/planets/approx_pos.html
 */
object SolarEphemeris {
    private data class Elements(
        val name: String, val color: Int,
        val base: DoubleArray, val rate: DoubleArray
    )

    private val data = listOf(
        Elements("Mercury", 0xFFBEC4CC.toInt(),
            doubleArrayOf(.38709927,.20563593,7.00497902,252.25032350,77.45779628,48.33076593),
            doubleArrayOf(.00000037,.00001906,-.00594749,149472.67411175,.16047689,-.12534081)),
        Elements("Venus", 0xFFFFD9A9.toInt(),
            doubleArrayOf(.72333566,.00677672,3.39467605,181.97909950,131.60246718,76.67984255),
            doubleArrayOf(.00000390,-.00004107,-.00078890,58517.81538729,.00268329,-.27769418)),
        Elements("Earth", 0xFF91C9FF.toInt(),
            doubleArrayOf(1.00000261,.01671123,-.00001531,100.46457166,102.93768193,0.0),
            doubleArrayOf(.00000562,-.00004392,-.01294668,35999.37244981,.32327364,0.0)),
        Elements("Mars", 0xFFF1A58B.toInt(),
            doubleArrayOf(1.52371034,.09339410,1.84969142,-4.55343205,-23.94362959,49.55953891),
            doubleArrayOf(.00001847,.00007882,-.00813131,19140.30268499,.44441088,-.29257343)),
        Elements("Jupiter", 0xFFE4C7A4.toInt(),
            doubleArrayOf(5.20288700,.04838624,1.30439695,34.39644051,14.72847983,100.47390909),
            doubleArrayOf(-.00011607,-.00013253,-.00183714,3034.74612775,.21252668,.20469106)),
        Elements("Saturn", 0xFFF1D9A8.toInt(),
            doubleArrayOf(9.53667594,.05386179,2.48599187,49.95424423,92.59887831,113.66242448),
            doubleArrayOf(-.00125060,-.00050991,.00193609,1222.49362201,-.41897216,-.28867794)),
        Elements("Uranus", 0xFFA7DCDC.toInt(),
            doubleArrayOf(19.18916464,.04725744,.77263783,313.23810451,170.95427630,74.01692503),
            doubleArrayOf(-.00196176,-.00004397,-.00242939,428.48202785,.40805281,.04240589)),
        Elements("Neptune", 0xFF779DFA.toInt(),
            doubleArrayOf(30.06992276,.00859048,1.77004347,-55.12002969,44.96476227,131.78422574),
            doubleArrayOf(.00026291,.00005105,.00035372,218.45945325,-.32241464,-.00508664))
    )
    private var lastBucket = Long.MIN_VALUE
    private var cached: List<PlanetPosition> = emptyList()

    @Synchronized
    fun positions(timeMillis: Long): List<PlanetPosition> {
        val bucket = timeMillis / (15L * 60L * 1000L)
        if (lastBucket == bucket) return cached
        val centuries = ((timeMillis - 946728000000L) / 86_400_000.0) / 36525.0
        cached = data.map { entry ->
            val v = DoubleArray(6) { i -> entry.base[i] + entry.rate[i] * centuries }
            val a = v[0]
            val eccentricity = v[1]
            val inclination = Math.toRadians(v[2])
            val perihelion = Math.toRadians(v[4])
            val ascending = Math.toRadians(v[5])
            val meanAnomaly = Math.toRadians(((v[3] - v[4]) % 360 + 360) % 360)
            var anomaly = meanAnomaly
            repeat(10) {
                anomaly -= (anomaly - eccentricity * sin(anomaly) - meanAnomaly) /
                    (1 - eccentricity * cos(anomaly))
            }
            val xp = a * (cos(anomaly) - eccentricity)
            val yp = a * sqrt(1 - eccentricity * eccentricity) * sin(anomaly)
            val w = perihelion - ascending
            val co = cos(ascending)
            val so = sin(ascending)
            val cw = cos(w)
            val sw = sin(w)
            val ci = cos(inclination)
            val si = sin(inclination)
            val point = SpacePoint(
                (cw * co - sw * so * ci) * xp + (-sw * co - cw * so * ci) * yp,
                (cw * so + sw * co * ci) * xp + (-sw * so + cw * co * ci) * yp,
                sw * si * xp + cw * si * yp
            )
            PlanetPosition(entry.name, point, entry.color)
        }
        lastBucket = bucket
        return cached
    }
}
