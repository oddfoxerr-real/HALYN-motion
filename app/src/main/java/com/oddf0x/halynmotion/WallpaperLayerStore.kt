package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Persistent, bottom-to-top composition order. Background is always the base
 * layer, so replacing a photo never destroys separate imported cutouts.
 *
 * Existing v0.35 widgets and the existing photo-bound depth mask are migrated
 * by REFERENCE, without copying either asset or changing their saved settings.
 * User-supplied image layers own immutable, UUID-keyed local PNG files.
 */
data class WallpaperLayer(
    val id: String,
    val kind: String,
    val name: String,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
    val x: Float = .5f,
    val y: Float = .5f,
    val scale: Float = 1f,
    val rotation: Float = 0f
) {
    fun sanitized(): WallpaperLayer = copy(
        kind = if (kind in setOf("image", "widget", "subject")) kind else "image",
        name = name.take(40),
        opacity = opacity.takeIf { it.isFinite() }?.coerceIn(.05f, 1f) ?: 1f,
        x = x.takeIf { it.isFinite() }?.coerceIn(.04f, .96f) ?: .5f,
        y = y.takeIf { it.isFinite() }?.coerceIn(.05f, .95f) ?: .5f,
        scale = scale.takeIf { it.isFinite() }?.coerceIn(.25f, 3f) ?: 1f,
        rotation = rotation.takeIf { it.isFinite() }?.coerceIn(-180f, 180f) ?: 0f
    )

    companion object {
        const val SUBJECT_ID = "subject"
        fun widget(widget: WallpaperWidget) = WallpaperLayer(
            "widget:" + widget.id, "widget",
            WallpaperWidget.LABELS[widget.kind] ?: widget.kind
        )
        fun subject() = WallpaperLayer(SUBJECT_ID, "subject", "Cropped subject")
    }
}

