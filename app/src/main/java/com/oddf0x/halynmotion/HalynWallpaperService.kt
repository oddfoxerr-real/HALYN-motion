package com.oddf0x.halynmotion

import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.Choreographer
import android.view.SurfaceHolder

/**
 * Genuine Android live wallpaper. The renderer is active only while the
 * system says this wallpaper is visible; it releases all scheduled frames
 * immediately on hidden, surface loss or service teardown.
 */
class HalynWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = MotionEngine()

    inner class MotionEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val preferences = MotionPreferences(this@HalynWallpaperService)
        private val renderer = HalynRenderer(this@HalynWallpaperService)
        private val clockRenderer = ClockOverlayRenderer(this@HalynWallpaperService)
        private val spatialTracker = SpatialTracker(this@HalynWallpaperService)
        private val gyroParallax = GyroParallax(this@HalynWallpaperService)
        private val choreographer = Choreographer.getInstance()
        private var framePosted = false
        private var lastFrameNs = 0L
        private var settings = preferences.get()
        private var visible = false
        private var surfaceReady = false
        private var surfaceWidth = 1
        private var surfaceHeight = 1
        private var touchX = .5f
        private var touchY = .5f
        private var offsetX = .5f
        private var touchDownX = 0f
        private var touchDownY = 0f
        private var touchDownMs = 0L
        private var startTimeMs = SystemClock.uptimeMillis()
        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                framePosted = false
                if (!visible || !surfaceReady) return
                val targetIntervalNs = 1_000_000_000L / settings.fps.coerceIn(15, 60)
                if (lastFrameNs == 0L || frameTimeNanos - lastFrameNs >= targetIntervalNs - 1_000_000L) {
                    lastFrameNs = frameTimeNanos
                    renderFrame()
                }
                scheduleFrame()
            }
        }

        private fun stopFrames() {
            choreographer.removeFrameCallback(frameCallback)
            framePosted = false
            lastFrameNs = 0L
        }

        private fun scheduleFrame() {
            if (visible && surfaceReady && !framePosted) {
                framePosted = true
                choreographer.postFrameCallback(frameCallback)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(acceptsTouch(settings))
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
            gyroParallax.recenter()
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
                stopFrames()
                spatialTracker.stop()
                gyroParallax.stop()
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
            // The star scene must not hijack home-screen taps unless opted in.
            if (!acceptsTouch(settings)) return
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    touchDownMs = SystemClock.uptimeMillis()
                    touchX = (event.x / surfaceWidth).coerceIn(0f, 1f)
                    touchY = (event.y / surfaceHeight).coerceIn(0f, 1f)
                }
                MotionEvent.ACTION_MOVE -> {
                    touchX = (event.x / surfaceWidth).coerceIn(0f, 1f)
                    touchY = (event.y / surfaceHeight).coerceIn(0f, 1f)
                }
                MotionEvent.ACTION_UP -> {
                    if (settings.preset == StarWindowRenderer.SCENE_INDEX &&
                        SystemClock.uptimeMillis() - touchDownMs <= 330L &&
                        kotlin.math.abs(event.x - touchDownX) < 30f * resources.displayMetrics.density &&
                        kotlin.math.abs(event.y - touchDownY) < 30f * resources.displayMetrics.density) {
                        val pose = spatialTracker.pose()
                        val destination = SpaceNavigation.pick(
                            pose, event.x, event.y,
                            surfaceWidth.toFloat(), surfaceHeight.toFloat(),
                            AstronomyCatalog.stars(this@HalynWallpaperService)
                        )
                        if (settings.skyAligned) preferences.setSkyAligned(false)
                        if (destination != null) spatialTracker.flyTo(destination)
                        else spatialTracker.warpToward(
                            event.x / surfaceWidth.coerceAtLeast(1),
                            event.y / surfaceHeight.coerceAtLeast(1),
                            surfaceWidth.toFloat() / surfaceHeight.coerceAtLeast(1)
                        )
                    }
                }
            }
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            if (key == "star_recenter_token") spatialTracker.recenter()
            if (key == "star_warp_token") {
                // Location tracking is for the terrestrial sky. Hyperspace
                // switches to exploration, never a false GPS position.
                if (preferences.get().skyAligned) preferences.setSkyAligned(false)
                spatialTracker.configureSky(preferences.get())
                val ray = preferences.warpCoordinates()
                spatialTracker.warpToward(ray[0], ray[1], ray[2])
            }
            if (key == "star_destination_token" &&
                settings.preset == StarWindowRenderer.SCENE_INDEX) {
                val destination = SpaceNavigation.destinationForName(
                    preferences.destinationName(),
                    AstronomyCatalog.stars(this@HalynWallpaperService),
                    discoveries = spatialTracker.pose().generatedWorlds
                )
                if (destination != null) {
                    if (preferences.get().skyAligned) preferences.setSkyAligned(false)
                    spatialTracker.configureSky(preferences.get())
                    spatialTracker.flyTo(destination)
                }
            }
            settings = preferences.get()
            // Dragging the clock updates several preference keys in quick
            // succession. Do not tear down the animation/sensors per pixel.
            if (key == "clock_x" || key == "clock_y" ||
                key == "clock_custom_placement" || key == "clock_font" ||
                key == "clock_font_revision") {
                scheduleFrame()
            } else {
                refresh()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            stopFrames()
            spatialTracker.stop()
            gyroParallax.stop()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            surfaceReady = false
            stopFrames()
            spatialTracker.stop()
            gyroParallax.stop()
            preferences.raw.unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }

        private fun acceptsTouch(state: MotionState): Boolean =
            if (state.preset == StarWindowRenderer.SCENE_INDEX) state.starTapTravel else state.touch

        private fun refresh() {
            // Android launchers stop forwarding taps when the wallpaper does not
            // request them. This also protects users who disable tap-to-travel.
            setTouchEventsEnabled(acceptsTouch(settings))
            stopFrames()
            if (visible && surfaceReady && settings.preset == StarWindowRenderer.SCENE_INDEX) {
                spatialTracker.configureSky(settings)
                spatialTracker.start(settings.starSensitivity)
                spatialTracker.setSensitivity(settings.starSensitivity)
                gyroParallax.stop()
            } else {
                spatialTracker.stop()
                if (visible && surfaceReady && settings.gyroMode != 0) {
                    gyroParallax.start()
                } else {
                    gyroParallax.stop()
                }
            }
            if (visible && surfaceReady) scheduleFrame()
        }

        private fun renderFrame() {
            if (!visible || !surfaceReady) return
            val startedMs = SystemClock.uptimeMillis()
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockHardwareCanvas()
                val tilt = if (settings.preset == StarWindowRenderer.SCENE_INDEX) {
                    ParallaxPose()
                } else {
                    gyroParallax.pose(settings)
                }
                renderer.draw(
                    canvas, settings,
                    (startedMs - startTimeMs) / 1000f,
                    touchX, touchY, offsetX,
                    if (settings.preset == StarWindowRenderer.SCENE_INDEX) spatialTracker.pose() else null,
                    gyro = tilt
                )
                clockRenderer.draw(canvas, settings)
                // Reuse the exact same gyro pose for background/foreground;
                // otherwise a moving ridge can visibly shear around the clock.
                renderer.drawForeground(canvas, settings, tilt, offsetX)
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
            // Choreographer schedules the next vsync callback instead of
            // accumulating timer-driven render work after a dropped frame.

        }
    }
}
