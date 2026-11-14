package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Eight original Material X-style minimalist live wallpapers.
 *
 * Unlike HALYN's AGSL luminous effects, these are matte, sculpted Canvas
 * compositions made of rounded paper layers, inset cutouts and flowing planes.
 * They share one low-cost renderer but have independently authored geometry
 * and movement rules. No model, bitmap or network download is required.
 */
class MaterialXRenderer {
    data class Scene(val name: String, val description: String)

    companion object {
        const val FIRST = 137
        const val COUNT = 8
        const val LAST = FIRST + COUNT - 1
        val SCENES = listOf(
            Scene("Paper Horizon", "Three overlapping paper horizons glide independently, with a floating sun."),
            Scene("Arc Stack", "A sequence of soft monolithic arches opens and closes as the device tilts."),
            Scene("Soft Fold", "Folded matte material sheets reveal narrow highlights as they move."),
            Scene("Pebble Lines", "Four flattened ceramic pebbles drift along staggered resting lines."),
            Scene("Ribbon Cutout", "A wide ribbon carves an S-shaped window through layered surfaces."),
            Scene("Mono Dunes", "Four hand-shaped sweeping dunes rise and fall at independent speeds."),
            Scene("Orbit Insets", "Rounded inset frames slide through one another with gentle parallax."),
            Scene("Ceramic Waves", "Smooth ceramic wave bowls shift apart to reveal a curved glowing seam.")
        )
        fun isMaterial(preset: Int): Boolean = preset in FIRST..LAST
        fun description(preset: Int): String? =
            if (isMaterial(preset)) SCENES[preset - FIRST].description else null
    }

