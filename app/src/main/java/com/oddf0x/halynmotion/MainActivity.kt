package com.oddf0x.halynmotion

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
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
    private lateinit var preview: LivePreview
    private lateinit var scenes: LinearLayout
    private lateinit var newScenes: LinearLayout
    private lateinit var clockStyles: LinearLayout
    private lateinit var clockPositions: LinearLayout
    private lateinit var clockOptionsPanel: LinearLayout
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
        buildColours(column)
        buildClock(column)
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

        root.addView(text("Made to move.", 34f, ink, Typeface.BOLD).apply {
            setPadding(0, dp(20), 0, 0)
            letterSpacing = -.035f
        })
        root.addView(text("An expressive canvas for your screen.", 14f, secondary).apply {
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
        foot.addView(text("Touch to explore the movement", 12f, Color.rgb(205, 205, 215)).apply {
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
        root.addView(section("The collection", "Twelve distinct motion styles."))
        root.addView(text("HALYN ORIGINALS", 11f, subtle, Typeface.BOLD).apply {
            letterSpacing = .14f
            setPadding(0, 0, 0, dp(7))
        })
        scenes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val rail = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(scenes)
        }
        root.addView(rail, LinearLayout.LayoutParams(-1, dp(159)))

        root.addView(text("NEW COLLECTION", 11f, subtle, Typeface.BOLD).apply {
            letterSpacing = .14f
            setPadding(0, dp(16), 0, dp(7))
        })
        newScenes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val newRail = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(newScenes)
        }
        root.addView(newRail, LinearLayout.LayoutParams(-1, dp(159)))
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
        val selected = prefs.get().clockPosition
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
    }

    private fun buildBehavior(root: LinearLayout) {
        root.addView(section("Preferences", "Everything else, kept simple."))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, 22, borderColor)
            setPadding(dp(17), dp(6), dp(17), dp(14))
        }
        addToggle(panel, "Touch interaction", "Respond to your gestures", prefs.get().touch) {
            prefs.setTouch(it)
        }
        panel.addView(View(this).apply { setBackgroundColor(borderColor) }, LinearLayout.LayoutParams(-1, dp(1)))
        addToggle(panel, "Parallax", "Follow home screen movement", prefs.get().parallax) {
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
        scenes.removeAllViews()
        newScenes.removeAllViews()
        val selected = prefs.get().preset
        HalynRenderer.PRESETS.forEachIndexed { index, name ->
            val active = selected == index
            val item = FrameLayout(this).apply {
                background = rounded(if (active) ink else borderColor, 19)
                setPadding(dp(if (active) 2 else 1), dp(if (active) 2 else 1),
                    dp(if (active) 2 else 1), dp(if (active) 2 else 1))
                isClickable = true
                isFocusable = true
                contentDescription = "Wallpaper ${index + 1} of 12, ${name}, ${if (active) "selected" else "not selected"}"
                setOnClickListener {
                    prefs.setPreset(index)
                    refreshScene()
                    preview.invalidate()
                }
            }
            val art = FrameLayout(this).apply {
                background = rounded(Color.rgb(10, 12, 21), 17)
                clipToOutline = true
            }
            art.addView(SceneTilePreview(index), FrameLayout.LayoutParams(-1, -1))
            val fade = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(230, 6, 7, 11))
            )
            val caption = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = fade
                setPadding(dp(9), dp(16), dp(9), dp(9))
                addView(text(String.format(Locale.US, "%02d", index + 1), 9f,
                    Color.rgb(202, 201, 211), Typeface.BOLD))
                addView(text(name, 12f, ink, Typeface.BOLD).apply {
                    setSingleLine(true)
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(4), 0, 0)
                })
            }
            art.addView(caption, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            item.addView(art, FrameLayout.LayoutParams(-1, -1))
            val target = if (index < 6) scenes else newScenes
            target.addView(item, LinearLayout.LayoutParams(dp(120), dp(154)).apply {
                marginEnd = dp(10)
            })
        }
        activeSceneName.text = HalynRenderer.PRESETS[selected]
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
    }

    override fun onPause() {
        if (::preview.isInitialized) preview.running = false
        super.onPause()
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
        private val renderer = HalynRenderer()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val state = prefs.get().copy(preset = sceneIndex, clockStyle = 0)
            // Thumbnails are intentionally static to avoid rendering twelve extra
            // continuously animated views while browsing the gallery.
            renderer.draw(canvas, state, 7f + sceneIndex * 1.7f)
        }
    }

    private inner class LivePreview : View(this@MainActivity) {
        private val renderer = HalynRenderer()
        private val clockRenderer = ClockOverlayRenderer()
        private val startTime = SystemClock.uptimeMillis()
        private var touchX = .5f
        private var touchY = .5f
        var running: Boolean = false
            set(value) {
                field = value
                if (value) postInvalidateOnAnimation()
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val state = prefs.get()
            renderer.draw(
                canvas, state,
                (SystemClock.uptimeMillis() - startTime) / 1000f,
                touchX, touchY
            )
            clockRenderer.draw(canvas, state)
            if (running) postInvalidateDelayed(1000L / state.fps)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    touchX = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    touchY = (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    performClick()
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
