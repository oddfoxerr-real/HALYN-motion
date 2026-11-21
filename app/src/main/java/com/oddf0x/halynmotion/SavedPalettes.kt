package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/** Named colour presets shared across all HALYN wallpapers, saved only on-device. */
class SavedPalettes(context: Context) {
    private val store = context.applicationContext
        .getSharedPreferences("halyn_motion_settings", Context.MODE_PRIVATE)
    private val key = "halyn_palette_library_v1"

    data class Palette(val name: String, val primary: Int, val quad: IntArray)

    private fun decode(): JSONArray = try {
        JSONArray(store.getString(key, "[]") ?: "[]")
    } catch (_: Exception) { JSONArray() }

    fun list(): List<Palette> {
        val source = decode()
        val entries = ArrayList<Palette>()
        for (i in 0 until minOf(source.length(), 24)) {
            val entry = source.optJSONObject(i) ?: continue
            val name = entry.optString("name", "").trim()
            if (name.isBlank()) continue
            val four = entry.optJSONArray("quad") ?: continue
            if (four.length() != 4) continue
            entries.add(Palette(
                name.take(32),
                entry.optInt("primary", Color.rgb(180, 181, 184)) or -0x1000000,
                IntArray(4) { index -> four.optInt(index, Color.GRAY) or -0x1000000 }
            ))
        }
        return entries
    }

    /** Overwrite by name rather than accumulating duplicate presets. */
    fun save(name: String, state: MotionState): Boolean {
        val cleaned = name.trim().take(32)
        if (cleaned.isEmpty()) return false
        val current = list().filterNot { it.name.equals(cleaned, ignoreCase = true) }
        val primary = if (state.palette == 5) state.customColor
            else HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0, 4)]
        persist((current + Palette(cleaned, primary, QuadColorSystem.colors(state))).takeLast(24))
        return true
    }

    fun remove(name: String) {
        persist(list().filterNot { it.name.equals(name, ignoreCase = true) })
    }

    fun apply(palette: Palette, prefs: MotionPreferences) {
        val id = prefs.get().preset
        val edit = prefs.raw.edit()
            .putInt("palette", 5)
            .putInt("custom_color", palette.primary or -0x1000000)
            .putBoolean("quad_enabled_" + id, true)
        for (i in 0..3) {
            edit.putInt("quad_color_" + id + "_" + i, palette.quad[i] or -0x1000000)
        }
        edit.apply()
    }

    private fun persist(values: List<Palette>) {
        val entries = JSONArray()
        for (palette in values) {
            val quad = JSONArray()
            palette.quad.forEach { quad.put(it) }
            entries.put(JSONObject()
                .put("name", palette.name)
                .put("primary", palette.primary)
                .put("quad", quad))
        }
        store.edit().putString(key, entries.toString()).apply()
    }

    companion object {
        val CALM_SETS = listOf(
            Palette("Graphite", Color.rgb(174, 176, 179),
                intArrayOf(0xFF202123.toInt(), 0xFF303133.toInt(), 0xFF404143.toInt(), 0xFF525355.toInt())),
            Palette("Fog", Color.rgb(199, 203, 207),
                intArrayOf(0xFF34383B.toInt(), 0xFF484D51.toInt(), 0xFF5B6064.toInt(), 0xFF757B7F.toInt())),
            Palette("Warm Stone", Color.rgb(191, 185, 178),
                intArrayOf(0xFF272625.toInt(), 0xFF393735.toInt(), 0xFF494540.toInt(), 0xFF68615B.toInt())),
            Palette("Slate Blue", Color.rgb(161, 175, 191),
                intArrayOf(0xFF242930.toInt(), 0xFF303943.toInt(), 0xFF424B55.toInt(), 0xFF6C7D91.toInt())),
            Palette("Soft Sage", Color.rgb(173, 187, 173),
                intArrayOf(0xFF262C28.toInt(), 0xFF343F38.toInt(), 0xFF4A564B.toInt(), 0xFF768578.toInt()))
        )
    }
}
