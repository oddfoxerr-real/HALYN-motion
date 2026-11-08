package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log

/** Thirty unique smooth procedural scenes with native-resolution AGSL shading. */
class SmoothWallpaperRenderer {
    data class Scene(
        val name: String, val category: String, val base: Int,
        val secondary: Int, val sky: Int, val light: Int,
        val roughness: Float, val metallic: Float, val field: String
    )

    companion object {
        const val FIRST = 75
        const val COUNT = 30
        const val LAST = FIRST + COUNT - 1
        val SCENES = listOf(
        Scene("Frosted Glass Ridges", "Glass & minerals", 0xFF718C96.toInt(), 0xFFD4E5E3.toInt(), 0xFF11212C.toInt(), 0xFFE2FAF3.toInt(), 0.36f, 0f, "float4 surface(float2 p,float t) {float f=sin(p.x*15.0+1.3*sin(p.y*3.7-t*.25)); float n=snoise(p*13.0+float2(0.0,t*.04)); return float4(.48+.25*f+.11*n,smoothstep(-.58,-.12,p.y),.16*pow(max(f,0.0),6.0),.30+.6*n);}"),
        Scene("Spun Aluminum", "Glass & minerals", 0xFF646F78.toInt(), 0xFFDFE5E8.toInt(), 0xFF0C151D.toInt(), 0xFFF9FDFF.toInt(), 0.23f, 1f, "float4 surface(float2 p,float t) {float2 q=p+float2(.04*sin(p.y*7.0+t*.17),0.0); float a=atan(q.y,q.x),r=length(q),g=sin(36.0*r+2.5*sin(a*4.0+t*.18)); return float4(.46+.22*g+.09*sin(a*7.0-t*.27),1.0-smoothstep(.53,.95,r),.08*pow(max(g,0.0),10.0),.23+.5*r);}"),
        Scene("Cathedral Quartz", "Glass & minerals", 0xFF7B9CB0.toInt(), 0xFFEBF3EE.toInt(), 0xFF101B30.toInt(), 0xFFD6F4FF.toInt(), 0.19f, 0f, "float4 surface(float2 p,float t) {float a=atan(p.y,p.x),r=length(p),c=pow(abs(cos(a*6.0+t*.045)),8.0),g=sin(r*21.0-c*3.2-t*.24);return float4(.3+.36*c+.15*g,1.0-smoothstep(.51,.73,r+.1*c),.27*c*pow(max(g,0.0),4.0),c);}"),
        Scene("Basalt Cross-Section", "Glass & minerals", 0xFF283039.toInt(), 0xFF74838D.toInt(), 0xFF080F19.toInt(), 0xFFBACAD3.toInt(), 0.83f, 0f, "float4 surface(float2 p,float t) {float strata=sin(p.y*17.0+1.9*sin(p.x*5.0+t*.09)),n=snoise(p*float2(6.0,2.3)),crack=exp(-27.0*abs(p.x-.18*sin(p.y*5.2+t*.13)));return float4(.42+.16*strata+.23*n+.10*crack,.96,.14*crack,.3+.6*n);}"),
        Scene("Acrylic Lens Stack", "Glass & minerals", 0xFF74889B.toInt(), 0xFFE9F3F6.toInt(), 0xFF0F2435.toInt(), 0xFFF3FFFF.toInt(), 0.15f, 0f, "float4 surface(float2 p,float t) {float2 q=p-float2(.09*sin(t*.14),-.06*cos(t*.12));float a=length(q-float2(-.16,.12)),b=length(q-float2(.23,-.1)),c=length(q+float2(.09,.27));float l=1.0-smoothstep(.24,.31,a),m=1.0-smoothstep(.21,.29,b),n=1.0-smoothstep(.18,.27,c);return float4(.12+.27*l+.23*m+.34*n,clamp(l+m+n,0.0,1.0),.13*exp(-90.0*abs(a-.27)),.25+.65*n);}"),
        Scene("Pearlescent Shell", "Glass & minerals", 0xFFB3AFA9.toInt(), 0xFFF9E9D6.toInt(), 0xFF292631.toInt(), 0xFFFAF2FF.toInt(), 0.31f, 0f, "float4 surface(float2 p,float t) {float a=atan(p.y,p.x),r=length(p),s=sin(33.0*r-6.0*a+t*.12),n=sin(68.0*r+3.0*sin(a*4.0));return float4(.49+.25*s+.08*n,1.0-smoothstep(.59,.78,r),.07*pow(max(s,0.0),8.0),.5+.45*sin(r*9.0));}"),
        Scene("Submerged Silk", "Nature & water", 0xFF173C4D.toInt(), 0xFF86B8BA.toInt(), 0xFF071D2D.toInt(), 0xFF9EE7D9.toInt(), 0.48f, 0f, "float4 surface(float2 p,float t) {float fabric=sin(p.x*12.0+2.7*sin(p.y*5.3-t*.35)),current=sin(p.y*7.6+p.x*2.2+t*.18),d=smoothstep(-.78,.39,p.y);return float4(.48+.27*fabric+.09*current,d,.16*pow(max(fabric,0.0),12.0),.3+.6*d);}"),
        Scene("Auroral Curtains", "Nature & water", 0xFF306859.toInt(), 0xFFB0E7AC.toInt(), 0xFF061325.toInt(), 0xFFD1F5D9.toInt(), 0.85f, 0f, "float4 surface(float2 p,float t) {float wave=sin(p.x*9.0+2.3*sin(p.y*2.0-t*.22)),wisp=sin(p.x*17.0+p.y*4.1+t*.13);float m=smoothstep(-.7,-.12,p.y)*(1.0-smoothstep(.30,.74,p.y));return float4(.4+.19*wave+.07*wisp,m*.88,.45*pow(max(0.0,wave*.5+.5),3.5)*m,.15+.7*(.5+.5*wave));}"),
        Scene("Wind-Carved Dunes", "Nature & water", 0xFFA78761.toInt(), 0xFFE5C59B.toInt(), 0xFF44525F.toInt(), 0xFFFFE0B2.toInt(), 0.86f, 0f, "float4 surface(float2 p,float t) {float horizon=.08+.07*sin(p.x*2.2),a=.29*sin(p.x*3.0-t*.025)+.13*sin(p.x*6.1+.9),b=.19*sin(p.x*5.3+.48)+.08*sin(p.y*8.0+p.x*3.2);return float4(.46+a+b,smoothstep(horizon-.04,horizon+.08,p.y),0.0,.2+.65*snoise(p*float2(4.7,2.3)));}"),
        Scene("Glacial Snowbanks", "Nature & water", 0xFFBDD0DA.toInt(), 0xFFF6FAFC.toInt(), 0xFF1A2B44.toInt(), 0xFFE5F8FF.toInt(), 0.88f, 0f, "float4 surface(float2 p,float t) {float bank=.12*sin(p.x*3.6+.4)+.10*sin(p.x*7.3+1.6),snow=smoothstep(bank-.02,bank+.055,p.y),moon=exp(-24.0*length(p-float2(.31,-.34)));return float4(.46+.20*sin(p.x*6.0+p.y*1.4)+.09*cos(p.x*11.0),snow,.30*moon*(1.0-snow),.70+.15*sin(p.x*2.5));}"),
        Scene("Mist-Cut Canyon", "Nature & water", 0xFF5C6867.toInt(), 0xFFAFC1B6.toInt(), 0xFF233745.toInt(), 0xFFF0E3C4.toInt(), 0.84f, 0f, "float4 surface(float2 p,float t) {float cut=abs(p.x+.07*sin(p.y*4.8)),cliff=smoothstep(.12,.42,cut),c=sin(17.0*p.y+4.0*cliff+1.1*sin(p.x*7.0+t*.015)),fog=exp(-7.0*cut)*(1.0-smoothstep(.03,.7,p.y));return float4(.25+.43*cliff+.12*c,.94,.10*fog,.23+.60*cliff);}"),
        Scene("Rainy Window", "Nature & water", 0xFF4B6984.toInt(), 0xFFB2C9D5.toInt(), 0xFF12233C.toInt(), 0xFFE5F3FF.toInt(), 0.17f, 0f, "float4 surface(float2 p,float t) {float2 q=p*3.5,id=floor(q),seed=float2(hash21(id),hash21(id+float2(5.3,7.1))),center=id+float2(.18+.64*seed.x,.2+.57*seed.y);center.y+=.37*fract(t*.04+seed.x);float2 d=q-center;d.x+=.14*sin(d.y*4.0+seed.y*6.28);float drop=1.0-smoothstep(.10,.26,length(d*float2(1.0,.75))),trail=exp(-15.0*abs(d.x))*smoothstep(.01,.45,-d.y);return float4(.17+.69*drop+.13*trail,.86,.08*drop,.35+.55*drop);}"),
        Scene("Satin Pleats", "Tactile materials", 0xFF553948.toInt(), 0xFFD8A9BE.toInt(), 0xFF1E1A2C.toInt(), 0xFFFCE8EA.toInt(), 0.33f, 0f, "float4 surface(float2 p,float t) {float fold=sin(p.x*13.0+2.8*sin(p.y*3.2-t*.21)),cross=sin(p.x*5.1-p.y*7.0+t*.08);return float4(.48+.28*fold+.09*cross,.98,.08*pow(max(fold,0.0),8.0),.25+.63*(.5+.5*fold));}"),
        Scene("Titanium Brushwork", "Tactile materials", 0xFF727E81.toInt(), 0xFFE2E5E4.toInt(), 0xFF141C26.toInt(), 0xFFF7F9F5.toInt(), 0.24f, 1f, "float4 surface(float2 p,float t) {float bend=p.x+.11*sin(p.y*2.1+t*.035),ribs=sin(25.0*bend+2.2*sin(p.y*4.0)),contour=sin(p.y*5.1+p.x*1.5);return float4(.46+.17*ribs+.11*contour,1.0-smoothstep(.55,.68,abs(p.x)),.11*pow(max(ribs,0.0),12.0),.34+.35*contour);}"),
        Scene("Silken Carbon Weave", "Tactile materials", 0xFF243039.toInt(), 0xFF7B9096.toInt(), 0xFF0A111A.toInt(), 0xFFC4D4D8.toInt(), 0.59f, 0f, "float4 surface(float2 p,float t) {float warp=sin(p.x*17.0+1.9*sin(p.y*3.0-t*.11)),weft=sin(p.y*13.0+.9*sin(p.x*4.7+t*.07)),cross=.5+.5*warp*weft;return float4(.44+.17*warp+.13*weft+.06*cross,.99,.07*pow(max(0.0,warp*weft),8.0),.25+.6*cross);}"),
        Scene("Porcelain Sculpture", "Tactile materials", 0xFFC4C9BD.toInt(), 0xFFFBF5E6.toInt(), 0xFF45474C.toInt(), 0xFFFFF8EE.toInt(), 0.22f, 0f, "float4 surface(float2 p,float t) {float2 q=p+float2(.02*sin(t*.15),0.0);float bowl=length(q*float2(1.1,.85)),rim=exp(-26.0*abs(bowl-.33)),inside=1.0-smoothstep(.29,.47,bowl);return float4(.13+.32*inside+.29*rim-.12*bowl,1.0-smoothstep(.58,.7,bowl),.08*rim,.18+.7*inside);}"),
        Scene("Velvet Cascade", "Tactile materials", 0xFF2D2A3E.toInt(), 0xFF837286.toInt(), 0xFF0C111F.toInt(), 0xFFC8B5CE.toInt(), 0.95f, 0f, "float4 surface(float2 p,float t) {float fold=sin(p.x*10.0+1.6*sin(p.y*2.8+t*.10)),tuck=sin(p.x*3.3-p.y*6.0-t*.08);return float4(.49+.26*fold+.07*tuck,smoothstep(-.8,-.5,p.y),.025*pow(max(fold,0.0),10.0),.30+.48*(.5+.5*fold));}"),
        Scene("Slate Relief", "Tactile materials", 0xFF495663.toInt(), 0xFFA8B1BB.toInt(), 0xFF131D2B.toInt(), 0xFFD5DFE7.toInt(), 0.91f, 0f, "float4 surface(float2 p,float t) {float carve=sin(p.x*5.0+2.5*sin(p.y*4.0)),tool=sin(p.y*12.0+1.1*sin(p.x*7.0)),n=snoise(p*6.0);return float4(.44+.19*carve+.1*tool+.07*n,smoothstep(-.72,-.63,p.x)*(1.0-smoothstep(.62,.73,p.x)),0.0,.2+.56*snoise(p*2.0));}"),
        Scene("Cosmic Filament", "Space & atmosphere", 0xFF3C658E.toInt(), 0xFFACD4E9.toInt(), 0xFF040B19.toInt(), 0xFFD5F2FA.toInt(), 0.9f, 0f, "float4 surface(float2 p,float t) {float2 q=p+float2(.13*sin(p.y*4.1-t*.11),.04*sin(p.x*6.0+t*.13));float a=atan(q.y,q.x),r=length(q),f=sin(8.0*a+18.0*r-t*.24),cloud=snoise(q*3.3+float2(t*.027,-t*.020)),w=exp(-2.9*r);return float4(.42+.12*f+.23*cloud,.56*w,.31*pow(max(f,0.0),8.0)*w,.21+.64*cloud);}"),
        Scene("Solar Corona", "Space & atmosphere", 0xFFA57951.toInt(), 0xFFFFD299.toInt(), 0xFF080A19.toInt(), 0xFFFFE4B1.toInt(), 0.72f, 0f, "float4 surface(float2 p,float t) {float2 q=p-float2(.04,-.11);float r=length(q),a=atan(q.y,q.x),edge=.34+.028*sin(a*15.0+t*.09)+.012*sin(a*27.0-t*.08),disc=1.0-smoothstep(edge-.006,edge+.01,r),corona=exp(-19.0*abs(r-edge))*pow(.5+.5*sin(a*19.0+t*.12),2.0);return float4(.39+.23*disc+.11*corona,disc*.92,.75*corona+.16*disc,.18+.61*disc);}"),
        Scene("Observatory Night", "Space & atmosphere", 0xFF182B42.toInt(), 0xFF7295B7.toInt(), 0xFF050A19.toInt(), 0xFFE5F3FF.toInt(), 0.94f, 0f, "float4 surface(float2 p,float t) {float2 q=p*34.0,c=floor(q),o=float2(hash21(c),hash21(c+float2(11.3,4.7))),dist=length(fract(q)-o),star=pow(hash21(c+float2(8.4,1.9)),8.0)*(1.0-smoothstep(.015,.17,dist)),dome=1.0-smoothstep(.36,.46,length(p+float2(0.0,.29)));return float4(.38+.24*dome+.13*star,.25+.25*dome,.7*star+.11*dome,.2+.65*dome);}"),
        Scene("Planet Atmosphere", "Space & atmosphere", 0xFF3C6F92.toInt(), 0xFFC3D6DF.toInt(), 0xFF06152A.toInt(), 0xFFE6F5FF.toInt(), 0.72f, 0f, "float4 surface(float2 p,float t) {float2 q=p-float2(0.0,.69);float r=length(q*float2(1.0,.87)),limb=1.0-smoothstep(.83,.90,r),atm=exp(-48.0*abs(r-.85)),bands=.5+.5*sin(p.y*8.0+1.5*sin(p.x*3.0+t*.04));return float4(.19+.38*limb+.07*bands,limb,.62*atm,.23+.6*bands);}"),
        Scene("Gravitational Halo", "Space & atmosphere", 0xFF3B3E60.toInt(), 0xFFC8B8E9.toInt(), 0xFF050618.toInt(), 0xFFEDE4FF.toInt(), 0.44f, 0f, "float4 surface(float2 p,float t) {float2 q=p+float2(.07,-.04);float r=length(q),a=atan(q.y,q.x),ellipse=length(q*float2(1.38,.77)),ring=.30+.015*sin(a*10.0+t*.13),bright=exp(-46.0*abs(ellipse-ring)),shadow=1.0-smoothstep(.13,.18,r);return float4(.24+.27*bright+.05*cos(a*4.0),.44*(1.0-shadow),.87*bright,.2+.6*bright);}"),
        Scene("Meteor Smoke", "Space & atmosphere", 0xFF5F7483.toInt(), 0xFFD4E1E8.toInt(), 0xFF081421.toInt(), 0xFFD6F9F4.toInt(), 0.94f, 0f, "float4 surface(float2 p,float t) {float2 q=p-float2(-.40+.10*sin(t*.09),.30-.10*cos(t*.09));float axis=q.x*.83+q.y*.55,cross=abs(q.y*.83-q.x*.55),flow=snoise(q*5.5+float2(-t*.08,t*.05)),trail=exp(-9.0*cross)*(1.0-smoothstep(-.1,.65,axis));return float4(.27+.29*flow+.17*trail,trail*.84,.48*trail*exp(-8.0*abs(axis+.32)),.25+.65*flow);}"),
        Scene("Illuminated Colonnade", "Architecture & sculpture", 0xFF919185.toInt(), 0xFFDDCDB7.toInt(), 0xFF202837.toInt(), 0xFFFDE7C2.toInt(), 0.54f, 0f, "float4 surface(float2 p,float t) {float pers=1.0/(1.0+max(0.0,p.y+.2)*2.2),columns=abs(sin((p.x+.02*sin(t*.04))*7.0*pers)),shaft=1.0-smoothstep(.55,.81,columns),floor=smoothstep(.21,.28,p.y);float shadow=exp(-9.0*abs(p.x))*smoothstep(.1,.7,p.y);return float4(.23+.48*shaft*(1.0-floor)+.12*floor,.95,.18*shadow,.23+.61*shaft);}"),
        Scene("Marble Archways", "Architecture & sculpture", 0xFFABABA3.toInt(), 0xFFEBE0D1.toInt(), 0xFF2A3544.toInt(), 0xFFFAE8DC.toInt(), 0.58f, 0f, "float4 surface(float2 p,float t) {float x=abs(p.x),arch=length(float2(x*.92,(p.y-.02)*1.1)),opening=1.0-smoothstep(.29,.35,arch),pillars=smoothstep(.35,.44,x)*(1.0-smoothstep(.53,.59,x)),lintel=1.0-smoothstep(-.22,-.12,p.y),stone=clamp(1.0-opening+pillars+lintel*.12,0.0,1.0),vein=snoise(p*float2(7.0,2.6)+float2(t*.008,0.0));return float4(.25+.42*stone+.08*vein,stone,.05*pow(max(stone,0.0),7.0),.22+.61*vein);}"),
        Scene("Sculpted Wall Panels", "Architecture & sculpture", 0xFF777E80.toInt(), 0xFFD6DDD9.toInt(), 0xFF101923.toInt(), 0xFFF4F8FA.toInt(), 0.66f, 0f, "float4 surface(float2 p,float t) {float rib=sin(p.x*6.0+.13*sin(p.y*4.0)),recess=smoothstep(-.22,.38,rib),shoulder=sin(p.y*2.8+t*.018),shadow=exp(-18.0*abs(rib-.20));return float4(.21+.42*recess+.08*shoulder,.98,.06*shadow,.18+.62*recess);}"),
        Scene("Suspended Basalt", "Architecture & sculpture", 0xFF393B43.toInt(), 0xFFABB1B4.toInt(), 0xFF161A2A.toInt(), 0xFFF0DDD3.toInt(), 0.82f, 0f, "float4 surface(float2 p,float t) {float2 q=p-float2(.05*sin(t*.11),.04*cos(t*.09)),rot=float2(q.x*.86+q.y*.5,-q.x*.5+q.y*.86),b=abs(rot)-float2(.31,.27);float d=length(max(b,float2(0.0)))+min(max(b.x,b.y),0.0),slab=1.0-smoothstep(0.0,.04,d),shadow=exp(-13.0*length(q-float2(.10,.33)));return float4(.18+.51*slab+.12*shadow,slab,.10*shadow,.27+.55*slab);}"),
        Scene("Silent Atrium", "Architecture & sculpture", 0xFF959288.toInt(), 0xFFDDD4C3.toInt(), 0xFF262936.toInt(), 0xFFFFEAD1.toInt(), 0.77f, 0f, "float4 surface(float2 p,float t) {float door=1.0-smoothstep(.30,.35,length(float2(p.x,(p.y+.15)*1.18))),pers=1.0/(1.0+max(-.25,p.y+.3)*1.5),wall=smoothstep(.38,.58,abs(p.x)*pers),floor=smoothstep(.08,.15,p.y),win=exp(-15.0*length(p-float2(.23,-.18)));return float4(.30+.31*wall+.16*floor-.15*door,.94,.21*win,.20+.58*wall);}"),
        Scene("Twisting Ribbon Canopy", "Architecture & sculpture", 0xFF737E7E.toInt(), 0xFFD6DEDB.toInt(), 0xFF192838.toInt(), 0xFFF5FCF9.toInt(), 0.36f, 0f, "float4 surface(float2 p,float t) {float twist=p.x+.21*sin(p.y*5.7+t*.18),r=sin(twist*9.4+1.9*sin(p.y*3.3)),width=.24+.07*sin(p.y*4.1-t*.16),edge=abs(twist-.20*sin(p.y*2.6+t*.12)),canopy=1.0-smoothstep(width-.04,width+.04,edge);return float4(.21+.35*(.5+.5*r)*canopy+.12*canopy,canopy,.12*pow(max(r,0.0),7.0),.27+.62*(.5+.5*r));}")
        )
        fun isSmooth(value: Int): Boolean = value in FIRST..LAST
    }

