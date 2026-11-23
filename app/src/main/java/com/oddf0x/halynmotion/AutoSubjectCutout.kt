package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Handler
import android.os.Looper
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * On-device automatic foreground masking with Google ML Kit Subject Segmentation.
 *
 * The model is delivered by Google Play services and may need downloading once.
 * Inference runs only when a person selects Auto cutout or opts into automatic
 * masking after image imports. HALYN does not upload photographs for processing.
 *
 * A generation identifier prevents an old async inference from being applied
 * over a photo selected after processing began.
 */
class AutoSubjectCutout(private val context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var busy = false
    private var activeGeneration = Long.MIN_VALUE
    private var queued: Pair<(String) -> Unit, (Boolean, String) -> Unit>? = null

    fun start(
        onProgress: (String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        val store = PhotoWallpaperStore(app)
        if (busy) {
            if (store.generation() != activeGeneration) {
                queued = onProgress to onComplete
                onProgress("New photo queued. Finishing the previous mask safely…")
            } else {
                onProgress("Already detecting the subject in this photo…")
            }
            return
        }
        if (!store.available()) {
            onComplete(false, "Select a photo before using automatic cutout.")
            return
        }
        busy = true
        val expectedGeneration = store.generation()
        activeGeneration = expectedGeneration
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val segmenter = try {
            SubjectSegmentation.getClient(options)
        } catch (error: Exception) {
            busy = false
            onComplete(false, "Google Play services cannot start subject detection.")
            return
        }

        fun finish(ok: Boolean, message: String) {
            main.post {
                busy = false
                activeGeneration = Long.MIN_VALUE
                try { segmenter.close() } catch (_: Exception) { }
                val next = queued
                queued = null
                if (next != null) {
                    // Suppress stale photo success/failure UI; continue using the
                    // most recently selected image instead.
                    start(next.first, next.second)
                } else {
                    onComplete(ok, message)
                }
            }
        }

        fun process() {
            if (store.generation() != expectedGeneration) {
                finish(false, "The photo changed. Select Auto cutout on the new image.")
                return
            }
            onProgress("Detecting the main subject on this device…")
            Thread {
                var input: Bitmap? = null
                try {
                    val photo = File(app.filesDir, "halyn_my_wallpaper.webp")
                    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(photo)) {
                            decoder, info, _ ->
                        val longest = max(info.size.width, info.size.height)
                        require(info.size.width >= 32 && info.size.height >= 32)
                        val ratio = min(1f, 1600f / longest)
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.setTargetSize(
                            (info.size.width * ratio).roundToInt().coerceAtLeast(1),
                            (info.size.height * ratio).roundToInt().coerceAtLeast(1)
                        )
                    }
                    input = bitmap
                    val original = bitmap
                    segmenter.process(InputImage.fromBitmap(original, 0))
                        .addOnSuccessListener { result ->
                            val foreground = result.foregroundBitmap
                            if (foreground == null) {
                                original.recycle()
                                finish(false, "No clear subject detected. Use a transparent layer or trace manually.")
                                return@addOnSuccessListener
                            }
                            onProgress("Saving the cutout…")
                            Thread {
                                val success = try {
                                    DepthLayerStore(app)
                                        .installSegmentedForeground(foreground, expectedGeneration)
                                } catch (_: Exception) {
                                    false
                                } finally {
                                    if (foreground !== original && !foreground.isRecycled) {
                                        foreground.recycle()
                                    }
                                    if (!original.isRecycled) original.recycle()
                                }
                                if (success) {
                                    MotionPreferences(app).setDepthEnabled(true)
                                    finish(true, "Automatic subject cutout ready.")
                                } else if (store.generation() != expectedGeneration) {
                                    finish(false, "Photo changed during masking. Try the new image.")
                                } else {
                                    finish(false, "Subject edges could not be saved. Try a different photo or manual outline.")
                                }
                            }.start()
                        }
                        .addOnFailureListener { error ->
                            if (!original.isRecycled) original.recycle()
                            val detail = error.message.orEmpty().lowercase()
                            finish(false, if ("model" in detail || "download" in detail)
                                "The subject model is still downloading. Retry Auto cutout."
                            else "Automatic cutout failed on this photo. Manual tracing remains available.")
                        }
                } catch (_: Exception) {
                    input?.let { if (!it.isRecycled) it.recycle() }
                    finish(false, "Could not decode the selected photo for auto masking.")
                }
            }.start()
        }

        try {
            val installer = ModuleInstall.getClient(app)
            onProgress("Checking the on-device subject model…")
            installer.areModulesAvailable(segmenter)
                .addOnSuccessListener { availability ->
                    if (availability.areModulesAvailable()) {
                        process()
                        return@addOnSuccessListener
                    }
                    onProgress("Installing the on-device subject model…")
                    val request = ModuleInstallRequest.newBuilder().addApi(segmenter).build()
                    installer.installModules(request)
                        .addOnSuccessListener {
                            // A successful request is NOT proof that the download
                            // completed. Check real availability before inference.
                            var attempts = 0
                            fun check() {
                                if (store.generation() != expectedGeneration) {
                                    finish(false, "Photo changed while the model was loading.")
                                    return
                                }
                                installer.areModulesAvailable(segmenter)
                                    .addOnSuccessListener { result ->
                                        if (result.areModulesAvailable()) {
                                            process()
                                        } else if (++attempts < 15) {
                                            main.postDelayed({ check() }, 1800L)
                                        } else {
                                            finish(false,
                                                "The model is still downloading. Retry Auto cutout after Google Play services finishes.")
                                        }
                                    }
                                    .addOnFailureListener {
                                        finish(false, "Unable to check the on-device subject model.")
                                    }
                            }
                            check()
                        }
                        .addOnFailureListener {
                            finish(false, "Could not download the masking model. Check Google Play services and internet.")
                        }
                }
                .addOnFailureListener {
                    finish(false, "Google Play services cannot check the subject model.")
                }
        } catch (_: Exception) {
            finish(false, "Automatic detection is unavailable on this device.")
        }
    }
}
