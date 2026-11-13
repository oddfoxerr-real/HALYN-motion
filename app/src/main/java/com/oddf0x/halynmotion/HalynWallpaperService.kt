package com.oddf0x.halynmotion

import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
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
        private val signalSampler = SceneSignalSampler(this@HalynWallpaperService, preferences.raw)
        private val choreographer = Choreographer.getInstance()
        private val mainHandler = Handler(Looper.getMainLooper())
        private var framePosted = false
        private var tickPosted = false
        private var lastFrameNs = 0L
        private var adaptiveFps = 30
        private var slowFrames = 0
        private var fastFrames = 0
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

        private fun isStillPhoto(): Boolean =
            settings.preset == PhotoWallpaperStore.SCENE_INDEX && settings.gyroMode == 0

        private fun effectiveFps(): Int =
            minOf(settings.fps, adaptiveFps).coerceIn(24, 60)

        private fun targetIntervalNs(): Long = 1_000_000_000L / effectiveFps()

        private val staticClockTick = Runnable {
            tickPosted = false
            if (visible && surfaceReady && isStillPhoto()) scheduleFrame(immediate = true)
        }

        private fun scheduleStaticClockTick() {
            if (tickPosted || !visible || !surfaceReady || !isStillPhoto() ||
                settings.clockStyle == 0) return
            // Wake at a clock-second boundary, not 30–60 times per second.
            val untilNextSecond = 1_000L - System.currentTimeMillis() % 1_000L
            tickPosted = true
            mainHandler.postDelayed(staticClockTick, untilNextSecond.coerceAtLeast(16L) + 5L)
        }

        private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
            framePosted = false
            if (!visible || !surfaceReady) return@FrameCallback
            lastFrameNs = frameTimeNanos
            val started = System.nanoTime()
            renderFrame()
            observeFrameCost(System.nanoTime() - started)
            scheduleFrame()
        }

        private fun observeFrameCost(durationNs: Long) {
            if (isStillPhoto()) return
            val budgetNs = targetIntervalNs()
            if (durationNs > budgetNs * 85L / 100L) {
                slowFrames++
                fastFrames = 0
                if (slowFrames >= 20) {
                    adaptiveFps = when {
                        adaptiveFps > 45 -> 45
                        adaptiveFps > 30 -> 30
                        else -> 24
                    }
                    slowFrames = 0
                }
            } else {
                slowFrames = (slowFrames - 2).coerceAtLeast(0)
                if (durationNs < budgetNs / 2L && adaptiveFps < settings.fps) {
                    fastFrames++
                    if (fastFrames >= 300) {
                        adaptiveFps = when {
                            adaptiveFps < 30 -> 30
                            adaptiveFps < 45 -> 45
                            else -> 60
                        }.coerceAtMost(settings.fps)
                        fastFrames = 0
                    }
                } else {
                    fastFrames = 0
                }
            }
        }

        private fun stopFrames() {
            choreographer.removeFrameCallback(frameCallback)
            mainHandler.removeCallbacks(staticClockTick)
            framePosted = false
            tickPosted = false
            lastFrameNs = 0L
            slowFrames = 0
            fastFrames = 0
            adaptiveFps = settings.fps.coerceIn(24, 60)
        }

        private fun scheduleFrame(immediate: Boolean = false) {
            if (!visible || !surfaceReady) return
            if (isStillPhoto() && !immediate && lastFrameNs != 0L) {
                scheduleStaticClockTick()
                return
            }
            if (framePosted) {
                if (!immediate) return
                choreographer.removeFrameCallback(frameCallback)
                framePosted = false
            }
            // Queue near the next requested presentation deadline instead of
            // waking the main thread on every display vsync at lower FPS.
            val delayMs = if (immediate || lastFrameNs == 0L) 0L else {
                ((lastFrameNs + targetIntervalNs() - System.nanoTime() -
                    4_000_000L) / 1_000_000L).coerceAtLeast(0L)
            }
            framePosted = true
            choreographer.postFrameCallbackDelayed(frameCallback, delayMs)
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(acceptsTouch(settings))
            preferences.raw.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = true
            renderer.resetHardwareState()
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
            if (isStillPhoto()) scheduleFrame(immediate = true)
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
            val previous = settings
            settings = preferences.get()
            if (settings.fps != previous.fps) {
                adaptiveFps = settings.fps.coerceIn(24, 60)
                slowFrames = 0
                fastFrames = 0
            }
            val trackerChanged = settings.preset != previous.preset ||
                settings.gyroMode != previous.gyroMode ||
                settings.starSensitivity != previous.starSensitivity ||
                settings.skyAligned != previous.skyAligned ||
                key == "sky_latitude" || key == "sky_longitude" ||
                key == "sky_fix_time"
            if (trackerChanged) {
                refresh()
            } else {
                if (acceptsTouch(settings) != acceptsTouch(previous)) {
                    setTouchEventsEnabled(acceptsTouch(settings))
                }
                // Colour, clock position, frame rate and motion strength
                // edits are repaint-only: do not restart the sensors.
                scheduleFrame(immediate = true)
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
                    gyro = tilt,
                    touchForce = if (settings.touch && touchDownMs > 0L)
                        (1f - (startedMs - touchDownMs) / 2400f).coerceIn(0f, 1f)
                    else 0f,
                    reactions = signalSampler.sample()
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