    private val shaderCache = object : LinkedHashMap<Int, RuntimeShader>(6, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, RuntimeShader>?): Boolean =
            size > 6
    }
    private val failed = mutableSetOf<Int>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private var gradient: LinearGradient? = null
    private var gradientColor = Int.MIN_VALUE
    private var gradientHeight = -1

    fun resetHardwareState() {
        shaderCache.clear()
        failed.clear()
    }

    fun draw(canvas: Canvas, state: MotionState, time: Float,
             touchX: Float, touchY: Float, offsetX: Float, pose: ParallaxPose) {
        val index = (state.preset - FIRST).coerceIn(0, COUNT - 1)
        val spec = SCENES[index]
        val w = canvas.width.coerceAtLeast(1).toFloat()
        val h = canvas.height.coerceAtLeast(1).toFloat()
        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated && index !in failed) {
            try {
                val shader = shaderCache[index] ?: RuntimeShader(PREFIX + spec.field + SUFFIX).also {
                    shaderCache[index] = it
                }
                shader.setFloatUniform("u_resolution", w, h)
                shader.setFloatUniform("u_time", time * (.33f + state.speed * .67f))
                shader.setFloatUniform("u_quality", state.shaderQuality.toFloat())
                shader.setFloatUniform("u_intensity", state.intensity.coerceIn(.35f, 2.3f))
                shader.setFloatUniform("u_depth", state.shadesDepth.coerceIn(.4f, 2f))
                shader.setFloatUniform("u_gloss", state.shadesGloss.coerceIn(.1f, 2f))
                shader.setFloatUniform("u_emission", state.shadesGlow.coerceIn(0f, 2f))
                shader.setFloatUniform("u_roughness", spec.roughness)
                shader.setFloatUniform("u_metallic", spec.metallic)
                fun uniformColor(name: String, value: Int) {
                    shader.setFloatUniform(name, Color.red(value) / 255f,
                        Color.green(value) / 255f, Color.blue(value) / 255f)
                }
                uniformColor("u_base", spec.base)
                uniformColor("u_second", spec.secondary)
                uniformColor("u_sky", spec.sky)
                uniformColor("u_light", spec.light)
                val tint = if (state.palette == 5) state.customColor
                    else HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0, 4)]
                uniformColor("u_tint", tint)
                shader.setFloatUniform("u_parallax", pose.x +
                    if (state.parallax) (offsetX - .5f) * .4f else 0f, pose.y)
                shader.setFloatUniform("u_touch",
                    if (state.touch) touchX.coerceIn(0f, 1f) else .5f,
                    if (state.touch) touchY.coerceIn(0f, 1f) else .5f)
                paint.shader = shader
                canvas.drawRect(0f, 0f, w, h, paint)
                return
            } catch (e: RuntimeException) {
                paint.shader = null
                shaderCache.remove(index)
                failed.add(index)
                Log.w("HALYN_SMOOTH", "Shader unavailable for " + spec.name, e)
            }
        }
        val height = canvas.height.coerceAtLeast(1)
        if (gradient == null || gradientColor != spec.base || gradientHeight != height) {
            gradient = LinearGradient(0f, 0f, 0f, height.toFloat(),
                intArrayOf(spec.sky, spec.base, spec.sky), null, Shader.TileMode.CLAMP)
            gradientColor = spec.base
            gradientHeight = height
        }
        fallbackPaint.shader = gradient
        canvas.drawRect(0f, 0f, w, height.toFloat(), fallbackPaint)
    }

    private val PREFIX = """
uniform float2 u_resolution;
uniform float3 u_base;
uniform float3 u_second;
uniform float3 u_sky;
uniform float3 u_light;
uniform float3 u_tint;
uniform float2 u_parallax;
uniform float2 u_touch;
uniform float u_time;
uniform float u_quality;
uniform float u_intensity;
uniform float u_depth;
uniform float u_gloss;
uniform float u_emission;
uniform float u_roughness;
uniform float u_metallic;

float hash21(float2 p) {
    return fract(sin(dot(p, float2(127.1,311.7))) * 43758.5453);
}
float snoise(float2 p) {
    float2 id=floor(p);
    float2 f=fract(p);
    f=f*f*(3.0-2.0*f);
    float a=mix(hash21(id),hash21(id+float2(1.0,0.0)),f.x);
    float b=mix(hash21(id+float2(0.0,1.0)),hash21(id+float2(1.0,1.0)),f.x);
    return mix(a,b,f.y);
}
float3 filmic(float3 c) {
    c=max(c,float3(0.0));
    return clamp((c*(2.51*c+float3(.03)))/
        (c*(2.43*c+float3(.59))+float3(.14)),float3(0.0),float3(1.0));
}
""".trimIndent()

    private val SUFFIX = """
half4 main(float2 frag) {
    float2 res=max(u_resolution,float2(1.0));
    float size=min(res.x,res.y);
    float2 p=(frag-res*.5)/size-u_parallax*float2(.06,.045);
    float t=u_time;
    float4 s=surface(p,t);
    float eps=max(1.0/size,.0028);
    if (u_quality < .5) eps*=1.65;
    float2 slope=float2(
        surface(p+float2(eps,0.0),t).x-s.x,
        surface(p+float2(0.0,eps),t).x-s.x)/eps;
    float3 normal=normalize(float3(-slope*u_depth*.28,1.0));
    float3 view=normalize(float3(-p.x*.23,-p.y*.23,1.55));
    float3 key=normalize(float3(-.51,-.67,1.0));
    float3 fill=normalize(float3(.65,.24,.95));
    float rough=clamp(u_roughness/max(.4,u_gloss),.11,.99);
    float diffuse=max(dot(normal,key),0.0);
    float bounce=max(dot(normal,fill),0.0);
    float spec=pow(max(dot(normal,normalize(key+view)),0.0),
        mix(12.0,90.0,1.0-rough));
    float other=pow(max(dot(normal,normalize(fill+view)),0.0),
        mix(8.0,60.0,1.0-rough));
    float fresnel=pow(1.0-clamp(dot(normal,view),0.0,1.0),5.0);
    float shadow=clamp(.58+normal.z*.42-length(slope)*.017,.34,1.0);
    float2 touch=(u_touch-.5)*res/size;
    float touchGlow=exp(-12.0*length(p-touch))*.035;
    float heightFade=smoothstep(-.72,.75,p.y);
    float atmosphere=exp(-3.0*length(p-float2(.14,-.18)));
    float3 bg=u_sky*(.65+.31*heightFade)+u_light*(.045+.082*atmosphere);
    float3 material=mix(u_base,u_second,clamp(s.w,0.0,1.0));
    float3 ambient=mix(u_light,u_tint,.09);
    float3 lit=material*(.27+diffuse*.64+bounce*.19)*shadow;
    lit+=ambient*(.11*diffuse+.043*bounce);
    float3 reflection=mix(u_light,u_second,.34)*spec*(.48+.72*u_metallic);
    reflection+=u_light*other*.13;
    lit+=reflection*(.65+.45*u_gloss);
    lit+=u_second*fresnel*(.16+.44*u_metallic);
    float3 col=mix(bg,lit,clamp(s.y,0.0,1.0));
    col+=(u_light*.72+u_second*.28)*
        (max(0.0,s.z)*u_emission+touchGlow)*u_intensity;
    if (u_quality > 1.5) {
        float mist=snoise(p*4.1+float2(t*.022,-t*.014));
        col+=u_second*pow(mist,3.0)*.03*(.4+.6*atmosphere);
    }
    col*=.79+.21*(1.0-smoothstep(.45,1.35,length(p)));
    col=pow(filmic(col*1.44*u_intensity),float3(.94));
    float d=fract(52.9829189*fract(frag.x*.06711056+frag.y*.00583715))-.5;
    col=clamp(col+float3(d/255.0),float3(0.0),float3(1.0));
    return half4(half3(col),1.0);
}
""".trimIndent()
}
