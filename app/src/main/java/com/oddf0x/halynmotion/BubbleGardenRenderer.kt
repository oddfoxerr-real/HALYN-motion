package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Interactive, almost stationary soap-glass bubbles.
 *
 * Drag a bubble beyond half its radius to pop it. Its six droplets and outward
 * ripple dissipate, then the same bubble grows back at exactly its home anchor.
 * The small resting shimmer never changes the stored location; no sensors,
 * accessibility, screen recording or microphone permissions are needed.
 *
 * Each HALYN renderer owns one BubbleGardenRenderer, so the editor preview and
 * wallpaper engine have independent touches, animation and burst lifetimes.
 */
class BubbleGardenRenderer {
    companion object {
        const val SCENE_INDEX = 155
        const val SCENE_NAME = "Bubble Garden"
        const val DESCRIPTION =
            "Mostly stationary translucent bubbles. Gently drag one until its membrane " +
                "stretches and bursts into droplets, then watch it reform in place."
        private const val BURST_DURATION = 0.85f
        private const val REFORM_START = 2.35f
        private const val REFORM_DURATION = 0.95f
    }

    private data class Bubble(
        val homeX: Float,
        val homeY: Float,
        val size: Float,
        var burstAt: Long = -1L,
        var burstX: Float = 0f,
        var burstY: Float = 0f,
        var pressX: Float = 0f,
        var pressY: Float = 0f,
        var displacementX: Float = 0f,
        var displacementY: Float = 0f,
        var recoilX: Float = 0f,
        var recoilY: Float = 0f,
        var lastRelease: Long = -1L
    )

    // Hand-placed rather than randomised: each bubble reforms in the same spot.
    // Sizes are in units of min(viewportWidth, viewportHeight).
    private val bubbles = arrayOf(
        Bubble(.19f, .13f, .075f),
        Bubble(.71f, .16f, .105f),
        Bubble(.43f, .31f, .125f),
        Bubble(.83f, .36f, .072f),
        Bubble(.16f, .45f, .106f),
        Bubble(.65f, .50f, .147f),
        Bubble(.38f, .58f, .078f),
        Bubble(.82f, .71f, .111f),
        Bubble(.20f, .79f, .124f),
        Bubble(.55f, .85f, .094f),
        Bubble(.84f, .91f, .061f)
    )
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val backdrop = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private var backdropGradient: LinearGradient? = null
    private var membraneGradient: RadialGradient? = null
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var cachedColor = 0
    private var selected = -1
    private var viewportWidth = 1f
    private var viewportHeight = 1f

    fun onTouch(action: Int, x: Float, y: Float) {
        val nx = x.coerceIn(0f, 1f)
        val ny = y.coerceIn(0f, 1f)
        val now = SystemClock.uptimeMillis()
        val unit = min(viewportWidth, viewportHeight)
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                selected = -1
                var best = Float.POSITIVE_INFINITY
                for (i in bubbles.indices) {
                    val b = bubbles[i]
                    if (b.burstAt >= 0L && now - b.burstAt < 3_400L) continue
                    val d = hypot(
                        (nx - b.homeX) * viewportWidth,
                        (ny - b.homeY) * viewportHeight
                    )
                    if (d <= unit * b.size * 1.16f && d < best) {
                        best = d
                        selected = i
                    }
                }
                if (selected >= 0) {
                    val b = bubbles[selected]
                    b.pressX = nx
                    b.pressY = ny
                    b.displacementX = 0f
                    b.displacementY = 0f
                    b.recoilX = 0f
                    b.recoilY = 0f
                    b.lastRelease = -1L
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (selected < 0) return
                val b = bubbles[selected]
                val dx = (nx - b.pressX) * viewportWidth
                val dy = (ny - b.pressY) * viewportHeight
                val distance = hypot(dx, dy)
                val threshold = unit * b.size * .53f
                if (distance > threshold) {
                    b.burstAt = now
                    b.burstX = (b.homeX * viewportWidth + dx).coerceIn(0f, viewportWidth)
                    b.burstY = (b.homeY * viewportHeight + dy).coerceIn(0f, viewportHeight)
                    b.displacementX = 0f
                    b.displacementY = 0f
                    selected = -1
                } else {
                    b.displacementX = dx * .87f
                    b.displacementY = dy * .87f
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (selected >= 0) {
                    val b = bubbles[selected]
                    b.recoilX = b.displacementX
                    b.recoilY = b.displacementY
                    b.displacementX = 0f
                    b.displacementY = 0f
                    b.lastRelease = now
                }
                selected = -1
            }
        }
    }

