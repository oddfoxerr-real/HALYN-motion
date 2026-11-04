package com.oddf0x.halynmotion

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import kotlin.math.roundToInt

/**
 * Compact, native dark wallpaper studio. No Compose/WebView dependency, no ads,
 * no network permissions. Controls update the active wallpaper immediately.
 */
class MainActivity : Activity() {
    private val prefs by lazy { MotionPreferences(this) }
    private lateinit var preview: Preview
    private lateinit var presetItems: LinearLayout
    private lateinit var paletteItems: LinearLayout
    private lateinit var fpsItems: LinearLayout

    private val page = Color.rgb(8, 9, 14)
    private val surface = Color.rgb(22, 24, 33)
    private val accent = Color.rgb(185, 174, 255)
    private val white = Color.rgb(249, 249, 253)
    private val muted = Color.rgb(151, 151, 166)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page
        window.decorView.systemUiVisibility = 0

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(page)
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(36))
        }
        scroll.addView(content)

        content.addView(label("HALYN  /  MOTION", 12f, accent, Typeface.BOLD))
        content.addView(label("Make motion yours.", 31f, white, Typeface.BOLD).apply {
            setPadding(0, dp(8), 0, 0)
        })
        content.addView(label("Living wallpapers, without the clutter.", 14f, muted).apply {
            setPadding(0, dp(8), 0, dp(18))
        })

        val previewCard = FrameLayout(this).apply {
            background = rounded(Color.rgb(12, 15, 26), 24)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
                }
            }
        }
        preview = Preview().apply {
            contentDescription = "Animated live wallpaper preview. Touch to interact."
            isClickable = true
        }
        previewCard.addView(preview, FrameLayout.LayoutParams(-1, -1))
        previewCard.addView(label("LIVE PREVIEW", 11f, white, Typeface.BOLD).apply {
            background = rounded(Color.argb(210, 9, 12, 20), 40)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(14), dp(14), 0, 0)
        })
        content.addView(previewCard, LinearLayout.LayoutParams(-1, dp(360)))
        content.addView(label("Touch the preview to interact with the wallpaper.", 12f, muted).apply {
            setPadding(0, dp(12), 0, dp(16))
        })

        content.addView(sectionLabel("Motion styles"))
        presetItems = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val presetScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(presetItems)
        }
        content.addView(presetScroll, LinearLayout.LayoutParams(-1, dp(56)))
        paintPresets()

        content.addView(sectionLabel("Accent palette"))
        paletteItems = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        content.addView(paletteItems, LinearLayout.LayoutParams(-1, dp(58)))
        paintPalettes()

        content.addView(sectionLabel("Motion controls"))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(surface, 20)
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }
        addSlider(
            controls,
            "Intensity",
            ((prefs.get().intensity - .35f) / 1.95f * 100f).roundToInt(),
            { progress -> .35f + 1.95f * (progress / 100f) },
            { value -> prefs.setIntensity(value) }
        )
        addSlider(
            controls,
            "Motion speed",
            ((prefs.get().speed - .25f) / 1.75f * 100f).roundToInt(),
            { progress -> .25f + 1.75f * (progress / 100f) },
            { value -> prefs.setSpeed(value) }
        )
        content.addView(controls)

        content.addView(sectionLabel("Performance"))
        fpsItems = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(fpsItems, LinearLayout.LayoutParams(-1, dp(48)))
        paintFps()

        content.addView(sectionLabel("Interaction"))
        val toggles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(surface, 20)
            setPadding(dp(16), dp(6), dp(16), dp(6))
        }
        addToggle(toggles, "Touch response", "Wallpaper reacts to gestures", prefs.get().touch) {
            prefs.setTouch(it)
        }
        addToggle(toggles, "Home screen parallax", "Subtle depth when changing pages", prefs.get().parallax) {
            prefs.setParallax(it)
        }
        content.addView(toggles)

        val apply = label("Apply live wallpaper", 16f, page, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = rounded(accent, 22)
            isClickable = true
            isFocusable = true
            contentDescription = "Open Android's system wallpaper preview and apply HALYN Motion"
            setOnClickListener { openWallpaperPicker() }
        }
        content.addView(apply, LinearLayout.LayoutParams(-1, dp(56)).apply {
            topMargin = dp(24)
        })

        content.addView(label("GPU-generated  •  Private  •  No background activity when hidden", 11f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (::preview.isInitialized) preview.running = true
    }

    override fun onPause() {
        if (::preview.isInitialized) preview.running = false
        super.onPause()
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
                Toast.makeText(this, "Open Wallpapers in your phone settings and select HALYN Motion.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun paintPresets() {
        presetItems.removeAllViews()
        val selected = prefs.get().preset
        HalynRenderer.PRESETS.forEachIndexed { index, name ->
            val active = index == selected
            val item = label(name, 13f, if (active) page else white, if (active) Typeface.BOLD else Typeface.NORMAL).apply {
                setPadding(dp(16), dp(12), dp(16), dp(12))
                gravity = Gravity.CENTER
                background = rounded(if (active) accent else surface, 18)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.setPreset(index)
                    paintPresets()
                    preview.invalidate()
                }
            }
            presetItems.addView(item, LinearLayout.LayoutParams(-2, dp(44)).apply {
                rightMargin = dp(8)
            })
        }
    }

    private fun paintPalettes() {
        paletteItems.removeAllViews()
        HalynRenderer.PALETTE_COLORS.forEachIndexed { index, color ->
            val circle = View(this).apply {
                val selected = prefs.get().palette == index
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(if (selected) 3 else 1), if (selected) white else surface)
                }
                contentDescription = "Palette: ${HalynRenderer.PALETTES[index]}"
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.setPalette(index)
                    paintPalettes()
                    preview.invalidate()
                }
            }
            paletteItems.addView(circle, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                rightMargin = dp(14)
            })
        }
    }

    private fun paintFps() {
        fpsItems.removeAllViews()
        val current = prefs.get().fps
        listOf(30, 45, 60).forEach { fps ->
            val selected = fps == current
            val item = label("${fps} FPS", 13f, if (selected) page else muted, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                background = rounded(if (selected) accent else surface, 16)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.setFps(fps)
                    paintFps()
                }
            }
            fpsItems.addView(item, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                rightMargin = dp(8)
            })
        }
    }

    private fun addSlider(
        root: LinearLayout,
        title: String,
        initial: Int,
        fromProgress: (Int) -> Float,
        onValue: (Float) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(label(title, 14f, white, Typeface.BOLD), LinearLayout.LayoutParams(0, -2, 1f))
        val value = label(String.format(java.util.Locale.US, "%.2f×", fromProgress(initial)), 12f, muted)
        row.addView(value)
        root.addView(row)
        val seek = SeekBar(this).apply {
            max = 100
            progress = initial.coerceIn(0, 100)
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(67, 67, 83))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val amount = fromProgress(progress)
                    value.text = String.format(java.util.Locale.US, "%.2f×", amount)
                    onValue(amount)
                    preview.invalidate()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        root.addView(seek, LinearLayout.LayoutParams(-1, dp(48)))
    }

    private fun addToggle(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        enabled: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(68)
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label(title, 14f, white, Typeface.BOLD))
        titles.addView(label(subtitle, 12f, muted).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = Switch(this).apply {
            isChecked = enabled
            contentDescription = title
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        row.addView(toggle)
        parent.addView(row)
    }

    private fun sectionLabel(title: String): TextView = label(title, 14f, white, Typeface.BOLD).apply {
        setPadding(0, dp(12), 0, dp(10))
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun label(value: String, size: Float, color: Int, weight: Int = Typeface.NORMAL): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.create("sans-serif", weight)
            includeFontPadding = true
        }

    private fun dp(number: Int): Int = (number * resources.displayMetrics.density).roundToInt()

    private inner class Preview : View(this@MainActivity) {
        private val renderer = HalynRenderer()
        private val startedAt = SystemClock.uptimeMillis()
        private var touchX = .5f
        private var touchY = .5f
        var running = false
            set(value) {
                field = value
                if (value) postInvalidateOnAnimation()
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val state = prefs.get()
            renderer.draw(
                canvas, state,
                (SystemClock.uptimeMillis() - startedAt) / 1000f,
                touchX, touchY
            )
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
