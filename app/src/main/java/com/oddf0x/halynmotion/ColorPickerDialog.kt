package com.oddf0x.halynmotion

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Minimal HSV colour editor with real saturation/value coordinates, full hue
 * spectrum, typed #RRGGBB support, accessible arrow-key adjustments and a
 * cancellable Apply step. It uses standard Android Views; no permission,
 * network, dependency, or bitmap screenshot is involved.
 */
class ColorPickerDialog(
    private val activity: Activity,
    initialColor: Int,
    private val onApply: (Int) -> Unit
) {
    private val dialog = Dialog(activity)
    private val hsv = FloatArray(3)
    private var selectedColor = initialColor or -0x1000000
    private val sheet = Color.rgb(24, 25, 29)
    private val white = Color.rgb(247, 247, 250)
    private val secondary = Color.rgb(158, 158, 168)

    private lateinit var spectrum: SaturationValueView
    private lateinit var hue: HueStrip
    private lateinit var swatch: View
    private lateinit var colorText: TextView
    private lateinit var hexField: EditText

    init {
        Color.colorToHSV(selectedColor, hsv)
    }

    fun show() {
        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = background(sheet, 30)
            setPadding(dp(22), dp(24), dp(22), dp(22))
        }
        scroll.addView(column)

        val eyebrow = text("COLOUR STUDIO", 11f, secondary, Typeface.BOLD).apply {
            letterSpacing = .16f
        }
        column.addView(eyebrow)
        column.addView(text("Your exact shade.", 26f, white, Typeface.BOLD).apply {
            setPadding(0, dp(7), 0, 0)
        })
        column.addView(text("Move the point to fine-tune your colour.", 13f, secondary).apply {
            setPadding(0, dp(5), 0, dp(19))
        })

        spectrum = SaturationValueView(activity, hsv) { saturation, value ->
            hsv[1] = saturation
            hsv[2] = value
            updateFromHsv()
        }
        spectrum.contentDescription = "Saturation and brightness picker. Swipe to choose, or use arrow keys."
        spectrum.isFocusable = true
        column.addView(spectrum, LinearLayout.LayoutParams(-1, dp(232)))

        column.addView(text("HUE", 11f, secondary, Typeface.BOLD).apply {
            letterSpacing = .12f
            setPadding(0, dp(20), 0, dp(11))
        })
        hue = HueStrip(activity, hsv) { hueValue ->
            hsv[0] = hueValue
            updateFromHsv()
            spectrum.invalidate()
        }
        hue.contentDescription = "Hue slider. Swipe horizontally or use arrow keys."
        hue.isFocusable = true
        column.addView(hue, LinearLayout.LayoutParams(-1, dp(38)))

        val hexLabel = text("HEX VALUE", 11f, secondary, Typeface.BOLD).apply {
            letterSpacing = .12f
            setPadding(0, dp(22), 0, dp(10))
        }
        column.addView(hexLabel)

        val hexRow = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = background(Color.rgb(37, 39, 45), 17)
        }
        swatch = View(activity).apply {
            background = background(selectedColor, 10)
            contentDescription = "Selected colour preview"
        }
        hexRow.addView(swatch, LinearLayout.LayoutParams(dp(40), dp(40)))

        hexField = EditText(activity).apply {
            setSingleLine(true)
            setText(hex(selectedColor))
            setTextSize(16f)
            setTextColor(white)
            selectAllOnFocus = true
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = null
            setPadding(dp(15), 0, 0, 0)
            filters = arrayOf(InputFilter.LengthFilter(7))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            imeOptions = EditorInfo.IME_ACTION_DONE
            contentDescription = "HEX colour, six digits with leading hash"
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    updateFromHex()
                    val input = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    input.hideSoftInputFromWindow(windowToken, 0)
                    clearFocus()
                    true
                } else {
                    false
                }
            }
        }
        hexRow.addView(hexField, LinearLayout.LayoutParams(0, dp(44), 1f))
        colorText = text("RGB", 11f, secondary, Typeface.NORMAL)
        hexRow.addView(colorText)
        column.addView(hexRow)
        updateReadout()

        val actions = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(23), 0, 0)
        }
        val cancel = action("Cancel", Color.rgb(45, 46, 52), white) { dialog.dismiss() }
        val apply = action("Use colour", white, Color.rgb(12, 13, 16)) {
            val typed = parseHex(hexField.text.toString())
            if (typed == null) {
                hexField.error = "Use #RRGGBB, for example #B6A4FF"
                hexField.requestFocus()
                return@action
            }
            onApply(typed)
            dialog.dismiss()
        }
        actions.addView(cancel, LinearLayout.LayoutParams(0, dp(51), 1f).apply {
            marginEnd = dp(9)
        })
        actions.addView(apply, LinearLayout.LayoutParams(0, dp(51), 1.32f))
        column.addView(actions)

        dialog.setContentView(scroll)
        dialog.setCancelable(true)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.68f)
        }
        dialog.show()
        val available = activity.resources.displayMetrics.widthPixels
        dialog.window?.setLayout((available - dp(28)).coerceAtLeast(dp(260)), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun updateFromHsv() {
        selectedColor = Color.HSVToColor(hsv)
        updateReadout()
        spectrum.invalidate()
        hue.invalidate()
        // A drag always becomes the current colour, even if the HEX field was
        // previously focused. Otherwise Apply could restore a stale typed value.
        hexField.setText(hex(selectedColor))
    }

    private fun updateFromHex() {
        val parsed = parseHex(hexField.text.toString())
        if (parsed == null) {
            hexField.error = "Enter a six-digit HEX colour"
            return
        }
        selectedColor = parsed
        Color.colorToHSV(selectedColor, hsv)
        updateReadout()
        spectrum.invalidate()
        hue.invalidate()
        hexField.setText(hex(selectedColor))
    }

    private fun updateReadout() {
        if (::swatch.isInitialized) swatch.background = background(selectedColor, 10)
        if (::colorText.isInitialized) colorText.text =
            "${Color.red(selectedColor)}, ${Color.green(selectedColor)}, ${Color.blue(selectedColor)}"
    }

    private fun action(title: String, fill: Int, ink: Int, onClick: () -> Unit): TextView =
        text(title, 14f, ink, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = background(fill, 16)
            isFocusable = true
            isClickable = true
            minHeight = dp(48)
            setOnClickListener { onClick() }
        }

    private fun text(value: String, size: Float, color: Int, weight: Int = Typeface.NORMAL): TextView =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.create("sans-serif", weight)
            includeFontPadding = true
        }

    private fun background(color: Int, radius: Int) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(color)
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()

    private fun hex(color: Int): String =
        String.format(Locale.US, "#%06X", color and 0x00FFFFFF)

    private fun parseHex(value: String): Int? {
        val trimmed = value.trim().removePrefix("#")
        if (!trimmed.matches(Regex("[a-fA-F0-9]{6}"))) return null
        return Color.rgb(
            trimmed.substring(0, 2).toInt(16),
            trimmed.substring(2, 4).toInt(16),
            trimmed.substring(4, 6).toInt(16)
        )
    }

    private class SaturationValueView(
        context: Context,
        private val hsv: FloatArray,
        private val onChange: (Float, Float) -> Unit
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat().coerceAtLeast(1f)
            val h = height.toFloat().coerceAtLeast(1f)
            rect.set(0f, 0f, w, h)
            val mask = Path().apply {
                addRoundRect(rect, 18f * resources.displayMetrics.density, 18f * resources.displayMetrics.density, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(mask)
            paint.shader = null
            paint.color = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
            canvas.drawRect(rect, paint)
            paint.shader = LinearGradient(0f, 0f, w, 0f, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawRect(rect, paint)
            paint.shader = LinearGradient(0f, 0f, 0f, h, 0x00000000, Color.BLACK, Shader.TileMode.CLAMP)
            canvas.drawRect(rect, paint)
            paint.shader = null
            canvas.restore()

            val cx = hsv[1].coerceIn(0f, 1f) * w
            val cy = (1f - hsv[2].coerceIn(0f, 1f)) * h
            val d = resources.displayMetrics.density
            stroke.strokeWidth = 2.6f * d
            stroke.color = Color.argb(180, 0, 0, 0)
            canvas.drawCircle(cx, cy, 10.8f * d, stroke)
            stroke.strokeWidth = 2.5f * d
            stroke.color = Color.WHITE
            canvas.drawCircle(cx, cy, 8.5f * d, stroke)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val sat = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val value = (1f - event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    onChange(sat, value)
                    contentDescription = "Saturation ${(sat * 100).roundToInt()}%, brightness ${(value * 100).roundToInt()}%"
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return super.onTouchEvent(event)
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
            var sat = hsv[1]
            var value = hsv[2]
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> sat -= .025f
                KeyEvent.KEYCODE_DPAD_RIGHT -> sat += .025f
                KeyEvent.KEYCODE_DPAD_UP -> value += .025f
                KeyEvent.KEYCODE_DPAD_DOWN -> value -= .025f
                else -> return super.onKeyDown(keyCode, event)
            }
            onChange(sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    private class HueStrip(
        context: Context,
        private val hsv: FloatArray,
        private val onChange: (Float) -> Unit
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val rect = RectF()
        private val hues = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
            Color.BLUE, Color.MAGENTA, Color.RED
        )

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat().coerceAtLeast(1f)
            val h = height.toFloat().coerceAtLeast(1f)
            val d = resources.displayMetrics.density
            rect.set(0f, 7f * d, w, h - 7f * d)
            paint.shader = LinearGradient(0f, 0f, w, 0f, hues, null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, 10f * d, 10f * d, paint)
            paint.shader = null
            val x = hsv[0].coerceIn(0f, 359.99f) / 360f * w
            ring.strokeWidth = 2.4f * d
            ring.color = Color.argb(180, 0, 0, 0)
            canvas.drawCircle(x, h / 2f, 10.5f * d, ring)
            ring.color = Color.WHITE
            ring.strokeWidth = 2.6f * d
            canvas.drawCircle(x, h / 2f, 8f * d, ring)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val hue = (event.x / width.coerceAtLeast(1)).coerceIn(0f, .9999f) * 360f
                    onChange(hue)
                    contentDescription = "Hue ${hue.roundToInt()} degrees"
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return super.onTouchEvent(event)
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
            val offset = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -2f
                KeyEvent.KEYCODE_DPAD_RIGHT -> 2f
                else -> return super.onKeyDown(keyCode, event)
            }
            onChange((hsv[0] + offset + 360f) % 360f)
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
