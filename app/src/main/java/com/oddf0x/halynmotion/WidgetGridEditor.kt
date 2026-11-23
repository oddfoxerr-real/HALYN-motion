package com.oddf0x.halynmotion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Touch overlay for wallpaper-drawn widgets.
 * - Tap to select, drag to move on a 5% grid.
 * - Bottom-right handle changes size; top-right handle rotates.
 * - Two fingers on the selected widget pinch + rotate simultaneously.
 * - Changes are previewed without re-writing preferences on each frame.
 */
class WidgetGridEditor(
    context: Context,
    private val onPreview: (List<WallpaperWidget>) -> Unit,
    private val onCommit: (List<WallpaperWidget>) -> Unit,
    private val onSelection: (String?) -> Unit
) : View(context) {
    private var elements: List<WallpaperWidget> = emptyList()
    private var selected: String? = null
    private var touchMode = 0
    private var pointerStartX = 0f
    private var pointerStartY = 0f
    private var gestureStart: WallpaperWidget? = null
    private var beginDistance = 1f
    private var beginAngle = 0f
    private var moved = false
    var snapEnabled: Boolean = true
        set(value) { field = value; invalidate() }

    private val lines = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(66, 255, 255, 255)
        strokeWidth = 1f * resources.displayMetrics.density
    }
    private val selection = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textSize = 12f * resources.displayMetrics.scaledDensity
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Widget grid editor. Drag to move, use the lower-right handle for size " +
            "and upper-right handle for rotation. Two-finger pinch also works."
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun replace(items: List<WallpaperWidget>, selectedId: String? = selected) {
        elements = items.map { it.safe() }
        selected = selectedId?.takeIf { id -> elements.any { it.id == id } }
            ?: elements.firstOrNull()?.id
        invalidate()
    }
    fun selectedId(): String? = selected
    fun items(): List<WallpaperWidget> = elements

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        for (i in 1..19) {
            val x = w * i / 20f
            val y = h * i / 20f
            canvas.drawLine(x, 0f, x, h, lines)
            canvas.drawLine(0f, y, w, y, lines)
        }
        elements.forEach { item ->
            val box = bounds(item)
            val chosen = item.id == selected
            selection.alpha = if (chosen) 245 else 95
            canvas.drawRoundRect(box, dp(8f), dp(8f), selection)
            if (chosen) {
                canvas.drawCircle(box.right, box.bottom, dp(9f), accent)
                canvas.drawCircle(box.right, box.top, dp(9f), accent)
                canvas.drawLine(box.centerX() - dp(11f), box.centerY(),
                    box.centerX() + dp(11f), box.centerY(), selection)
                canvas.drawLine(box.centerX(), box.centerY() - dp(11f),
                    box.centerX(), box.centerY() + dp(11f), selection)
                canvas.drawText(
                    WallpaperWidget.LABELS[item.kind] ?: item.kind,
                    box.left, (box.top - dp(8f)).coerceAtLeast(dp(18f)), text
                )
                text.color = Color.rgb(20, 20, 24)
                canvas.drawText("+", box.right-dp(4f), box.bottom+dp(5f), text)
                text.color = Color.WHITE
                canvas.drawText("⟳", box.right-dp(7f), box.top+dp(5f), text)
            }
        }
    }

    private fun bounds(item: WallpaperWidget): RectF {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val x = item.x * w
        val y = item.y * h
        val wr = when (item.kind) {
            "clock" -> .68f
            "date" -> .60f
            "weekday" -> .63f
            "battery" -> .40f
            "divider" -> .54f
            else -> .56f
        }
        val hr = when (item.kind) {
            "clock" -> .20f
            "battery" -> .085f
            "divider" -> .055f
            else -> .085f
        }
        val halfW = w * wr * item.scale * .5f
        val halfH = h * hr * item.scale * .5f
        return RectF(x-halfW, y-halfH, x+halfW, y+halfH)
    }

    private fun selectedWidget(): WallpaperWidget? =
        elements.firstOrNull { it.id == selected }

    private fun update(next: WallpaperWidget) {
        elements = elements.map { if (it.id == next.id) next.safe() else it }
        invalidate()
        onPreview(elements)
    }

    private fun pointerAngle(e: MotionEvent): Float =
        Math.toDegrees(
            atan2((e.getY(1)-e.getY(0)).toDouble(),
                (e.getX(1)-e.getX(0)).toDouble())
        ).toFloat()

    private fun pointerDistance(e: MotionEvent): Float =
        hypot(e.getX(1)-e.getX(0), e.getY(1)-e.getY(0)).coerceAtLeast(1f)

    private fun snapped(value: Float, step: Float): Float =
        if (snapEnabled) (value / step).roundToInt() * step else value

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val item = elements.asReversed().firstOrNull {
                    val area = bounds(it)
                    area.inset(-dp(18f), -dp(18f))
                    area.contains(e.x, e.y)
                }
                if (item == null) {
                    selected = null
                    onSelection(null)
                    invalidate()
                    return true
                }
                selected = item.id
                onSelection(selected)
                val box = bounds(item)
                touchMode = when {
                    hypot(e.x-box.right, e.y-box.bottom) < dp(32f) -> 2 // size
                    hypot(e.x-box.right, e.y-box.top) < dp(32f) -> 3 // rotate
                    else -> 1 // drag
                }
                pointerStartX = e.x
                pointerStartY = e.y
                gestureStart = item
                moved = false
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.pointerCount >= 2 && selectedWidget() != null) {
                    gestureStart = selectedWidget()
                    touchMode = 4
                    beginDistance = pointerDistance(e)
                    beginAngle = pointerAngle(e)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val base = gestureStart ?: return true
                val cx = base.x * width
                val cy = base.y * height
                val updated = when {
                    touchMode == 4 && e.pointerCount >= 2 -> {
                        base.copy(
                            scale = snapped(
                                (base.scale * pointerDistance(e) / beginDistance)
                                    .coerceIn(.35f, 2.5f), .05f
                            ),
                            rotation = snapped(
                                (base.rotation + pointerAngle(e)-beginAngle)
                                    .coerceIn(-180f, 180f), 5f
                            )
                        )
                    }
                    touchMode == 2 -> {
                        val before = hypot(pointerStartX-cx, pointerStartY-cy)
                            .coerceAtLeast(dp(20f))
                        val after = hypot(e.x-cx, e.y-cy)
                        base.copy(scale = snapped(
                            (base.scale * after / before).coerceIn(.35f, 2.5f), .05f
                        ))
                    }
                    touchMode == 3 -> {
                        val before = atan2(pointerStartY-cy, pointerStartX-cx)
                        val after = atan2(e.y-cy, e.x-cx)
                        val angle = Math.toDegrees((after-before).toDouble()).toFloat()
                        base.copy(rotation = snapped(
                            (base.rotation + angle).coerceIn(-180f, 180f), 5f
                        ))
                    }
                    else -> base.copy(
                        x = snapped(
                            (base.x + (e.x - pointerStartX) / width.coerceAtLeast(1))
                                .coerceIn(.04f, .96f), .05f
                        ),
                        y = snapped(
                            (base.y + (e.y - pointerStartY) / height.coerceAtLeast(1))
                                .coerceIn(.05f, .95f), .05f
                        )
                    )
                }
                moved = true
                update(updated)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Complete a pinch before the single remaining pointer moves.
                if (touchMode == 4) touchMode = 0
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (moved) onCommit(elements)
                else performClick()
                touchMode = 0
                gestureStart = null
                moved = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (moved) onCommit(elements)
                touchMode = 0
                gestureStart = null
                moved = false
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
