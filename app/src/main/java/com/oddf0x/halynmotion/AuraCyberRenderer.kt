package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
 * Twelve original, interactive live scenes: six atmospheric battle-energy
 * compositions and six restrained network/cyber optical systems.
 *
 * All 12 are real Canvas animations, not static artwork with a filter.
 * The per-renderer gesture state is independent between studio previews,
 * gallery cards and Android's live wallpaper engine. Every design preserves
 * stable prior HALYN preset numbers (new range 156..167).
 */
class AuraCyberRenderer {
    data class Scene(val name: String, val mechanic: String)

    companion object {
        const val FIRST = 156
        const val COUNT = 12
        const val LAST = FIRST + COUNT - 1
        val SCENES = listOf(
            Scene("Aura Strike", "Living energy aura with a fast-swipe strike-line blast and shockwave."),
            Scene("Nova Clash", "Two opposing energy flows collide; movement compresses their boundary."),
            Scene("Impact Veil", "Translucent energy sheets split into a diagonal impact trail."),
            Scene("Void Rush", "Deep spatial distortion converges toward a moving vanishing point."),
            Scene("Energy Break", "A flexible luminous membrane ruptures briefly under a rapid gesture."),
            Scene("Pulse Slash", "Thin orbiting energy threads unleash offset slash afterimages."),
            Scene("Cipher Veil", "Quiet glass data panels, moving trace packets and local decrypt scans."),
            Scene("Ghost Trace", "Branching network paths and signal packets flare along a fast swipe."),
            Scene("Vector Breach", "Architectural optical seams part momentarily on fast movements."),
            Scene("Packet Drift", "Curving signal routes with moving packets that bend around touch."),
            Scene("Zero Grid", "A matte technical grid with passing illumination and touch ripples."),
            Scene("Signal Intrusion", "A low-noise scan field, selective interference and narrow echo waves.")
        )
        fun isAuraCyber(preset: Int): Boolean = preset in FIRST..LAST
        fun description(preset: Int): String? =
            if (isAuraCyber(preset)) SCENES[preset - FIRST].mechanic else null
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val path = Path()
    private var backdrop: LinearGradient? = null
    private var glow: RadialGradient? = null
    private var lastW = -1
    private var lastH = -1
    private var lastColor = 0
    private var lastScene = -1

    private var pressed = false
    private var touchX = .5f
    private var touchY = .5f
    private var lastTouchX = .5f
    private var lastTouchY = .5f
    private var lastTouchMs = -1L
    private var gestureStartX = .5f
    private var gestureStartY = .5f
    private var burstTime = -10_000L
    private var burstX = .5f
    private var burstY = .5f
    private var burstDX = 1f
    private var burstDY = 0f
    private var burstPower = 0f
    private var previousTiltX = 0f
    private var previousTiltY = 0f
    private var lastTiltSample = -1L

    /**
     * Detect real swipe speed from MotionEvent timestamps and pixel distances,
     * not an unreliable 2.4-second "touch force" flag. DP/S allows identical
     * sensitivity in the studio preview and the wallpaper's full-size canvas.
     */
    fun onTouch(
        action: Int, x: Float, y: Float, eventMs: Long,
        widthPx: Float, heightPx: Float, density: Float, sensitivity: Float
    ) {
        val w = widthPx.coerceAtLeast(1f)
        val h = heightPx.coerceAtLeast(1f)
        val nx = (x/w).coerceIn(0f, 1f)
        val ny = (y/h).coerceIn(0f, 1f)
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                gestureStartX = nx
                gestureStartY = ny
                touchX = nx
                touchY = ny
                lastTouchX = nx
                lastTouchY = ny
                lastTouchMs = eventMs
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pressed) return
                val elapsed = eventMs - lastTouchMs
                val dxPx = (nx - lastTouchX)*w
                val dyPx = (ny - lastTouchY)*h
                val speed = if (elapsed in 1L..220L) {
                    hypot(dxPx,dyPx)*1000f/
                        (elapsed.toFloat()*density.coerceAtLeast(.75f))
                } else 0f
                val travelledDp = hypot((nx-gestureStartX)*w,
                    (ny-gestureStartY)*h)/density.coerceAtLeast(.75f)
                val threshold = 1200f/sensitivity.coerceIn(.5f,2f)
                if (speed >= threshold && travelledDp > 22f &&
                    eventMs - burstTime > 640L) {
                    burst(nx, ny, dxPx, dyPx, eventMs,
                        (speed/2100f).coerceIn(.55f,1.35f))
                }
                lastTouchX = nx
                lastTouchY = ny
                lastTouchMs = eventMs
                touchX = nx
                touchY = ny
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchX = nx
                touchY = ny
                pressed = false
                lastTouchMs = -1L
            }
        }
    }

    private fun burst(x: Float,y: Float,dx: Float,dy: Float,now: Long,power: Float) {
        burstX = x
        burstY = y
        val d = hypot(dx,dy).coerceAtLeast(.001f)
        burstDX = dx/d
        burstDY = dy/d
        burstTime = now
        burstPower = power
    }

    /**
     * Shared motion signal for the new dedicated GPU compositor. Reuse the
     * proven timestamped touch logic instead of running duplicate detectors.
     * This Canvas renderer is now the GPU-failure recovery path only.
     */
    data class Impact(
        val age: Float,
        val x: Float,
        val y: Float,
        val directionX: Float,
        val directionY: Float,
        val power: Float,
        val pointerX: Float,
        val pointerY: Float,
        val pointerDown: Boolean
    )

    fun sampleImpact(pose: ParallaxPose, state: MotionState): Impact {
        val now = SystemClock.uptimeMillis()
        tiltBurst(pose, now, state)
        val age = if (burstTime > 0L) {
            ((now - burstTime) / (720f *
                state.strikeDuration.coerceIn(.5f, 1.8f))).coerceIn(0f, 2f)
        } else 2f
        return Impact(
            age, burstX, burstY,
            burstDX, burstDY, burstPower,
            touchX, touchY, pressed
        )
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f,1f)
        return Color.rgb(
            (Color.red(a)*(1f-f)+Color.red(b)*f).toInt().coerceIn(0,255),
            (Color.green(a)*(1f-f)+Color.green(b)*f).toInt().coerceIn(0,255),
            (Color.blue(a)*(1f-f)+Color.blue(b)*f).toInt().coerceIn(0,255)
        )
    }

    private fun tint(a: Int, alpha: Float): Int =
        Color.argb((alpha.coerceIn(0f,1f)*255f).toInt(),
            Color.red(a),Color.green(a),Color.blue(a))

    private fun palette(state: MotionState, index: Int): IntArray {
        if (state.fourColorsEnabled) return QuadColorSystem.colors(state)
        val base = when (index) {
            0 -> 0xFF4F9EFF.toInt()
            1 -> 0xFF8B76FF.toInt()
            2 -> 0xFF78C8FF.toInt()
            3 -> 0xFF997AFF.toInt()
            4 -> 0xFF4CD9EB.toInt()
            5 -> 0xFF71ADFF.toInt()
            6 -> 0xFF63CECB.toInt()
            7 -> 0xFF56B5CC.toInt()
            8 -> 0xFF6CA5F9.toInt()
            9 -> 0xFF83D5CB.toInt()
            10 -> 0xFF7E8FF9.toInt()
            else -> 0xFF66B5CF.toInt()
        }
        val primary = if (state.palette == 0) base
            else if (state.palette == 5) state.customColor
            else HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0,4)]
        return intArrayOf(
            primary, mix(primary, Color.WHITE,.43f),
            mix(primary,0xFF6178E9.toInt(),.33f),
            mix(primary,Color.WHITE,.79f)
        )
    }

    private fun backdrop(canvas: Canvas, w: Float,h: Float, primary: Int,scene: Int) {
        if (backdrop == null || lastW != w.toInt() || lastH != h.toInt() ||
            lastColor != primary || lastScene != scene) {
            backdrop = LinearGradient(
                0f,0f,w*.64f,h,
                intArrayOf(Color.rgb(2,4,12),
                    mix(Color.rgb(6,9,20),primary, if (scene<6) .105f else .037f),
                    Color.rgb(2,5,12)),null, Shader.TileMode.CLAMP)
            glow = RadialGradient(0f,0f,1f,
                intArrayOf(tint(primary,.42f),tint(primary,.16f),
                    tint(primary,.035f),tint(primary,0f)),
                floatArrayOf(0f,.24f,.61f,1f),Shader.TileMode.CLAMP)
            lastW = w.toInt()
            lastH = h.toInt()
            lastColor = primary
            lastScene = scene
        }
        paint.reset()
        paint.isAntiAlias = true
        paint.isDither = true
        paint.shader = backdrop
        canvas.drawRect(0f,0f,w,h,paint)
        paint.shader = null
    }

    private fun haze(canvas: Canvas,x: Float,y: Float,rx: Float,ry: Float,alpha: Float) {
        canvas.save()
        canvas.translate(x,y)
        canvas.scale(rx.coerceAtLeast(1f),ry.coerceAtLeast(1f))
        paint.style=Paint.Style.FILL
        paint.color=Color.WHITE
        paint.shader=glow
        paint.alpha=(255f*alpha.coerceIn(0f,1f)).toInt()
        canvas.drawCircle(0f,0f,1f,paint)
        paint.shader=null
        paint.alpha=255
        canvas.restore()
    }

    private fun line(
        canvas: Canvas,ax: Float,ay: Float,bx: Float,by: Float,
        color: Int,width: Float,opacity: Float
    ) {
        paint.shader=null
        paint.style=Paint.Style.STROKE
        paint.strokeWidth=width.coerceAtLeast(.45f)
        paint.strokeCap=Paint.Cap.ROUND
        paint.color=color
        paint.alpha=(255f*opacity.coerceIn(0f,1f)).toInt()
        canvas.drawLine(ax,ay,bx,by,paint)
        paint.style=Paint.Style.FILL
        paint.alpha=255
    }

    private fun curve(
        canvas: Canvas, ax: Float,ay: Float,cx1: Float,cy1: Float,
        cx2: Float,cy2: Float,bx: Float,by: Float,
        color: Int,width: Float,opacity: Float
    ) {
        path.reset()
        path.moveTo(ax,ay)
        path.cubicTo(cx1,cy1,cx2,cy2,bx,by)
        paint.shader=null
        paint.color=color
        paint.alpha=(255f*opacity.coerceIn(0f,1f)).toInt()
        paint.style=Paint.Style.STROKE
        paint.strokeCap=Paint.Cap.ROUND
        paint.strokeWidth=width.coerceAtLeast(.5f)
        canvas.drawPath(path,paint)
        paint.style=Paint.Style.FILL
        paint.alpha=255
    }

    private fun arc(
        canvas: Canvas, cx: Float,cy: Float,rx: Float,ry: Float,
        start: Float,sweep: Float,color: Int,width: Float,alpha: Float
    ) {
        paint.shader=null
        paint.style=Paint.Style.STROKE
        paint.strokeWidth=width.coerceAtLeast(.5f)
        paint.strokeCap=Paint.Cap.ROUND
        paint.color=color
        paint.alpha=(255f*alpha.coerceIn(0f,1f)).toInt()
        canvas.drawArc(cx-rx,cy-ry,cx+rx,cy+ry,start,sweep,false,paint)
        paint.style=Paint.Style.FILL
        paint.alpha=255
    }

    private fun dot(canvas: Canvas,x: Float,y: Float,r: Float,color: Int,alpha: Float) {
        paint.shader=null
        paint.style=Paint.Style.FILL
        paint.color=color
        paint.alpha=(255f*alpha.coerceIn(0f,1f)).toInt()
        canvas.drawCircle(x,y,r.coerceAtLeast(.5f),paint)
        paint.alpha=255
    }

    private fun panel(canvas: Canvas,left: Float,top: Float,right: Float,bottom: Float,
                      fill: Int,alpha: Float,radius: Float) {
        paint.shader=null
        paint.color=fill
        paint.style=Paint.Style.FILL
        paint.alpha=(alpha.coerceIn(0f,1f)*255f).toInt()
        canvas.drawRoundRect(left,top,right,bottom,radius,radius,paint)
        paint.alpha=255
    }

    private fun tiltBurst(pose: ParallaxPose, now: Long, state: MotionState) {
        if (state.gyroMode == 0) {
            lastTiltSample = -1L
            return
        }
        if (lastTiltSample > 0L) {
            val dt=(now-lastTiltSample).toFloat()/1000f
            val dx=pose.x-previousTiltX
            val dy=pose.y-previousTiltY
            val speed=if (dt in .012f..0.20f) hypot(dx,dy)/dt else 0f
            // GyroParallax is filtered and normalized; this threshold avoids
            // natural hand jitter repeatedly triggering large flashes.
            if (speed > 2.6f/state.strikeSensitivity && hypot(dx,dy)>.09f &&
                now-burstTime > 850L) {
                burst(.5f+pose.x*.15f,.5f+pose.y*.1f,dx,dy,now,
                    (speed/6f).coerceIn(.38f,.75f))
            }
        }
        previousTiltX=pose.x
        previousTiltY=pose.y
        lastTiltSample=now
    }

    fun draw(
        canvas: Canvas,state: MotionState,seconds: Float,
        pose: ParallaxPose=ParallaxPose(),reactions: SceneSignals=SceneSignals(),
        offsetX: Float=.5f
    ) {
        val index=state.preset-FIRST
        if (index !in 0 until COUNT) return
        val w=canvas.width.coerceAtLeast(1).toFloat()
        val h=canvas.height.coerceAtLeast(1).toFloat()
        val m=min(w,h)
        val colors=palette(state,index)
        val now=SystemClock.uptimeMillis()
        tiltBurst(pose,now,state)
        backdrop(canvas,w,h,colors[0],index)
        val t=seconds*(.45f+state.speed*.63f)
        val parallaxX=pose.x*w*.04f+(offsetX-.5f)*w*.05f
        val parallaxY=pose.y*h*.028f
        val cx=w*.50f+parallaxX
        val cy=h*.48f+parallaxY
        val strength=state.intensity.coerceIn(.35f,2.3f)

        when(index) {
            0 -> auraStrike(canvas,w,h,m,t,cx,cy,colors,strength)
            1 -> novaClash(canvas,w,h,m,t,cx,cy,colors,strength)
            2 -> impactVeil(canvas,w,h,m,t,cx,cy,colors,strength)
            3 -> voidRush(canvas,w,h,m,t,cx,cy,colors,strength)
            4 -> energyBreak(canvas,w,h,m,t,cx,cy,colors,strength)
            5 -> pulseSlash(canvas,w,h,m,t,cx,cy,colors,strength)
            6 -> cipherVeil(canvas,w,h,m,t,colors,reactions)
            7 -> ghostTrace(canvas,w,h,m,t,colors)
            8 -> vectorBreach(canvas,w,h,m,t,colors,reactions)
            9 -> packetDrift(canvas,w,h,m,t,colors,reactions)
            10 -> zeroGrid(canvas,w,h,m,t,colors)
            else -> signalIntrusion(canvas,w,h,m,t,colors,reactions)
        }
        if (pressed && index>=6) {
            val px=touchX*w; val py=touchY*h
            arc(canvas,px,py,m*.055f,m*.055f,30f,210f,
                colors[1],m*.003f,.28f)
        }
        val age=(now-burstTime).toFloat()/
            (720f*state.strikeDuration.coerceIn(.5f,1.8f))
        if (age in 0f..1f) {
            if (index<6) drawBattleBurst(canvas,w,h,m,age,index,colors,strength)
            else drawCyberBurst(canvas,w,h,m,age,index,colors,strength)
        }
    }

    private fun auraStrike(
        c: Canvas,w: Float,h: Float,m: Float,t: Float,x: Float,y: Float,
        col: IntArray,s: Float
    ) {
        haze(c,x,y,m*.67f,h*.39f,.87f*s)
        haze(c,x,y,m*.24f,h*.25f,.76f*s)
        for(i in 0..8) {
            val f=i/8f
            val sway=sin(t*.75f+i*1.61f)*m*(.025f+.013f*f)
            val spread=(f-.5f)*m*.55f
            val ax=x+spread
            val ay=y+h*.36f
            val crestY=y-h*(.30f+.11f*sin(t*.46f+i*.36f))
            curve(c,ax,ay,ax+spread*.13f+sway,y+h*.11f,
                x-spread*.3f-sway,crestY+h*.08f,x+spread*.42f+sway,crestY,
                if (i%3==0) col[1] else col[0],
                m*(.004f+.003f*(1f-abs(f-.5f))),.32f+.35f*s)
        }
        for(i in 0..17) {
            val seed=i*2.39996f
            val rise=(t*(.065f+i%3*.015f)+i*.137f)%1f
            val px=x+sin(seed+t*.21f)*m*(.08f+rise*.28f)
            val py=y+h*.28f-rise*h*.58f
            dot(c,px,py,m*(.0014f+i%3*.0005f),
                col[i%4],(.60f-rise*.45f)*s)
        }
        arc(c,x,y,m*.20f,m*.23f,35f+t*12f,108f,col[3],m*.004f,.36f*s)
    }

    private fun novaClash(
        c: Canvas,w: Float,h: Float,m: Float,t: Float,x: Float,y: Float,
        col: IntArray,s: Float
    ) {
        haze(c,w*.27f,h*.45f,m*.36f,h*.49f,.69f*s)
        haze(c,w*.78f,h*.52f,m*.35f,h*.43f,.54f*s)
        for(i in 0..7) {
            val phase=t*.50f+i*.56f
            val wobble=m*.07f*sin(phase)
            val fy=h*(.15f+i*.098f)
            curve(c,-w*.07f,fy,w*.28f,fy+wobble,
                w*.46f,fy-wobble,w*.50f+wobble*.12f,fy,
                col[if(i%2==0)0 else 2],m*.007f,.28f+.26f*s)
            curve(c,w*1.07f,fy+h*.045f,w*.80f,fy-wobble,
                w*.57f,fy+wobble,w*.52f-wobble*.12f,fy,
                col[1],m*.006f,.23f+.27f*s)
        }
        val seam=w*.50f+m*.03f*sin(t*.73f)
        curve(c,seam,h*.12f,seam-m*.06f,h*.34f,seam+m*.06f,h*.61f,
            seam,h*.91f,col[3],m*.008f,.61f*s)
        arc(c,seam,h*.49f,m*(.11f+.018f*sin(t)),m*.12f,
            t*24f,260f,col[1],m*.004f,.65f*s)
    }

    private fun impactVeil(
        c: Canvas,w: Float,h: Float,m: Float,t: Float,x: Float,y: Float,
        col: IntArray,s: Float
    ) {
        haze(c,x,y,m*.64f,h*.26f,.54f*s)
        for(i in 0..5) {
            val slide=m*.08f*sin(t*(.34f+i*.08f)+i*.87f)
            val yy=h*(.15f+i*.15f)
            curve(c,-w*.22f,yy+slide,w*.18f,yy-m*.15f+slide,
                w*.77f,yy+m*.10f+slide,w*1.16f,yy-m*.23f+slide,
                col[i%4],m*(.021f+i*.002f),.11f+.08f*s)
            curve(c,-w*.20f,yy+slide,w*.18f,yy-m*.15f+slide,
                w*.77f,yy+m*.10f+slide,w*1.16f,yy-m*.23f+slide,
                col[(i+1)%4],m*.003f,.34f*s)
        }
        arc(c,x,y,m*.20f,m*.32f,135f+t*17f,130f,col[3],m*.005f,.51f)
    }

    private fun voidRush(
        c: Canvas,w: Float,h: Float,m: Float,t: Float,x: Float,y: Float,
        col: IntArray,s: Float
    ) {
        haze(c,x,y,m*.45f,m*.35f,.34f*s)
        for(i in 0..9) {
            val progress=(i*.134f+t*.09f)%1f
            val rad=m*(.055f+progress*.88f)
            arc(c,x,y,rad,rad*.77f,30f+progress*32f,225f,
                col[i%4],m*(.0015f+progress*.003f),
                (.32f+.23f*(1f-progress))*s)
        }
        for(i in 0..18) {
            val a=i*2.399f
            val r=m*(.18f+((i*.173f+t*.11f)%1f)*.57f)
            val dx=cos(a);val dy=sin(a)
            line(c,x+dx*r,y+dy*r*.77f,
                x+dx*(r+m*.07f),y+dy*(r+m*.07f)*.77f,
                col[i%4],m*.002f,.17f+.18f*s)
        }
    }

    private fun energyBreak(
        c: Canvas,w: Float,h: Float,m: Float,t: Float,x: Float,y: Float,
        col: IntArray,s: Float
    ) {
        haze(c,x,y,m*.67f,h*.37f,.58f*s)
        for(i in 0..8) {
            val yy=h*(.18f+i*.085f)
            val wave=m*.035f*sin(t*.95f+i*.51f)
            curve(c,-w*.10f,yy,w*.25f,yy+wave*2f,
                w*.72f,yy-wave*1.6f,w*1.12f,yy+wave,
                col[i%4],m*.0034f,.34f*s)
        }
        arc(c,x,y,m*.17f,m*.29f,215f,t*21f+150f,col[3],m*.004f,.46f*s)
    }

    private fun pulseSlash(
        c: Canvas,w: Float,h: Float,m: Float,t: Float,x: Float,y: Float,
        col: IntArray,s: Float
    ) {
        haze(c,x,y,m*.48f,h*.32f,.63f*s)
        for(i in 0..6) {
            val drift=m*.043f*sin(t*.56f+i*.92f)
            val startX=w*(.10f+i*.095f)
            curve(c,startX-drift,h*.85f,
                startX+w*.08f,h*.51f+drift,
                startX+w*.04f,h*.25f-drift,
                startX+w*.25f,h*.12f,
                col[i%4],m*.004f,.31f+.10f*s)
        }
        arc(c,x,y,m*.23f,m*.20f,t*17f,123f,col[3],m*.004f,.53f*s)
    }

    private fun cipherVeil(
        c: Canvas,w: Float,h: Float,m: Float,t: Float,
        col: IntArray,signals: SceneSignals
    ) {
        // Dense optical glass panels with understated scan illumination.
        for(i in 0..3) {
            val left=w*(.10f+i*.065f)
            val top=h*(.12f+i*.15f)+sin(t*.35f+i*.80f)*m*.017f
            panel(c,left,top,w*(.89f-i*.052f),top+h*.23f,
                col[i%4],.026f,m*.019f)
            line(c,left,top+h*.005f,w*(.89f-i*.052f),top+h*.005f,
                col[(i+1)%4],m*.0018f,.36f)
        }
        for(i in 0..13) {
            val yy=h*(.16f+i*.052f)
            val drift=((t*.052f+i*.091f)%1f)
            val x=w*(.17f+drift*.63f)
            line(c,w*.17f,yy,w*.83f,yy,col[2],m*.0011f,.072f)
            dot(c,x,yy,m*.0022f,col[i%4],.44f)
        }
        val scanY=h*((t*.075f)%1f)
        line(c,w*.08f,scanY,w*.92f,scanY,col[1],m*.0033f,.25f)
        if(signals.notification>0f)
            arc(c,w*.61f,h*.48f,m*.14f,m*.14f,38f,250f,
                col[3],m*.003f,signals.notification*.70f)
    }

    private fun ghostTrace(
        c: Canvas,w: Float,h: Float,m: Float,t: Float,col: IntArray
    ) {
        for(i in 0..11) {
            val x1=w*(.10f+(i%3)*.29f)
            val y1=h*(.10f+(i/3)*.24f)
            val x2=x1+w*.15f*sin(.8f+i*1.4f)
            val y2=y1+h*.17f
            curve(c,x1,y1,x1+w*.07f,y1+h*.08f,
                x2-w*.05f,y2-h*.05f,x2,y2,
                col[i%4],m*.0017f,.23f)
            dot(c,x1,y1,m*.0032f,col[(i+1)%4],.45f)
            val advance=(t*.19f+i*.127f)%1f
            val px=x1+(x2-x1)*advance
            val py=y1+(y2-y1)*advance
            dot(c,px,py,m*.0039f,col[1],.54f)
        }
    }

    private fun vectorBreach(
        c: Canvas,w: Float,h: Float,m: Float,t: Float,
        col: IntArray,signals: SceneSignals
    ) {
        val breathe=sin(t*.38f)*w*.019f
        for(i in 0..5) {
            val y=h*(.06f+i*.18f)
            val x=w*(.13f+i*.061f)+breathe
            curve(c,-w*.12f,y+h*.18f,x,y+h*.10f,
                x+w*.29f,y-h*.05f,w*1.12f,y-h*.18f,
                col[(i+1)%4],m*.0026f,
                .14f+.07f*signals.charging)
            line(c,x,y+h*.10f,x+w*.31f,y-h*.07f,
                col[i%4],m*.007f,.10f+signals.charging*.08f)
        }
        panel(c,w*.16f+breathe,h*.40f,w*.89f+breathe,h*.53f,
            col[0],.06f,m*.012f)
    }

    private fun packetDrift(
        c: Canvas,w: Float,h: Float,m: Float,t: Float,
        col: IntArray,signals: SceneSignals
    ) {
        val density=if(signals.music>.5f) 19 else 13
        for(i in 0..density) {
            val y=h*(.11f+i/(density+1f)*.78f)
            val bend=m*(.045f+.025f*sin(i*1.21f))
            val offset=if(pressed) {
                val distance=abs(y-touchY*h)/h
                exp(-distance*11f)*m*.047f
            } else 0f
            curve(c,w*.08f,y,w*.33f,y-bend-offset,
                w*.69f,y+bend+offset,w*.92f,y,
                col[i%4],m*.0016f,.13f)
            val pos=(t*(.075f+.015f*(i%4))+i*.191f)%1f
            val x=w*(.08f+.84f*pos)
            val packetY=y+bend*sin(pos*6.28318f)*.67f
            dot(c,x,packetY,m*.0029f,col[(i+1)%4],.48f)
        }
    }

    private fun zeroGrid(
        c: Canvas,w: Float,h: Float,m: Float,t: Float,col: IntArray
    ) {
        val cols=9;val rows=18
        for(i in 0..cols) {
            val x=w*(.085f+.83f*i/cols.toFloat())
            line(c,x,h*.07f,x,h*.93f,col[0],m*.001f,.095f)
        }
        for(j in 0..rows) {
            val y=h*(.07f+.86f*j/rows.toFloat())
            line(c,w*.085f,y,w*.915f,y,col[0],m*.001f,.086f)
        }
        val scan=h*(.10f+.80f*((t*.11f)%1f))
        line(c,w*.085f,scan,w*.915f,scan,col[1],m*.0023f,.40f)
        for(i in 0..8) {
            val f=((t*.17f+i*.193f)%1f)
            val x=w*(.13f+((i*3)%9)/9f*.73f)
            val y=h*(.11f+f*.75f)
            panel(c,x,y,x+w*.055f,y+h*.018f,
                col[(i+1)%4],.12f,m*.003f)
        }
    }

    private fun signalIntrusion(
        c: Canvas,w: Float,h: Float,m: Float,t: Float,
        col: IntArray,signals: SceneSignals
    ) {
        val phase=(t*.13f)%1f
        for(i in 0..23) {
            val yy=h*(.07f+i*.038f)
            val noise=sin(i*4.71f+phase*12f)
            val offset=w*.022f*noise
            val length=w*(.25f+.49f*(.5f+.5f*sin(i*2.38f)))
            line(c,w*.16f+offset,yy,w*.16f+length+offset,yy,
                col[i%4],m*.0017f,.085f+.075f*abs(noise))
        }
        val scan=h*(.08f+.83f*phase)
        line(c,w*.12f,scan,w*.88f,scan,col[1],m*.004f,.37f)
        if(signals.notification>.01f)
            arc(c,w*.53f,h*.51f,m*.11f,m*.11f,73f,200f,
                col[2],m*.004f,signals.notification*.50f)
    }

    private fun drawBattleBurst(
        c: Canvas,w: Float,h: Float,m: Float,p: Float,index: Int,
        col: IntArray,strength: Float
    ) {
        val fade=(1f-p)*(1f-p)
        val bx=burstX*w;val by=burstY*h
        val spread=m*(.13f+p*.65f)*burstPower
        val dx=burstDX;val dy=burstDY
        // Velocity-aligned slash afterimages; no radial 4-armed shapes.
        val count=if(index==3) 24 else if(index==5) 12 else 17
        for(i in 0 until count) {
            val jitter=sin(i*3.137f+index*.7f)
            val lateral=(i-count*.5f)/count.toFloat()
            val centerX=bx-dy*lateral*spread*.95f+
                dx*spread*(.25f+jitter*.12f)
            val centerY=by+dx*lateral*spread*.95f+
                dy*spread*(.25f+jitter*.12f)
            val len=m*(.095f+.25f*((i*7)%9)/9f)*
                (1f+p*.36f)*burstPower
            line(c,centerX-dx*len*.80f,centerY-dy*len*.80f,
                centerX+dx*len*.20f,centerY+dy*len*.20f,
                col[i%4],m*(if(i%5==0).006f else .0026f),
                fade*(.60f+.20f*strength))
        }
        arc(c,bx,by,spread*.78f,spread*.78f,17f,235f,
            col[3],m*.006f,fade*.57f)
        haze(c,bx,by,spread*.75f,spread*.75f,fade*.78f)
    }

    private fun drawCyberBurst(
        c: Canvas,w: Float,h: Float,m: Float,p: Float,index: Int,
        col: IntArray,strength: Float
    ) {
        val fade=(1f-p)*(1f-p)
        val bx=burstX*w;val by=burstY*h
        val range=m*(.10f+.55f*p)*burstPower
        val slope=burstDX
        val tilt=burstDY
        when(index) {
            6,10 -> {
                arc(c,bx,by,range,range*.9f,
                    20f+p*17f,225f,col[1],m*.003f,fade*.69f)
                for(i in 0..5) {
                    val yy=by+(i-2.5f)*m*.039f
                    line(c,bx-range*.63f,yy,bx+range*.63f,yy,
                        col[i%4],m*.0014f,fade*.24f)
                }
            }
            7,9 -> {
                for(i in 0..9) {
                    val px=bx+(i-4.5f)*m*.034f
                    val py=by+sin(i*1.8f)*m*.047f
                    line(c,px-slope*range*.42f,py-tilt*range*.42f,
                        px+slope*range*.40f,py+tilt*range*.40f,
                        col[i%4],m*.0022f,fade*.58f)
                }
            }
            8 -> {
                val shift=range*.32f
                line(c,bx-shift,by+shift,bx+range,by-range,
                    col[3],m*.006f,fade*.68f)
                line(c,bx-shift-m*.018f,by+shift,
                    bx+range-m*.018f,by-range,
                    col[0],m*.002f,fade*.55f)
            }
            else -> {
                for(i in 0..13) {
                    val y=by+(i-6.5f)*m*.021f
                    val off=m*.030f*sin(i*7.33f+p*21f)
                    line(c,bx-range*.67f+off,y,bx+range*.67f+off,y,
                        col[i%4],m*.0019f,fade*.33f)
                }
            }
        }
        dot(c,bx,by,m*(.004f+fade*.007f),
            col[1],fade*(.45f+.25f*strength))
    }
}
