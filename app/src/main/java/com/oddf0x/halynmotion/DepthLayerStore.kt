package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * HALYN's offline foreground asset. A subject layer and background share image-space
 * coordinates; the real clock is drawn between them. No fake screenshot, cloud masking,
 * continuous segmentation process, or proprietary model is required.
 *
 * A transparent PNG from an artist or a hand-traced subject is supported. The mask
 * editor saves a real alpha cutout with anti-aliased edges. Disk writes are atomic and
 * must be called from a worker thread. Compositing is a single bitmap operation.
 */
class DepthLayerStore(private val context: Context) {
    private val file get() = File(context.filesDir, "halyn_subject_layer.png")
    private val photoFile get() = File(context.filesDir, "halyn_my_wallpaper.webp")
    private var cached: Bitmap? = null
    private var cachedRevision = Long.MIN_VALUE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    private fun settings() = context.getSharedPreferences(
        "halyn_motion_settings", Context.MODE_PRIVATE
    )

    private fun currentPhotoGeneration() =
        settings().getLong("photo_generation", 0L)

    fun available(): Boolean {
        val prefs = settings()
        return file.isFile && file.length() > 1024L &&
            prefs.getLong("depth_source_generation", -1L) == currentPhotoGeneration()
    }

    fun clear() {
        file.delete()
        cached?.recycle()
        cached = null
        cachedRevision = Long.MIN_VALUE
        val prefs = settings()
        prefs.edit()
            .putLong("depth_source_generation", -1L)
            .putLong("depth_generation", prefs.getLong("depth_generation", 0L) + 1L)
            .apply()
    }

