package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Real-time clock painted onto the actual WallpaperService Canvas.
 * No app widgets, overlay permission, polling service, or network connection.
 *
 * Styles: Digital, Split, Monospace, Analog, and Orbit. Every style supports
 * optional date, 12/24 hour formatting and a live seconds indicator.
 */
class ClockOverlayRenderer {
    private val typefaceLight = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val typefaceMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val typefaceMono = Typeface.create("monospace", Typeface.NORMAL)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val dateFormatter = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    private val calendar = Calendar.getInstance()
    private var lastSecond = Long.MIN_VALUE
    private var timeKey = -1
    private var time = ""
    private var hours = ""
    private var minutes = ""
    private var suffix = ""
    private var date = ""
    private var second = 0
    private var hour = 0
    private var minute = 0

    private fun updateTime(now: Long, state: MotionState) {
        val secondKey = now / 1000L
        val key = (if (state.clock24h) 1 else 0) * 2 + if (state.clockSeconds) 1 else 0
        if (lastSecond == secondKey && timeKey == key) return
        lastSecond = secondKey
        timeKey = key
        calendar.timeInMillis = now
        hour = calendar.get(Calendar.HOUR_OF_DAY)
        minute = calendar.get(Calendar.MINUTE)
        second = calendar.get(Calendar.SECOND)
        val shownHour = if (state.clock24h) hour else (hour % 12).let { if (it == 0) 12 else it }
        hours = if (state.clock24h) shownHour.toString().padStart(2, '0') else shownHour.toString()
        minutes = minute.toString().padStart(2, '0')
        time = hours + ":" + minutes + if (state.clockSeconds) ":"
            + second.toString().padStart(2, '0') else ""
        suffix = if (state.clock24h) "" else if (hour < 12) "AM" else "PM"
        date = dateFormatter.format(Date(now)).uppercase(Locale.getDefault())
    }

