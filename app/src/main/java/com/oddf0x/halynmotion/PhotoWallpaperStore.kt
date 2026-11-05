package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** User-provided images remain on-device. No media library scans or cloud uploads. */
class PhotoWallpaperStore(private val context: Context) {
    private val imageFile get() = File(context.filesDir, "halyn_my_wallpaper.webp")
    private var cached: Bitmap? = null
    private var cachedModified = -1L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun available(): Boolean = imageFile.isFile && imageFile.length() > 0L

    /** Decode from SAF into a bounded temporary file, downsample, validate, then replace. */
    fun import(uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri) ?: return false
        if (!mime.startsWith("image/")) return false
        val raw = File(context.cacheDir, "halyn_image_input.tmp")
        val output = File(context.filesDir, "halyn_image_new.webp")
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            input.use { source ->
                FileOutputStream(raw).use { destination ->
                    val buffer = ByteArray(32 * 1024)
                    var length = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        length += read
                        if (length > 24L * 1024 * 1024) return false
                        destination.write(buffer, 0, read)
                    }
                }
            }
            if (raw.length() < 1024L) return false
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(raw.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            val options = BitmapFactory.Options().apply {
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > 2200) sample *= 2
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(raw.absolutePath, options) ?: return false
            try {
                FileOutputStream(output).use { stream ->
                    if (!decoded.compress(Bitmap.CompressFormat.WEBP_LOSSY, 91, stream)) return false
                    stream.fd.sync()
                }
            } finally {
                decoded.recycle()
            }
            if (output.length() < 1000L) return false
            val previous = imageFile
            // Move a completely decoded, validated image into place only after
            // all work succeeds. The old image stays if import fails.
            output.copyTo(previous, overwrite = true)
            output.delete()
            cached?.recycle()
            cached = null
            cachedModified = -1L
            true
        } catch (_: Exception) {
            false
        } finally {
            raw.delete()
            output.delete()
        }
    }

    private fun image(): Bitmap? {
        val file = imageFile
        if (!file.isFile) return null
        if (cached != null && cachedModified == file.lastModified() && cached?.isRecycled == false) {
            return cached
        }
        cached?.recycle()
        cached = try {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } catch (_: Exception) { null }
        cachedModified = file.lastModified()
        return cached
    }

    fun draw(canvas: Canvas, gyro: ParallaxPose) {
        val w = canvas.width.toFloat().coerceAtLeast(1f)
        val h = canvas.height.toFloat().coerceAtLeast(1f)
        val photo = image()
        if (photo == null) {
            canvas.drawColor(Color.rgb(26, 35, 54))
            return
        }
        val bw = photo.width.toFloat()
        val bh = photo.height.toFloat()
        // Fit center-crop with 8% overscan. Movement only within the available
        // crop margin so gyro can never reveal black edges.
        val scale = max(w / bw, h / bh) * 1.08f
        val srcW = min(bw, w / scale)
        val srcH = min(bh, h / scale)
        val slackX = (bw - srcW) * .5f
        val slackY = (bh - srcH) * .5f
        val x = (bw - srcW) * .5f + gyro.x * slackX * .9f
        val y = (bh - srcH) * .5f + gyro.y * slackY * .9f
        val left = x.coerceIn(0f,(bw-srcW).coerceAtLeast(0f))
        val top = y.coerceIn(0f,(bh-srcH).coerceAtLeast(0f))
        val source = Rect(left.roundToInt(),top.roundToInt(),
            (left+srcW).roundToInt().coerceAtMost(photo.width),
            (top+srcH).roundToInt().coerceAtMost(photo.height))
        canvas.drawBitmap(photo, source, RectF(0f,0f,w,h), paint)
    }

    companion object {
        const val SCENE_INDEX = PosterSceneRenderer.LAST + 1
    }
}
