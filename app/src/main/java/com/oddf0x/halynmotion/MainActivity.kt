package com.oddf0x.halynmotion

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.ArrayAdapter
import android.widget.EditText
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
 * accessible Android views. Preserves all 28 original shaders alongside 30 new ones.
 */
class MainActivity : Activity() {
    private val prefs by lazy { MotionPreferences(this) }
    private val savedPalettes by lazy { SavedPalettes(this) }
    private val previewTracker by lazy { SpatialTracker(this) }
    private val previewGyro by lazy { GyroParallax(this) }
    private val previewSignals by lazy { SceneSignalSampler(this, prefs.raw) }
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
    private lateinit var clockStyles: LinearLayout
    private lateinit var clockPositions: LinearLayout
    private lateinit var clockFonts: LinearLayout
    private lateinit var fontLibraryButton: TextView
    private lateinit var clockPlacementToggle: Switch
    private var awaitingClockOverlayPermission = false
    private val originalCards = mutableListOf<Pair<Int, LinearLayout>>()
    private val originalTileRenderer by lazy { HalynRenderer(this) }
    private lateinit var effectPanel: LinearLayout
    private lateinit var auraCyberControls: LinearLayout
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
    private lateinit var fourColorSwatches: LinearLayout
    private lateinit var fourColorToggle: Switch
    private lateinit var frameRates: LinearLayout
    private lateinit var qualityModes: LinearLayout
    private lateinit var activeSceneName: TextView
    private lateinit var activeMechanicDescription: TextView
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
        buildFluidGestures(column)
        buildColours(column)
        buildClock(column)
        buildStarWindow(column)
        buildMotion(column)
        buildBehavior(column)
        buildFooter(column)
        buildApplyBar(frame)

        setContentView(frame)
        refreshScene()
        refreshPalette()
        refreshFps()
        refreshShaderQuality()
        refreshClockStyles()
        refreshClockPositions()
        refreshClockFonts()
        refreshStarDensity()
        refreshStarEffects()
        refreshGyroModes()
        refreshSkyUi()
        syncStarTracking()
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

        root.addView(text("Fluid. Made personal.", 32f, ink, Typeface.BOLD).apply {
            setPadding(0, dp(20), 0, 0)
            letterSpacing = -.035f
        })
        root.addView(text("Fluid wallpapers you can shape with your own gestures. Space stays.", 14f, secondary).apply {
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
        activeMechanicDescription = text("", 12f, Color.rgb(205, 205, 215)).apply {
            setPadding(0, dp(5), 0, 0)
            maxLines = 3
        }
        foot.addView(activeMechanicDescription)
        shell.addView(foot, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        val density = resources.displayMetrics
        val availableWidth = (density.widthPixels / density.density).toInt() - 44
        // Keeps a phone-like canvas while avoiding an overwhelming preview on small devices.
        val heightDp = (availableWidth * 1.08f).roundToInt().coerceIn(290, 378)
        root.addView(shell, LinearLayout.LayoutParams(-1, dp(heightDp)))
    }

    private fun buildOriginalRail(root: LinearLayout, title: String, indexes: List<Int>) {
        root.addView(text(title, 13f, secondary, Typeface.BOLD).apply {
            setPadding(0, dp(12), 0, dp(9))
        })
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        indexes.forEach { index ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(cardColor, 17, borderColor)
                clipToOutline = true
                isClickable = true
                isFocusable = true
                contentDescription = "Select ${HalynRenderer.PRESETS[index]} animated wallpaper. " +
                    (FluidMotionRenderer.description(index)
                        ?: CalmSlateRenderer.description(index)
                        ?: SmoothWallpaperRenderer.mechanicDescription(index)
                        ?: MaterialXRenderer.description(index)
                        ?: GlassOrbitCollectionRenderer.description(index)
                        ?: AuraCyberRenderer.description(index)
                        ?: "HALYN original effect")
                setOnClickListener {
                    prefs.setPreset(index)
                    refreshScene()
                    syncStarTracking()
                    syncMotionTracking()
                    preview.invalidate()
                }
            }
            val artwork = SceneTilePreview(index).apply {
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            card.addView(artwork, LinearLayout.LayoutParams(-1, dp(166)))
            card.addView(text(HalynRenderer.PRESETS[index], 12f, ink, Typeface.BOLD).apply {
                maxLines = 2
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(7), dp(10), dp(9))
            }, LinearLayout.LayoutParams(-1, dp(51)))
            rail.addView(card, LinearLayout.LayoutParams(dp(149), dp(217)).apply {
                marginEnd = dp(10)
            })
            originalCards.add(index to card)
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(rail)
        }, LinearLayout.LayoutParams(-1, dp(226)))
    }

