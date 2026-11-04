package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Visually coherent lit 3D-style planet discs rendered onto a native Canvas.
 * These are procedural material illustrations, not NASA imagery or surface maps.
 * World coordinates and angular positions come from the ephemeris/catalog.
 */
class SpaceWorldPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val atmosphere = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.rgb(230, 234, 247)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }
    private val circleClip = Path()

    private fun lighten(color: Int, amount: Float): Int = Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * amount).toInt().coerceIn(0,255),
        (Color.green(color) + (255 - Color.green(color)) * amount).toInt().coerceIn(0,255),
        (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt().coerceIn(0,255)
    )

    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0,255),
        (Color.green(color) * factor).toInt().coerceIn(0,255),
        (Color.blue(color) * factor).toInt().coerceIn(0,255)
    )

    fun render(
        canvas: Canvas, x: Float, y: Float,
        radius: Float, color: Int, name: String,
        seconds: Float, ringed: Boolean, simulated: Boolean,
        sun: Boolean = false
    ) {
        if (radius <= 0f || !radius.isFinite()) return
        val r = radius.coerceIn(3f, min(canvas.width,canvas.height)*.47f)
        val scale = max(.70f, canvas.width / 1080f)
        val rawGlow = if (sun) .28f else .11f
        val glowColors = intArrayOf(
            Color.argb((if (sun) 95 else 35), Color.red(color), Color.green(color), Color.blue(color)),
            Color.argb((if (sun) 19 else 8), Color.red(color), Color.green(color), Color.blue(color)),
            Color.TRANSPARENT
        )
        atmosphere.shader = RadialGradient(x,y,max(5f,r*3.2f),glowColors,
            floatArrayOf(0f,.35f,1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x,y,r*3.2f,atmosphere)
        atmosphere.shader = null

        if(ringed && r>9f) drawRing(canvas,x,y,r,color,true)

        fill.shader = RadialGradient(x-r*.38f,y-r*.38f,max(r*.4f,r*1.55f),
            intArrayOf(lighten(color,if(sun) .65f else .50f),color,
                darken(color,if(sun) .42f else .18f), Color.rgb(2,4,12)),
            floatArrayOf(0f,.37f,.72f,1f),Shader.TileMode.CLAMP)
        canvas.drawCircle(x,y,r,fill)
        fill.shader = null

        if(r>16f && !sun) {
            circleClip.reset()
            circleClip.addCircle(x,y,r,Path.Direction.CW)
            canvas.save()
            canvas.clipPath(circleClip)
            val asGas = name == "Jupiter" || name == "Saturn" ||
                name == "Neptune" || name == "Uranus" || simulated
            if(asGas) {
                // Gas cloud bands are visually stylized, not factual remote-sensing.
                for(i in 0..13) {
                    val yy=y-r + r*(i+.5f)/7f
                    val phase=sin(i*1.41f + seconds*.05f + if(simulated) 2f else 0f)
                    val opacity=(27+abs(phase)*27).toInt().coerceIn(0,65)
                    ringPaint.color= if(i%2==0) lighten(color,.75f) else darken(color,.22f)
                    ringPaint.alpha=opacity
                    ringPaint.strokeWidth=max(1.3f,r*.045f)
                    canvas.drawLine(x-r*1.1f,yy+phase*r*.04f,x+r*1.1f,yy-phase*r*.04f,ringPaint)
                }
            } else if(name=="Earth" || name=="Mars" || simulated) {
                for(i in 0..6) {
                    val px=x+r*(sin(i*1.93f+seconds*.023f)*.64f)
                    val py=y+r*(sin(i*.91f+1.17f)*.61f)
                    fill.color=if(name=="Earth") Color.argb(75,123,185,152)
                        else Color.argb(50,226,196,163)
                    canvas.drawOval(px-r*.19f,py-r*.055f,px+r*.22f,py+r*.075f,fill)
                }
            }
            canvas.restore()
        }
        if(ringed && r>9f) drawRing(canvas,x,y,r,color,false)

        val edgePaint=ringPaint
        edgePaint.color=lighten(color,.56f)
        edgePaint.alpha=if(sun) 135 else 65
        edgePaint.strokeWidth=max(1f,r*.008f)
        canvas.drawCircle(x,y,r,edgePaint)

        labelPaint.textSize=max(11.5f,20f*scale)
        labelPaint.color=Color.argb(225,238,242,250)
        canvas.drawText(name + if(simulated) "  ·  GENERATED" else "",
            x+r+max(8f,r*.10f),y-r*.6f,labelPaint)
        if(simulated && r>24f) {
            labelPaint.textSize=max(10f,15f*scale)
            labelPaint.color=Color.argb(190,176,193,219)
            canvas.drawText("PROCEDURAL / NOT CATALOGUED",x+r+max(8f,r*.10f),
                y-r*.6f+labelPaint.textSize*1.2f,labelPaint)
        }
    }

    private fun drawRing(c: Canvas,x:Float,y:Float,r:Float,color:Int,far:Boolean) {
        c.save()
        c.rotate(-22f,x,y)
        ringPaint.style=Paint.Style.STROKE
        for(i in 0..2) {
            ringPaint.color=if(i==1) lighten(color,.58f) else color
            ringPaint.alpha=if(far) 35+15*i else 70+28*i
            ringPaint.strokeWidth=max(1f,r*(.06f+.018f*i))
            c.drawOval(x-r*(1.55f+.10f*i),y-r*(.45f+.06f*i),
                x+r*(1.55f+.10f*i),y+r*(.45f+.06f*i),ringPaint)
        }
        c.restore()
    }
}
