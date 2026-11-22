package com.oddf0x.halynmotion

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/** In-app drawing surface: record, assign, test, save and delete custom gestures. */
class FluidGestureStudio(
    private val activity: Activity,
    private val gestures: FluidGestures,
    private val previewEffect: (Int, Float, Float) -> Unit
) {
    private val bg = Color.rgb(19, 20, 23)
    private val panel = Color.rgb(39, 41, 45)
    private val ink = Color.rgb(237, 237, 239)
    private val muted = Color.rgb(159, 161, 168)
    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).roundToInt()
    private fun rounded(c: Int, radius: Int) = GradientDrawable().apply {
        setColor(c)
        cornerRadius = dp(radius).toFloat()
    }
    private fun label(value: String, size: Float, color: Int = ink) =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(0, dp(5), 0, dp(5))
        }
    private fun button(value: String, run: () -> Unit) =
        Button(activity).apply {
            text = value
            isAllCaps = false
            textSize = 13f
            setTextColor(ink)
            background = rounded(panel, 12)
            minHeight = dp(48)
            setOnClickListener { run() }
        }

    fun show() {
        val dialog = Dialog(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(bg, 24)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        content.addView(label("GESTURE STUDIO", 11f, muted))
        content.addView(label("Draw a motion. Choose its effect.", 20f))
        content.addView(label(
            "Draw one finger path in the pad. Save it and repeat the same gesture " +
                "on your fluid wallpaper.", 12f, muted))

        val pad = GesturePad(activity)
        content.addView(pad, LinearLayout.LayoutParams(-1, dp(236)).apply { topMargin = dp(11) })
        val status = label("Draw a smooth continuous stroke.", 12f, muted)
        content.addView(status)
        pad.onComplete = { count ->
            status.text = if (count >= 6) "Captured " + count + " points. Choose an effect."
                else "Stroke too short. Draw a longer one."
        }

        val name = EditText(activity).apply {
            setSingleLine(true)
            setText("Gesture " + (gestures.load().size + 1))
            setTextColor(ink)
            setHintTextColor(muted)
            textSize = 14f
            hint = "Gesture name"
            contentDescription = "Name for recorded gesture"
            background = rounded(panel, 12)
            filters = arrayOf(InputFilter.LengthFilter(30))
            setPadding(dp(14), 0, dp(14), 0)
        }
        content.addView(name, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })

        var action = 0
        val choose = button("Effect: " + FluidGestures.ACTIONS[action]) {}
        choose.setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle("Effect when gesture matches")
                .setSingleChoiceItems(FluidGestures.ACTIONS.toTypedArray(), action) { alert, index ->
                    action = index
                    choose.text = "Effect: " + FluidGestures.ACTIONS[index]
                    alert.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        content.addView(choose, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })

        content.addView(label("SAVED GESTURES", 11f, muted).apply {
            setPadding(0, dp(20), 0, dp(7))
        })
        val saved = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        var refreshRows: () -> Unit = {}
        refreshRows = {
            saved.removeAllViews()
            val all = gestures.load()
            if (all.isEmpty()) saved.addView(label("None saved yet", 12f, muted))
            for (item in all) {
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(label(item.name + " · " + FluidGestures.ACTIONS[item.action], 12f),
                    LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(button("Delete") {
                    AlertDialog.Builder(activity)
                        .setTitle("Delete " + item.name + "?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            gestures.delete(item.name)
                            refreshRows()
                        }.show()
                }, LinearLayout.LayoutParams(dp(88), dp(46)))
                saved.addView(row)
            }
        }
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("Try effect") {
            val matched = gestures.match(pad.points())
            previewEffect(matched?.action ?: action, .5f, .5f)
            Toast.makeText(activity,
                if (matched != null) "Matched " + matched.name else "Preview: " +
                    FluidGestures.ACTIONS[action], Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, dp(49), 1f))
        row.addView(button("Save gesture") {
            if (gestures.save(name.text.toString(), action, pad.points())) {
                status.text = "Saved. Repeat this path to trigger " + FluidGestures.ACTIONS[action] + "."
                pad.clear()
                refreshRows()
            } else {
                status.text = "Draw a longer gesture and give it a name."
            }
        }, LinearLayout.LayoutParams(0, dp(49), 1f).apply { marginStart = dp(8) })
        content.addView(row, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(9) })
        content.addView(saved)
        refreshRows()

        content.addView(label(
            "One UI may intercept launcher swipes, so recognition on the home " +
                "screen depends on which touch events the launcher forwards. " +
                "The in-app drawing pad captures the full gesture.", 11f, muted
        ).apply { setPadding(0, dp(12), 0, dp(6)) })
        content.addView(button("Close") { dialog.dismiss() },
            LinearLayout.LayoutParams(-1, dp(48)))

        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        dialog.setContentView(scroll)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(260)),
            ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private class GesturePad(activity: Activity) : View(activity) {
        private val stroke = FluidStroke()
        private val line = Path()
        private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(49, 51, 56)
            strokeWidth = 1f
        }
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(224, 225, 229)
            strokeWidth = 5f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        var onComplete: (Int) -> Unit = {}
        init {
            background = GradientDrawable().apply {
                setColor(Color.rgb(28, 30, 34))
                cornerRadius = 18f * resources.displayMetrics.density
            }
            contentDescription = "Draw a single-finger custom motion gesture"
            isClickable = true
            isFocusable = true
        }
        fun points(): List<PointF> = stroke.points()
        fun clear() { stroke.clear(); invalidate() }

        override fun onDraw(canvas: Canvas) {
            canvas.drawLine(width * .5f, 0f, width * .5f, height.toFloat(), guide)
            canvas.drawLine(0f, height * .5f, width.toFloat(), height * .5f, guide)
            val points = stroke.points()
            line.reset()
            points.forEachIndexed { i, p ->
                val x = p.x * width
                val y = p.y * height
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            canvas.drawPath(line, ink)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.pointerCount != 1) {
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) clear()
                return true
            }
            if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                for (i in 0 until event.historySize) {
                    stroke.handle(MotionEvent.ACTION_MOVE,
                        (event.getHistoricalX(i) / width.coerceAtLeast(1)).coerceIn(0f, 1f),
                        (event.getHistoricalY(i) / height.coerceAtLeast(1)).coerceIn(0f, 1f))
                }
            }
            val complete = stroke.handle(event.actionMasked,
                (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
                (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f))
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                onComplete(if (complete) stroke.points().size else 0)
                performClick()
            }
            invalidate()
            return true
        }
        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
