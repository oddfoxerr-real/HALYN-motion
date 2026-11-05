package com.oddf0x.halynmotion

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.roundToInt

/**
 * HALYN Motion — a focused wallpaper studio.
 *
 * Editorial typography, a generous live preview, understated scene selection,
 * one-touch palettes and a full HSV/HEX editor. All controls use standard
 * accessible Android views. The original six renderer scenes are preserved.
 */
class MainActivity : Activity() {
    private val prefs by lazy { MotionPreferences(this) }
    private val previewTracker by lazy { SpatialTracker(this) }
    private val previewGyro by lazy { GyroParallax(this) }
    private val skyLocation by lazy {
        SkyLocationController(this, prefs,
            onStatus = { message -> if (::skyStatus.isInitialized) skyStatus.text = message },
            onSaved = {
                refreshSkyUi()
                syncStarTracking()
                if (::preview.isInitialized) preview.invalidate()
            })
    }
    private lateinit var preview: LivePreview
    private lateinit var onlineGallery: CommonsGalleryView
    private lateinit var onlineCredit: TextView
    private lateinit var scenes: LinearLayout
    private lateinit var newScenes: LinearLayout
    private lateinit var clockStyles: LinearLayout
    private lateinit var clockPositions: LinearLayout
    private lateinit var clockFonts: LinearLayout
    private lateinit var clockPlacementToggle: Switch
    private var awaitingClockOverlayPermission = false
    private lateinit var mountainScenes: LinearLayout
    private val posterRails = mutableListOf<LinearLayout>()
    private lateinit var myImageScenes: LinearLayout
    private lateinit var gyroModes: LinearLayout
    private lateinit var gyroPanel: LinearLayout
    private lateinit var mountainDepthPanel: LinearLayout
    private lateinit var clockOptionsPanel: LinearLayout
    private lateinit var starControls: LinearLayout
    private lateinit var densityChoices: LinearLayout
    private lateinit var effectsChoices: LinearLayout
    private lateinit var skyStatus: TextView
    private lateinit var skyToggle: TextView
    private lateinit var palettes: LinearLayout
    private lateinit var frameRates: LinearLayout
    private lateinit var activeSceneName: TextView
    private lateinit var activeColourName: TextView

    private val backgroundColor = Color.rgb(10, 11, 14)
    private val cardColor = Color.rgb(22, 23, 28)
    private val borderColor = Color.rgb(47, 48, 55)
    private val ink = Color.rgb(248, 248, 250)
    private val secondary = Color.rgb(157, 158, 167)
    private val subtle = Color.rgb(112, 113, 122)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        window.decorView.systemUiVisibility = 0

