package com.oddf0x.halynmotion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Explicitly opt-in home-screen clock editor.
 *
 * WallpaperService cannot reliably capture dragging through a launcher.
 * A temporary TYPE_APPLICATION_OVERLAY therefore provides its own grid above
 * the actual home screen, and closes completely when the X is tapped.
 *
 * A user-visible foreground notification also exposes Exit, in case the
 * overlay window is interrupted. No overlay exists outside edit sessions.
 */
class ClockPlacementService : Service() {
    private val prefs by lazy { MotionPreferences(this) }
    private val windows by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var editor: PlacementView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (editor != null) return START_NOT_STICKY
        val notifications = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL, "Clock placement", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown only while positioning the HALYN wallpaper clock." })

        val stopIntent = Intent(this, ClockPlacementService::class.java).setAction(ACTION_STOP)
        val exit = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("HALYN · Move clock")
            .setContentText("Drag on the grid. Tap X or Exit to save and close.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "Exit", exit
            ).build())
            .build()
        try {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (_: RuntimeException) {
            stopSelf()
            return START_NOT_STICKY
        }

        val view = PlacementView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "HALYN clock placement"
        }
        try {
            windows.addView(view, params)
            editor = view
        } catch (_: RuntimeException) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val existing = editor
        editor = null
        if (existing != null) {
            try {
                windows.removeViewImmediate(existing)
            } catch (_: RuntimeException) {
                // Overlay permission may be revoked during the edit.
            }
        }
        super.onDestroy()
    }

    private inner class PlacementView(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 225, 233, 255)
            strokeWidth = density
        }
        private val strong = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 2f * density
            style = Paint.Style.STROKE
        }
        private val type = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.WHITE
            textSize = 13f * density
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setShadowLayer(4f * density, 0f, 1f * density, Color.BLACK)
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val closeBounds = RectF()
        private var dragging = false
        private var x = prefs.get().let {
            if (it.clockCustomPlacement) it.clockX else .5f
        }
        private var y = prefs.get().let {
            if (it.clockCustomPlacement) it.clockY else when(it.clockPosition) {
                0 -> .265f
                2 -> .71f
                else -> if (MountainSceneRenderer.isMountain(it.preset)) .71f else .49f
            }
        }

        private fun px(value: Float) = value * density

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat().coerceAtLeast(1f)
            val h = height.toFloat().coerceAtLeast(1f)

            // Transparent grid; the true launcher and active wallpaper show below.
            for (i in 1 until 20) {
                val xGrid = w * i / 20f
                val yGrid = h * i / 20f
                canvas.drawLine(xGrid, 0f, xGrid, h, grid)
                canvas.drawLine(0f, yGrid, w, yGrid, grid)
            }

            val cx = x * w
            val cy = y * h
            val radius = min(w, h) * .15f
            strong.alpha = 225
            canvas.drawRoundRect(cx - radius, cy - radius * .58f,
                cx + radius, cy + radius * .58f, px(14f), px(14f), strong)
            canvas.drawLine(cx - px(15f), cy, cx + px(15f), cy, strong)
            canvas.drawLine(cx, cy - px(15f), cx, cy + px(15f), strong)
            type.textAlign = Paint.Align.CENTER
            type.textSize = px(12f)
            canvas.drawText("DRAG CLOCK", cx, cy - radius * .58f - px(13f), type)

            fill.color = Color.argb(205, 12, 15, 23)
            val closeX = w - px(42f)
            val closeY = px(64f)
            val closeRadius = px(24f)
            closeBounds.set(closeX - px(32f), closeY - px(32f),
                closeX + px(32f), closeY + px(32f))
            canvas.drawCircle(closeX, closeY, closeRadius, fill)
            strong.alpha = 255
            canvas.drawLine(closeX - px(8f), closeY - px(8f),
                closeX + px(8f), closeY + px(8f), strong)
            canvas.drawLine(closeX + px(8f), closeY - px(8f),
                closeX - px(8f), closeY + px(8f), strong)
            type.textAlign = Paint.Align.LEFT
            type.textSize = px(11f)
            canvas.drawText("HALYN  /  CLOCK GRID", px(20f), closeY + px(4f), type)
            type.textSize = px(10f)
            canvas.drawText("Snap: 5%  ·  X saves & exits", px(20f), closeY + px(21f), type)
        }

        private fun updateFromTouch(event: MotionEvent) {
            val nx = (event.x / width.coerceAtLeast(1)).coerceIn(.08f, .92f)
            val ny = (event.y / height.coerceAtLeast(1)).coerceIn(.14f, .88f)
            x = (nx * 20f).roundToInt().coerceIn(2, 18) / 20f
            y = (ny * 20f).roundToInt().coerceIn(3, 17) / 20f
            prefs.setClockPlacement(x, y)
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean = when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (closeBounds.contains(event.x, event.y)) {
                    dragging = false
                } else {
                    dragging = true
                    updateFromTouch(event)
                }
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) updateFromTouch(event)
                true
            }
            MotionEvent.ACTION_UP -> {
                if (closeBounds.contains(event.x, event.y) && !dragging) {
                    performClick()
                    stopSelf()
                } else {
                    if (dragging) updateFromTouch(event)
                    dragging = false
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                true
            }
            else -> true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    companion object {
        private const val CHANNEL = "halyn_clock_placement"
        private const val NOTIFICATION_ID = 6207
        const val ACTION_STOP = "com.oddf0x.halynmotion.STOP_CLOCK_PLACEMENT"
    }
}
