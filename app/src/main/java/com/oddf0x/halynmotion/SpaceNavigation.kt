package com.oddf0x.halynmotion

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One consistent camera projection for star picking, planetary approach and
 * touch rays. No fabricated NASA discoveries are made.
 */
object SpaceNavigation {
    data class Destination(
        val name: String,
        val point: SpacePoint,
        val stopDistanceAu: Double,
        val kind: Kind
    )
    enum class Kind { SUN, PLANET, CATALOG_STAR, GENERATED }

    fun ray(pose: StarPose, normalizedX: Float, normalizedY: Float, aspect: Float): SpacePoint {
        // Inverse of the star renderer's perspective projection:
        // pixelX = width/2 + height*0.82*side/forward.
        // This makes the travelled ray pass through the exact tapped pixel.
        val dx = (normalizedX.coerceIn(0f, 1f) - .5) * aspect / .82
        val dy = (.5 - normalizedY.coerceIn(0f, 1f)) / .82
        val out = SpacePoint(
            dx * pose.right[0] + dy * pose.up[0] - pose.outward[0],
            dx * pose.right[1] + dy * pose.up[1] - pose.outward[1],
            dx * pose.right[2] + dy * pose.up[2] - pose.outward[2]
        )
        return normalize(out)
    }

    fun normalize(v: SpacePoint): SpacePoint {
        val len = max(1e-10, v.length())
        return v * (1.0 / len)
    }

    fun project(
        pose: StarPose, target: SpacePoint, width: Float, height: Float
    ): Pair<Float, Float>? {
        val dx = target.x - pose.originAu.x
        val dy = target.y - pose.originAu.y
        val dz = target.z - pose.originAu.z
        val forward = -(dx * pose.outward[0] + dy * pose.outward[1] + dz * pose.outward[2])
        if (forward <= .0000001) return null
        val side = dx * pose.right[0] + dy * pose.right[1] + dz * pose.right[2]
        val vertical = dx * pose.up[0] + dy * pose.up[1] + dz * pose.up[2]
        val scale = height * .82
        val x = (width * .5 + scale * side / forward).toFloat()
        val y = (height * .5 - scale * vertical / forward).toFloat()
        return if (x.isFinite() && y.isFinite()) x to y else null
    }

    /**
     * Explicit destination selection is preferred when in the Solar System.
     * Clicking a body label or disc launches a short AU-scale approach, while
     * empty space launches a 1-ly flight in the exact touched direction.
     */
    fun pick(
        pose: StarPose,
        x: Float, y: Float, width: Float, height: Float,
        stars: List<CatalogStar>,
        nowMs: Long = System.currentTimeMillis()
    ): Destination? {
        val size = max(1f, min(width, height))
        val radiusPx = max(38f, size * .09f)
        var best: Destination? = null
        var bestDistance = radiusPx * radiusPx

        fun consider(candidate: Destination) {
            val projected = project(pose, candidate.point, width, height) ?: return
            val dx = x - projected.first
            val dy = y - projected.second
            val dist2 = dx * dx + dy * dy
            if (dist2 < bestDistance) {
                bestDistance = dist2
                best = candidate
            }
        }

        consider(Destination("Sun", SpacePoint(0.0, 0.0, 0.0), .032, Kind.SUN))
        SolarEphemeris.positions(nowMs).forEach { planet ->
            consider(Destination(planet.name, planet.location,
                planetStopDistance(planet.name), Kind.PLANET))
        }

        // Only brighter and named real catalogue stars are direct touch
        // targets. Their stored coordinates, including known parallax, define
        // where the user actually travels.
        for (star in stars) {
            if (star.magnitude > 3.0f) break
            if (star.name.isBlank() || star.location.length() > 1e10) continue
            consider(Destination(star.name, star.location, .12, Kind.CATALOG_STAR))
        }
        for (world in pose.generatedWorlds) {
            consider(Destination(world.name, world.center, .004, Kind.GENERATED))
        }
        for (star in stars) {
            if (star.name.isNotBlank() && star.location.length() < 1e10 &&
                (star.location - pose.originAu).length() < 12.0) {
                for (world in ImaginedWorlds.around(star)) {
                    consider(Destination(world.name, world.location, .004, Kind.GENERATED))
                }
            }
        }
        return best
    }

    private fun planetStopDistance(name: String): Double = when (name) {
        "Mercury" -> .00035
        "Venus" -> .00080
        "Earth" -> .00085
        "Mars" -> .00055
        "Jupiter" -> .0060
        "Saturn" -> .0050
        "Uranus" -> .0026
        "Neptune" -> .0025
        else -> .0060
    }

    fun destinationForName(
        name: String, stars: List<CatalogStar>,
        nowMs: Long = System.currentTimeMillis(),
        discoveries: List<GeneratedWorld> = emptyList()
    ): Destination? {
        if (name == "Sun") return Destination(name, SpacePoint(0.0, 0.0, 0.0), .032, Kind.SUN)
        val planet = SolarEphemeris.positions(nowMs).firstOrNull { it.name == name }
        if (planet != null) return Destination(name, planet.location, planetStopDistance(name), Kind.PLANET)
        val star = stars.firstOrNull { it.name == name && it.location.length() < 1e10 }
        if (star != null) return Destination(name, star.location, .12, Kind.CATALOG_STAR)
        val generated = discoveries.firstOrNull { it.name == name }
        if (generated != null) return Destination(name, generated.center, .004, Kind.GENERATED)
        if (name.startsWith("Simulated ") && " · " in name) {
            val host = name.substringAfterLast(" · ")
            val parent = stars.firstOrNull { it.name == host }
            val world = parent?.let { ImaginedWorlds.around(it).firstOrNull { w -> w.name == name } }
            if (world != null) return Destination(name, world.location, .004, Kind.GENERATED)
        }
        return null
    }
}

/** Visually generated worlds are clearly hypothetical, never reported NASA discoveries. */
object ImaginedWorlds {
    data class World(val location: SpacePoint, val name: String, val tint: Int, val ring: Boolean)
    fun around(star: CatalogStar): List<World> {
        val seed = (star.name.hashCode() * 31L + star.location.x.toBits()).toInt()
        val count = 2 + (seed ushr 2 and 3)
        val palette = intArrayOf(
            0xFF9AB9D9.toInt(), 0xFFD9A68B.toInt(),
            0xFF9AB9A1.toInt(), 0xFFE3D0A0.toInt(),
            0xFFC2ACDA.toInt(), 0xFFE2A978.toInt()
        )
        return (0 until count).map { i ->
            val a = (i + 1) * 1.9 + (seed and 7) * .03
            val phase = (seed ushr (i + 3) and 255) / 255.0 * 6.283185307
            val radiusAu = .32 + (i + 1) * (i + 1) * .24
            val inclination = ((seed ushr (i + 1)) and 7) * .013
            val offset = SpacePoint(
                kotlin.math.cos(a + phase) * radiusAu,
                kotlin.math.sin(a + phase) * radiusAu,
                kotlin.math.sin(phase * 1.5 + a) * radiusAu * inclination
            )
            World(
                star.location + offset,
                "Simulated ${i + 1} · ${star.name}",
                palette[((seed ushr 3) + i * 3) % palette.size],
                i == count - 1 && seed % 3 == 0
            )
        }
    }
}
