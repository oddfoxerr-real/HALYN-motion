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
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun available(): Boolean = imageFile.isFile && imageFile.length() > 0

    fun install(uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri).orEmpty()
        // Some SAF providers omit MIME; the actual bytes are verified by ImageDecoder.
        if (mime.isNotBlank() && !mime.startsWith("image/") &&
            mime != "application/octet-stream") return false
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
    /**
     * Each change uses unique staging files and an atomic filesystem replace.
     * The old wallpaper remains readable until the new one is fully encoded.
     * A generation number invalidates caches and old subject masks reliably
     * (file timestamps alone are not sufficient when switching quickly).
     */
    fun installStream(input: InputStream, limitBytes: Long = 48L * 1024L * 1024L): Boolean {
        val raw = File.createTempFile("halyn_input_", ".img", context.cacheDir)
        val pending = File.createTempFile("halyn_photo_", ".webp", context.filesDir)
        return try {
            FileOutputStream(raw).use { out ->
                val buffer = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > limitBytes) return false
                    out.write(buffer, 0, n)
                }
                out.fd.sync()
            }
            if (raw.length() < 1024L) return false
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(raw)) {
                decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                require(info.size.width >= 32 && info.size.height >= 32 && longest <= 30000)
                val scale = min(1f, 4096f / longest.toFloat())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1)
                )
            }
            try {
                FileOutputStream(pending).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, out)) return false
                    out.fd.sync()
                }
            } finally {
                bitmap.recycle()
            }
            if (pending.length() < 1024L) return false
            synchronized(INSTALL_LOCK) {
                val target = imageFile
                // Both files reside in filesDir, permitting an atomic rename on ext4/f2fs.
                try {
                    java.nio.file.Files.move(
                        pending.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    // Standard REPLACE_EXISTING is still a single filesystem move.
                    java.nio.file.Files.move(
                        pending.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                }
                // Every newly committed photo has a distinct revision. Masks tied
                // to previous revisions are automatically rejected while they remain
                // on disk for potential recovery.
                val prefs = settings()
                prefs.edit()
                    .putLong("photo_generation", prefs.getLong("photo_generation", 0L) + 1L)
                    .putBoolean("depth_enabled", false)
                    .commit()
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            raw.delete()
            pending.delete()
        }
    }

    private var cachedGeneration = Long.MIN_VALUE
    private var attemptedGeneration = Long.MIN_VALUE

    private fun image(): Bitmap? {
        val generation = generation()
        if (cachedGeneration == generation && cached?.isRecycled == false) return cached
        if (attemptedGeneration == generation) return cached?.takeUnless { it.isRecycled }
        attemptedGeneration = generation
        val file = imageFile
        if (!file.isFile || file.length() < 1024L) return cached
        val newBitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // Keep the downloaded 4K file, but render near device resolution
                // to reduce the live wallpaper's GPU/heap pressure.
                val longest = max(info.size.width, info.size.height)
                val factor = min(1f, 2560f / longest.coerceAtLeast(1))
                decoder.setTargetSize(
                    (info.size.width * factor).roundToInt().coerceAtLeast(1),
                    (info.size.height * factor).roundToInt().coerceAtLeast(1)
                )
            }
        } catch (_: Exception) { null }
        if (newBitmap != null) {
            val old = cached
            cached = newBitmap
            cachedGeneration = generation
            if (old !== newBitmap && old?.isRecycled == false) old.recycle()
        }
        return cached
    }

    fun generation(): Long = settings().getLong("photo_generation", 0L)

    private fun settings() = context.getSharedPreferences(
        "halyn_motion_settings", Context.MODE_PRIVATE
    )

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

    /**
     * Draw the transparent cutout in precisely the same image-space crop as
     * the backdrop, then move only the subject a few pixels with the gyro.
     * This is rendered AFTER the clock to create real optical occlusion.
     */
    fun drawSubjectLayer(
        canvas: Canvas, subject: Bitmap, gyro: ParallaxPose, strength: Float
    ) {
        if (subject.isRecycled) return
        val w = canvas.width.toFloat().coerceAtLeast(1f)
        val h = canvas.height.toFloat().coerceAtLeast(1f)
        val bw = subject.width.toFloat()
        val bh = subject.height.toFloat()
        val scale = max(w / bw, h / bh) * 1.08f
        val srcW = min(bw, w / scale)
        val srcH = min(bh, h / scale)
        val slackX = (bw - srcW) * .5f
        val slackY = (bh - srcH) * .5f
        val left = ((bw - srcW) * .5f + gyro.x * slackX * .9f)
            .coerceIn(0f, (bw - srcW).coerceAtLeast(0f))
        val top = ((bh - srcH) * .5f + gyro.y * slackY * .9f)
            .coerceIn(0f, (bh - srcH).coerceAtLeast(0f))
        val crop = Rect(
            left.roundToInt(), top.roundToInt(),
            (left + srcW).roundToInt().coerceAtMost(subject.width),
            (top + srcH).roundToInt().coerceAtMost(subject.height)
        )
        canvas.save()
        val motion = strength.coerceIn(0f, 2f)
        canvas.translate(gyro.x * w * .018f * motion, gyro.y * h * .012f * motion)
        canvas.drawBitmap(subject, crop, RectF(0f, 0f, w, h), paint)
        canvas.restore()
    }

    companion object {
        private val INSTALL_LOCK = Any()
        const val SCENE_INDEX = 51 // Preserve saved online/offline photo wallpaper slots.
    }
}