class WallpaperLayerStore(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "halyn_motion_settings", Context.MODE_PRIVATE
    )
    private val dir by lazy { File(context.filesDir, "halyn_layers").apply { mkdirs() } }

    fun active() = prefs.getBoolean("layer_stack_enabled", false)

    /** Used only by an explicit editing action, not on every rendered frame. */
    fun activate(widgets: List<WallpaperWidget>, hasSubject: Boolean): List<WallpaperLayer> {
        val items = merged(widgets, hasSubject)
        save(items)
        return items
    }

    private fun stored(): List<WallpaperLayer> = try {
        val raw = JSONArray(prefs.getString("layer_stack_v1", "[]"))
        (0 until min(raw.length(), 32)).mapNotNull { index ->
            val obj = raw.optJSONObject(index) ?: return@mapNotNull null
            val id = obj.optString("id")
            val kind = obj.optString("kind")
            if (id.isBlank() || kind !in setOf("image", "widget", "subject")) return@mapNotNull null
            WallpaperLayer(
                id = id, kind = kind, name = obj.optString("name"),
                visible = obj.optBoolean("visible", true),
                locked = obj.optBoolean("locked", false),
                opacity = obj.optDouble("opacity", 1.0).toFloat(),
                x = obj.optDouble("x", .5).toFloat(),
                y = obj.optDouble("y", .5).toFloat(),
                scale = obj.optDouble("scale", 1.0).toFloat(),
                rotation = obj.optDouble("rotation", .0).toFloat()
            ).sanitized()
        }.distinctBy { it.id }
    } catch (_: Exception) { emptyList() }

    /**
     * A new widget is placed under the subject by default, matching old
     * HALYN clock-behind-subject behaviour; user reordering is preserved.
     */
    fun merged(widgets: List<WallpaperWidget>, hasSubject: Boolean): List<WallpaperLayer> {
        val ids = widgets.associateBy { "widget:" + it.id }
        val existing = stored().filter { layer ->
            when (layer.kind) {
                "widget" -> layer.id in ids
                "subject" -> hasSubject
                else -> assetFile(layer.id).isFile
            }
        }
        val present = existing.map { it.id }.toSet()
        val additions = widgets.map(WallpaperLayer::widget).filter { it.id !in present }
        val subject = if (hasSubject && WallpaperLayer.SUBJECT_ID !in present)
            listOf(WallpaperLayer.subject()) else emptyList()
        val subjectIndex = existing.indexOfFirst { it.kind == "subject" }
        val stack = existing.toMutableList()
        if (subjectIndex >= 0) stack.addAll(subjectIndex, additions) else stack.addAll(additions)
        stack.addAll(subject)
        return stack.take(32)
    }

    fun save(items: List<WallpaperLayer>) {
        val arr = JSONArray()
        items.distinctBy { it.id }.take(32).forEach { layer ->
            val l = layer.sanitized()
            arr.put(JSONObject().apply {
                put("id", l.id); put("kind", l.kind); put("name", l.name)
                put("visible", l.visible); put("locked", l.locked)
                put("opacity", l.opacity.toDouble()); put("x", l.x.toDouble())
                put("y", l.y.toDouble()); put("scale", l.scale.toDouble())
                put("rotation", l.rotation.toDouble())
            })
        }
        prefs.edit().putString("layer_stack_v1", arr.toString())
            .putBoolean("layer_stack_enabled", true)
            .putLong("layer_stack_revision", prefs.getLong("layer_stack_revision", 0L) + 1L)
            .apply()
    }

    fun update(id: String, operation: (WallpaperLayer) -> WallpaperLayer) {
        save(stored().map { if (it.id == id) operation(it).sanitized() else it })
    }

    fun move(id: String, direction: Int) {
        val items = stored().toMutableList()
        val at = items.indexOfFirst { it.id == id }
        val to = at + direction.coerceIn(-1, 1)
        if (at < 0 || to !in items.indices || items[at].locked) return
        val moved = items.removeAt(at)
        items.add(to, moved)
        save(items)
    }

    /** No stale image path is accepted from stored metadata. */
    private fun assetFile(id: String): File {
        val safeId = id.removePrefix("image:")
        if (!safeId.matches(Regex("[a-f0-9-]{36}"))) {
            return File(dir, "_invalid_.png")
        }
        return File(dir, "layer_" + safeId + ".png")
    }

    fun remove(id: String) {
        val removed = stored().firstOrNull { it.id == id } ?: return
        if (removed.locked) return
        if (removed.kind == "image") assetFile(id).delete()
        // Subject removal hides the existing cutout; do not destroy a crop.
        if (removed.kind == "subject") {
            update(id) { it.copy(visible = false) }
            return
        }
        save(stored().filterNot { it.id == id })
    }

    /**
     * Import an already cut-out transparent PNG/WebP, or a normal image.
     * The alpha crop is trimmed to its visible bounding box on import,
     * making resizing intuitive even for full-canvas transparent PNGs.
     * Storage is atomic and independent of the background photo generation.
     */
    fun importImage(uri: Uri): WallpaperLayer? {
        val id = "image:" + UUID.randomUUID().toString()
        val destination = assetFile(id)
        val pending = File.createTempFile("halyn_layer_", ".png", dir)
        var decoded: Bitmap? = null
        var trimmed: Bitmap? = null
        return try {
            val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            if (size != null && size > 52L * 1024L * 1024L) return null
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                require(info.size.width >= 16 && info.size.height >= 16 && longest <= 30000)
                val factor = min(1f, 2400f / longest)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(
                    (info.size.width * factor).roundToInt().coerceAtLeast(1),
                    (info.size.height * factor).roundToInt().coerceAtLeast(1)
                )
            }
            decoded = bitmap
            val cropped = trimAlpha(bitmap)
            trimmed = cropped
            FileOutputStream(pending).use { out ->
                if (!cropped.compress(Bitmap.CompressFormat.PNG, 100, out)) return null
                out.fd.sync()
            }
            if (pending.length() < 128L) return null
            try {
                java.nio.file.Files.move(pending.toPath(), destination.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(pending.toPath(), destination.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            val title = context.contentResolver.query(uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }?.substringBeforeLast('.')?.take(36).orEmpty().ifBlank { "Image layer" }
            val item = WallpaperLayer(id, "image", title)
            save(stored() + item)
            item
        } catch (_: Exception) {
            destination.delete()
            null
        } finally {
            pending.delete()
            if (trimmed !== decoded && trimmed?.isRecycled == false) trimmed.recycle()
            if (decoded?.isRecycled == false) decoded.recycle()
        }
    }

    private fun trimAlpha(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val small = Bitmap.createScaledBitmap(src, min(256, src.width), min(256, src.height), true)
        try {
            val w = small.width
            val h = small.height
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            var minX = w; var minY = h; var maxX = -1; var maxY = -1
            for (y in 0 until h) for (x in 0 until w) {
                if ((pixels[y * w + x] ushr 24) > 10) {
                    minX = min(minX, x); minY = min(minY, y)
                    maxX = max(maxX, x); maxY = max(maxY, y)
                }
            }
            if (maxX < 0 || maxY < 0) return src
            val left = (minX.toFloat() / w * src.width).toInt().coerceIn(0, src.width - 1)
            val top = (minY.toFloat() / h * src.height).toInt().coerceIn(0, src.height - 1)
            val right = ((maxX + 1f) / w * src.width).roundToInt().coerceIn(left + 1, src.width)
            val bottom = ((maxY + 1f) / h * src.height).roundToInt().coerceIn(top + 1, src.height)
            if (left == 0 && top == 0 && right == src.width && bottom == src.height) return src
            return Bitmap.createBitmap(src, left, top, right - left, bottom - top)
        } finally {
            if (small !== src && !small.isRecycled) small.recycle()
        }
    }

    private data class Cache(val bitmap: Bitmap, val modified: Long)
    private val images = LinkedHashMap<String, Cache>()

    /**
     * GPU canvas gets at most four cached independent layers per process.
     * All previews and WallpaperService instances own separate bitmap caches.
     */
    private fun image(id: String): Bitmap? {
        val file = assetFile(id)
        if (!file.isFile) return null
        val cache = images[id]
        if (cache?.modified == file.lastModified() && !cache.bitmap.isRecycled)
            return cache.bitmap
        val decoded = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val edge = max(info.size.width, info.size.height)
                val ratio = min(1f, 1700f / edge.coerceAtLeast(1))
                decoder.setTargetSize(
                    (info.size.width * ratio).roundToInt().coerceAtLeast(1),
                    (info.size.height * ratio).roundToInt().coerceAtLeast(1))
            }
        } catch (_: Exception) { null } ?: return null
        images.remove(id)?.bitmap?.let { if (!it.isRecycled) it.recycle() }
        images[id] = Cache(decoded, file.lastModified())
        while (images.size > 4) {
            val oldest = images.entries.firstOrNull() ?: break
            images.remove(oldest.key)
            if (!oldest.value.bitmap.isRecycled) oldest.value.bitmap.recycle()
        }
        return decoded
    }

    /** Sprite uses content crop and position rather than full-screen scaling. */
    fun drawImage(canvas: Canvas, layer: WallpaperLayer, viewport: RectF) {
        val bmp = image(layer.id) ?: return
        val w = viewport.width().coerceAtLeast(1f)
        val h = viewport.height().coerceAtLeast(1f)
        val scale = min(w * .68f / bmp.width, h * .54f / bmp.height) * layer.scale
        val dw = bmp.width * scale
        val dh = bmp.height * scale
        val cx = viewport.left + layer.x * w
        val cy = viewport.top + layer.y * h
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (layer.opacity * 255).roundToInt().coerceIn(0, 255)
        }
        canvas.save()
        canvas.clipRect(viewport)
        canvas.rotate(layer.rotation, cx, cy)
        canvas.drawBitmap(bmp, null, RectF(
            cx - dw * .5f, cy - dh * .5f,
            cx + dw * .5f, cy + dh * .5f
        ), paint)
        canvas.restore()
    }
}
