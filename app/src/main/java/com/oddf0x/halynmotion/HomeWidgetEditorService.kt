package com.oddf0x.halynmotion

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Short-lived, user-invoked editor drawn directly above the real launcher.
 *
 * This is deliberately a separate service from HALYN's old clock mover:
 * it can be closed without affecting the launcher, the wallpaper process,
 * system widgets, or the legacy overlay's operation. Every element being
 * edited is an independent HALYN wallpaper item, not a foreign AppWidget.
 *
 * A tiny debounced preference write makes the REAL wallpaper move underneath
 * the transparent grid during gestures. A persistent foreground notification
 * always offers an escape hatch when system gesture navigation is unavailable.
 */
class HomeWidgetEditorService : Service() {
    private val preferences by lazy { MotionPreferences(this) }
    private val widgets by lazy { WallpaperWidgetStore(this) }
    private val windowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    private val handler = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var grid: WidgetGridEditor? = null
    private var inspector: LinearLayout? = null
    private var title: TextView? = null
    private var snapButton: TextView? = null
    private var selectedId: String? = null
    private var pending: List<WallpaperWidget>? = null
    private var lastSavedAt = 0L
    private var inputDialog: AlertDialog? = null

    private val flushPending = Runnable {
        val list = pending ?: return@Runnable
        pending = null
        widgets.save(list)
        lastSavedAt = android.os.SystemClock.uptimeMillis()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (root != null) return START_NOT_STICKY

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Home screen widget editor", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Visible only while editing HALYN wallpaper widgets on the home screen."
        })
        val exitIntent = Intent(this, HomeWidgetEditorService::class.java)
            .setAction(ACTION_EXIT)
        val exit = PendingIntent.getService(this, 1, exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("HALYN · Editing home-screen widgets")
            .setContentText("Move, resize, rotate or add widgets. Done closes the editor.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "Exit editor", exit
            ).build())
            .build()
        try {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } catch (_: RuntimeException) {
            stopSelf()
            return START_NOT_STICKY
        }

        val editor = makeEditor()
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
            title = "HALYN widget editor"
        }
        try {
            windowManager.addView(editor, params)
            root = editor
        } catch (_: RuntimeException) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun makeEditor(): FrameLayout {
        // First activation migrates the existing clock. Further sessions load
        // the layout without resetting its position or its user-selected colour.
        val items = widgets.activate(preferences.get())
        selectedId = items.firstOrNull()?.id

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "HALYN home-screen widget editor"
        }
        grid = WidgetGridEditor(
            this,
            onPreview = { draft -> schedulePreview(draft) },
            onCommit = { result ->
                pending = result
                commit()
            },
            onSelection = { id ->
                selectedId = id
                rebuildInspector()
            }
        ).also { editor ->
            container.addView(
                editor, FrameLayout.LayoutParams(-1, -1)
            )
            editor.replace(items, selectedId)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(7), dp(9), dp(7))
            background = panelBackground(0xE70F1118.toInt(), 18)
            elevation = dp(8).toFloat()
        }
        title = text("HALYN  ·  Home edit", 14, Color.WHITE, true)
        header.addView(title, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
        snapButton = button("Grid 5%") {
            grid?.snapEnabled = !(grid?.snapEnabled ?: true)
            snapButton?.text = if (grid?.snapEnabled == true) "Grid 5%" else "Free"
        }
        header.addView(snapButton, LinearLayout.LayoutParams(-2, dp(44)).apply {
            marginEnd = dp(7)
        })
        header.addView(button("Done  ✓") {
            commit()
            stopSelf()
        }, LinearLayout.LayoutParams(-2, dp(44)))
        container.addView(header, FrameLayout.LayoutParams(-1, dp(61),
            Gravity.TOP).apply {
            setMargins(dp(10), dp(55), dp(10), 0)
        })

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(12))
            background = panelBackground(0xEB101219.toInt(), 20)
            elevation = dp(10).toFloat()
        }
        footer.addView(text("ADD TO WALLPAPER", 10, 0xFFB4B8C5.toInt(), true)
            .apply { letterSpacing = .12f })

        val addRail = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        WallpaperWidget.KINDS.forEach { kind ->
            addRail.addView(button("+ " + (WallpaperWidget.LABELS[kind] ?: kind)) {
                add(kind)
            }, LinearLayout.LayoutParams(-2, dp(44)).apply {
                marginEnd = dp(7)
            })
        }
        footer.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(addRail)
        }, LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(9)
        })
        inspector = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        footer.addView(inspector, LinearLayout.LayoutParams(-1, -2))
        footer.addView(
            text("Drag to move  ·  Corners resize/rotate  ·  Pinch with two fingers",
                11, 0xFFB4B8C5.toInt()).apply {
                setPadding(dp(3), dp(8), dp(3), 0)
            }
        )
        container.addView(footer, FrameLayout.LayoutParams(-1, -2,
            Gravity.BOTTOM).apply {
            setMargins(dp(10), 0, dp(10), dp(28))
        })
        rebuildInspector()
        return container
    }

    private fun schedulePreview(items: List<WallpaperWidget>) {
        pending = items
        val elapsed = android.os.SystemClock.uptimeMillis() - lastSavedAt
        if (elapsed > 90L) {
            handler.removeCallbacks(flushPending)
            flushPending.run()
        } else {
            handler.removeCallbacks(flushPending)
            handler.postDelayed(flushPending, 95L)
        }
    }

    private fun commit() {
        handler.removeCallbacks(flushPending)
        flushPending.run()
    }

    private fun add(kind: String) {
        commit()
        val created = widgets.add(kind)
        if (created == null) {
            Toast.makeText(this, "Up to 12 wallpaper elements", Toast.LENGTH_SHORT).show()
            return
        }
        selectedId = created.id
        grid?.replace(widgets.load(), selectedId)
        rebuildInspector()
    }

    private fun updateSelected(update: (WallpaperWidget) -> WallpaperWidget) {
        val id = selectedId ?: return
        val updated = (pending ?: widgets.load()).map { widget ->
            if (widget.id == id) update(widget).safe() else widget
        }
        pending = updated
        grid?.replace(updated, id)
        schedulePreview(updated)
    }

    private fun selected(): WallpaperWidget? {
        val id = selectedId ?: return null
        return (pending ?: widgets.load()).firstOrNull { it.id == id }
    }

    private fun rebuildInspector() {
        val column = inspector ?: return
        column.removeAllViews()
        val current = selected()
        if (current == null) {
            column.addView(text("Tap an element on the wallpaper to edit it",
                12, Color.WHITE).apply { setPadding(0, dp(13), 0, dp(3)) })
            return
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(text(
            (WallpaperWidget.LABELS[current.kind] ?: current.kind) + "  ·  Selected",
            14, Color.WHITE, true
        ), LinearLayout.LayoutParams(0, dp(42), 1f).apply {
            gravity = Gravity.CENTER_VERTICAL
        })
        if (current.kind == "text") {
            heading.addView(button("Text") { editText(current) },
                LinearLayout.LayoutParams(-2, dp(40)).apply { marginEnd = dp(7) })
        }
        heading.addView(button("Remove") {
            commit()
            widgets.delete(current.id)
            val remainder = widgets.load()
            selectedId = remainder.firstOrNull()?.id
            grid?.replace(remainder, selectedId)
            rebuildInspector()
        }, LinearLayout.LayoutParams(-2, dp(40)))
        column.addView(heading)

        val sliders = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        fun control(
            name: String, min: Float, max: Float, original: Float,
            format: (Float) -> String,
            write: (WallpaperWidget, Float) -> WallpaperWidget
        ) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(text(name, 12, Color.WHITE),
                LinearLayout.LayoutParams(0, -2, 1f))
            val readout = text(format(original), 12, 0xFFB4B8C5.toInt())
            row.addView(readout)
            sliders.addView(row)
            val seek = SeekBar(this).apply {
                this.max = 100
                progress = ((original - min) / (max - min) * 100f)
                    .roundToInt().coerceIn(0, 100)
                contentDescription = name + " for " + current.kind
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                    override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val result = min + (max - min) * value / 100f
                        readout.text = format(result)
                        updateSelected { write(it, result) }
                    }
                    override fun onStopTrackingTouch(bar: SeekBar?) = commit()
                })
            }
            sliders.addView(seek, LinearLayout.LayoutParams(-1, dp(36)))
        }
        control("Size", .35f, 2.5f, current.scale,
            { String.format(Locale.US, "%d%%", (it * 100f).roundToInt()) },
            { widget, value -> widget.copy(scale = value) })
        control("Rotation", -180f, 180f, current.rotation,
            { String.format(Locale.US, "%d°", it.roundToInt()) },
            { widget, value -> widget.copy(rotation = value) })
        column.addView(sliders)

        val palettes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val shades = listOf(
            Color.WHITE, 0xFFB2B8C1.toInt(), 0xFFF1BB79.toInt(),
            0xFFE77E89.toInt(), 0xFF87BCFA.toInt(), 0xFFAD99EB.toInt()
        )
        shades.forEach { shade ->
            val active = shade == current.color
            val disc = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(shade)
                    setStroke(dp(if (active) 3 else 1),
                        if (active) Color.WHITE else 0xFF43464F.toInt())
                }
                contentDescription = "Use colour " + hex(shade)
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    updateSelected { it.copy(color = shade) }
                    commit()
                    rebuildInspector()
                }
            }
            palettes.addView(disc, LinearLayout.LayoutParams(dp(33), dp(33)).apply {
                marginEnd = dp(9)
            })
        }
        palettes.addView(button("Hex") { customColour(current) },
            LinearLayout.LayoutParams(-2, dp(38)))
        column.addView(palettes, LinearLayout.LayoutParams(-1, dp(42)))
    }

    private fun customColour(item: WallpaperWidget) {
        showInputDialog(
            title = "Widget colour", initial = hex(item.color),
            hint = "#RRGGBB", max = 7,
            inputType = InputType.TYPE_CLASS_TEXT
        ) { entered ->
            val normalized = entered.trim().removePrefix("#")
            val valid = normalized.matches(Regex("[0-9a-fA-F]{6}"))
            if (!valid) {
                Toast.makeText(this, "Enter a hex colour such as #E8E8E8",
                    Toast.LENGTH_LONG).show()
            } else {
                val color = Color.parseColor("#" + normalized)
                updateSelected { it.copy(color = color) }
                commit()
                rebuildInspector()
            }
        }
    }

    private fun editText(item: WallpaperWidget) {
        showInputDialog(
            title = "Custom wallpaper text",
            initial = item.label,
            hint = "Your label",
            max = 48,
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        ) { next ->
            updateSelected { it.copy(label = next.take(48)) }
            commit()
        }
    }

    private fun showInputDialog(
        title: String, initial: String, hint: String,
        max: Int, inputType: Int, onApply: (String) -> Unit
    ) {
        if (!Settings.canDrawOverlays(this)) return
        val entry = EditText(this).apply {
            setText(initial)
            setSelection(text.length)
            this.hint = hint
            this.inputType = inputType
            filters = arrayOf(InputFilter.LengthFilter(max))
            setSingleLine(true)
        }
        val holder = FrameLayout(this).apply {
            setPadding(dp(19), 0, dp(19), 0)
            addView(entry, FrameLayout.LayoutParams(-1, dp(56)))
        }
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setView(holder)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
                onApply(entry.text.toString())
            }
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.setOnDismissListener { if (inputDialog === dialog) inputDialog = null }
        inputDialog?.dismiss()
        inputDialog = dialog
        try {
            dialog.show()
            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
        } catch (_: RuntimeException) {
            inputDialog = null
            Toast.makeText(this, "Open HALYN to edit detailed text",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun panelBackground(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), 0xFF41444D.toInt())
        }

    private fun button(label: String, action: () -> Unit): TextView =
        text(label, 12, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(13), 0, dp(13), 0)
            background = panelBackground(0xFF30333B.toInt(), 12)
            isFocusable = true
            isClickable = true
            contentDescription = label
            setOnClickListener { action() }
        }

    private fun text(label: String, size: Int, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = label
            setTextColor(color)
            textSize = size.toFloat()
            typeface = Typeface.create(
                "sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL
            )
            includeFontPadding = true
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun hex(color: Int) = String.format(Locale.US, "#%06X", color and 0xFFFFFF)

    override fun onDestroy() {
        handler.removeCallbacks(flushPending)
        flushPending.run()
        inputDialog?.dismiss()
        inputDialog = null
        root?.let {
            try {
                windowManager.removeViewImmediate(it)
            } catch (_: RuntimeException) {
                // Overlay permission may be revoked while editing.
            }
        }
        root = null
        grid = null
        inspector = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "halyn_home_widget_editor"
        private const val NOTIFICATION_ID = 6212
        const val ACTION_EXIT = "com.oddf0x.halynmotion.EXIT_HOME_WIDGET_EDITOR"
    }
}