    /** Decode with an upper bound so one photograph cannot exhaust the wallpaper process. */
    private fun decode(file: File, longestEdge: Int = 2560): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val length = max(info.size.width, info.size.height)
            require(info.size.width >= 32 && info.size.height >= 32 && length <= 30000)
            val factor = min(1f, longestEdge.toFloat() / length)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(
                (info.size.width * factor).roundToInt().coerceAtLeast(1),
                (info.size.height * factor).roundToInt().coerceAtLeast(1)
            )
        }

    /** The selected overlay must be transparent and match the full background aspect ratio. */
    fun installTransparentLayer(uri: Uri): Boolean {
        val expectedGeneration = currentPhotoGeneration()
        var source: Bitmap? = null
        return try {
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime.isNotBlank() && mime !in setOf("image/png", "image/webp", "application/octet-stream")) return false
            val image = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, uri)
            ) { decoder, info, _ ->
                val length = max(info.size.width, info.size.height)
                require(info.size.width >= 32 && info.size.height >= 32 && length <= 30000)
                val ratio = min(1f, 3072f / length)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(
                    (info.size.width * ratio).roundToInt().coerceAtLeast(1),
                    (info.size.height * ratio).roundToInt().coerceAtLeast(1)
                )
            }
            source = image
            val backdrop = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photoFile.absolutePath, backdrop)
            if (backdrop.outWidth <= 0 || backdrop.outHeight <= 0) return false
            val imageRatio = image.width.toFloat() / image.height
            val backdropRatio = backdrop.outWidth.toFloat() / backdrop.outHeight
            if (kotlin.math.abs(imageRatio / backdropRatio - 1f) > .025f) return false
            if (!image.hasAlpha()) return false
            var hasTransparency = false
            val stepX = (image.width / 32).coerceAtLeast(1)
            val stepY = (image.height / 32).coerceAtLeast(1)
            outer@ for (y in 0 until image.height step stepY) {
                for (x in 0 until image.width step stepX) {
                    if (android.graphics.Color.alpha(image.getPixel(x, y)) < 248) {
                        hasTransparency = true
                        break@outer
                    }
                }
            }
            if (!hasTransparency) return false
            write(image, expectedGeneration)
        } catch (_: Exception) {
            false
        } finally {
            source?.recycle()
        }
    }

    /**
     * Creates a full-resolution, anti-aliased subject cutout from an outline traced
     * in normalized source-image coordinates (not preview or display coordinates).
     * This makes the cutout stay aligned on different launcher screen ratios.
     */
    fun createCutout(points: List<PointF>): Boolean {
        if (points.size < 8 || !photoFile.isFile) return false
        val expectedGeneration = currentPhotoGeneration()
        var source: Bitmap? = null
        var output: Bitmap? = null
        return try {
            val bitmap = decode(photoFile)
            source = bitmap
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            output = result
            val path = Path()
            points.forEachIndexed { index, p ->
                val x = p.x.coerceIn(0f, 1f) * bitmap.width
                val y = p.y.coerceIn(0f, 1f) * bitmap.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            val canvas = Canvas(result)
            val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawPath(path, mask)
            mask.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, 0f, 0f, mask)
            mask.xfermode = null
            write(result, expectedGeneration)
        } catch (_: Exception) {
            false
        } finally {
            source?.recycle()
            output?.recycle()
        }
    }

    /**
     * Called by the on-device ML Kit subject detector. Segmented foregrounds
     * use the original photo's aspect ratio, so no screen-coordinate scaling
     * or user interaction is required. Never apply a stale ML result to a
     * newly selected wallpaper.
     */
    fun installSegmentedForeground(
        bitmap: Bitmap, expectedGeneration: Long
    ): Boolean {
        if (bitmap.isRecycled || !bitmap.hasAlpha() || !photoFile.isFile) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photoFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        val photoAspect = bounds.outWidth.toFloat() / bounds.outHeight
        val maskAspect = bitmap.width.toFloat() / bitmap.height
        if (kotlin.math.abs(photoAspect / maskAspect - 1f) > .03f) return false
        val strideX = (bitmap.width / 32).coerceAtLeast(1)
        val strideY = (bitmap.height / 32).coerceAtLeast(1)
        var transparent = 0
        var visible = 0
        for (y in 0 until bitmap.height step strideY) for (x in 0 until bitmap.width step strideX) {
            val alpha = android.graphics.Color.alpha(bitmap.getPixel(x, y))
            if (alpha < 200) transparent++ else if (alpha > 50) visible++
        }
        // Reject blank or fully opaque ML outputs; they obscure the clock.
        if (transparent < 3 || visible < 3) return false
        return write(bitmap, expectedGeneration)
    }

    private fun write(bitmap: Bitmap, expectedGeneration: Long): Boolean {
        val pending = File.createTempFile("halyn_depth_", ".png", context.filesDir)
        return try {
            if (currentPhotoGeneration() != expectedGeneration) return false
            FileOutputStream(pending).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return false
                out.fd.sync()
            }
            if (pending.length() < 1024L || currentPhotoGeneration() != expectedGeneration) {
                return false
            }
            try {
                java.nio.file.Files.move(
                    pending.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    pending.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
            // Source generation is checked both when saving and drawing.
            // A concurrent image change cannot display the previous cutout.
            val prefs = settings()
            prefs.edit()
                .putLong("depth_source_generation", expectedGeneration)
                .putLong("depth_generation", prefs.getLong("depth_generation", 0L) + 1L)
                .apply()
            cached?.recycle()
            cached = null
            cachedRevision = Long.MIN_VALUE
            currentPhotoGeneration() == expectedGeneration
        } catch (_: Exception) {
            false
        } finally {
            pending.delete()
        }
    }

    fun draw(
        canvas: Canvas, state: MotionState, gyro: ParallaxPose,
        photo: PhotoWallpaperStore, viewport: RectF? = null
    ) {
        if (!state.depthEnabled) return
        val foreground = read() ?: return
        photo.drawSubjectLayer(
            canvas, foreground, gyro, state.depthStrength, viewport, state.photoFitMode
        )
    }

    private fun read(): Bitmap? {
        if (!available()) return null
        val revision = settings().getLong("depth_generation", 0L)
        if (cachedRevision == revision && cached?.isRecycled == false) return cached
        val decoded = try { decode(file) } catch (_: Exception) { null } ?: return null
        val old = cached
        cached = decoded
        cachedRevision = revision
        if (old !== decoded && old?.isRecycled == false) old.recycle()
        return cached
    }

}
