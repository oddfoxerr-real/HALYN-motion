package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Astronomical Star Window.
 *
 * Stars are real J2000 Yale/Hipparcos catalogue positions and known distances.
 * The Sun and 8 planets use JPL's 1800–2050 approximate Keplerian orbital
 * elements. No random procedural stars or decorative fake constellations.
 *
 * Physics coordinates are Double in AU. The user's estimated inertial camera
 * motion and exact 1-ly intentional warp affect actual 3D projection. Planet
 * glyph radii are intentionally enlarged for visibility — body distances and
 * angles are NOT exaggerated.
 *
 * Hardware-accelerated Canvas is used for grouped point rendering. Buffers
 * and catalog coordinates are cached, preventing per-frame 9k-star allocation.
 */
class StarWindowRenderer(context: Context) {
    private val stars = AstronomyCatalog.stars(context)
    private val cinematicFx = SpaceEffectsRenderer()
    private val worldPainter = SpaceWorldPainter()
    private val constellationLines = loadConstellationLines(context)
    private val buckets = Array(9) { FloatArray(18_300) }
    private val counts = IntArray(9)
    private val paintBuckets = Array(9) { idx ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            val hueGroup = idx % 3
            val brightness = idx / 3
            val color = when (hueGroup) {
                0 -> Color.rgb(178, 202, 245)
                2 -> Color.rgb(255, 209, 173)
                else -> Color.rgb(244, 247, 255)
            }
            this.color = color
            alpha = intArrayOf(115, 205, 250)[brightness]
            strokeWidth = floatArrayOf(1.35f, 2.2f, 3.45f)[brightness]
        }
    }
    private val haze = Paint(Paint.ANTI_ALIAS_FLAG)
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(52, 147, 176, 230)
        style = Paint.Style.STROKE
        strokeWidth = 1.1f
    }
    private val bodyGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyCore = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.argb(221, 243, 246, 255)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 23f
        setShadowLayer(6f, 0f, 1f, Color.BLACK)
    }
    private var backgroundColor = Color.TRANSPARENT
    private var cachedHeight = -1

    private data class Projection(val x: Float, val y: Float, val distance: Double)
    private data class StrokedDirection(val path: List<SpacePoint>)

    fun draw(
        canvas: Canvas,
        state: MotionState,
        seconds: Float,
        pose: StarPose
    ) {
        val width = canvas.width.coerceAtLeast(1).toFloat()
        val height = canvas.height.coerceAtLeast(1).toFloat()
        val smaller = min(width, height)
        val scale = max(.62f, width / 1080f)
        val accent = if (state.palette == 5) state.customColor
            else HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0, 4)]

        // Material layer is separate from astronomical data; Survey/GPS
        // mode draws an accurate, uncluttered sky without fictional worlds.
        cinematicFx.drawBackground(canvas, state, seconds, pose)

        val projectionScale = height * .82f
        val right = pose.right
        val up = pose.up
        val outward = pose.outward
        val origin = pose.originAu

        fun project(point: SpacePoint): Projection? {
            val dx = point.x - origin.x
            val dy = point.y - origin.y
            val dz = point.z - origin.z
            val forward = -(dx * outward[0] + dy * outward[1] + dz * outward[2])
            if (forward <= 0.001) return null
            val side = dx * right[0] + dy * right[1] + dz * right[2]
            val vertical = dx * up[0] + dy * up[1] + dz * up[2]
            val x = (width * .5 + projectionScale * side / forward).toFloat()
            val y = (height * .5 - projectionScale * vertical / forward).toFloat()
            if (!x.isFinite() || !y.isFinite()) return null
            if (x < -35f || x > width + 35f || y < -35f || y > height + 35f) return null
            return Projection(x, y, sqrt(dx * dx + dy * dy + dz * dz))
        }

        val maxStars = when (state.starDensity) {
            0 -> min(1600, stars.size)
            2 -> stars.size
            else -> min(5200, stars.size)
        }
        counts.fill(0)
        for (i in 0 until maxStars) {
            val star = stars[i]
            val point = project(star.location) ?: continue
            val brightness = when {
                star.magnitude < 1.9f -> 2
                star.magnitude < 4.2f -> 1
                else -> 0
            }
            val temp = when {
                star.bv < .20f -> 0
                star.bv > 1.05f -> 2
                else -> 1
            }
            val group = brightness * 3 + temp
            val offset = counts[group]
            val points = buckets[group]
            if (offset + 1 >= points.size) continue
            points[offset] = point.x
            points[offset + 1] = point.y
            counts[group] += 2
        }
        for (i in 0..8) {
            val count = counts[i]
            if (count <= 0) continue
            val ink = paintBuckets[i]
            ink.strokeWidth = floatArrayOf(1.35f, 2.2f, 3.45f)[i / 3] * scale
            canvas.drawPoints(buckets[i], 0, count, ink)
        }

        // BSD-3-clause d3-celestial constellation paths in actual J2000 sky
        // directions. Lines are traditional visual groupings, not physical
        // structures or claimed interstellar navigation routes.
        if (state.starLines) {
            guide.strokeWidth = max(1f, scale)
            for (constellation in constellationLines) {
                var previous: Projection? = null
                for (point in constellation.path) {
                    val projected = project(point)
                    if (previous != null && projected != null) {
                        canvas.drawLine(previous.x, previous.y, projected.x, projected.y, guide)
                    }
                    previous = projected
                }
            }
        }

        // Solar System world coordinates, in AU, from JPL's Keplerian model.
        // Visibility requires looking in the actual object's direction.
        val planets = SolarEphemeris.positions(System.currentTimeMillis())
        drawPlanet(canvas, SpacePoint(0.0,0.0,0.0), "Sun",
            Color.rgb(255, 234, 181), true, scale, ::project, seconds)
        for (planet in planets) {
            drawPlanet(canvas, planet.location, planet.name,
                planet.tint, false, scale, ::project, seconds)
        }

        // When approaching a named catalogue star, add coherent hypothetical
        // companion worlds. Positions are deterministic within that star's
        // frame, but the worlds are explicitly marked GENERATED: their orbits,
        // surfaces and existence are not claimed as NASA discoveries.
        if (state.starEffects >= 2 && !pose.skyAligned) {
            val closestNamed = stars.asSequence()
                .filter { it.name.isNotEmpty() && it.location.length() < 1e10 }
                .filter { (it.location - origin).length() < 12.0 }
                .minByOrNull { (it.location - origin).length() }
            if (closestNamed != null) {
                for (world in ImaginedWorlds.around(closestNamed)) {
                    drawPlanet(canvas, world.location, world.name, world.tint,
                        false, scale, ::project, seconds, simulated = true,
                        rings = world.ring)
                }
            }
        }

        // Named stars retain correct catalog directions. Labels are selective
        // to avoid visually overwhelming a minimalist live wallpaper.
        var namesDrawn = 0
        for (star in stars) {
            if (namesDrawn >= 5 || star.magnitude > .9f) break
            if (star.name.isEmpty()) continue
            val point = project(star.location) ?: continue
            label.textSize = 20f * scale
            label.color = Color.argb(165, 226, 232, 250)
            canvas.drawText(star.name, point.x + 9f * scale,
                point.y - 9f * scale, label)
            namesDrawn++
        }

        // Unmistakable warp tunnel + actual travel distance and arrival,
        // even if the launcher never forwards touch events (the app button does).
        cinematicFx.drawOverlay(canvas, state, pose)
    }

    private fun drawPlanet(
        canvas: Canvas,
        location: SpacePoint,
        name: String,
        colour: Int,
        sun: Boolean,
        scale: Float,
        project: (SpacePoint) -> Projection?,
        seconds: Float,
        simulated: Boolean = false,
        rings: Boolean = false
    ) {
        val point = project(location) ?: return
        val radiusAu = when(name) {
            "Sun" -> .00465047
            "Mercury" -> .0000163
            "Venus" -> .00004045
            "Earth" -> .0000426
            "Mars" -> .0000227
            "Jupiter" -> .000467
            "Saturn" -> .000389
            "Uranus" -> .000169
            "Neptune" -> .000165
            else -> if(simulated) .00032 else .0042
        }
        val apparent = (radiusAu * canvas.height.toDouble() * .82 /
            point.distance.coerceAtLeast(.00001)).toFloat()
        // Far-away planets remain visible as legible markers; nearby planets
        // become large discs at the actual catalogued viewing direction.
        val radius = max((if (sun) 5f else 3.5f) * scale,
            apparent).coerceAtMost(min(canvas.width,canvas.height)*.45f)
        worldPainter.render(canvas,point.x,point.y,radius,colour,name,
            seconds,rings || name=="Saturn" || name=="Uranus",simulated,sun)
    }

    private fun loadConstellationLines(context: Context): List<StrokedDirection> {
        val result = ArrayList<StrokedDirection>()
        val tilt = Math.toRadians(23.43929111)
        context.assets.open("constellation_strokes.csv").bufferedReader().useLines { rows ->
            rows.forEach { row ->
                if (row.isBlank() || row.startsWith("#") || !row.contains(':')) return@forEach
                val parts = row.substringAfter(':').trim().split(' ')
                val points = parts.mapNotNull { pair ->
                    val coords = pair.split(',')
                    if (coords.size != 2) return@mapNotNull null
                    val ra = coords[0].toDoubleOrNull() ?: return@mapNotNull null
                    val dec = coords[1].toDoubleOrNull() ?: return@mapNotNull null
                    val raRad = Math.toRadians(ra)
                    val decRad = Math.toRadians(dec)
                    val x = kotlin.math.cos(decRad) * kotlin.math.cos(raRad)
                    val y = kotlin.math.cos(decRad) * kotlin.math.sin(raRad)
                    val z = kotlin.math.sin(decRad)
                    val distance = 1.0e12
                    SpacePoint(
                        x * distance,
                        (y * kotlin.math.cos(tilt) + z * kotlin.math.sin(tilt)) * distance,
                        (-y * kotlin.math.sin(tilt) + z * kotlin.math.cos(tilt)) * distance
                    )
                }
                if (points.size > 1) result.add(StrokedDirection(points))
            }
        }
        return result
    }

    companion object {
        const val SCENE_INDEX = 12
    }
}