    private val themes = arrayOf(
        intArrayOf(0xFF101820.toInt(), 0xFF354859.toInt(), 0xFF8CB0B1.toInt(), 0xFFD8CCB4.toInt()),
        intArrayOf(0xFF121821.toInt(), 0xFF516275.toInt(), 0xFF91B3B7.toInt(), 0xFFE1E0D7.toInt()),
        intArrayOf(0xFF10171C.toInt(), 0xFF455C60.toInt(), 0xFFB1C3BA.toInt(), 0xFFE9D5C0.toInt()),
        intArrayOf(0xFF18191B.toInt(), 0xFF555A5C.toInt(), 0xFFA6B6B6.toInt(), 0xFFE3DAC9.toInt()),
        intArrayOf(0xFF0F1920.toInt(), 0xFF476974.toInt(), 0xFF8CB1B7.toInt(), 0xFFE5D7C3.toInt()),
        intArrayOf(0xFF18181E.toInt(), 0xFF454D5A.toInt(), 0xFF8FA9B7.toInt(), 0xFFE4CFC2.toInt()),
        intArrayOf(0xFF171A23.toInt(), 0xFF536276.toInt(), 0xFF9FBCC0.toInt(), 0xFFE1D5CA.toInt()),
        intArrayOf(0xFF101A1D.toInt(), 0xFF3E6769.toInt(), 0xFF9DBCB4.toInt(), 0xFFEBDBBF.toInt())
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val path = Path()
    private var backgroundGradient: LinearGradient? = null
    private var backgroundKey = 0L

    private fun color(canvas: Canvas, chosen: Int, alpha: Int = 255) {
        paint.shader = null
        paint.color = chosen
        paint.alpha = alpha.coerceIn(0, 255)
        paint.style = Paint.Style.FILL
    }

    private fun round(canvas: Canvas, x: Float, y: Float, right: Float, bottom: Float,
                      radius: Float, shade: Int) {
        color(canvas, shade)
        canvas.drawRoundRect(x, y, right, bottom, radius, radius, paint)
    }

    private fun ellipse(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, shade: Int) {
        color(canvas, shade)
        canvas.drawOval(cx-rx, cy-ry, cx+rx, cy+ry, paint)
    }

    private fun paperWave(
        canvas: Canvas, w: Float, h: Float, top: Float, height: Float,
        leftBend: Float, rightBend: Float, shade: Int
    ) {
        path.reset()
        path.moveTo(-w*.08f, top)
        path.cubicTo(w*.24f, top-leftBend, w*.66f, top+rightBend, w*1.08f, top)
        path.lineTo(w*1.08f, top+height+h*.03f)
        path.lineTo(-w*.08f, top+height+h*.03f)
        path.close()
        color(canvas, shade)
        canvas.drawPath(path, paint)
    }

    private fun setBackdrop(canvas: Canvas, w: Float, h: Float, scene: Int, shades: IntArray) {
        // A 2-stop matte field rather than the neon optics used by other HALYN scenes.
        val key = shades.fold((scene+1).toLong()*31L+w.toLong()*37L+h.toLong()) {
            acc, value -> acc*31L + value
        }
        if (backgroundGradient == null || backgroundKey != key) {
            backgroundGradient = LinearGradient(
                0f, 0f, w*.80f, h,
                intArrayOf(shades[0], mixed(shades[0], shades[1], .24f)),
                null, Shader.TileMode.CLAMP
            )
            backgroundKey = key
        }
        paint.shader = backgroundGradient
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun mixed(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a)*(1f-f) + Color.red(b)*f).toInt().coerceIn(0,255),
            (Color.green(a)*(1f-f) + Color.green(b)*f).toInt().coerceIn(0,255),
            (Color.blue(a)*(1f-f) + Color.blue(b)*f).toInt().coerceIn(0,255)
        )
    }

    fun draw(canvas: Canvas, state: MotionState, seconds: Float, pose: ParallaxPose) {
        val scene = (state.preset - FIRST).coerceIn(0, COUNT-1)
        val w = canvas.width.coerceAtLeast(1).toFloat()
        val h = canvas.height.coerceAtLeast(1).toFloat()
        val m = min(w,h)
        val shades = if (state.fourColorsEnabled) QuadColorSystem.colors(state)
            else themes[scene].copyOf().apply {
                if (state.palette != 0) {
                    val tint = if (state.palette == 5) state.customColor else
                        HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0, 4)]
                    this[1] = mixed(this[1], tint, .31f)
                    this[2] = mixed(this[2], tint, .25f)
                    this[3] = mixed(this[3], tint, .18f)
                }
            }
        setBackdrop(canvas, w, h, scene, shades)
        val t = seconds*(.12f+state.speed*.11f)
        val gyroX = pose.x*w*.06f
        val gyroY = pose.y*h*.032f
        val motion = sin(t)
        when (scene) {
            0 -> {
                // Paper Horizon: layers move at different rates, not one global parallax.
                ellipse(canvas, w*.71f+gyroX*.25f, h*.29f+sin(t*.49f)*m*.018f,
                    m*.115f, m*.115f, shades[3])
                paperWave(canvas,w,h,h*.48f+gyroY*.24f,h*.56f,
                    m*.10f*sin(t*.24f),m*.06f,shades[1])
                paperWave(canvas,w,h,h*.65f-gyroY*.20f,h*.42f,
                    -m*.09f,m*.07f*cos(t*.30f),shades[2])
                paperWave(canvas,w,h,h*.83f+sin(t*.33f)*m*.022f,h*.19f,
                    m*.05f,m*.10f,shades[0])
            }
            1 -> {
                // Arc Stack: nested monolithic portals with responsive width.
                val open = sin(t*.41f)*m*.018f + gyroX*.20f
                round(canvas,w*.08f+open,h*.25f,w*.92f-open,h*1.12f,m*.36f,shades[1])
                round(canvas,w*.19f-open,h*.36f,w*.81f+open,h*1.13f,m*.27f,shades[2])
                round(canvas,w*.29f+open*.6f,h*.47f,w*.71f-open*.6f,h*1.13f,m*.20f,shades[3])
                round(canvas,w*.40f,h*.59f,w*.60f,h*1.13f,m*.12f,shades[0])
            }
            2 -> {
                // Soft Fold: bevel highlights follow independent diagonal sheets.
                path.reset()
                path.moveTo(-w*.07f,h*.40f+gyroY)
                path.lineTo(w*.74f+gyroX,h*.15f)
                path.lineTo(w*1.08f,h*.32f)
                path.lineTo(w*.20f,h*.59f)
                path.close()
                color(canvas,shades[1]);canvas.drawPath(path,paint)
                path.reset()
                path.moveTo(-w*.08f,h*.64f)
                path.lineTo(w*.80f+gyroX*.3f,h*.42f+motion*m*.026f)
                path.lineTo(w*1.08f,h*.58f)
                path.lineTo(w*.05f,h*.88f)
                path.close()
                color(canvas,shades[2]);canvas.drawPath(path,paint)
                paperWave(canvas,w,h,h*.84f+gyroY*.12f,h*.20f,m*.05f,-m*.06f,shades[3])
            }
            3 -> {
                // Pebble Lines: deliberately non-circular, smooth ceramic stones.
                for (i in 0..3) {
                    val shift = sin(t*.33f + i*1.8f)*m*.018f
                    val x = w*(.27f + (i%2)*.43f) + gyroX*(.1f+i*.06f)
                    val y = h*(.24f + i*.17f) + shift
                    ellipse(canvas,x,y,m*(.17f+i*.010f),m*(.070f+i*.012f),
                        shades[(i+1)%4])
                }
                round(canvas,w*.09f,h*.91f,w*.91f,h*.916f,m*.003f,shades[3])
            }
            4 -> {
                // Ribbon Cutout: two paths define the layered negative-space window.
                path.reset()
                path.moveTo(-w*.14f,h*.15f)
                path.cubicTo(w*.92f,h*.09f,w*.02f,h*.58f,w*1.12f,h*.61f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = m*.46f
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = shades[1]
                paint.alpha = 255
                canvas.save()
                canvas.translate(gyroX*.22f,gyroY*.20f)
                canvas.drawPath(path,paint)
                paint.strokeWidth=m*.30f
                paint.color=shades[2]
                canvas.drawPath(path,paint)
                paint.strokeWidth=m*.16f
                paint.color=shades[0]
                canvas.drawPath(path,paint)
                canvas.restore()
                paint.style=Paint.Style.FILL
                ellipse(canvas,w*.60f,h*.47f+sin(t*.27f)*m*.02f,
                    m*.083f,m*.083f,shades[3])
            }
            5 -> {
                // Mono Dunes: four handmade bezier trajectories with staggered drift.
                for (i in 0..3) {
                    val top = h*(.29f + i*.18f) + sin(t*.17f+i*.91f)*m*.03f
                    paperWave(canvas,w,h,top,h-top,
                        m*(.11f-.025f*i),m*(.04f+.017f*i),
                        shades[(i+1)%4])
                }
            }
            6 -> {
                // Orbit Insets: asymmetric stacks of architectural rounded rectangles.
                for (i in 0..3) {
                    val inset = w*(.07f+i*.085f)
                    val vertical = h*(.17f+i*.091f)
                    val shift = sin(t*.22f+i*.6f)*m*.018f + gyroX*.08f
                    round(canvas,inset+shift,vertical,
                        w-inset+shift,h-vertical*.58f,m*(.21f-i*.028f),
                        shades[(i+1)%4])
                }
            }
            else -> {
                // Ceramic Waves: independently rocking convex bowls and seam.
                paperWave(canvas,w,h,h*.34f+sin(t*.23f)*m*.018f,
                    h*.80f,-m*.16f,m*.07f,shades[1])
                paperWave(canvas,w,h,h*.55f+gyroY*.20f,
                    h*.52f,m*.10f,m*.13f,shades[2])
                ellipse(canvas,w*.64f+gyroX*.18f,h*.48f,m*.16f,m*.095f,shades[3])
                paperWave(canvas,w,h,h*.77f+sin(t*.41f)*m*.012f,
                    h*.29f,-m*.075f,m*.045f,shades[0])
            }
        }
    }
}
