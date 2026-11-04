package com.oddf0x.halynmotion

import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.max

/**
 * Genuine Android live wallpaper. The renderer is active only while the
 * system says this wallpaper is visible; it releases all scheduled frames
 * immediately on hidden, surface loss or service teardown.
 */
class HalynWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = MotionEngine()

    inner class MotionEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val preferences = MotionPreferences(this@HalynWallpaperService)
        private val renderer = HalynRenderer()
        private val handler = Handler(Looper.getMainLooper())
        private var settings = preferences.get()
        private var visible = false
        private var surfaceReady = false
        private var surfaceWidth = 1
        private var surfaceHeight = 1
        private var touchX = .5f
        private var touchY = .5f
        private var offsetX = .5f
        private var startTimeMs = SystemClock.uptimeMillis()
        private val tick = Runnable { renderFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            preferences.raw.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = true
            refresh()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width.coerceAtLeast(1)
            surfaceHeight = height.coerceAtLeast(1)
            surfaceReady = true
            refresh()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible
            if (visible) {
                settings = preferences.get()
                refresh()
            } else {
                handler.removeCallbacks(tick)
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            offsetX = xOffset.coerceIn(0f, 1f)
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            if (!settings.touch) return
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    touchX = (event.x / surfaceWidth).coerceIn(0f, 1f)
                    touchY = (event.y / surfaceHeight).coerceIn(0f, 1f)
                }
            }
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            settings = preferences.get()
            refresh()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            handler.removeCallbacks(tick)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            surfaceReady = false
            handler.removeCallbacks(tick)
            preferences.raw.unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }

        private fun refresh() {
            handler.removeCallbacks(tick)
            if (visible && surfaceReady) handler.post(tick)
        }

        private fun renderFrame() {
            if (!visible || !surfaceReady) return
            val startedMs = SystemClock.uptimeMillis()
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockHardwareCanvas()
                renderer.draw(
                    canvas, settings,
                    (startedMs - startTimeMs) / 1000f,
                    touchX, touchY, offsetX
                )
            } catch (_: IllegalArgumentException) {
                // The system may be replacing the wallpaper surface.
            } catch (_: IllegalStateException) {
                // A destroyed or temporarily unavailable hardware canvas.
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (_: IllegalStateException) {
                        // Surface can be removed during a wallpaper transition.
                    }
                }
            }
            if (!visible || !surfaceReady) return
            val frameMs = 1000L / settings.fps.coerceIn(15, 60)
            val elapsedMs = SystemClock.uptimeMillis() - startedMs
            handler.postDelayed(tick, max(8L, frameMs - elapsedMs))
        }
    }
}