    fun draw(canvas: Canvas, state: MotionState, elapsedSeconds: Float) {
        val w = canvas.width.coerceAtLeast(1).toFloat()
        val h = canvas.height.coerceAtLeast(1).toFloat()
        viewportWidth = w
        viewportHeight = h
        val m = min(w, h)
        val hue = if (state.palette == 5) state.customColor
            else HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0, 4)]
        val tint = if (state.fourColorsEnabled) state.colorOne else hue
        if (backdropGradient == null || cachedWidth != canvas.width ||
            cachedHeight != canvas.height || cachedColor != tint) {
            backdropGradient = LinearGradient(
                0f, 0f, w*.37f, h,
                intArrayOf(Color.rgb(2, 6, 13), mix(Color.rgb(6, 12, 23), tint, .11f),
                    Color.rgb(2, 5, 12)),
                floatArrayOf(0f, .62f, 1f),
                Shader.TileMode.CLAMP
            )
            membraneGradient = RadialGradient(
                -.32f, -.38f, 1.50f,
                intArrayOf(
                    Color.argb(76, 244, 250, 255),
                    withAlpha(tint, 47),
                    withAlpha(tint, 14),
                    Color.argb(2, 10, 21, 36)
                ),
                floatArrayOf(0f, .28f, .69f, 1f),
                Shader.TileMode.CLAMP
            )
            cachedWidth = canvas.width
            cachedHeight = canvas.height
            cachedColor = tint
        }
        backdrop.shader = backdropGradient
        canvas.drawRect(0f, 0f, w, h, backdrop)
        backdrop.shader = null

        val now = SystemClock.uptimeMillis()
        val seconds = elapsedSeconds * (.65f + state.speed * .32f)
        for (i in bubbles.indices) {
            val b = bubbles[i]
            val radius = m * b.size
            val colors = if (state.fourColorsEnabled) {
                intArrayOf(state.colorOne, state.colorTwo, state.colorThree, state.colorFour)
            } else {
                intArrayOf(tint, mix(tint, Color.WHITE, .33f),
                    mix(tint, Color.WHITE, .70f), mix(tint, Color.WHITE, .85f))
            }
            if (b.burstAt >= 0L) {
                val age = (now - b.burstAt) / 1000f
                if (age < BURST_DURATION) {
                    drawBurst(canvas, b, age, radius, colors, i)
                    continue
                }
                if (age < REFORM_START) continue
            }
            val rebuild = if (b.burstAt >= 0L) {
                ((now - b.burstAt) / 1000f - REFORM_START)
                    .div(REFORM_DURATION).coerceIn(0f, 1f)
            } else 1f
            val grow = rebuild * rebuild * (3f - 2f * rebuild)
            if (grow <= 0.001f) continue
            if (grow >= 1f) b.burstAt = -1L

            val recoilAge = if (b.lastRelease >= 0) (now - b.lastRelease) / 1000f
                else 99f
            val spring = if (recoilAge < 1.1f) exp(-4.1f * recoilAge) *
                cos(recoilAge * 13.4f) else 0f
            val dx = if (selected == i) b.displacementX else b.recoilX * spring
            val dy = if (selected == i) b.displacementY else b.recoilY * spring
            val x = w * b.homeX + dx
            val y = h * b.homeY + dy
            val shimmer = 1f + .012f * sin(seconds * (1.15f + i * .09f) + i*.82f)
            val stretch = if (selected == i) {
                (hypot(dx,dy) / (radius*2f)).coerceIn(0f, .28f)
            } else .015f * abs(spring)
            val rx = radius * grow * shimmer * (1f + stretch)
            val ry = radius * grow * (2f - shimmer) * (1f - stretch*.38f)
            drawBubble(canvas, x, y, rx, ry, colors, seconds, i)
        }
    }

    private fun drawBubble(
        canvas: Canvas, x: Float, y: Float, radiusX: Float, radiusY: Float,
        colors: IntArray, seconds: Float, index: Int
    ) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(radiusX, radiusY)
        bubblePaint.shader = membraneGradient
        bubblePaint.color = Color.WHITE
        bubblePaint.alpha = 255
        bubblePaint.style = Paint.Style.FILL
        canvas.drawCircle(0f, 0f, 1f, bubblePaint)
        bubblePaint.shader = null

        val hue = colors[index % colors.size]
        rimPaint.color = mix(hue, Color.WHITE, .55f)
        rimPaint.alpha = 159
        rimPaint.strokeWidth = .030f
        canvas.drawCircle(0f, 0f, .985f, rimPaint)

        // Two non-symmetric reflections; no four-spoke rotational motif.
        rimPaint.color = colors[(index+1)%colors.size]
        rimPaint.alpha = 225
        rimPaint.strokeWidth = .086f
        val shimmer = (seconds * (8f + .7f * index)).mod(360f)
        canvas.drawArc(-.88f,-.88f,.88f,.88f, 198f+shimmer*.09f, 73f,
            false, rimPaint)
        rimPaint.color = Color.WHITE
        rimPaint.alpha = 127
        rimPaint.strokeWidth = .026f
        canvas.drawArc(-.73f,-.73f,.73f,.73f, 305f-shimmer*.06f, 48f,
            false, rimPaint)

        bubblePaint.color = Color.argb(82, 255, 255, 255)
        canvas.drawOval(-.58f, -.76f, -.24f, -.61f, bubblePaint)
        bubblePaint.color = withAlpha(colors[(index+2)%colors.size], 47)
        canvas.drawOval(.28f,.47f,.70f,.64f,bubblePaint)
        canvas.restore()
    }

    private fun drawBurst(
        canvas: Canvas, bubble: Bubble, age: Float, radius: Float,
        colors: IntArray, index: Int
    ) {
        val fade = (1f - age/BURST_DURATION).coerceIn(0f, 1f)
        if (fade <= 0f) return
        rimPaint.shader = null
        rimPaint.color = mix(colors[index%colors.size], Color.WHITE, .55f)
        rimPaint.alpha = (fade*178f).toInt().coerceIn(0,255)
        rimPaint.strokeWidth = (radius*.045f).coerceAtLeast(1f)
        canvas.drawCircle(bubble.burstX, bubble.burstY,
            radius*(.40f + age*1.50f), rimPaint)
        bubblePaint.shader = null
        bubblePaint.style = Paint.Style.FILL
        for (particle in 0..6) {
            val angle = .43f + particle*2f*3.14159265f/7f + index*.37f
            val distance = radius*(.35f + age*(1.20f + particle*.12f))
            val x = bubble.burstX + cos(angle)*distance
            val y = bubble.burstY + sin(angle)*distance +
                radius*age*age*.37f
            val droplet = radius*(.085f + (particle%3)*.025f)*fade
            bubblePaint.color = withAlpha(
                colors[(particle+index)%colors.size], (fade*218).toInt()
            )
            canvas.drawCircle(x, y, droplet, bubblePaint)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0,255),
            Color.red(color), Color.green(color), Color.blue(color))

    private fun mix(a: Int, b: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f,1f)
        return Color.rgb(
            (Color.red(a)*(1f-t) + Color.red(b)*t).toInt().coerceIn(0,255),
            (Color.green(a)*(1f-t) + Color.green(b)*t).toInt().coerceIn(0,255),
            (Color.blue(a)*(1f-t) + Color.blue(b)*t).toInt().coerceIn(0,255)
        )
    }
}
