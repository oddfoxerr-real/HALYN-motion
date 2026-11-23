package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

/** Wallpaper-drawn elements, not Android launcher AppWidgets. */
data class WallpaperWidget(
    val id: String,
    val kind: String,
    val x: Float = .5f,
    val y: Float = .3f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val color: Int = Color.WHITE,
    val label: String = "Your text"
) {
    fun safe(): WallpaperWidget = copy(
        kind = if (kind in KINDS) kind else "text",
        x = x.takeIf { it.isFinite() }?.coerceIn(.04f, .96f) ?: .5f,
        y = y.takeIf { it.isFinite() }?.coerceIn(.05f, .95f) ?: .3f,
        scale = scale.takeIf { it.isFinite() }?.coerceIn(.35f, 2.5f) ?: 1f,
        rotation = rotation.takeIf { it.isFinite() }?.coerceIn(-180f, 180f) ?: 0f,
        color = color or -0x1000000,
        label = label.take(48)
    )

    companion object {
        val KINDS = listOf("clock", "date", "battery", "text", "weekday", "divider")
        val LABELS = mapOf(
            "clock" to "Clock", "date" to "Date", "battery" to "Battery",
            "text" to "Text", "weekday" to "Day", "divider" to "Divider"
        )
        fun new(kind: String, index: Int = 0): WallpaperWidget = WallpaperWidget(
            id = UUID.randomUUID().toString(),
            kind = kind,
            y = (.30f + index * .12f).coerceAtMost(.83f),
            label = if (kind == "text") "MY WALLPAPER" else ""
        )
    }
}

class WallpaperWidgetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "halyn_motion_settings", Context.MODE_PRIVATE
    )

    fun active(): Boolean = prefs.getBoolean("widget_composer_enabled", false)

    /** Migrate the user's current clock on first edit, without changing it first. */
    fun activate(state: MotionState): List<WallpaperWidget> {
        if (!active()) {
            val initial = if (state.clockStyle != 0) listOf(
                WallpaperWidget.new("clock").copy(
                    x = if (state.clockCustomPlacement) state.clockX else .5f,
                    y = if (state.clockCustomPlacement) state.clockY else
                        when (state.clockPosition) {
                            0 -> .265f
                            2 -> .71f
                            else -> if (state.clockStyle == 6) .36f else .49f
                        },
                    color = if (state.clockAccent)
                        if (state.palette == 5) state.customColor
                        else HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0, 4)]
                    else Color.WHITE
                )
            ) else emptyList()
            save(initial)
        }
        return load()
    }

    fun load(): List<WallpaperWidget> {
        if (!active()) return emptyList()
        val json = prefs.getString("widget_composition_v1", "[]").orEmpty()
        return try {
            val data = JSONArray(json)
            (0 until minOf(data.length(), 12)).mapNotNull { i ->
                val item = data.optJSONObject(i) ?: return@mapNotNull null
                val kind = item.optString("kind")
                if (kind !in WallpaperWidget.KINDS) return@mapNotNull null
                fun f(key: String, fallback: Float): Float =
                    item.optDouble(key, fallback.toDouble()).toFloat()
                WallpaperWidget(
                    id = item.optString("id").takeIf { it.isNotBlank() } ?: "widget_$i",
                    kind = kind,
                    x = f("x", .5f), y = f("y", .3f),
                    scale = f("scale", 1f), rotation = f("rotation", 0f),
                    color = item.optInt("color", Color.WHITE),
                    label = item.optString("label", "")
                ).safe()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(items: List<WallpaperWidget>) {
        val array = JSONArray()
        items.take(12).forEach { w ->
            val v = w.safe()
            array.put(JSONObject().apply {
                put("id", v.id)
                put("kind", v.kind)
                put("x", v.x.toDouble())
                put("y", v.y.toDouble())
                put("scale", v.scale.toDouble())
                put("rotation", v.rotation.toDouble())
                put("color", v.color)
                put("label", v.label)
            })
        }
        prefs.edit()
            .putString("widget_composition_v1", array.toString())
            .putBoolean("widget_composer_enabled", true)
            .putInt("widget_revision", prefs.getInt("widget_revision", 0) + 1)
            .apply()
    }

    fun add(kind: String): WallpaperWidget? {
        if (kind !in WallpaperWidget.KINDS) return null
        val items = load()
        if (items.size >= 12) return null
        val new = WallpaperWidget.new(kind, items.size)
        save(items + new)
        return new
    }

    fun update(id: String, transform: (WallpaperWidget) -> WallpaperWidget) {
        save(load().map { if (it.id == id) transform(it).safe() else it })
    }

    fun delete(id: String) = save(load().filterNot { it.id == id })
}
