package com.oddf0x.halynmotion

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Single-stroke, local manual subject masking. Designed for the existing
 * photo rather than requiring a fragile network or portrait-only model.
 *
 * The outline is recorded in original-image coordinates. The actual high-quality
 * alpha PNG is generated off the UI thread by DepthLayerStore.
 */
class DepthMaskEditor(
    private val host: Activity,
    private val onSaved: () -> Unit
) {
    fun show() {
        val original = File(host.filesDir, "halyn_my_wallpaper.webp")
        if (!original.isFile) {
            Toast.makeText(host, "Choose a photograph first", Toast.LENGTH_LONG).show()
            return
        }
        val preview = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(original)) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val factor = min(1f, 1200f / max(width, height))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(
                    (width * factor).roundToInt().coerceAtLeast(1),
                    (height * factor).roundToInt().coerceAtLeast(1)
                )
            }
        } catch (_: Exception) {
            Toast.makeText(host, "Unable to open photograph", Toast.LENGTH_LONG).show()
            return
        }
        val density = host.resources.displayMetrics.density
        val dp = { value: Int -> (value * density).roundToInt() }
        val column = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
        }
        column.addView(TextView(host).apply {
            text = "Draw one continuous outline around the subject. Lift to close it. " +
                "Draw again to replace the outline. This creates a real transparent foreground."
            setTextColor(Color.rgb(211, 211, 215))
            textSize = 13f
            setPadding(dp(3), 0, dp(3), dp(12))
        })
        val editor = OutlineCanvas(host, preview)
        column.addView(editor, LinearLayout.LayoutParams(
            -1, min(dp(470), (host.resources.displayMetrics.heightPixels * .57f).roundToInt())
                .coerceAtLeast(dp(230))
        ))
        val dialog = AlertDialog.Builder(host)
            .setTitle("Create subject depth")
            .setView(column)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save cutout", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selection = editor.outline()
                if (selection.size < 8) {
                    Toast.makeText(host, "Trace around the entire subject first", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                Thread {
                    val ok = DepthLayerStore(host.applicationContext).createCutout(selection)
                    host.runOnUiThread {
                        if (!host.isFinishing && !host.isDestroyed) {
                            if (ok) {
                                MotionPreferences(host).setDepthEnabled(true)
                                onSaved()
                                dialog.dismiss()
                                Toast.makeText(host, "Subject cutout created", Toast.LENGTH_SHORT).show()
                            } else {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                Toast.makeText(host, "Could not save subject cutout", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }.start()
            }
        }
        dialog.setOnDismissListener { preview.recycle() }
        dialog.show()
    }

    private class OutlineCanvas(host: Activity, private val photo: Bitmap) : View(host) {
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * resources.displayMetrics.density
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = Color.WHITE
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f * resources.displayMetrics.density
            color = Color.argb(140, 0, 0, 0)
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(48, 255, 255, 255)
            style = Paint.Style.FILL
        }
        private val rect = RectF()
        private val points = ArrayList<PointF>()
        private var finished = false

        init {
            contentDescription = "Trace around the subject with one finger, then save the cutout"
            isFocusable = true
            isClickable = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(13, 14, 18))
            val scale = min(width.toFloat() / photo.width, height.toFloat() / photo.height)
            val dstW = photo.width * scale
            val dstH = photo.height * scale
            rect.set((width - dstW) * .5f, (height - dstH) * .5f,
                (width + dstW) * .5f, (height + dstH) * .5f)
            canvas.drawBitmap(photo, null, rect, imagePaint)
            if (points.isNotEmpty()) {
                val path = Path()
                points.forEachIndexed { i, point ->
                    val x = rect.left + point.x * rect.width()
                    val y = rect.top + point.y * rect.height()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (finished) {
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }
                canvas.drawPath(path, glowPaint)
                canvas.drawPath(path, linePaint)
            }
        }

        fun outline(): List<PointF> = if (finished) points.map { PointF(it.x, it.y) } else emptyList()

        private fun record(x: Float, y: Float) {
            if (!rect.contains(x, y)) return
            val point = PointF(
                ((x - rect.left) / rect.width()).coerceIn(0f, 1f),
                ((y - rect.top) / rect.height()).coerceIn(0f, 1f)
            )
            val last = points.lastOrNull()
            if (last == null || hypot(last.x - point.x, last.y - point.y) > .003f) {
                points.add(point)
            }
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!rect.contains(event.x, event.y)) return false
                    points.clear()
                    finished = false
                    record(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    for (index in 0 until event.historySize) {
                        record(event.getHistoricalX(index), event.getHistoricalY(index))
                    }
                    record(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    record(event.x, event.y)
                    finished = points.size >= 8
                    performClick()
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    points.clear()
                    finished = false
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
