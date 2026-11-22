package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.PointF
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Gesture templates are recorded as actual finger paths, not inferred from names.
 * Each shape has its own selected fluid response. All data stays in local preferences.
 *
 * One-stroke, single-finger only. Relative positions and size are normalized
 * so a small drawing can match a larger one. Direction is intentionally retained.
 */
class FluidGestures(context: Context) {
    data class Template(val name: String, val action: Int, val path: List<PointF>)

    companion object {
        const val PREF_KEY = "fluid_custom_gestures_v1"
        const val MAX_TEMPLATES = 12
        const val POINTS = 32
        val ACTIONS = listOf("Water ripple", "Whirlpool", "Push current", "Light bloom",
            "Calm surface", "Fluid surge")
    }

    private val prefs = context.applicationContext
        .getSharedPreferences("halyn_motion_settings", Context.MODE_PRIVATE)

    fun load(): List<Template> {
        val result = ArrayList<Template>()
        val all = try { JSONArray(prefs.getString(PREF_KEY, "[]")) } catch (_: Exception) {
            return result
        }
        for (i in 0 until min(all.length(), MAX_TEMPLATES)) {
            val data = all.optJSONObject(i) ?: continue
            val name = data.optString("name").trim().take(30)
            val action = data.optInt("action", -1)
            val samples = data.optJSONArray("path") ?: continue
            if (name.isEmpty() || action !in ACTIONS.indices || samples.length() != POINTS) continue
            val points = ArrayList<PointF>()
            for (j in 0 until samples.length()) {
                val p = samples.optJSONArray(j) ?: break
                val x = p.optDouble(0, Double.NaN).toFloat()
                val y = p.optDouble(1, Double.NaN).toFloat()
                if (!x.isFinite() || !y.isFinite() || x !in -2f..2f || y !in -2f..2f) break
                points.add(PointF(x, y))
            }
            if (points.size == POINTS) result.add(Template(name, action, points))
        }
        return result
    }

    fun save(name: String, action: Int, raw: List<PointF>): Boolean {
        if (action !in ACTIONS.indices) return false
        val processed = normalize(raw) ?: return false
        val label = name.trim().take(30)
        if (label.isEmpty()) return false
        val existing = load().filterNot { it.name.equals(label, true) }
        val updated = (existing + Template(label, action, processed)).takeLast(MAX_TEMPLATES)
        persist(updated)
        return true
    }

    fun delete(name: String) {
        persist(load().filterNot { it.name.equals(name, true) })
    }

    fun match(raw: List<PointF>): Template? {
        val query = normalize(raw) ?: return null
        var best: Template? = null
        var bestDistance = Float.MAX_VALUE
        for (template in load()) {
            if (template.path.size != query.size) continue
            var total = 0f
            for (i in query.indices) {
                val a = query[i]
                val b = template.path[i]
                total += hypot(a.x - b.x, a.y - b.y)
            }
            val score = total / query.size
            if (score < bestDistance) {
                best = template
                bestDistance = score
            }
        }
        return if (bestDistance < .18f) best else null
    }

    private fun persist(values: List<Template>) {
        val all = JSONArray()
        for (template in values) {
            val points = JSONArray()
            template.path.forEach { points.put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) }
            all.put(JSONObject()
                .put("name", template.name)
                .put("action", template.action)
                .put("path", points))
        }
        prefs.edit().putString(PREF_KEY, all.toString()).apply()
    }

    private fun normalize(raw: List<PointF>): List<PointF>? {
        if (raw.size < 6) return null
        // Eliminate stationary duplicates and reject taps / accidental short swipes.
        val clean = ArrayList<PointF>()
        for (p in raw.take(512)) {
            if (!p.x.isFinite() || !p.y.isFinite()) continue
            if (clean.isEmpty() || hypot(p.x - clean.last().x, p.y - clean.last().y) > .004f)
                clean.add(PointF(p.x, p.y))
        }
        if (clean.size < 6) return null
        var length = 0f
        for (i in 1 until clean.size)
            length += hypot(clean[i].x - clean[i-1].x, clean[i].y - clean[i-1].y)
        if (length < .18f) return null

        val sampled = ArrayList<PointF>()
        sampled.add(PointF(clean.first().x, clean.first().y))
        val targetSpacing = length / (POINTS - 1)
        var distance = 0f
        var i = 1
        var previous = clean.first()
        while (i < clean.size && sampled.size < POINTS) {
            val next = clean[i]
            val segment = hypot(next.x - previous.x, next.y - previous.y)
            if (segment < .000001f) { i++; previous = next; continue }
            if (distance + segment >= targetSpacing) {
                val f = ((targetSpacing - distance) / segment).coerceIn(0f, 1f)
                val inserted = PointF(previous.x + f * (next.x - previous.x),
                    previous.y + f * (next.y - previous.y))
                sampled.add(inserted)
                previous = inserted
                distance = 0f
            } else {
                distance += segment
                previous = next
                i++
            }
        }
        while (sampled.size < POINTS) sampled.add(PointF(clean.last().x, clean.last().y))
        val cx = sampled.map { it.x }.average().toFloat()
        val cy = sampled.map { it.y }.average().toFloat()
        val minX = sampled.minOf { it.x }; val maxX = sampled.maxOf { it.x }
        val minY = sampled.minOf { it.y }; val maxY = sampled.maxOf { it.y }
        val scale = max(maxX - minX, maxY - minY).coerceAtLeast(.05f)
        return sampled.map { PointF((it.x - cx) / scale, (it.y - cy) / scale) }
    }
}

/** Captures one stroke from either the dedicated recorder or the live wallpaper. */
class FluidStroke {
    private val samples = ArrayList<PointF>()
    private var tracking = false
    fun clear() { samples.clear(); tracking = false }
    fun points(): List<PointF> = samples.toList()

    fun handle(action: Int, x: Float, y: Float): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                samples.clear()
                tracking = true
                samples.add(PointF(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)))
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking && samples.size < 512) {
                    val px = x.coerceIn(0f, 1f)
                    val py = y.coerceIn(0f, 1f)
                    if (samples.isEmpty() ||
                        hypot(px - samples.last().x, py - samples.last().y) >= .005f)
                        samples.add(PointF(px, py))
                }
                return false
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                val px = x.coerceIn(0f, 1f)
                val py = y.coerceIn(0f, 1f)
                if (samples.isNotEmpty() &&
                    hypot(px - samples.last().x, py - samples.last().y) >= .005f)
                    samples.add(PointF(px, py))
                tracking = false
                return samples.size >= 6
            }
            MotionEvent.ACTION_CANCEL -> clear()
        }
        return false
    }
}
