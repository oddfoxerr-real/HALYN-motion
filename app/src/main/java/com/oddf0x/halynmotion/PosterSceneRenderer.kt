package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Thirty original, recognizable subject-led clock posters.
 * Six subject families x five distinct compositions. Not branded characters.
 *
 * Artwork is drawn in a 360x640 design space; an overscanned atmospheric
 * layer and foreground objects get subtly different gyro positions. Clock
 * is drawn between these two stages by HalynRenderer's caller.
 */
class PosterSceneRenderer {
    data class Scene(
        val title: String,
        val skyTop: Int,
        val skyMid: Int,
        val skyBottom: Int,
        val accent: Int,
        val shadow: Int,
        val family: Int,
        val variant: Int
    )

    companion object {
        const val FIRST = 21
        const val COUNT = 30
        const val LAST = FIRST + COUNT - 1
        fun isPoster(index: Int) = index in FIRST..LAST
        val SCENES = listOf(
            Scene("Blue Rose",0xFF111526.toInt(),0xFFCE8AA6.toInt(),0xFFF1B6BF.toInt(),0xFF5F9ECF.toInt(),0xFF153D58.toInt(),0,0),
            Scene("Red Anemone",0xFF0E090E.toInt(),0xFF35080B.toInt(),0xFF08080A.toInt(),0xFFF53234.toInt(),0xFF220910.toInt(),0,1),
            Scene("Sunflower Hour",0xFFEF9B42.toInt(),0xFFF5D271.toInt(),0xFFFFEDAE.toInt(),0xFFFFD447.toInt(),0xFF446A36.toInt(),0,2),
            Scene("Lunar Lotus",0xFF101E3F.toInt(),0xFF4F5895.toInt(),0xFF151D37.toInt(),0xFFE3C3EB.toInt(),0xFF19445B.toInt(),0,3),
            Scene("Sakura Drift",0xFF7194D4.toInt(),0xFFF4B2B8.toInt(),0xFFF5DAA2.toInt(),0xFFFFB8CB.toInt(),0xFF41344B.toInt(),0,4),

            Scene("Sunset GT",0xFF3A4B8D.toInt(),0xFFE8827D.toInt(),0xFFFFAF51.toInt(),0xFFE63B62.toInt(),0xFF17142D.toInt(),1,0),
            Scene("Midnight Rider",0xFF091224.toInt(),0xFF233F6C.toInt(),0xFF080B15.toInt(),0xFF66D8FA.toInt(),0xFF0B1222.toInt(),1,1),
            Scene("Coastal Drive",0xFF94C5DC.toInt(),0xFFFAD1A2.toInt(),0xFFB1CDD3.toInt(),0xFFFCF5E8.toInt(),0xFF284E64.toInt(),1,2),
            Scene("Neon Circuit",0xFF120D2E.toInt(),0xFF482A71.toInt(),0xFF171023.toInt(),0xFF6D64FF.toInt(),0xFF111224.toInt(),1,3),
            Scene("Desert Runner",0xFF4984BA.toInt(),0xFFED9865.toInt(),0xFFE2A35E.toInt(),0xFFFFDB76.toInt(),0xFF532E35.toInt(),1,4),

            Scene("Alone by the Sea",0xFF2975B4.toInt(),0xFFB0D6EB.toInt(),0xFF315B90.toInt(),0xFFFFFFFF.toInt(),0xFF122E4F.toInt(),2,0),
            Scene("Foggy Pines",0xFF9CA6A4.toInt(),0xFFD2B9AD.toInt(),0xFF314F56.toInt(),0xFFFFD8AC.toInt(),0xFF1B3437.toInt(),2,1),
            Scene("Peach Horizon",0xFF85A5CE.toInt(),0xFFF3B6AD.toInt(),0xFFFCDFB9.toInt(),0xFFF7F1E8.toInt(),0xFF585D7B.toInt(),2,2),
            Scene("Deep Blue",0xFF06132F.toInt(),0xFF183D65.toInt(),0xFF051429.toInt(),0xFFBFDFFC.toInt(),0xFF07263B.toInt(),2,3),
            Scene("Ember Forest",0xFF641B24.toInt(),0xFFF0633B.toInt(),0xFFF9B56A.toInt(),0xFFFFD36C.toInt(),0xFF132A27.toInt(),2,4),

            Scene("Night Cat",0xFF1B283F.toInt(),0xFF303A53.toInt(),0xFF121A2B.toInt(),0xFFADC2F1.toInt(),0xFF141724.toInt(),3,0),
            Scene("Little Fox",0xFF345C84.toInt(),0xFFE5A38A.toInt(),0xFFF5C5A0.toInt(),0xFFFFA65B.toInt(),0xFF6D392A.toInt(),3,1),
            Scene("Moon Owl",0xFF0A1634.toInt(),0xFF263B63.toInt(),0xFF111A2A.toInt(),0xFFB5A2EA.toInt(),0xFF171F3B.toInt(),3,2),
            Scene("Cozy Panda",0xFFF2BE60.toInt(),0xFFF9DA9D.toInt(),0xFFDD9E53.toInt(),0xFFF9F7EA.toInt(),0xFF322927.toInt(),3,3),
            Scene("Shadow Rabbit",0xFF4D4474.toInt(),0xFFDF9BC5.toInt(),0xFF7B537C.toInt(),0xFFE7D4F1.toInt(),0xFF272033.toInt(),3,4),

            Scene("Quiet Street",0xFF273B64.toInt(),0xFF7C87A1.toInt(),0xFF212940.toInt(),0xFFFFCB82.toInt(),0xFF152438.toInt(),4,0),
            Scene("Streetlamp Rain",0xFF121A2C.toInt(),0xFF31546D.toInt(),0xFF182436.toInt(),0xFFFFD18B.toInt(),0xFF112132.toInt(),4,1),
            Scene("Skyline Blue",0xFF09122B.toInt(),0xFF26467A.toInt(),0xFF1C3157.toInt(),0xFF58C2ED.toInt(),0xFF101C35.toInt(),4,2),
            Scene("Lantern Alley",0xFF201B32.toInt(),0xFF583441.toInt(),0xFF1D1B2B.toInt(),0xFFFF955C.toInt(),0xFF231B2A.toInt(),4,3),
            Scene("Rooftop Glow",0xFF17233F.toInt(),0xFF675A8D.toInt(),0xFF1B2C42.toInt(),0xFFCE8FFF.toInt(),0xFF101D2E.toInt(),4,4),

            Scene("Mountain Cabin",0xFF14365E.toInt(),0xFF426D88.toInt(),0xFF203E4F.toInt(),0xFFFFD28D.toInt(),0xFF203A38.toInt(),5,0),
            Scene("Island Gate",0xFF3B75BD.toInt(),0xFFF4B58E.toInt(),0xFFD8DEE4.toInt(),0xFFFEDEA9.toInt(),0xFF35526B.toInt(),5,1),
            Scene("Desert Arch",0xFF477AAF.toInt(),0xFFF9B874.toInt(),0xFFEAA265.toInt(),0xFFFFDC9A.toInt(),0xFF814C40.toInt(),5,2),
            Scene("Last Lighthouse",0xFF102F54.toInt(),0xFF507998.toInt(),0xFF122C49.toInt(),0xFFFFE5AA.toInt(),0xFF233E52.toInt(),5,3),
            Scene("Temple Sunrise",0xFF3F5B8D.toInt(),0xFFD89A84.toInt(),0xFFE8CEB3.toInt(),0xFFF8E0B3.toInt(),0xFF493B54.toInt(),5,4)
        )
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val path = Path()

    fun drawBackground(canvas: Canvas, index: Int, time: Float, gyro: ParallaxPose) {
        val scene = SCENES[(index - FIRST).coerceIn(0, COUNT - 1)]
        canvas.save()
        canvas.scale(canvas.width / 360f, canvas.height / 640f)
        p.shader = LinearGradient(0f, 0f, 0f, 640f,
            intArrayOf(scene.skyTop, scene.skyMid, scene.skyBottom),
            floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 360f, 640f, p)
        p.shader = null

        canvas.save()
        canvas.translate(gyro.x * 7f, gyro.y * 5f)
        when (scene.family) {
            0 -> flowerBackground(canvas, scene, time)
            1 -> motorBackground(canvas, scene, time)
            2 -> natureBackground(canvas, scene, time)
            3 -> creatureBackground(canvas, scene, time)
            4 -> urbanBackground(canvas, scene, time)
            else -> travelBackground(canvas, scene, time)
        }
        canvas.restore()
        canvas.restore()
    }

    fun drawForeground(canvas: Canvas, index: Int, time: Float, gyro: ParallaxPose) {
        val scene = SCENES[(index - FIRST).coerceIn(0, COUNT - 1)]
        canvas.save()
        canvas.scale(canvas.width / 360f, canvas.height / 640f)
        canvas.translate(gyro.x * 12f, gyro.y * 8f)
        when (scene.family) {
            0 -> flowerForeground(canvas, scene, time)
            1 -> motorForeground(canvas, scene, time)
            2 -> natureForeground(canvas, scene, time)
            3 -> creatureForeground(canvas, scene, time)
            4 -> urbanForeground(canvas, scene, time)
            else -> travelForeground(canvas, scene, time)
        }
        canvas.restore()
    }

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, alpha: Int = 255) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = color
        p.alpha = alpha
        c.drawRect(l, t, r, b, p)
        p.alpha = 255
    }

    private fun ellipse(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int, alpha: Int = 255) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = color
        p.alpha = alpha
        c.drawOval(x-rx, y-ry, x+rx, y+ry, p)
        p.alpha = 255
    }

    private fun stroke(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float,
        color: Int, width: Float, alpha: Int = 255) {
        p.shader = null
        p.color = color
        p.alpha = alpha
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        p.strokeCap = Paint.Cap.ROUND
        c.drawLine(x1,y1,x2,y2,p)
        p.style = Paint.Style.FILL
        p.alpha = 255
    }

    private fun polygon(c: Canvas, color: Int, vararg coords: Float) {
        path.reset()
        path.moveTo(coords[0], coords[1])
        var i=2
        while (i+1 < coords.size) {
            path.lineTo(coords[i], coords[i+1])
            i+=2
        }
        path.close()
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = color
        p.alpha = 255
        c.drawPath(path,p)
    }

    private fun glow(c: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Int = 80) {
        val a = (alpha.coerceIn(0,255))
        p.shader = RadialGradient(x,y,radius,
            intArrayOf(Color.argb(a,Color.red(color),Color.green(color),Color.blue(color)),
                Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawCircle(x,y,radius,p)
        p.shader = null
    }

    private fun moon(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        glow(c,x,y,radius*2.7f,color,62)
        ellipse(c,x,y,radius,radius,color)
    }

    private fun mountain(c: Canvas, y: Float, color: Int, offset: Float) {
        polygon(c,color,-48f,650f,-48f,y+45f,0f,y+12f,48f,y+54f,
            102f,y-28f+offset,157f,y+30f,205f,y-44f+offset,260f,y+28f,
            305f,y-16f+offset,408f,y+35f,408f,650f)
    }

    private fun pine(c: Canvas, x: Float, y: Float, h: Float, color: Int) {
        rect(c,x-h*.035f,y-h*.22f,x+h*.035f,y,color)
        for (i in 0..2) {
            val yy=y-h*.20f-i*h*.22f
            val span=h*(.19f-i*.036f)
            polygon(c,color,x-span,yy+6f,x,yy-h*.35f,x+span,yy+6f)
        }
    }

    private fun flowerBackground(c: Canvas, s: Scene, t: Float) {
        if (s.variant==1) {
            glow(c,190f,286f,185f,s.accent,72)
            rect(c,0f,515f,360f,640f,0xFF090709.toInt())
        } else if (s.variant==3) {
            moon(c,275f,178f,32f,0xFFDCEBFF.toInt())
            mountain(c,500f,0xFF233D61.toInt(),10f)
        } else {
            if (s.variant==2 || s.variant==4) moon(c,265f,340f,52f,0xFFFFE8B5.toInt())
            if (s.variant==4) for(i in 0 until 14) {
                ellipse(c,(i*131%360).toFloat(),(170+i*23%350).toFloat(),
                    2.3f,3.5f,0xFFFFCFDC.toInt(),155)
            }
        }
        // Negligible motion: the subject is the visual focus.
    }

    private fun petal(c: Canvas, cx: Float, cy: Float, r: Float,
        angle: Float, color: Int, alpha: Int = 255) {
        c.save()
        c.rotate(angle,cx,cy)
        ellipse(c,cx,cy-r*.62f,r*.38f,r*.66f,color,alpha)
        c.restore()
    }

    private fun flowerForeground(c: Canvas, s: Scene, t: Float) {
        val cy = when(s.variant) { 0 -> 338f; 1 -> 286f; 2 -> 375f; 3 -> 362f; else -> 345f }
        val cx = when(s.variant) { 0 -> 202f; 1 -> 190f; 2 -> 192f; 3 -> 180f; else -> 155f }
        val r = when(s.variant) { 1 -> 100f; 2 -> 87f; else -> 74f }
        val green = if(s.variant==1) 0xFF303F30.toInt() else 0xFF35615A.toInt()
        // Gently bowed stem; petals and leaves physically overlap the date/clock.
        p.color = green
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        path.reset()
        path.moveTo(cx-23f,660f)
        path.cubicTo(cx-26f,560f,cx+20f,455f,cx,cy+17f)
        c.drawPath(path,p)
        p.style = Paint.Style.FILL
        polygon(c,green,cx-4f,504f,cx-72f,469f,cx-51f,516f,cx-6f,534f)
        polygon(c,0xFF427D65.toInt(),cx-6f,546f,cx+70f,511f,cx+49f,563f,cx,574f)
        val count=when(s.variant){1->9;2->14;3->8;else->7}
        for(i in 0 until count) {
            val angle=i*360f/count + sin(t*.12f)*1.5f
            petal(c,cx,cy,r,angle,s.accent,235)
        }
        for (i in 0 until count) {
            val angle=i*360f/count + 180f/count
            petal(c,cx,cy,r*.65f,angle,
                if(s.variant==2) 0xFFFFA834.toInt()
                else if(s.variant==0) 0xFFB6D7EE.toInt()
                else if(s.variant==1) 0xFFA30B1F.toInt()
                else 0xFFFFD6E1.toInt(),200)
        }
        ellipse(c,cx,cy,r*.28f,r*.28f,
            if(s.variant==2) 0xFF4D3721.toInt() else 0xFF2B2334.toInt())
        for(i in 0 until 18) {
            val a=i*6.2831853f/18f
            ellipse(c,cx+cos(a)*r*.15f,cy+sin(a)*r*.15f,
                2.3f,2.3f,if(s.variant==2) 0xFFFFDF82.toInt() else 0xFFD0B87B.toInt(),200)
        }
    }

    private fun motorBackground(c: Canvas,s:Scene,t:Float) {
        val horizon=if(s.variant==3) 392f else 358f
        glow(c,270f,horizon-40f,145f,s.accent,80)
        if(s.variant!=1) ellipse(c,275f,horizon-38f,57f,57f,0xFFFFD1A0.toInt(),175)
        polygon(c,0xFF192038.toInt(),-40f,420f,140f,390f,400f,412f,400f,640f,-40f,640f)
        // Speed lines form a clear vanishing point and physical roadway.
        val road=if(s.variant==2) 0xFF476778.toInt() else 0xFF171727.toInt()
        polygon(c,road,170f,horizon,218f,horizon,415f,640f,-55f,640f)
        for(i in 0 until 7) {
            val x=i*85f-110f
            stroke(c,x,650f,188f,horizon,0xFFEDD8CD.toInt(),1.8f,90)
        }
        for(i in 0 until 9) {
            val yy=455f+i*22f
            stroke(c,0f,yy,360f,yy+4f,s.accent,2f,45)
        }
    }

    private fun motorForeground(c:Canvas,s:Scene,t:Float) {
        if(s.variant==1) {
            // Head-on motorcycle with a rider, not another car recolour.
            ellipse(c,180f,506f,33f,76f,0xFF11121B.toInt())
            ellipse(c,180f,497f,24f,61f,0xFF333B45.toInt())
            ellipse(c,180f,429f,45f,24f,0xFF0C1521.toInt())
            polygon(c,0xFF1E2939.toInt(),143f,405f,167f,350f,197f,350f,220f,405f)
            ellipse(c,181f,336f,31f,40f,0xFF090F17.toInt())
            stroke(c,133f,399f,225f,399f,0xFF222B35.toInt(),7f)
            ellipse(c,155f,418f,10f,7f,s.accent)
            ellipse(c,208f,418f,10f,7f,s.accent)
            glow(c,155f,418f,38f,s.accent,115)
            glow(c,208f,418f,38f,s.accent,115)
            return
        }
        val yy=if(s.variant==4) 491f else 486f
        // Rear 3/4 sports coupé silhouette and simple translucent glass.
        polygon(c,0xFF0A0A12.toInt(),48f,yy+66f,54f,yy+20f,
            103f,yy+1f,143f,yy-49f,259f,yy-48f,309f,yy+2f,
            332f,yy+27f,326f,yy+66f)
        polygon(c,s.accent,55f,yy+44f,73f,yy+6f,142f,yy-42f,
            256f,yy-41f,301f,yy+10f,321f,yy+45f)
        polygon(c,0xFF182B45.toInt(),151f,yy-31f,243f,yy-31f,
            275f,yy+2f,123f,yy+2f)
        polygon(c,0xFF34445C.toInt(),159f,yy-26f,233f,yy-26f,250f,yy-6f,142f,yy-6f)
        for(x in listOf(90f,289f)) {
            ellipse(c,x,yy+55f,31f,31f,0xFF0B0D14.toInt())
            ellipse(c,x,yy+55f,18f,18f,0xFF677185.toInt())
            ellipse(c,x,yy+55f,11f,11f,0xFF202633.toInt())
        }
        rect(c,89f,yy+12f,131f,yy+23f,0xFFFF5D76.toInt())
        rect(c,258f,yy+12f,306f,yy+23f,0xFFFF5D76.toInt())
        if(s.variant==0||s.variant==3) {
            polygon(c,0xFF111322.toInt(),70f,yy-36f,97f,yy-36f,109f,yy-5f,76f,yy-5f)
        }
        stroke(c,105f,yy+90f,315f,yy+112f,s.accent,5f,85)
    }

    private fun natureBackground(c:Canvas,s:Scene,t:Float) {
        if(s.variant==3) {
            moon(c,240f,218f,38f,0xFFE4E9FF.toInt())
        } else {
            moon(c,240f,328f,if(s.variant==4) 80f else 43f,s.accent)
        }
        mountain(c,406f,s.variant==4?0xFF3B5962.toInt():0xFF738CAC.toInt(),10f)
        mountain(c,459f,s.variant==4?0xFF234E4D.toInt():0xFF46728C.toInt(),-5f)
        if(s.variant==0||s.variant==2||s.variant==3) {
            rect(c,0f,474f,360f,640f,0xFF204A74.toInt())
            for(i in 0 until 15) {
                val yy=487f+i*11f
                stroke(c,40f+sin(i.toFloat())*18f,yy,300f-sin(i.toFloat())*20f,yy,
                    0xFFB8D3EA.toInt(),1.2f,45+i*5)
            }
        }
        if(s.variant==1||s.variant==4) for(i in 0 until 30) {
            val x=i*29f-70f
            pine(c,x,541f+(i%3)*24f,55f+i%4*10f,s.shadow)
        }
    }

    private fun natureForeground(c:Canvas,s:Scene,t:Float) {
        if(s.variant==0 || s.variant==3) {
            // Person at the waterline; a real subject, not a random geometry field.
            ellipse(c,177f,466f,14f,16f,0xFF0B192D.toInt())
            polygon(c,0xFF0B192D.toInt(),163f,478f,191f,478f,201f,537f,151f,537f)
            stroke(c,163f,532f,155f,577f,0xFF0B192D.toInt(),9f)
            stroke(c,189f,532f,196f,578f,0xFF0B192D.toInt(),9f)
            stroke(c,177f,579f,181f,631f,0xFF7E98B6.toInt(),2f,95)
        } else if (s.variant==1 || s.variant==4) {
            for(i in 0 until 9) pine(c,i*62f-56f,620f+i%3*15f,
                120f+(i%3)*30f,s.shadow)
        } else {
            polygon(c,0xFF253E55.toInt(),-30f,610f,0f,540f,102f,560f,195f,596f,390f,555f,390f,670f)
            stroke(c,113f,552f,150f,580f,0xFF152D40.toInt(),4f)
        }
    }

    private fun creatureBackground(c:Canvas,s:Scene,t:Float) {
        if(s.variant==2) moon(c,285f,145f,40f,0xFFEBE5F8.toInt())
        if(s.variant==1||s.variant==3) {
            glow(c,180f,400f,210f,s.accent,90)
            polygon(c,0xFF604C4C.toInt(),-45f,562f,150f,555f,400f,551f,400f,650f,-45f,650f)
        } else {
            ellipse(c,180f,484f,133f,132f,s.accent,25)
        }
    }

    private fun creatureForeground(c:Canvas,s:Scene,t:Float) {
        val cx=180f
        val cy=438f+sin(t*.18f)*1.7f
        // Each creature gets distinct ears / silhouette / markings.
        when(s.variant) {
            0 -> {
                polygon(c,s.shadow,89f,396f,105f,307f,156f,369f,215f,361f,265f,307f,275f,405f)
                ellipse(c,cx,cy,110f,107f,s.shadow)
            }
            1 -> {
                polygon(c,s.accent,79f,400f,94f,308f,163f,354f,218f,354f,274f,304f,289f,400f)
                ellipse(c,cx,cy,110f,107f,s.accent)
                ellipse(c,cx,cy+41f,60f,38f,0xFFFFF4E6.toInt())
            }
            2 -> {
                ellipse(c,cx,cy,105f,114f,s.shadow)
                polygon(c,s.shadow,85f,398f,105f,330f,142f,359f,219f,359f,256f,331f,279f,398f)
                ellipse(c,135f,440f,49f,55f,s.accent,125)
                ellipse(c,225f,440f,49f,55f,s.accent,125)
            }
            3 -> {
                ellipse(c,cx,cy,110f,111f,0xFFF4EEE2.toInt())
                ellipse(c,100f,365f,38f,39f,s.shadow)
                ellipse(c,262f,365f,38f,39f,s.shadow)
                ellipse(c,145f,454f,32f,42f,s.shadow)
                ellipse(c,216f,454f,32f,42f,s.shadow)
            }
            else -> {
                ellipse(c,146f,331f,29f,104f,s.shadow)
                ellipse(c,218f,331f,29f,104f,s.shadow)
                ellipse(c,cx,cy+10f,105f,104f,s.shadow)
                ellipse(c,146f,321f,12f,68f,s.accent,150)
                ellipse(c,218f,321f,12f,68f,s.accent,150)
            }
        }
        if (s.variant==3) {
            ellipse(c,151f,443f,18f,23f,0xFFF7F7FF.toInt())
            ellipse(c,210f,443f,18f,23f,0xFFF7F7FF.toInt())
        } else {
            ellipse(c,143f,448f,22f,25f,0xFFFDFEFF.toInt())
            ellipse(c,217f,448f,22f,25f,0xFFFDFEFF.toInt())
        }
        ellipse(c,150f,450f,10f,15f,0xFF15213B.toInt())
        ellipse(c,210f,450f,10f,15f,0xFF15213B.toInt())
        ellipse(c,154f,445f,4f,6f,0xFFFFFFFF.toInt())
        ellipse(c,214f,445f,4f,6f,0xFFFFFFFF.toInt())
        ellipse(c,180f,490f,12f,8f,0xFF2D252B.toInt())
        stroke(c,163f,509f,180f,517f,0xFF473438.toInt(),2.5f)
        stroke(c,180f,517f,198f,509f,0xFF473438.toInt(),2.5f)
    }

    private fun urbanBackground(c:Canvas,s:Scene,t:Float) {
        glow(c,250f,342f,135f,s.accent,60)
        for(i in 0 until 20) {
            val x=i*25f-45f
            val height=110f+((i*37)%160)
            val yy=490f-height
            rect(c,x,yy,x+27f,640f,if(i%3==0) 0xFF273856.toInt() else s.shadow)
            for (r in 0 until 10) {
                val top=yy+11f+r*17f
                if(top>=492f) break
                for(col in 0..1) {
                    if((i+r+col*3)%5==0) continue
                    rect(c,x+5f+col*10f,top,x+9f+col*10f,top+8f,s.accent,110)
                }
            }
        }
        rect(c,0f,486f,360f,640f,0xFF1E2939.toInt(),160)
        for(i in 0 until 12) {
            val yy=511f+i*12f
            stroke(c,10f,yy,347f,yy+3f,s.accent,1f,65)
        }
    }

    private fun urbanForeground(c:Canvas,s:Scene,t:Float) {
        if(s.variant==1||s.variant==0) {
            // Warm overhead streetlamp and rain trails.
            stroke(c,82f,636f,84f,305f,0xFF111928.toInt(),8f)
            stroke(c,83f,306f,131f,305f,0xFF111928.toInt(),7f)
            glow(c,128f,311f,88f,s.accent,150)
            ellipse(c,128f,314f,18f,8f,s.accent)
            for(i in 0 until 27) {
                val x=(i*73%370).toFloat()
                val y=(i*131%640).toFloat()
                stroke(c,x,y,x-9f,y+29f,0xFFC8D8E4.toInt(),1.1f,76)
            }
        } else if(s.variant==3) {
            for(i in 0 until 8) {
                val x=20f+i*43f
                stroke(c,x,160f,x+3f,340f,0xFF131B2A.toInt(),1.6f)
                glow(c,x+2f,343f,39f,s.accent,90)
                ellipse(c,x+2f,343f,12f,18f,s.accent)
            }
        } else {
            polygon(c,0xFF1B2437.toInt(),0f,486f,42f,414f,70f,420f,98f,485f,360f,487f,360f,650f,0f,650f)
            stroke(c,10f,414f,360f,432f,0xFF161C2C.toInt(),5f)
            for (i in 0 until 8) {
                val x=20f+i*49f
                ellipse(c,x,440f,3f,3f,s.accent)
            }
        }
    }

    private fun travelBackground(c:Canvas,s:Scene,t:Float) {
        val moonColor=if(s.variant==0||s.variant==3)0xFFE5E5F8.toInt() else s.accent
        moon(c,265f,232f,if(s.variant==2)64f else 38f,moonColor)
        mountain(c,454f,if(s.variant==2) 0xFFA96F63.toInt() else 0xFF7890A2.toInt(),10f)
        mountain(c,503f,if(s.variant==2) 0xFF855B55.toInt() else 0xFF3B6272.toInt(),-5f)
        if(s.variant==1||s.variant==3) {
            rect(c,0f,540f,360f,640f,0xFF21516B.toInt())
            for(i in 0 until 10) {
                stroke(c,i*23f,557f+i*8f,360f-i*14f,559f+i*8f,s.accent,1.3f,70)
            }
        }
    }

    private fun travelForeground(c:Canvas,s:Scene,t:Float) {
        when(s.variant) {
            0 -> {
                // Small cabin with peaked roof and warm windows.
                rect(c,90f,480f,270f,621f,0xFF604C49.toInt())
                polygon(c,0xFF263C40.toInt(),69f,489f,179f,421f,293f,488f)
                rect(c,126f,522f,164f,565f,s.accent)
                rect(c,192f,522f,231f,567f,s.accent)
                stroke(c,145f,522f,145f,565f,0xFF573E40.toInt(),5f)
                stroke(c,213f,522f,213f,566f,0xFF573E40.toInt(),5f)
                glow(c,145f,540f,75f,s.accent,70)
            }
            1 -> {
                // Stately ruined gate on a grassy sky-island.
                polygon(c,s.shadow,-35f,595f,19f,553f,314f,552f,410f,594f,410f,650f,-35f,650f)
                for(x in listOf(95f,242f)) rect(c,x,394f,x+26f,556f,0xFFDDD2BD.toInt())
                rect(c,78f,387f,292f,409f,0xFFF0E2C8.toInt())
                polygon(c,0xFFC9BDAA.toInt(),73f,386f,184f,353f,295f,385f)
            }
            2 -> {
                // Sandstone arch with a real open aperture.
                rect(c,86f,422f,129f,609f,s.shadow)
                rect(c,230f,420f,275f,609f,s.shadow)
                rect(c,90f,403f,271f,452f,s.shadow)
                polygon(c,0xFFA77359.toInt(),80f,405f,171f,359f,282f,405f)
                rect(c,0f,595f,360f,640f,0xFFAE7959.toInt())
            }
            3 -> {
                // Lighthouse with a lighthouse beam that softly sweeps.
                polygon(c,0xFFE7E1D6.toInt(),150f,572f,166f,350f,204f,350f,217f,572f)
                rect(c,161f,343f,209f,365f,0xFF243A50.toInt())
                polygon(c,0xFF182D45.toInt(),151f,341f,185f,312f,218f,341f)
                glow(c,185f,349f,115f,s.accent,130)
                rect(c,177f,344f,196f,353f,s.accent)
                polygon(c,0xFF183A4F.toInt(),-20f,590f,80f,552f,195f,590f,360f,558f,380f,650f,-20f,650f)
            }
            else -> {
                // Distant temple arches and nearby columns.
                rect(c,73f,416f,106f,585f,0xFFDCD1C2.toInt())
                rect(c,253f,416f,286f,585f,0xFFDCD1C2.toInt())
                rect(c,65f,407f,293f,430f,0xFFEEE0C9.toInt())
                polygon(c,0xFFEAD5BD.toInt(),58f,405f,180f,357f,300f,405f)
                rect(c,106f,449f,123f,584f,0xFFC6B5AA.toInt())
                rect(c,237f,449f,252f,584f,0xFFC6B5AA.toInt())
                polygon(c,s.shadow,-40f,623f,120f,587f,268f,587f,420f,625f,420f,650f,-40f,650f)
            }
        }
    }
}
