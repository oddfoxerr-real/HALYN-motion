package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
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
    private var cachedModified = -1L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun available(): Boolean = file.isFile && file.length() > 1024L

    fun clear() {
        file.delete()
        cached?.recycle()
        cached = null
        cachedModified = -1L
    }

    /** Decode with an upper bound so one photograph cannot exhaust the wallpaper process. */
    private fun decode(file: File, longestEdge: Int = 3072): Bitmap =
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
        var source: Bitmap? = null
        return try {
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime !in setOf("image/png", "image/webp")) return false
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
            write(image)
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
            write(result)
        } catch (_: Exception) {
            false
        } finally {
            source?.recycle()
            output?.recycle()
        }
    }

    private fun write(bitmap: Bitmap): Boolean {
        val next = File(context.filesDir, "halyn_subject_layer.next.png")
        return try {
            FileOutputStream(next).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return false
                out.fd.sync()
            }
            if (next.length() < 1024L) return false
            if (!next.renameTo(file)) {
                next.copyTo(file, overwrite = true)
                next.delete()
            }
            cached?.recycle()
            cached = null
            cachedModified = -1L
            true
        } finally {
            next.delete()
        }
    }

    fun draw(canvas: Canvas, state: MotionState, gyro: ParallaxPose, photo: PhotoWallpaperStore) {
        if (!state.depthEnabled) return
        val foreground = read() ?: return
        photo.drawSubjectLayer(canvas, foreground, gyro, state.depthStrength)
    }

    private fun read(): Bitmap? {
        val source = file
        if (!source.isFile) return null
        if (cached != null && !cached!!.isRecycled && cachedModified == source.lastModified()) {
            return cached
        }
        cached?.recycle()
        cached = try { decode(source) } catch (_: Exception) { null }
        cachedModified = source.lastModified()
        return cached
    }
}
