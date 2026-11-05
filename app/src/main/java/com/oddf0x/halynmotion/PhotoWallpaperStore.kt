package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Holds the selected 4K wallpaper on device for offline use.
 * Decodes to max 4096 px on the longest edge; does not reduce 4K to 2K.
 * Original source/creator metadata lives separately in MotionPreferences.
 */
class PhotoWallpaperStore(private val context: Context) {
    private val imageFile get() = File(context.filesDir, "halyn_my_wallpaper.webp")
    private var cached: Bitmap? = null
    private var cachedModified = -1L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun available(): Boolean = imageFile.isFile && imageFile.length() > 0

    fun install(uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri) ?: return false
        if (!mime.startsWith("image/")) return false
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                installStream(it, 48L * 1024L * 1024L)
            } ?: false
        } catch (_: Exception) { false }
    }

    /**
     * The caller must run this OFF the UI thread.
     * Never overwrite a working wallpaper until a fully decoded replacement
     * has been encoded to a temporary local file.
     */
    fun installStream(input: InputStream, limitBytes: Long = 48L * 1024L * 1024L): Boolean {
        val raw = File(context.cacheDir, "halyn_wallpaper_download.tmp")
        val next = File(context.filesDir, "halyn_wallpaper_next.webp")
        return try {
            FileOutputStream(raw).use { out ->
                val buffer = ByteArray(32 * 1024)
                var count = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    count += n
                    if (count > limitBytes) return false
                    out.write(buffer, 0, n)
                }
                out.fd.sync()
            }
            if (raw.length() < 1024) return false

            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(raw)) {
                decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (info.size.width < 32 || info.size.height < 32 ||
                    longest > 30000) throw IllegalArgumentException("Invalid image dimensions")
                val factor = min(1f, 4096f / longest.toFloat())
                val width = (info.size.width * factor).roundToInt().coerceAtLeast(1)
                val height = (info.size.height * factor).roundToInt().coerceAtLeast(1)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(width, height)
            }
            try {
                FileOutputStream(next).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, out)) {
                        return false
                    }
                    out.fd.sync()
                }
            } finally {
                bitmap.recycle()
            }
            if (next.length() < 1024) return false
            val target = imageFile
            if (!next.renameTo(target)) {
                // Rare filesystem fallback; previous file is left untouched
                // on normal successful atomic rename.
                next.copyTo(target, overwrite = true)
                next.delete()
            }
            cached?.recycle()
            cached = null
            cachedModified = -1L
            true
        } catch (_: Exception) {
            false
        } finally {
            raw.delete()
            next.delete()
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
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (_: Exception) { null }
        cachedModified = file.lastModified()
        return cached
    }

    fun draw(canvas: Canvas, gyro: ParallaxPose) {
        val w = canvas.width.toFloat().coerceAtLeast(1f)
        val h = canvas.height.toFloat().coerceAtLeast(1f)
        val photo = image()
        if (photo == null) {
            canvas.drawColor(Color.rgb(12, 16, 26))
            return
        }
        val bw = photo.width.toFloat()
        val bh = photo.height.toFloat()
        // Fit centre crop + 8% overscan; clamp gyro to crop margins.
        val scale = max(w / bw, h / bh) * 1.08f
        val srcW = min(bw, w / scale)
        val srcH = min(bh, h / scale)
        val slackX = (bw - srcW) * .5f
        val slackY = (bh - srcH) * .5f
        val x = (bw - srcW) * .5f + gyro.x * slackX * .9f
        val y = (bh - srcH) * .5f + gyro.y * slackY * .9f
        val left = x.coerceIn(0f, (bw - srcW).coerceAtLeast(0f))
        val top = y.coerceIn(0f, (bh - srcH).coerceAtLeast(0f))
        val crop = Rect(
            left.roundToInt(), top.roundToInt(),
            (left + srcW).roundToInt().coerceAtMost(photo.width),
            (top + srcH).roundToInt().coerceAtMost(photo.height)
        )
        canvas.drawBitmap(photo, crop, RectF(0f, 0f, w, h), paint)
    }

    companion object {
        const val SCENE_INDEX = PosterSceneRenderer.LAST + 1
    }
}
