package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Independent wallpaper overlay items. All of them are rendered between
 * the real photograph and its optional subject foreground mask.
 *
 * Clock is rendered through HALYN's existing clock atelier (custom font and
 * face styles stay supported); the other items are light Canvas primitives.
 */
class WallpaperWidgetRenderer(context: Context) {
    private val store = WallpaperWidgetStore(context)
    private val clock = ClockOverlayRenderer(context)
    private val battery = context.applicationContext.getSystemService(Context.BATTERY_SERVICE)
        as BatteryManager
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun active(): Boolean = store.active()

    fun draw(
        canvas: Canvas, state: MotionState,
        viewport: RectF? = null,
        overrideItems: List<WallpaperWidget>? = null,
        now: Long = System.currentTimeMillis()
    ) {
        val area = viewport ?: RectF(
            0f, 0f, canvas.width.toFloat(), canvas.height.toFloat()
        )
        val items = overrideItems ?: store.load()
        for (item in items.take(12)) {
            val w = item.safe()
            val centerX = area.left + w.x * area.width()
            val centerY = area.top + w.y * area.height()
            canvas.save()
            canvas.clipRect(area)
            canvas.rotate(w.rotation, centerX, centerY)
            canvas.scale(w.scale, w.scale, centerX, centerY)
            if (w.kind == "clock") {
                clock.draw(
                    canvas,
                    state.copy(
                        clockStyle = state.clockStyle.takeIf { it != 0 } ?: 6,
                        clockCustomPlacement = true,
                        clockX = w.x, clockY = w.y
                    ), now, area, overrideTint = w.color
                )
            } else {
                drawItem(canvas, area, centerX, centerY, w, now)
            }
            canvas.restore()
        }
    }

    private fun drawItem(
        canvas: Canvas, area: RectF, x: Float, y: Float,
        w: WallpaperWidget, now: Long
    ) {
        val unit = min(area.width(), area.height())
        fill.clearShadowLayer()
        fill.color = w.color
        fill.alpha = 240
        fill.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        fill.textSize = unit * .067f
        fill.setShadowLayer(unit * .008f, 0f, unit * .003f, Color.argb(150, 0, 0, 0))
        val locale = Locale.getDefault()
        when (w.kind) {
            "date" -> {
                fill.textSize = unit * .055f
                val text = SimpleDateFormat("EEE, d MMM yyyy", locale).format(Date(now))
                fitted(canvas, text, x, y + fill.textSize * .33f, area.width() * .79f)
            }
            "weekday" -> {
                fill.textSize = unit * .11f
                val day = SimpleDateFormat("EEEE", locale)
                    .format(Date(now)).uppercase(locale)
                fitted(canvas, day, x, y + fill.textSize * .33f, area.width() * .79f)
            }
            "battery" -> {
                val capacity = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    .coerceIn(0, 100)
                val width = unit * .32f
                val height = unit * .105f
                val border = RectF(x - width*.5f, y-height*.5f, x+width*.5f, y+height*.5f)
                stroke.color = w.color
                stroke.alpha = 205
                stroke.strokeWidth = unit * .004f
                canvas.drawRoundRect(border, height * .38f, height * .38f, stroke)
                fill.color = w.color
                fill.alpha = 105
                val inset = unit * .009f
                canvas.drawRoundRect(RectF(border.left + inset, border.top + inset,
                    border.left + inset + (width-2*inset)*capacity/100f,
                    border.bottom-inset), height*.28f, height*.28f, fill)
                fill.alpha = 255
                fill.textSize = unit * .049f
                canvas.drawText("$capacity%", x, y + fill.textSize*.34f, fill)
            }
            "text" -> {
                fill.textSize = unit * .062f
                fitted(canvas, w.label.ifBlank { "MY WALLPAPER" },
                    x, y + fill.textSize*.33f, area.width()*.85f)
            }
            "divider" -> {
                stroke.color = w.color
                stroke.alpha = 215
                stroke.strokeWidth = unit*.0035f
                canvas.drawLine(x-area.width()*.23f, y,
                    x+area.width()*.23f, y, stroke)
            }
        }
    }

    private fun fitted(canvas: Canvas, value: String, x: Float, y: Float, maxWidth: Float) {
        val width = fill.measureText(value)
        if (width > maxWidth && width > 0f) fill.textSize *= maxWidth / width
        canvas.drawText(value, x, y, fill)
    }
}