    fun draw(canvas: Canvas, state: MotionState, now: Long = System.currentTimeMillis()) {
        if (state.clockStyle == 0) return
        updateTime(now, state)

        val w = canvas.width.toFloat().coerceAtLeast(1f)
        val h = canvas.height.toFloat().coerceAtLeast(1f)
        val centerX = w * .5f
        val centerY = when (state.clockPosition) {
            0 -> h * .265f
            2 -> h * .71f
            else -> h * .49f
        }
        val size = min(w, h * .74f)
        val white = Color.rgb(252, 251, 253)
        val tint = if (state.clockAccent) {
            if (state.palette == 5) state.customColor
            else HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0, 4)]
        } else white
        canvas.save()
        val showDate = state.clockDate
        val progress = (second + (now % 1000L) / 1000f) / 60f
        when (state.clockStyle) {
            1 -> digital(canvas, centerX, centerY, size, tint, showDate, progress)
            2 -> split(canvas, centerX, centerY, size, tint, showDate, progress)
            3 -> mono(canvas, centerX, centerY, size, tint, showDate, progress)
            4 -> analog(canvas, centerX, centerY, size, tint, showDate, progress, state.clockSeconds)
            5 -> orbit(canvas, centerX, centerY, size, tint, showDate, progress)
        }
        canvas.restore()
    }

    private fun ink(color: Int, alpha: Int, size: Float, font: Typeface) {
        labelPaint.reset()
        labelPaint.isAntiAlias = true
        labelPaint.isSubpixelText = true
        labelPaint.color = color
        labelPaint.alpha = alpha
        labelPaint.textSize = size
        labelPaint.typeface = font
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.setShadowLayer(size * .08f, 0f, size * .03f, Color.argb(120, 3, 5, 10))
    }

    private fun line(color: Int, alpha: Int, width: Float) {
        linePaint.reset()
        linePaint.isAntiAlias = true
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.color = color
        linePaint.alpha = alpha
        linePaint.strokeWidth = width
    }

    private fun date(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        ink(color, 205, size, typefaceMedium)
        // Avoid clipping localized dates on narrow wallpapers and studio previews.
        if (labelPaint.measureText(date) > canvas.width * .85f) {
            labelPaint.textSize *= (canvas.width * .85f / labelPaint.measureText(date))
        }
        canvas.drawText(date, x, y, labelPaint)
    }

    private fun digital(
        c: Canvas, x: Float, y: Float, size: Float, color: Int, showDate: Boolean, progress: Float
    ) {
        ink(color, 255, size * .204f, typefaceLight)
        c.drawText(time, x, y + size * .055f, labelPaint)
        if (suffix.isNotEmpty()) {
            ink(color, 200, size * .031f, typefaceMedium)
            c.drawText(suffix, x + size * .32f, y - size * .045f, labelPaint)
        }
        line(color, 90, size * .003f)
        c.drawLine(x - size * .25f, y + size * .11f, x + size * .25f, y + size * .11f, linePaint)
        line(color, 240, size * .0035f)
        c.drawLine(x - size * .25f, y + size * .11f,
            x - size * .25f + size * .5f * progress, y + size * .11f, linePaint)
        if (showDate) date(c, x, y + size * .17f, size * .035f, color)
    }

    private fun split(
        c: Canvas, x: Float, y: Float, size: Float, color: Int, showDate: Boolean, progress: Float
    ) {
        val fontSize = size * .236f
        ink(color, 255, fontSize, typefaceLight)
        c.drawText(hours, x, y - size * .008f, labelPaint)
        c.drawText(minutes, x, y + size * .204f, labelPaint)
        if (suffix.isNotEmpty()) {
            ink(color, 210, size * .032f, typefaceMedium)
            c.drawText(suffix, x + size * .23f, y + size * .015f, labelPaint)
        }
        line(color, 110, size * .0025f)
        c.drawLine(x - size * .09f, y + size * .041f, x + size * .09f, y + size * .041f, linePaint)
        if (showDate) date(c, x, y + size * .28f, size * .031f, color)
    }

    private fun mono(
        c: Canvas, x: Float, y: Float, size: Float, color: Int, showDate: Boolean, progress: Float
    ) {
        ink(color, 245, size * .11f, typefaceMono)
        c.drawText(time, x, y + size * .028f, labelPaint)
        line(color, 180, size * .004f)
        c.drawLine(x - size * .245f, y + size * .080f,
            x - size * .245f + .49f * size * progress, y + size * .080f, linePaint)
        line(color, 65, size * .004f)
        c.drawLine(x - size * .245f, y + size * .080f,
            x + size * .245f, y + size * .080f, linePaint)
        if (showDate) date(c, x, y + size * .16f, size * .030f, color)
    }

    private fun analog(
        c: Canvas, x: Float, y: Float, size: Float, color: Int,
        showDate: Boolean, progress: Float, showSeconds: Boolean
    ) {
        val radius = size * .202f
        line(color, 105, size * .0023f)
        c.drawCircle(x, y, radius, linePaint)
        for (index in 0..59) {
            val angle = index / 60f * PI.toFloat() * 2f - PI.toFloat() / 2f
            val major = index % 5 == 0
            val inside = radius * if (major) .82f else .92f
            val outside = radius * .963f
            line(color, if (major) 215 else 105, size * if (major) .0035f else .0018f)
            c.drawLine(x + cos(angle) * inside, y + sin(angle) * inside,
                x + cos(angle) * outside, y + sin(angle) * outside, linePaint)
        }
        val hoursAngle = ((hour % 12) + minute / 60f) / 12f * PI.toFloat() * 2f - PI.toFloat() / 2f
        val minutesAngle = (minute + progress) / 60f * PI.toFloat() * 2f - PI.toFloat() / 2f
        line(color, 255, size * .009f)
        c.drawLine(x - cos(hoursAngle) * radius * .14f, y - sin(hoursAngle) * radius * .14f,
            x + cos(hoursAngle) * radius * .52f, y + sin(hoursAngle) * radius * .52f, linePaint)
        line(color, 255, size * .005f)
        c.drawLine(x - cos(minutesAngle) * radius * .15f, y - sin(minutesAngle) * radius * .15f,
            x + cos(minutesAngle) * radius * .77f, y + sin(minutesAngle) * radius * .77f, linePaint)
        if (showSeconds) {
            val angle = progress * PI.toFloat() * 2f - PI.toFloat() / 2f
            line(color, 220, size * .002f)
            c.drawLine(x - cos(angle) * radius * .2f, y - sin(angle) * radius * .2f,
                x + cos(angle) * radius * .91f, y + sin(angle) * radius * .91f, linePaint)
        }
        linePaint.style = Paint.Style.FILL
        linePaint.color = color
        c.drawCircle(x, y, size * .007f, linePaint)
        if (showDate) date(c, x, y + radius + size * .07f, size * .032f, color)
    }

    private fun orbit(
        c: Canvas, x: Float, y: Float, size: Float, color: Int, showDate: Boolean, progress: Float
    ) {
        val radius = size * .244f
        rect.set(x - radius, y - radius, x + radius, y + radius)
        line(color, 65, size * .0025f)
        c.drawCircle(x, y, radius, linePaint)
        line(color, 255, size * .005f)
        c.drawArc(rect, -90f, (progress * 360f).coerceIn(0f, 359.99f), false, linePaint)
        ink(color, 255, size * .116f, typefaceLight)
        c.drawText(time, x, y + size * .028f, labelPaint)
        if (showDate) date(c, x, y + size * .10f, size * .028f, color)
    }
}