        val frame = FrameLayout(this).apply {
            setBackgroundColor(backgroundColor)
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            isFillViewport = true
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(136))
        }
        scroll.addView(column)
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        buildHeader(column)
        buildPreview(column)
        buildScenes(column)
        // Preserve legacy preferences for users with an older active scene,
        // but do not offer generated artwork in the new online-only gallery.
        buildColours(LinearLayout(this))
        buildClock(column)
        buildStarWindow(LinearLayout(this))
        buildMotion(column)
        buildBehavior(column)
        buildFooter(column)
        buildApplyBar(frame)

        setContentView(frame)
        refreshScene()
        refreshPalette()
        refreshFps()
        refreshClockStyles()
        refreshClockPositions()
        refreshClockFonts()
        refreshStarDensity()
        refreshStarEffects()
        refreshGyroModes()
        refreshSkyUi()
        syncStarTracking()
        onlineGallery.loadInitial()
    }

    private fun buildHeader(root: LinearLayout) {
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(text("H A L Y N", 12f, ink, Typeface.BOLD).apply {
            letterSpacing = .16f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(text("MOTION STUDIO", 10f, subtle, Typeface.BOLD).apply {
            letterSpacing = .14f
        })
        root.addView(top)

        root.addView(text("Real 4K wallpapers.", 32f, ink, Typeface.BOLD).apply {
            setPadding(0, dp(20), 0, 0)
            letterSpacing = -.035f
        })
        root.addView(text("Curated online. Your clock. Your style.", 14f, secondary).apply {
            setPadding(0, dp(5), 0, dp(24))
        })
    }

    private fun buildPreview(root: LinearLayout) {
        val shell = FrameLayout(this).apply {
            background = rounded(Color.rgb(13, 15, 25), 29)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(29).toFloat())
                }
            }
        }
        preview = LivePreview().apply {
            contentDescription = "Animated wallpaper. Swipe to interact with the live scene."
            isFocusable = true
            isClickable = true
        }
        shell.addView(preview, FrameLayout.LayoutParams(-1, -1))

        val chip = text("●  LIVE WALLPAPER", 10f, ink, Typeface.BOLD).apply {
            letterSpacing = .1f
            background = rounded(Color.argb(175, 13, 14, 20), 16)
            setPadding(dp(14), dp(9), dp(14), dp(9))
        }
        shell.addView(chip, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(18), dp(18), 0, 0)
        })

        val foot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(230, 7, 9, 16))
            )
            setPadding(dp(20), dp(45), dp(20), dp(21))
        }
        activeSceneName = text("", 24f, ink, Typeface.BOLD)
        foot.addView(activeSceneName)
        foot.addView(text("Real online photo · Clock & gyro below", 12f, Color.rgb(205, 205, 215)).apply {
            setPadding(0, dp(4), 0, 0)
        })
        shell.addView(foot, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        val density = resources.displayMetrics
        val availableWidth = (density.widthPixels / density.density).toInt() - 44
        // Keeps a phone-like canvas while avoiding an overwhelming preview on small devices.
        val heightDp = (availableWidth * 1.08f).roundToInt().coerceIn(290, 378)
        root.addView(shell, LinearLayout.LayoutParams(-1, dp(heightDp)))
    }

    private fun buildScenes(root: LinearLayout) {
        onlineGallery = CommonsGalleryView(this) { image ->
            prefs.raw.edit()
                .putString("online_title", image.name)
                .putString("online_artist", image.artist)
                .putString("online_license", image.license)
                .putString("online_source", image.sourcePage)
                .putString("online_license_url", image.licenseUrl)
                .putString("online_resolution", image.dimensionLabel)
                .apply()
            prefs.photoImported()
            refreshScene()
            syncStarTracking()
            syncMotionTracking()
            preview.invalidate()
        }
        root.addView(onlineGallery, LinearLayout.LayoutParams(-1, -2))

        root.addView(section("From your phone", "Use a photo or image you already have."))
        root.addView(text("Choose a local image  ↗", 14f, ink, Typeface.BOLD).apply {
            background = rounded(cardColor, 16, borderColor)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            minHeight = dp(54)
            isClickable = true
            isFocusable = true
            contentDescription = "Choose an existing picture from your phone"
            setOnClickListener { chooseWallpaperPhoto() }
        })
        onlineCredit = text("", 12f, secondary).apply {
            setPadding(dp(2), dp(13), dp(2), dp(7))
            isClickable = true
            isFocusable = true
            contentDescription = "View source and creator attribution of the active wallpaper"
            setOnClickListener {
                val source = prefs.raw.getString("online_source", null)
                if (!source.isNullOrBlank()) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source)))
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Source link unavailable", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        root.addView(onlineCredit)
    }

    private fun chooseWallpaperPhoto() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(pick, REQUEST_WALLPAPER_IMAGE)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No image picker available", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildColours(root: LinearLayout) {
        root.addView(section("Colour", "Find the right atmosphere."))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 22, borderColor)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        palettes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        panel.addView(palettes, LinearLayout.LayoutParams(-1, dp(53)))

        val divider = View(this).apply { setBackgroundColor(borderColor) }
        panel.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(12)
            bottomMargin = dp(13)
        })

        val pickerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        activeColourName = text("", 13f, secondary, Typeface.NORMAL)
        pickerRow.addView(activeColourName, LinearLayout.LayoutParams(0, -2, 1f))
        val edit = text("Edit colour  ›", 13f, ink, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setPadding(dp(10), 0, 0, 0)
            isFocusable = true
            isClickable = true
            contentDescription = "Open full colour picker, with hue, saturation, brightness and HEX"
            setOnClickListener { openColourEditor() }
        }
        pickerRow.addView(edit, LinearLayout.LayoutParams(-2, dp(48)))
        panel.addView(pickerRow)
        root.addView(panel)
    }

    private fun buildClock(root: LinearLayout) {
        root.addView(section("Clock atelier", "Time, designed into the wallpaper."))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 22, borderColor)
            setPadding(dp(17), dp(17), dp(17), dp(17))
        }
        panel.addView(text("CLOCK FACE", 10f, subtle, Typeface.BOLD).apply {
            letterSpacing = .15f
            setPadding(0, 0, 0, dp(8))
        })

        clockStyles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val stylesRail = HorizontalScrollView(this).apply {
            clipToPadding = false
            isHorizontalScrollBarEnabled = false
            addView(clockStyles)
        }
        panel.addView(stylesRail, LinearLayout.LayoutParams(-1, dp(55)))

        clockOptionsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clockOptionsPanel.addView(text("PLACEMENT", 10f, subtle, Typeface.BOLD).apply {
            letterSpacing = .15f
            setPadding(0, dp(14), 0, dp(10))
        })
        clockPositions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        clockOptionsPanel.addView(clockPositions, LinearLayout.LayoutParams(-1, dp(45)))

        clockOptionsPanel.addView(text("FONT", 10f, subtle, Typeface.BOLD).apply {
            letterSpacing = .15f
            setPadding(0, dp(18), 0, dp(9))
        })
        clockFonts = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        clockOptionsPanel.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(clockFonts)
        }, LinearLayout.LayoutParams(-1, dp(48)))
        clockOptionsPanel.addView(text("Import .ttf / .otf  ↗", 13f, ink, Typeface.BOLD).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
            minimumHeight = dp(46)
            isFocusable = true
            isClickable = true
            contentDescription = "Import a custom TTF or OTF font for the wallpaper clock"
            setOnClickListener { chooseClockFont() }
        })
        val editClockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(77)
        }
        editClockRow.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Move clock on Home screen", 14f, ink, Typeface.BOLD))
            addView(text("5% snap grid · Drag and tap X to finish", 12f, secondary).apply {
                setPadding(0, dp(5), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        clockPlacementToggle = Switch(this).apply {
            isChecked = false
            contentDescription = "Enter live home-screen clock placement mode"
            thumbTintList = ColorStateList.valueOf(ink)
            trackTintList = ColorStateList.valueOf(Color.rgb(72, 73, 80))
            setOnCheckedChangeListener { _, checked -> if (checked) requestClockPlacement() }
        }
        editClockRow.addView(clockPlacementToggle)
        clockOptionsPanel.addView(editClockRow)
        val separator = View(this).apply { setBackgroundColor(borderColor) }
        clockOptionsPanel.addView(separator, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(16)
        })
        addToggle(clockOptionsPanel, "24-hour time", "Use the system time zone", prefs.get().clock24h) {
            prefs.setClock24h(it)
            preview.invalidate()
        }
        addToggle(clockOptionsPanel, "Date", "Show today's date beneath the clock", prefs.get().clockDate) {
            prefs.setClockDate(it)
            preview.invalidate()
        }
        addToggle(clockOptionsPanel, "Seconds", "Animate the seconds indicator", prefs.get().clockSeconds) {
            prefs.setClockSeconds(it)
            preview.invalidate()
        }
        addToggle(clockOptionsPanel, "Accent colour", "Match the wallpaper palette", prefs.get().clockAccent) {
            prefs.setClockAccent(it)
            preview.invalidate()
        }
        panel.addView(clockOptionsPanel)
        root.addView(panel)
    }

    private fun refreshClockFonts() {
        if (!::clockFonts.isInitialized) return
        clockFonts.removeAllViews()
        val selected = prefs.get().clockFont
        ClockFontStore.names.forEachIndexed { index, label ->
            val active = selected == index
            val chip = text(label, 12f, if (active) backgroundColor else secondary,
                if (active) Typeface.BOLD else Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                setPadding(dp(14), 0, dp(14), 0)
                background = rounded(if (active) ink else Color.rgb(36, 37, 44), 13)
                minHeight = dp(44)
                isClickable = true
                isFocusable = true
                contentDescription = "Clock font: " + label + (if (active) ", selected" else "")
                setOnClickListener {
                    if (index == 6 && !ClockFontStore.hasCustomFont(this@MainActivity)) {
                        chooseClockFont()
                    } else {
                        prefs.setClockFont(index)
                        refreshClockFonts()
                        preview.invalidate()
                    }
                }
            }
            clockFonts.addView(chip, LinearLayout.LayoutParams(-2, dp(44)).apply {
                marginEnd = dp(7)
            })
        }
    }

    private fun chooseClockFont() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(pick, REQUEST_CLOCK_FONT)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No document picker available", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestClockPlacement() {
        if (prefs.get().clockStyle == 0) {
            Toast.makeText(this, "Select a clock face first", Toast.LENGTH_SHORT).show()
            clockPlacementToggle.isChecked = false
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            awaitingClockOverlayPermission = true
            Toast.makeText(this, "Allow HALYN to draw over apps for this editor only", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName)))
            } catch (_: android.content.ActivityNotFoundException) {
                awaitingClockOverlayPermission = false
                clockPlacementToggle.isChecked = false
                Toast.makeText(this, "Overlay permission settings unavailable", Toast.LENGTH_LONG).show()
            }
            return
        }
        launchClockPlacement()
    }

    private fun launchClockPlacement() {
        awaitingClockOverlayPermission = false
        if (!Settings.canDrawOverlays(this)) {
            clockPlacementToggle.isChecked = false
            return
        }
        try {
            startForegroundService(Intent(this, ClockPlacementService::class.java))
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(home)
        } catch (_: RuntimeException) {
            clockPlacementToggle.isChecked = false
            Toast.makeText(this, "Could not open home-screen clock editor", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WALLPAPER_IMAGE && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            Thread {
                val installed = PhotoWallpaperStore(applicationContext).install(uri)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (installed) {
                        prefs.raw.edit()
                            .remove("online_source").remove("online_artist")
                            .remove("online_license").remove("online_license_url")
                            .remove("online_resolution")
                            .putString("online_title", "My image").apply()
                        prefs.photoImported()
                        refreshScene()
                        syncStarTracking()
                        syncMotionTracking()
                        preview.invalidate()
                        Toast.makeText(this, "Image saved offline at full quality", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Image import failed (48 MB limit)", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
            return
        }
        if (requestCode == REQUEST_CLOCK_FONT && resultCode == RESULT_OK && data?.data != null) {
            if (ClockFontStore.install(this, data.data!!)) {
                prefs.importedClockFont()
                refreshClockFonts()
                preview.invalidate()
                Toast.makeText(this, "Clock font imported locally", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Select a valid .ttf or .otf file under 3 MB", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshClockStyles() {
        clockStyles.removeAllViews()
        val names = listOf("Off", "Digital", "Stacked", "Mono", "Analogue", "Orbit")
        val selected = prefs.get().clockStyle
        names.forEachIndexed { index, name ->
            val active = index == selected
            val button = text(name, 13f, if (active) backgroundColor else secondary,
                if (active) Typeface.BOLD else Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), 0, dp(16), 0)
                minHeight = dp(44)
                background = rounded(if (active) ink else Color.rgb(36, 37, 44), 14)
                isFocusable = true
                isClickable = true
                contentDescription = "${name} clock, ${if (active) "selected" else "not selected"}"
                setOnClickListener {
                    prefs.setClockStyle(index)
                    refreshClockStyles()
                    preview.invalidate()
                }
            }
            clockStyles.addView(button, LinearLayout.LayoutParams(-2, dp(44)).apply {
                marginEnd = dp(8)
            })
        }
        clockOptionsPanel.visibility = if (selected == 0) View.GONE else View.VISIBLE
    }

    private fun refreshClockPositions() {
        clockPositions.removeAllViews()
        val names = listOf("Upper", "Centre", "Lower")
        val state = prefs.get()
        val selected = if (state.clockCustomPlacement) -1 else state.clockPosition
        names.forEachIndexed { index, name ->
            val active = index == selected
            val button = text(name, 12f, if (active) backgroundColor else secondary,
                if (active) Typeface.BOLD else Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                background = rounded(if (active) ink else Color.rgb(36, 37, 44), 13)
                isFocusable = true
                isClickable = true
                contentDescription = "${name} clock placement, ${if (active) "selected" else "not selected"}"
                setOnClickListener {
                    prefs.setClockPosition(index)
                    refreshClockPositions()
                    preview.invalidate()
                }
            }
            clockPositions.addView(button, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(7)
            })
        }
    }

    private fun buildStarWindow(root: LinearLayout) {
        starControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        starControls.addView(section("Star Window", "Walk through a fixed field of stars."))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 22, borderColor)
            setPadding(dp(17), dp(18), dp(17), dp(16))
        }
        panel.addView(text("WEIGHTLESS EXPLORATION", 10f, secondary, Typeface.BOLD).apply {
            letterSpacing = .10f
            setPadding(0, 0, 0, dp(8))
        })
        panel.addView(text(
            "Slow astronaut-like drift. Your phone becomes a window into a fixed astronomical starfield. No camera needed.",
            12f, secondary
        ).apply { setPadding(0, 0, 0, dp(16)) })

        addSlider(panel, "Movement response", prefs.get().starSensitivity,
            .15f, 1.4f) { value ->
            prefs.setStarSensitivity(value)
            previewTracker.setSensitivity(value)
        }

        panel.addView(text("STAR DENSITY", 10f, subtle, Typeface.BOLD).apply {
            letterSpacing = .12f
            setPadding(0, dp(13), 0, dp(10))
        })
        densityChoices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        panel.addView(densityChoices, LinearLayout.LayoutParams(-1, dp(44)))

        panel.addView(View(this).apply { setBackgroundColor(borderColor) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(15) })
        addToggle(panel, "Constellation lines", "Delicate fixed celestial geometry",
            prefs.get().starLines) {
            prefs.setStarLines(it)
            preview.invalidate()
        }
        panel.addView(text("VISUAL ATMOSPHERE", 10f, subtle, Typeface.BOLD).apply {
            letterSpacing = .12f
            setPadding(0, dp(13), 0, dp(10))
        })
        effectsChoices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        panel.addView(effectsChoices, LinearLayout.LayoutParams(-1, dp(44)))
        panel.addView(text(
            "Survey: real astronomy only. Nebula: luminous cosmic clouds. " +
                "Expedition: atmospheric light, rings and artistic worlds beyond the Solar System.",
            11f, secondary
        ).apply { setPadding(0, dp(10), 0, dp(5)) })

        panel.addView(View(this).apply { setBackgroundColor(borderColor) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16) })
        addToggle(
            panel,
            "Tap to travel",
            "Off by default · Tap planets or space to warp when enabled",
            prefs.get().starTapTravel
        ) { enabled ->
            prefs.setStarTapTravel(enabled)
            preview.invalidate()
        }

        val recenter = text("Return to the Solar System    ⟲", 14f, ink, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(42, 44, 53), 15)
            isClickable = true
            isFocusable = true
            contentDescription = "Return the Star Window camera to the Solar System starting point"
            setOnClickListener {
                previewTracker.recenter()
                prefs.recenterStarWindow()
                preview.invalidate()
                Toast.makeText(this@MainActivity, "Returned to the Solar System", Toast.LENGTH_SHORT).show()
            }
        }
        val warp = text("Warp one light-year    ↗", 14f, backgroundColor, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(ink, 15)
            isClickable = true
            isFocusable = true
            contentDescription = "Travel one light-year in the viewing direction"
            setOnClickListener { triggerStarWarp() }
        }
        panel.addView(warp, LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(16)
        })
        val destinationButton = text("Explore a destination    ✦", 14f, ink, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(42, 44, 53), 15)
            isClickable = true
            isFocusable = true
            contentDescription = "Choose the Sun, a planet or a nearby real star to visit"
            setOnClickListener { showDestinations() }
        }
        panel.addView(destinationButton, LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(9)
        })
        panel.addView(recenter, LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(12)
        })

        panel.addView(text("SKY AT YOUR LOCATION", 10f, subtle, Typeface.BOLD).apply {
            letterSpacing = .12f
            setPadding(0, dp(25), 0, dp(7))
        })
        skyStatus = text("", 12f, secondary)
        panel.addView(skyStatus)
        val locationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        val locate = text("Sync sky location", 13f, backgroundColor, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(ink, 14)
            isClickable = true
            isFocusable = true
            contentDescription = "Request location permission and align the sky to where you are"
            setOnClickListener { skyLocation.sync() }
        }
        locationRow.addView(locate, LinearLayout.LayoutParams(0, dp(49), 1.35f))
        skyToggle = text("Disable", 13f, ink, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(42, 44, 53), 14)
            isClickable = true
            isFocusable = true
            contentDescription = "Turn off location-based sky alignment and explore deep space"
            setOnClickListener {
                prefs.setSkyAligned(false)
                refreshSkyUi()
                syncStarTracking()
                preview.invalidate()
            }
        }
        locationRow.addView(skyToggle, LinearLayout.LayoutParams(0, dp(49), 0.8f).apply {
            marginStart = dp(9)
        })
        panel.addView(locationRow)
        panel.addView(text(
            "Location is requested only when you tap Sync. " +
                "The saved coordinates stay on your phone; GPS is not continually polled.",
            11f, subtle
        ).apply { setPadding(0, dp(11), 0, dp(2)) })

        panel.addView(text(
            "When Tap to travel is on, tap a planet or star to approach it, " +
                "or empty space to warp in that direction. When off, only the " +
                "Warp and Explore buttons navigate. Fictional worlds are marked simulated.",
            11f, subtle
        ).apply { setPadding(0, dp(16), 0, 0) })
        starControls.addView(panel)
        root.addView(starControls)
    }

    private fun refreshStarDensity() {
        densityChoices.removeAllViews()
        val selected = prefs.get().starDensity
        listOf("Sparse", "Balanced", "Rich").forEachIndexed { index, name ->
            val active = selected == index
            val item = text(name, 12f, if (active) backgroundColor else secondary,
                if (active) Typeface.BOLD else Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                background = rounded(if (active) ink else Color.rgb(36, 37, 44), 13)
                isClickable = true
                isFocusable = true
                contentDescription = "${name} star density, ${if (active) "selected" else "not selected"}"
                setOnClickListener {
                    prefs.setStarDensity(index)
                    refreshStarDensity()
                    preview.invalidate()
                }
            }
            densityChoices.addView(item, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(7)
            })
        }
    }

    private fun refreshStarEffects() {
        effectsChoices.removeAllViews()
        val selected = prefs.get().starEffects
        listOf("Survey", "Nebula", "Expedition").forEachIndexed { index, title ->
            val active = index == selected
            val chip = text(title, 12f, if (active) backgroundColor else secondary,
                if (active) Typeface.BOLD else Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                background = rounded(if (active) ink else Color.rgb(36, 37, 44), 13)
                isClickable = true
                isFocusable = true
                contentDescription = "$title space atmosphere, ${if (active) "selected" else "not selected"}"
                setOnClickListener {
                    prefs.setStarEffects(index)
                    refreshStarEffects()
                    preview.invalidate()
                }
            }
            effectsChoices.addView(chip, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(7)
            })
        }
    }

    private fun refreshSkyUi() {
        if (!::skyStatus.isInitialized) return
        val state = prefs.get()
        skyStatus.text = if (state.skyFixTime == 0L) {
            "Exploration mode  ·  Location not shared"
        } else {
            val lat = kotlin.math.abs(state.skyLatitude)
            val lon = kotlin.math.abs(state.skyLongitude)
            val latitude = String.format(Locale.US, "%.2f°%s", lat, if (state.skyLatitude < 0) "S" else "N")
            val longitude = String.format(Locale.US, "%.2f°%s", lon, if (state.skyLongitude < 0) "W" else "E")
            val aligned = if (state.skyAligned) "Local sky active" else "Saved · Exploration mode"
            "$aligned  ·  $latitude / $longitude"
        }
        skyToggle.visibility = if (state.skyAligned) View.VISIBLE else View.GONE
    }

    private fun showDestinations() {
        val names = arrayOf("Sun", "Mercury", "Venus", "Earth", "Mars",
            "Jupiter", "Saturn", "Uranus", "Neptune", "Sirius", "Rigil Kentaurus",
            "Vega", "Arcturus")
        android.app.AlertDialog.Builder(this)
            .setTitle("Navigate to…")
            .setItems(names) { _, index ->
                val destination = SpaceNavigation.destinationForName(
                    names[index], AstronomyCatalog.stars(this)
                )
                if (destination != null) {
                    if (prefs.get().skyAligned) prefs.setSkyAligned(false)
                    refreshSkyUi()
                    previewTracker.configureSky(prefs.get())
                    previewTracker.flyTo(destination)
                    prefs.requestDestination(destination.name)
                    preview.invalidate()
                    Toast.makeText(this, "Approaching ${destination.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Catalog coordinates unavailable", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun triggerStarWarp() {
        // The GPS-aligned sky is meaningful on Earth. A hyperspace jump
        // explicitly switches to deep-space exploration.
        if (prefs.get().skyAligned) {
            prefs.setSkyAligned(false)
            refreshSkyUi()
        }
        previewTracker.configureSky(prefs.get())
        previewTracker.warpForward()
        // The wallpaper process has an independent tracker. Notify it too:
        // the former v0.5 only warped the in-app preview.
        prefs.requestWarp()
        preview.invalidate()
    }

    private fun syncStarTracking() {
        if (prefs.get().preset == StarWindowRenderer.SCENE_INDEX &&
            ::preview.isInitialized && preview.running) {
            previewTracker.configureSky(prefs.get())
            previewTracker.start(prefs.get().starSensitivity)
        } else {
            previewTracker.stop()
        }
    }

    private fun syncMotionTracking() {
        val state = prefs.get()
        if (::preview.isInitialized && preview.running &&
            state.preset != StarWindowRenderer.SCENE_INDEX && state.gyroMode != 0) {
            previewGyro.start()
        } else {
            previewGyro.stop()
        }
    }

    private fun buildMotion(root: LinearLayout) {
        root.addView(section("Motion", "Quiet, or full of life."))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 22, borderColor)
            setPadding(dp(17), dp(15), dp(17), dp(11))
        }
        addSlider(
            panel, "Strength", prefs.get().intensity,
            min = .35f, max = 2.3f, prefs::setIntensity
        )
        addSlider(
            panel, "Flow", prefs.get().speed,
            min = .25f, max = 2.0f, prefs::setSpeed
        )
        root.addView(panel)

        gyroPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 22, borderColor)
            setPadding(dp(17), dp(15), dp(17), dp(13))
        }
        gyroPanel.addView(text("GYRO DEPTH", 10f, subtle, Typeface.BOLD).apply {
            letterSpacing = .14f
            setPadding(0, 0, 0, dp(12))
        })
        gyroModes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        gyroPanel.addView(gyroModes, LinearLayout.LayoutParams(-1, dp(44)))
        gyroPanel.addView(text(
            "Calibrates when the wallpaper becomes visible. Smooth, bounded movement; no drifting.",
            12f, secondary
        ).apply { setPadding(0, dp(13), 0, dp(3)) })
        addSlider(gyroPanel, "Gyro intensity", prefs.get().gyroStrength,
            min = .5f, max = 1.8f, prefs::setGyroStrength)

        mountainDepthPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        addSlider(mountainDepthPanel, "Mountain depth", prefs.get().mountainDepth,
            min = .5f, max = 1.6f, prefs::setMountainDepth)
        gyroPanel.addView(mountainDepthPanel)
        root.addView(gyroPanel, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })
    }

    private fun refreshGyroModes() {
        if (!::gyroModes.isInitialized) return
        gyroModes.removeAllViews()
        val names = listOf("Off", "Soft", "Dynamic", "Space")
        val current = prefs.get().gyroMode
        names.forEachIndexed { index, label ->
            val selected = index == current
            val chip = text(label, 12f,
                if (selected) backgroundColor else secondary,
                if (selected) Typeface.BOLD else Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                background = rounded(
                    if (selected) ink else Color.rgb(37, 38, 45), 12
                )
                isFocusable = true
                isClickable = true
                contentDescription = "Gyro $label, ${if (selected) "selected" else "not selected"}"
                setOnClickListener {
                    prefs.setGyroMode(index)
                    refreshGyroModes()
                    syncMotionTracking()
                    preview.invalidate()
                }
            }
            gyroModes.addView(chip, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(if (index < 3) 5 else 0)
            })
        }
    }

    private fun buildBehavior(root: LinearLayout) {
        root.addView(section("Preferences", "Everything else, kept simple."))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 22, borderColor)
            setPadding(dp(17), dp(6), dp(17), dp(14))
        }
        addToggle(panel, "Touch interaction", "Gesture response for non-space scenes", prefs.get().touch) {
            prefs.setTouch(it)
        }
        panel.addView(View(this).apply { setBackgroundColor(borderColor) }, LinearLayout.LayoutParams(-1, dp(1)))
        addToggle(panel, "Home-screen swipe", "Horizontal parallax when changing launcher pages", prefs.get().parallax) {
            prefs.setParallax(it)
        }

        val perfRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        perfRow.addView(text("Frame rate", 13f, ink, Typeface.BOLD), LinearLayout.LayoutParams(0, -2, 1f))
        frameRates = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        perfRow.addView(frameRates)
        panel.addView(View(this).apply { setBackgroundColor(borderColor) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(7) })
        panel.addView(perfRow)
        root.addView(panel)
    }

    private fun buildFooter(root: LinearLayout) {
        root.addView(text("Designed to feel at home on your screen.", 12f, subtle).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, 0)
        })
    }

    private fun buildApplyBar(root: FrameLayout) {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(13), dp(22), dp(15))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(22, 23, 27), backgroundColor)
            )
        }
        val apply = text("Set as wallpaper     ↗", 15f, backgroundColor, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            minHeight = dp(54)
            background = rounded(ink, 18)
            isClickable = true
            isFocusable = true
            contentDescription = "Preview and apply HALYN Motion using the Android wallpaper picker"
            setOnClickListener { openWallpaperPicker() }
        }
        bar.addView(apply, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private fun refreshScene() {
        val selected = prefs.get().preset
        val source = prefs.raw.getString("online_source", null)
        val ownPhoto = PhotoWallpaperStore(this).available()
        activeSceneName.text = when {
            selected == PhotoWallpaperStore.SCENE_INDEX && ownPhoto ->
                prefs.raw.getString("online_title", "My wallpaper") ?: "My wallpaper"
            selected == PhotoWallpaperStore.SCENE_INDEX -> "Choose a 4K wallpaper"
            else -> "Previous wallpaper"
        }
        if (::onlineCredit.isInitialized) {
            onlineCredit.visibility =
                if (selected == PhotoWallpaperStore.SCENE_INDEX && !source.isNullOrBlank())
                    View.VISIBLE else View.GONE
            onlineCredit.text = "Source & credits  ↗\n" +
                (prefs.raw.getString("online_artist", "") ?: "") + " · " +
                (prefs.raw.getString("online_license", "") ?: "") + "\n" +
                (prefs.raw.getString("online_resolution", "") ?: "")
        }
        if (::starControls.isInitialized) starControls.visibility = View.GONE
        if (::gyroPanel.isInitialized) {
            gyroPanel.visibility =
                if (selected == StarWindowRenderer.SCENE_INDEX) View.GONE else View.VISIBLE
            mountainDepthPanel.visibility = View.GONE
        }
    }

    private fun refreshPalette() {
        palettes.removeAllViews()
        val state = prefs.get()
        val colors = HalynRenderer.PALETTE_COLORS.toList() + state.customColor
        colors.forEachIndexed { index, color ->
            val selected = index == state.palette
            val tap = FrameLayout(this).apply {
                isClickable = true
                isFocusable = true
                contentDescription = if (index == 5) {
                    "Custom colour, ${hex(color)}, ${if (selected) "selected" else "not selected"}"
                } else {
                    "${HalynRenderer.PALETTES[index]}, ${if (selected) "selected" else "not selected"}"
                }
                setOnClickListener {
                    prefs.setPalette(index)
                    refreshPalette()
                    refreshScene()
                    preview.invalidate()
                }
            }
            val outline = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(if (selected) 2 else 1), if (selected) ink else borderColor)
                }
            }
            val center = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
            }
            tap.addView(outline, FrameLayout.LayoutParams(dp(43), dp(43), Gravity.CENTER))
            tap.addView(center, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
            palettes.addView(tap, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        val currentColor = colors[state.palette]
        val name = if (state.palette == 5) "Custom" else HalynRenderer.PALETTES[state.palette]
        activeColourName.text = "${name}  ·  ${hex(currentColor)}"
    }

    private fun openColourEditor() {
        val state = prefs.get()
        val current = if (state.palette == 5) state.customColor
            else HalynRenderer.PALETTE_COLORS[state.palette]
        ColorPickerDialog(this, current) { selected ->
            prefs.setCustomColor(selected)
            refreshPalette()
            refreshScene()
            preview.invalidate()
        }.show()
    }

    private fun refreshFps() {
        frameRates.removeAllViews()
        val selected = prefs.get().fps
        listOf(30, 45, 60).forEach { fps ->
            val active = fps == selected
            val choice = text("$fps", 12f, if (active) backgroundColor else secondary,
                if (active) Typeface.BOLD else Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                background = rounded(if (active) ink else Color.rgb(37, 38, 45), 11)
                isClickable = true
                isFocusable = true
                contentDescription = "$fps frames per second, ${if (active) "selected" else "not selected"}"
                setOnClickListener {
                    prefs.setFps(fps)
                    refreshFps()
                }
            }
            frameRates.addView(choice, LinearLayout.LayoutParams(dp(47), dp(39)).apply {
                marginStart = dp(5)
            })
        }
    }

    private fun addSlider(
        parent: LinearLayout, name: String, current: Float,
        min: Float, max: Float, onValue: (Float) -> Unit
    ) {
        val title = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        title.addView(text(name, 14f, ink, Typeface.BOLD), LinearLayout.LayoutParams(0, -2, 1f))
        val amount = text(String.format(Locale.US, "%.2f×", current), 12f, secondary)
        title.addView(amount)
        parent.addView(title)

        val slider = SeekBar(this).apply {
            this.max = 100
            progress = (((current - min) / (max - min)) * 100f).roundToInt().coerceIn(0, 100)
            progressTintList = ColorStateList.valueOf(ink)
            thumbTintList = ColorStateList.valueOf(ink)
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(71, 72, 79))
            contentDescription = name
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val value = min + (max - min) * progress / 100f
                    amount.text = String.format(Locale.US, "%.2f×", value)
                    onValue(value)
                    preview.invalidate()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        parent.addView(slider, LinearLayout.LayoutParams(-1, dp(49)))
    }

    private fun addToggle(
        parent: LinearLayout, name: String, caption: String,
        checked: Boolean, onValue: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(71)
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(text(name, 14f, ink, Typeface.BOLD))
        copy.addView(text(caption, 12f, secondary).apply {
            setPadding(0, dp(5), 0, 0)
        })
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Switch(this).apply {
            isChecked = checked
            contentDescription = name
            thumbTintList = ColorStateList.valueOf(ink)
            trackTintList = ColorStateList.valueOf(Color.rgb(72, 73, 80))
            setOnCheckedChangeListener { _, isChecked -> onValue(isChecked) }
        })
        parent.addView(row)
    }

    private fun section(title: String, subtitle: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(25), 0, dp(14))
            addView(text(title, 19f, ink, Typeface.BOLD).apply {
                letterSpacing = -.01f
            })
            addView(text(subtitle, 12f, secondary).apply {
                setPadding(0, dp(3), 0, 0)
            })
        }

    private fun openWallpaperPicker() {
        val component = ComponentName(this, HalynWallpaperService::class.java)
        try {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            })
        } catch (_: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (_: Exception) {
                Toast.makeText(this, "Find HALYN Motion under Wallpapers in Settings.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::preview.isInitialized) preview.running = true
        syncStarTracking()
        syncMotionTracking()
        if (awaitingClockOverlayPermission) {
            if (Settings.canDrawOverlays(this)) launchClockPlacement()
            else {
                awaitingClockOverlayPermission = false
                if (::clockPlacementToggle.isInitialized) clockPlacementToggle.isChecked = false
            }
        } else if (::clockPlacementToggle.isInitialized) {
            clockPlacementToggle.isChecked = false
        }
    }

    override fun onPause() {
        if (::preview.isInitialized) preview.running = false
        previewTracker.stop()
        previewGyro.stop()
        skyLocation.cancel()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        skyLocation.handlePermissionResult(requestCode)
    }

    companion object {
        private const val REQUEST_CLOCK_FONT = 6208
        private const val REQUEST_WALLPAPER_IMAGE = 6209
    }

    override fun onDestroy() {
        if (::onlineGallery.isInitialized) onlineGallery.close()
        skyLocation.cancel()
        super.onDestroy()
    }

    private fun text(value: String, size: Float, color: Int, weight: Int = Typeface.NORMAL): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.create("sans-serif", weight)
            includeFontPadding = true
        }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun hex(color: Int): String = String.format(Locale.US, "#%06X", color and 0x00FFFFFF)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private inner class SceneTilePreview(private val sceneIndex: Int) : View(this@MainActivity) {
        private val renderer = HalynRenderer(this@MainActivity)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val state = prefs.get().copy(preset = sceneIndex, clockStyle = 0)
            // Thumbnails are intentionally static to avoid rendering twelve extra
            // continuously animated views while browsing the gallery.
            renderer.draw(canvas, state, 7f + sceneIndex * 1.7f)
            // Mountain cards previously omitted their foreground entirely.
            renderer.drawForeground(canvas, state)
        }
    }

    private inner class LivePreview : View(this@MainActivity) {
        private val renderer = HalynRenderer(this@MainActivity)
        private val clockRenderer = ClockOverlayRenderer(this@MainActivity)
        private val startTime = SystemClock.uptimeMillis()
        private var touchX = .5f
        private var touchY = .5f
        private var pointerDownMs = 0L
        private var pointerDownX = 0f
        private var pointerDownY = 0f
        var running: Boolean = false
            set(value) {
                field = value
                if (value) postInvalidateOnAnimation()
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val state = prefs.get()
            val tilt = if (state.preset == StarWindowRenderer.SCENE_INDEX) {
                ParallaxPose()
            } else {
                previewGyro.pose(state)
            }
            renderer.draw(
                canvas, state,
                (SystemClock.uptimeMillis() - startTime) / 1000f,
                touchX, touchY,
                starPose = if (state.preset == StarWindowRenderer.SCENE_INDEX) previewTracker.pose() else null,
                gyro = tilt
            )
            clockRenderer.draw(canvas, state)
            renderer.drawForeground(canvas, state, tilt)
            if (running) postInvalidateDelayed(1000L / state.fps)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            // Do not intercept taps or scrolling in the Star Window preview
            // when the user prefers a passive live wallpaper.
            val state = prefs.get()
            if (state.preset == StarWindowRenderer.SCENE_INDEX && !state.starTapTravel) {
                return false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pointerDownMs = SystemClock.uptimeMillis()
                    pointerDownX = event.x
                    pointerDownY = event.y
                    touchX = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    touchY = (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    touchX = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    touchY = (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (prefs.get().preset == StarWindowRenderer.SCENE_INDEX &&
                        SystemClock.uptimeMillis() - pointerDownMs <= 330L &&
                        kotlin.math.abs(event.x - pointerDownX) < dp(24) &&
                        kotlin.math.abs(event.y - pointerDownY) < dp(24)) {
                        if (prefs.get().skyAligned) prefs.setSkyAligned(false)
                        refreshSkyUi()
                        previewTracker.configureSky(prefs.get())
                        val pose = previewTracker.pose()
                        val destination = SpaceNavigation.pick(
                            pose, event.x, event.y, width.toFloat(), height.toFloat(),
                            AstronomyCatalog.stars(this@MainActivity)
                        )
                        if (destination != null) {
                            previewTracker.flyTo(destination)
                            prefs.requestDestination(destination.name)
                        } else {
                            val nx = event.x / width.coerceAtLeast(1)
                            val ny = event.y / height.coerceAtLeast(1)
                            val aspect = width.toFloat() / height.coerceAtLeast(1)
                            previewTracker.warpToward(nx, ny, aspect)
                            prefs.requestWarpToward(nx, ny, aspect)
                        }
                    }
                    performClick()
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