    private fun refreshOriginalCards() {
        if (originalCards.isEmpty()) return
        val selected = prefs.get().preset
        originalCards.forEach { (index, card) ->
            card.background = rounded(
                if (index == selected) Color.rgb(35, 42, 54) else cardColor,
                17,
                if (index == selected) ink else borderColor
            )
            card.isSelected = index == selected
        }
    }

    private fun buildScenes(root: LinearLayout) {
        root.addView(section("Fluid Motion",
            "Six smooth, continuously moving liquid wallpapers. No landscapes or decorative shapes."))
        buildOriginalRail(root, "Liquid, satin & ink",
            (FluidMotionRenderer.FIRST..FluidMotionRenderer.LAST).toList())
        root.addView(section("Space Exploration",
            "The original interactive Star Window remains unchanged."))
        buildOriginalRail(root, "Star Window", listOf(StarWindowRenderer.SCENE_INDEX))
    }

    private fun buildFluidGestures(root: LinearLayout) {
        root.addView(section("Gesture Studio",
            "Record a finger motion and choose the effect it triggers."))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 22, borderColor)
            setPadding(dp(17), dp(16), dp(17), dp(16))
        }
        panel.addView(text("Assign a ripple, whirlpool, current push, light bloom, calm or surge to any recorded path.", 13f, secondary))
        panel.addView(text("Record and manage gestures  ›", 14f, ink, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(40, 42, 47), 13, borderColor)
            minHeight = dp(54)
            isClickable = true
            isFocusable = true
            contentDescription = "Open recorder for custom fluid gestures"
            setOnClickListener {
                FluidGestureStudio(this@MainActivity, FluidGestures(this@MainActivity)) { action, x, y ->
                    preview.triggerFluidEffect(action, x, y)
                }.show()
            }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(12) })
        panel.addView(text(
            "One UI may intercept home-screen swipes. The in-app recorder always receives the full stroke.",
            11f, secondary
        ).apply { setPadding(0, dp(12), 0, 0) })
        root.addView(panel)
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

        panel.addView(View(this).apply { setBackgroundColor(borderColor) },
            LinearLayout.LayoutParams(-1, dp(1)).apply {
                topMargin = dp(14)
                bottomMargin = dp(14)
            })
        fourColorToggle = Switch(this).apply {
            text = "Four-colour palette"
            setTextColor(ink)
            textSize = 15f
            minHeight = dp(48)
            isChecked = prefs.get().fourColorsEnabled
            contentDescription = "Use four independently selected colours on this wallpaper"
            setOnCheckedChangeListener { _, checked ->
                if (checked != prefs.get().fourColorsEnabled) {
                    prefs.setFourColorsEnabled(checked)
                    refreshFourColors()
                    preview.invalidate()
                }
            }
        }
        panel.addView(fourColorToggle, LinearLayout.LayoutParams(-1, dp(48)))
        panel.addView(text(
            "Each wallpaper remembers its four colours. Tap a swatch to edit it. " +
                "Turn this off to restore the wallpaper's original palette.",
            12f, secondary
        ).apply { setPadding(0, dp(4), 0, dp(10)) })
        fourColorSwatches = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        panel.addView(fourColorSwatches, LinearLayout.LayoutParams(-1, dp(65)))
        panel.addView(text("SAVE & REUSE", 10f, subtle, Typeface.BOLD).apply {
            letterSpacing = .13f
            setPadding(0, dp(15), 0, dp(10))
        })
        val savedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val saveButton = text("Save these colours", 13f, ink, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(39, 40, 44), 13, borderColor)
            isClickable = true
            isFocusable = true
            minHeight = dp(48)
            contentDescription = "Save the current colour settings under a reusable name"
            setOnClickListener { saveCurrentPaletteDialog() }
        }
        savedRow.addView(saveButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        val chooseButton = text("Saved presets", 13f, backgroundColor, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(ink, 13)
            isClickable = true
            isFocusable = true
            minHeight = dp(48)
            contentDescription = "Choose a saved colour palette or a built-in calm preset"
            setOnClickListener { showSavedPaletteDialog() }
        }
        savedRow.addView(chooseButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginStart = dp(8)
        })
        panel.addView(savedRow)
        root.addView(panel)
        refreshFourColors()
    }

    private fun saveCurrentPaletteDialog() {
        val nameField = EditText(this).apply {
            hint = "e.g. Quiet charcoal"
            isSingleLine = true
            setTextColor(ink)
            setHintTextColor(secondary)
            contentDescription = "Name for saved colour preset"
            filters = arrayOf(android.text.InputFilter.LengthFilter(32))
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Save colour preset")
            .setMessage("Remember this shade and four-colour settings for other wallpapers.")
            .setView(nameField)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val ok = savedPalettes.save(nameField.text.toString(), prefs.get())
                Toast.makeText(this,
                    if (ok) "Colour preset saved" else "Enter a name first",
                    Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showSavedPaletteDialog() {
        val builtIns = SavedPalettes.CALM_SETS
        val personal = savedPalettes.list()
        val all = builtIns + personal
        val options = all.mapIndexed { index, item ->
            (if (index < builtIns.size) "CALM  ·  " else "SAVED  ·  ") + item.name
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Colour presets")
            .setItems(options) { _, selected ->
                savedPalettes.apply(all[selected], prefs)
                refreshPalette()
                refreshFourColors()
                refreshScene()
                preview.invalidate()
                originalCards.forEach { (_, card) ->
                    (card.getChildAt(0) as? View)?.invalidate()
                }
                Toast.makeText(this, "Colours applied", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Delete saved") { _, _ -> showDeletePaletteDialog() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDeletePaletteDialog() {
        val personal = savedPalettes.list()
        if (personal.isEmpty()) {
            Toast.makeText(this, "No saved presets yet", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Delete saved preset")
            .setItems(personal.map { it.name }.toTypedArray()) { _, index ->
                val chosen = personal[index]
                AlertDialog.Builder(this)
                    .setTitle("Delete " + chosen.name + "?")
                    .setMessage("Other wallpapers and their saved colours stay unchanged.")
                    .setPositiveButton("Delete") { _, _ ->
                        savedPalettes.remove(chosen.name)
                        Toast.makeText(this, "Preset deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        clockOptionsPanel.addView(text("FONT LIBRARY", 10f, subtle, Typeface.BOLD).apply {
            letterSpacing = .15f
            setPadding(0, dp(16), 0, dp(7))
        })
        fontLibraryButton = text("", 14f, ink, Typeface.BOLD).apply {
            background = rounded(Color.rgb(35, 36, 43), 13)
            minimumHeight = dp(52)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isFocusable = true
            isClickable = true
            contentDescription = "Browse and manage imported clock fonts"
            setOnClickListener { openImportedFontLibrary() }
        }
        clockOptionsPanel.addView(fontLibraryButton, LinearLayout.LayoutParams(-1, -2))
        clockOptionsPanel.addView(text(
            "No HALYN font-count or file-size cap. Uses your available device storage.",
            11f, secondary
        ).apply { setPadding(dp(3), dp(9), dp(3), dp(4)) })
        clockOptionsPanel.addView(text("Add fonts  .ttf / .otf / .ttc  ↗", 13f, ink, Typeface.BOLD).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
            minimumHeight = dp(46)
            isFocusable = true
            isClickable = true
            contentDescription = "Import one or more OpenType or TrueType clock fonts"
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
        val state = prefs.get()

        ClockFontStore.names.forEachIndexed { index, label ->
            val active = state.clockFont == index
            val chip = text("$label  12:34", 12f,
                if (active) backgroundColor else ink,
                Typeface.NORMAL
            ).apply {
                gravity = Gravity.CENTER
                typeface = ClockFontStore.presetTypeface(index)
                    ?: Typeface.create("sans-serif-light", Typeface.NORMAL)
                setPadding(dp(13), 0, dp(13), 0)
                background = rounded(if (active) ink else Color.rgb(36, 37, 44), 13)
                minHeight = dp(44)
                isClickable = true
                isFocusable = true
                contentDescription = "Clock font preset: $label" +
                    if (active) ", selected" else ", available"
                setOnClickListener {
                    prefs.setClockFont(index)
                    if (prefs.get().clockStyle == 0) {
                        prefs.setClockStyle(1)
                        refreshClockStyles()
                    }
                    refreshClockFonts()
                    preview.invalidate()
                }
            }
            clockFonts.addView(chip, LinearLayout.LayoutParams(-2, dp(44)).apply {
                marginEnd = dp(7)
            })
        }

        if (::fontLibraryButton.isInitialized) {
            val imported = ClockFontStore.list(this)
            val chosen = imported.firstOrNull { it.id == state.clockFontId }
            val display = if (state.clockFont == 6 && chosen != null) {
                "Imported fonts (${imported.size})  ·  ${chosen.name}  ›"
            } else {
                "Imported fonts (${imported.size})  ·  Browse & manage  ›"
            }
            fontLibraryButton.text = display
            fontLibraryButton.typeface =
                if (state.clockFont == 6 && chosen != null)
                    ClockFontStore.preview(this, chosen.id) ?: Typeface.DEFAULT
                else Typeface.create("sans-serif-medium", Typeface.NORMAL)
            fontLibraryButton.contentDescription = display
        }
    }

    private fun selectClockFont(id: String) {
        prefs.selectImportedClockFont(id)
        // Selecting a font should immediately show the result in the studio,
        // even when the clock had previously been switched off.
        if (prefs.get().clockStyle == 0) {
            prefs.setClockStyle(1)
            refreshClockStyles()
        }
        refreshClockFonts()
        preview.invalidate()
    }

    private fun openImportedFontLibrary() {
        val fonts = ClockFontStore.list(this)
        if (fonts.isEmpty()) {
            chooseClockFont()
            return
        }
        val adapter = object : ArrayAdapter<ClockFontStore.ImportedFont>(
            this, android.R.layout.simple_list_item_1, fonts
        ) {
            override fun getView(
                position: Int, convertView: View?, parent: ViewGroup
            ): View {
                val cell = super.getView(position, convertView, parent) as TextView
                val item = getItem(position) ?: return cell
                val kb = item.bytes / 1024.0
                val sizeLabel = if (kb < 1024.0) {
                    "%.0f KB".format(Locale.ROOT, kb)
                } else {
                    "%.1f MB".format(Locale.ROOT, kb / 1024.0)
                }
                cell.text = "${item.name}  ·  12:34\n$sizeLabel · Long-press to remove"
                cell.typeface = ClockFontStore.preview(this@MainActivity, item.id)
                    ?: Typeface.DEFAULT
                cell.setTextColor(ink)
                cell.textSize = 15f
                cell.maxLines = 2
                cell.setPadding(dp(17), dp(12), dp(17), dp(12))
                cell.setBackgroundColor(cardColor)
                return cell
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Imported fonts (${fonts.size})")
            .setAdapter(adapter) { _, position -> selectClockFont(fonts[position].id) }
            .setPositiveButton("Add more") { _, _ -> chooseClockFont() }
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnShowListener {
            dialog.listView.setOnItemLongClickListener { _, _, position, _ ->
                val font = fonts[position]
                AlertDialog.Builder(this)
                    .setTitle("Remove ${font.name}?")
                    .setMessage("Only the copy stored in HALYN will be deleted.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove") { _, _ ->
                        if (ClockFontStore.remove(this, font.id)) {
                            prefs.removedImportedClockFont(font.id)
                            refreshClockFonts()
                            preview.invalidate()
                            dialog.dismiss()
                            openImportedFontLibrary()
                        } else {
                            Toast.makeText(this, "Unable to remove font", Toast.LENGTH_SHORT).show()
                        }
                    }.show()
                true
            }
        }
        dialog.show()
    }

    private fun chooseClockFont() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
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
        if (requestCode == REQUEST_CLOCK_FONT && resultCode == RESULT_OK && data != null) {
            val uris = linkedSetOf<Uri>()
            data.data?.let { uris.add(it) }
            data.clipData?.let { clips ->
                for (index in 0 until clips.itemCount) {
                    clips.getItemAt(index).uri?.let { uris.add(it) }
                }
            }
            if (uris.isEmpty()) return

            Toast.makeText(this, "Importing ${uris.size} font file(s)", Toast.LENGTH_SHORT).show()
            // Stream from SAF on an IO thread so large font libraries do not
            // freeze the Android UI during import or validation.
            Thread {
                val saved = mutableListOf<String>()
                var rejected = 0
                for (uri in uris) {
                    val id = ClockFontStore.install(applicationContext, uri)
                    if (id != null) saved.add(id) else rejected++
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (saved.isNotEmpty()) selectClockFont(saved.last())
                    else refreshClockFonts()
                    val status = when {
                        saved.isNotEmpty() && rejected == 0 ->
                            "${saved.size} font(s) added to HALYN"
                        saved.isNotEmpty() ->
                            "${saved.size} font(s) added · $rejected invalid or unavailable"
                        else -> "No valid .ttf, .otf, or .ttc fonts found"
                    }
                    Toast.makeText(this, status, Toast.LENGTH_LONG).show()
                }
            }.start()
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
        root.addView(section("Motion", "Visible movement, adjustable without sacrificing calm."))
        effectPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 22, borderColor)
            setPadding(dp(17), dp(16), dp(17), dp(12))
        }
        addSlider(effectPanel, "Movement strength", prefs.get().intensity,
            min = .35f, max = 2.3f, prefs::setIntensity)
        addSlider(effectPanel, "Motion speed", prefs.get().speed,
            min = .25f, max = 2.0f, prefs::setSpeed)
        effectPanel.addView(text(
            "Landscape animation continues even when the phone is resting. " +
                "Strength adjusts displacement; speed controls timing.",
            12f, secondary
        ).apply { setPadding(0, dp(4), 0, dp(4)) })
        root.addView(effectPanel)
        gyroPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 22, borderColor)
            setPadding(dp(17), dp(15), dp(17), dp(13))
        }
        gyroPanel.addView(text("OPTIONAL PARALLAX", 10f, subtle, Typeface.BOLD).apply {
            letterSpacing = .14f
            setPadding(0, 0, 0, dp(12))
        })
        gyroModes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        gyroPanel.addView(gyroModes, LinearLayout.LayoutParams(-1, dp(44)))
        gyroPanel.addView(text(
            "Parallax is separate from animation. Your landscapes still move when gyroscope is off.",
            12f, secondary
        ).apply { setPadding(0, dp(13), 0, dp(3)) })
        addSlider(gyroPanel, "Gyro intensity", prefs.get().gyroStrength,
            min = .5f, max = 1.8f, prefs::setGyroStrength)
        mountainDepthPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        root.addView(gyroPanel, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })
    }

    private fun refreshShaderQuality() {
        if (!::qualityModes.isInitialized) return
        qualityModes.removeAllViews()
        val selectedQuality = prefs.get().shaderQuality
        listOf("Efficient", "Balanced", "Cinematic").forEachIndexed { index, name ->
            val selected = index == selectedQuality
            val chip = text(name, 11f,
                if (selected) backgroundColor else secondary,
                if (selected) Typeface.BOLD else Typeface.NORMAL
            ).apply {
                gravity = Gravity.CENTER
                background = rounded(if (selected) ink else Color.rgb(37, 38, 45), 11)
                isFocusable = true
                isClickable = true
                contentDescription = "$name shader quality, ${if (selected) "selected" else "not selected"}"
                setOnClickListener {
                    prefs.setShaderQuality(index)
                    refreshShaderQuality()
                    preview.invalidate()
                }
            }
            qualityModes.addView(chip, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(if (index < 2) 6 else 0)
            })
        }
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
        // Source photographs are passive wallpapers; no tap-to-warp gesture
        // is enabled for photo mode, even if a previous procedural preset
        // stored touch interaction = true.
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
        panel.addView(text("Enable notification ripples  ↗", 13f, ink, Typeface.BOLD).apply {
            setPadding(0, dp(22), 0, dp(7))
            isClickable = true
            isFocusable = true
            contentDescription = "Open Android notification access settings. Optional for Notification Ripple and Signal Wings."
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        })
        panel.addView(text(
            "Animations simulate weather and motion locally. Existing space, clock and colour controls remain available.",
            12f, secondary
        ))
        root.addView(panel)
    }

    private fun buildFooter(root: LinearLayout) {
        root.addView(text("Six flowing environments · Star Window · Saved colours.", 12f, subtle).apply {
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
        refreshFourColors()
        val source = prefs.raw.getString("online_source", null)
        val ownPhoto = PhotoWallpaperStore(this).available()
        activeSceneName.text = when {
            selected == PhotoWallpaperStore.SCENE_INDEX && ownPhoto ->
                prefs.raw.getString("online_title", "My wallpaper") ?: "My wallpaper"
            selected == PhotoWallpaperStore.SCENE_INDEX -> "Choose a 4K wallpaper"
            else -> HalynRenderer.PRESETS.getOrElse(selected) { "HALYN Original" }
        }
        if (::activeMechanicDescription.isInitialized) {
            activeMechanicDescription.text =
                FluidMotionRenderer.description(selected)
                    ?: CalmSlateRenderer.description(selected)
                    ?: SmoothWallpaperRenderer.mechanicDescription(selected)
                    ?: MaterialXRenderer.description(selected)
                    ?: GlassOrbitCollectionRenderer.description(selected)
                    ?: AuraCyberRenderer.description(selected)
                    ?: (if (selected == BubbleGardenRenderer.SCENE_INDEX)
                        BubbleGardenRenderer.DESCRIPTION else null)
                    ?: "Live originals · 4K photography · Clock & motion"
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
        refreshOriginalCards()
        if (::auraCyberControls.isInitialized) {
            auraCyberControls.visibility = if (AuraCyberRenderer.isAuraCyber(selected))
                View.VISIBLE else View.GONE
        }
        if (::effectPanel.isInitialized) {
            effectPanel.visibility = if (FluidMotionRenderer.isFluid(selected)) View.VISIBLE else View.GONE
        }
        if (::starControls.isInitialized) starControls.visibility =
            if (selected == StarWindowRenderer.SCENE_INDEX) View.VISIBLE else View.GONE
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

    private fun refreshFourColors() {
        if (!::fourColorSwatches.isInitialized) return
        val state = prefs.get()
        fourColorToggle.isChecked = state.fourColorsEnabled
        fourColorSwatches.removeAllViews()
        val slots = arrayOf("First", "Second", "Third", "Fourth")
        for (index in 0..3) {
            val color = QuadColorSystem.color(state, index)
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = "${slots[index]} colour ${hex(color)}. Edit colour."
                setOnClickListener {
                    ColorPickerDialog(this@MainActivity, color) { chosen ->
                        prefs.setFourColor(index, chosen)
                        refreshFourColors()
                        preview.invalidate()
                        // Selection cards are static views; repaint to reflect new colours.
                        originalCards.forEach { (_, card) ->
                            (card.getChildAt(0) as? View)?.invalidate()
                        }
                    }.show()
                }
            }
            val disc = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(2), if (state.fourColorsEnabled) ink else borderColor)
                }
            }
            cell.addView(disc, LinearLayout.LayoutParams(dp(35), dp(35)))
            cell.addView(text("${index+1} · ${hex(color)}", 10f, secondary).apply {
                gravity = Gravity.CENTER
                maxLines = 1
            }, LinearLayout.LayoutParams(-1, dp(23)))
            fourColorSwatches.addView(cell, LinearLayout.LayoutParams(0, dp(64), 1f))
        }
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
        private val renderer get() = originalTileRenderer
        private val animationStart = SystemClock.uptimeMillis()
        private val animated = FluidMotionRenderer.isFluid(sceneIndex) ||
            MaterialXRenderer.isMaterial(sceneIndex) ||
            GlassOrbitCollectionRenderer.isGlassOrbit(sceneIndex) ||
            AuraCyberRenderer.isAuraCyber(sceneIndex) ||
            sceneIndex == BubbleGardenRenderer.SCENE_INDEX

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val state = prefs.getForPreset(sceneIndex).copy(clockStyle = 0, shaderQuality = 0)
            val seconds = if (animated) {
                (SystemClock.uptimeMillis() - animationStart) / 1000f +
                    sceneIndex * .37f
            } else {
                7f + sceneIndex * 1.7f
            }
            renderer.draw(canvas, state, seconds)
            // Mountain cards previously omitted their foreground entirely.
            renderer.drawForeground(canvas, state)
            // Gallery animations are intentionally capped to ~12fps and only
            // scheduled while visible to avoid 18 hidden animations consuming
            // battery or UI thread time on the Galaxy A56.
            if (animated && isShown && hasWindowFocus()) {
                val visibleBounds = android.graphics.Rect()
                if (getGlobalVisibleRect(visibleBounds) &&
                    visibleBounds.width() > 0 && visibleBounds.height() > 0) {
                    postInvalidateDelayed(84L)
                }
            }
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
                gyro = tilt,
                touchForce = if (state.touch && pointerDownMs > 0L)
                    (1f - (SystemClock.uptimeMillis() - pointerDownMs) / 2400f).coerceIn(0f, 1f)
                else 0f,
                reactions = previewSignals.sample()
            )
            clockRenderer.draw(canvas, state)
            renderer.drawForeground(canvas, state, tilt)
            if (running) {
                // A motionless photo without a clock needs no redraw loop;
                // with a clock, repaint only when the displayed second changes.
                val photoStill = state.preset == PhotoWallpaperStore.SCENE_INDEX &&
                    state.gyroMode == 0
                if (!photoStill) {
                    postInvalidateDelayed(1000L / state.fps)
                } else if (state.clockStyle != 0) {
                    val nextSecond = 1_000L - System.currentTimeMillis() % 1_000L
                    postInvalidateDelayed(nextSecond.coerceAtLeast(16L) + 5L)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            // Do not intercept taps or scrolling in the Star Window preview
            // when the user prefers a passive live wallpaper.
            val state = prefs.get()
            if ((state.preset == StarWindowRenderer.SCENE_INDEX && !state.starTapTravel) ||
                (state.preset != StarWindowRenderer.SCENE_INDEX && !state.touch)) {
                return false
            }
            if (state.preset == BubbleGardenRenderer.SCENE_INDEX) {
                renderer.onBubbleTouch(
                    event.actionMasked,
                    (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
                    (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                )
            }
            if (AuraCyberRenderer.isAuraCyber(state.preset)) {
                renderer.onAuraCyberTouch(
                    event.actionMasked, event.x, event.y, event.eventTime,
                    width.toFloat(), height.toFloat(),
                    resources.displayMetrics.density, state.strikeSensitivity
                )
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
