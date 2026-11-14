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

    /**
     * Every Material X preset has a conspicuous, continuous live cycle
     * (typically 7-13 seconds), plus several independent layer velocities.
     * The old animation multiplier was too small to notice in real use.
     * Drawing is bounded to a handful of Canvas paths per frame.
     */
    fun draw(
        canvas: Canvas, state: MotionState, seconds: Float, pose: ParallaxPose,
        touchForce: Float = 0f, reactions: SceneSignals = SceneSignals()
    ) {
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

        // A full-speed animation clock, scaled by the existing global motion
        // slider. Animation remains active even without motion sensors.
        val t = seconds*(.78f + state.speed*.24f)
        val gyroX = pose.x*w*.065f
        val gyroY = pose.y*h*.052f
        val reaction = touchForce.coerceIn(0f, 1f)
        when (scene) {
            0 -> {
                // Paper Horizon: three continuous travelling waves and a sun
                // travelling on its own longer orbit.
                ellipse(canvas,
                    w*(.63f+.19f*sin(t*.34f))+gyroX*.21f,
                    h*(.25f+.07f*cos(t*.41f))-reaction*h*.035f,
                    m*(.105f+.016f*sin(t*.73f)),
                    m*(.105f+.016f*sin(t*.73f)), shades[3])
                paperWave(canvas,w,h,
                    h*(.43f+.075f*sin(t*.68f))+gyroY*.21f,
                    h*.67f,
                    m*(.18f*sin(t*.59f)),m*(.15f*cos(t*.45f)),shades[1])
                paperWave(canvas,w,h,
                    h*(.61f+.070f*cos(t*.82f-.6f))-gyroY*.29f,
                    h*.55f,
                    m*(.16f*cos(t*.47f)),m*(.15f*sin(t*.72f)),shades[2])
                paperWave(canvas,w,h,
                    h*(.80f+.041f*sin(t*1.12f))+reaction*h*.024f,
                    h*.29f,m*(.10f*sin(t*.95f)),m*(.12f*cos(t*.68f)),
                    shades[0])
            }
            1 -> {
                // Arc Stack: four hinged architectural portals open and close
                // at different rates, revealing deeper matte surfaces.
                val open = sin(t*.77f)*w*.065f + gyroX*.31f
                val drift = h*.048f*cos(t*.61f)
                round(canvas,
                    w*.07f+open*.65f,h*.22f+drift,
                    w*.93f-open*.65f,h*1.13f,
                    m*.37f,shades[1])
                round(canvas,
                    w*.18f-open*.47f,h*.31f-drift*.35f,
                    w*.82f+open*.47f,h*1.14f,
                    m*.29f,shades[2])
                round(canvas,
                    w*.28f+open*.53f,h*.43f+drift*.40f,
                    w*.72f-open*.53f,h*1.14f,
                    m*.20f,shades[3])
                val throat = w*.038f*sin(t*1.04f)+reaction*w*.035f
                round(canvas,w*.39f+throat,h*.54f-drift*.24f,
                    w*.61f-throat,h*1.14f,m*.11f,shades[0])
            }
            2 -> {
                // Soft Fold: three separately sliding sheets with a moving
                // reflection line, not just a bobbing static polygon.
                val slide1 = w*.090f*sin(t*.62f)+gyroX*.33f
                val slide2 = w*.078f*cos(t*.83f-.3f)-gyroX*.22f
                path.reset()
                path.moveTo(-w*.17f+slide1,h*.34f+gyroY)
                path.lineTo(w*.66f+slide1,h*.11f+h*.045f*sin(t*.78f))
                path.lineTo(w*1.15f+slide1,h*.29f)
                path.lineTo(w*.19f+slide1,h*.61f)
                path.close()
                color(canvas,shades[1]);canvas.drawPath(path,paint)

                path.reset()
                path.moveTo(-w*.14f+slide2,h*.61f)
                path.lineTo(w*.75f+slide2,h*.34f+h*.060f*cos(t*.63f))
                path.lineTo(w*1.13f+slide2,h*.57f)
                path.lineTo(w*.06f+slide2,h*.90f)
                path.close()
                color(canvas,shades[2]);canvas.drawPath(path,paint)
                paperWave(canvas,w,h,
                    h*(.82f+.043f*sin(t*.94f)),h*.33f,
                    m*(.11f*sin(t*.69f)),-m*(.12f*cos(t*.52f)),shades[3])
                paint.style=Paint.Style.STROKE
                paint.strokeWidth=m*.009f
                paint.strokeCap=Paint.Cap.ROUND
                paint.color=shades[3]
                paint.alpha=167
                canvas.drawLine(w*.12f+slide1,h*.51f,
                    w*.72f+slide1,h*(.23f+.038f*sin(t*.78f)),paint)
                paint.style=Paint.Style.FILL
                paint.alpha=255
            }
            3 -> {
                // Pebble Lines: each ceramic stone travels along an offset
                // path of its own and independently breathes in scale.
                for (i in 0..3) {
                    val phase = t*(.47f+i*.12f)+i*1.76f
                    val shiftX = w*.13f*sin(phase)+gyroX*(.15f+i*.07f)
                    val shiftY = h*.045f*cos(phase*1.27f)
                    val centerX = w*(.28f+(i%2)*.40f)+shiftX
                    val centerY = h*(.20f+i*.186f)+shiftY
                    val breathe = 1f+.18f*sin(phase*.91f)
                    ellipse(canvas,centerX,centerY,
                        m*(.15f+i*.012f)*breathe,
                        m*(.067f+i*.011f)*(1f+.14f*cos(phase)),
                        shades[(i+1)%4])
                }
                round(canvas,w*.085f,h*.925f,w*.915f,h*.932f,m*.004f,shades[3])
            }
            4 -> {
                // Ribbon Cutout: deform the actual Bezier control points on
                // every frame; a layered ribbon flows through the opening.
                val sway = w*.11f*sin(t*.64f)+gyroX*.28f
                path.reset()
                path.moveTo(-w*.16f,h*(.19f+.05f*sin(t*.51f)))
                path.cubicTo(
                    w*(.80f+.16f*cos(t*.73f)),h*(.06f+.07f*sin(t*.62f)),
                    w*(.08f+.10f*sin(t*.81f)),h*(.57f+.06f*cos(t*.52f)),
                    w*1.16f+sway,h*(.62f+.046f*sin(t*.67f)))
                paint.style=Paint.Style.STROKE
                paint.strokeWidth=m*.49f
                paint.strokeCap=Paint.Cap.ROUND
                paint.color=shades[1]
                paint.alpha=255
                canvas.drawPath(path,paint)
                paint.strokeWidth=m*.33f
                paint.color=shades[2]
                canvas.drawPath(path,paint)
                paint.strokeWidth=m*.17f
                paint.color=shades[0]
                canvas.drawPath(path,paint)
                paint.style=Paint.Style.FILL
                ellipse(canvas,
                    w*(.55f+.13f*sin(t*.73f))+gyroX*.18f,
                    h*(.46f+.10f*cos(t*.68f)),
                    m*(.072f+.013f*sin(t*.91f)),
                    m*(.072f+.013f*sin(t*.91f)),shades[3])
            }
            5 -> {
                // Mono Dunes: four independently moving hill contours with
                // heights that change continuously, not a fixed poster.
                for (i in 0..3) {
                    val top = h*(.26f+i*.18f)+
                        h*.065f*sin(t*(.42f+i*.13f)+i*.84f)
                    paperWave(canvas,w,h,top,h-top+h*.13f,
                        m*(.22f*sin(t*(.58f+i*.09f)+i*.65f)),
                        m*(.17f*cos(t*(.41f+i*.13f)-i*.51f)),
                        shades[(i+1)%4])
                }
            }
            6 -> {
                // Orbit Insets: four architectural frames translate and
                // expand independently, like orbiting sheets of cut paper.
                for (i in 0..3) {
                    val phase=t*(.45f+i*.10f)+i*.90f
                    val inset=w*(.07f+i*.083f)
                    val vertical=h*(.13f+i*.092f)
                    val slideX=w*.055f*sin(phase)+gyroX*.20f
                    val slideY=h*.045f*cos(phase*1.23f)+gyroY*.20f
                    val grow=m*.020f*sin(phase*.83f)
                    round(canvas,
                        inset+slideX-grow,vertical+slideY-grow,
                        w-inset+slideX+grow,
                        h-vertical*.58f+slideY+grow,
                        m*(.22f-i*.028f),shades[(i+1)%4])
                }
            }
            else -> {
                // Ceramic Waves: oscillating bowls separate, reconnect,
                // and carry a floating highlight on a different rhythm.
                paperWave(canvas,w,h,
                    h*(.31f+.075f*sin(t*.68f)),h*.85f,
                    m*(.19f*sin(t*.73f)),m*(.15f*cos(t*.57f)),shades[1])
                paperWave(canvas,w,h,
                    h*(.52f+.070f*cos(t*.91f))+gyroY*.26f,h*.61f,
                    m*(.15f*cos(t*.59f)),m*(.20f*sin(t*.76f)),shades[2])
                ellipse(canvas,
                    w*(.60f+.17f*sin(t*.54f))+gyroX*.15f,
                    h*(.43f+.055f*cos(t*.83f))-
                        reactions.music*h*.025f,
                    m*(.14f+.025f*cos(t*.88f)),
                    m*(.083f+.015f*sin(t*.61f)),
                    shades[3])
                paperWave(canvas,w,h,
                    h*(.78f+.046f*sin(t*1.02f))+reaction*h*.021f,
                    h*.35f,m*(.14f*sin(t*.64f)),
                    m*(.12f*cos(t*.81f)),shades[0])
            }
        }
    }
}
