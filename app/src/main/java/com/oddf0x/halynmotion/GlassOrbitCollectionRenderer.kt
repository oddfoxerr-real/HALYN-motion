package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Ten original optics-first wallpapers inspired by HALYN's untouched Glass Orbit preset (7).
 *
 * Presets 145..154 are independent of Material X (137..144) and the smooth
 * scene collection (75..136), so existing saved wallpaper IDs remain stable.
 * RuntimeShader sources are intentionally short and compiled one at a time.
 * A glass-ring Canvas implementation recovers from unsupported GPU shaders.
 */
class GlassOrbitCollectionRenderer {
    data class Scene(val name: String, val description: String)

    companion object {
        const val FIRST = 145
        const val COUNT = 10
        const val LAST = FIRST + COUNT - 1

        val SCENES = listOf(
            Scene("Orbital Lens", "Tilt moves a double-layer optical lens across a floating glass rim."),
            Scene("Refractive Orbit", "A living glass thickness field changes lens curvature as it orbits."),
            Scene("Twin Glass Rings", "Two offset glass rings counter-orbit with separate gyroscopic highlights."),
            Scene("Caustic Ring", "Charging intensifies travelling caustic glints around a refractive rim."),
            Scene("Eclipse Lens", "A translucent glass eclipse passes across an illuminated inner lens."),
            Scene("Prism Orbit", "Two tilted transparent orbits refract against each other at different speeds."),
            Scene("Crystal Orbit", "Touch sweeps a glint over three differently tilted crystal rims."),
            Scene("Orbital Bloom", "A smooth inflated optical shell inhales and exhales around its central void."),
            Scene("Glass Nebula Ring", "A glass lens suspends drifting translucent wisps that shift with the time of day."),
            Scene("Luminous Orbit", "Active music playback brightens a travelling arc without listening to audio.")
        )

        fun isGlassOrbit(preset: Int): Boolean = preset in FIRST..LAST
        fun description(preset: Int): String? =
            if (isGlassOrbit(preset)) SCENES[preset - FIRST].description else null

        private const val EXPRESSION_MARKER = "/* GLASS_ORBIT_EXPRESSION */"
        private val EXPRESSIONS = arrayOf(
            // Orbital Lens
            """
                float2 focus = p - float2(.05*sin(t*.32)+u_gyro.x*.085, .04*cos(t*.29)+u_gyro.y*.08);
                float d = length(focus*float2(1.0,1.22));
                float ring = exp(-93.0*abs(d-.265-.028*sin(angle*2.0-t*.40)));
                float inner = exp(-78.0*abs(d-.185));
                float shine = pow(max(0.0,cos(angle*1.8+t*.64)),12.0);
                energy = ring*(.60+.78*shine)+inner*.35;
                glow = exp(-12.0*abs(d-.26))*.25;
                glass = exp(-3.5*d)*(.10+.10*sin(angle*2.0-t*.2));
            """.trimIndent(),
            // Refractive Orbit
            """
                float bend = .035*sin(angle*3.0-t*.44)+.022*sin(angle*2.0+t*.29);
                float d = length(p*float2(1.0,1.32));
                float core = exp(-110.0*abs(d-.315-bend));
                float secondary = exp(-76.0*abs(d-.245+.018*sin(t*.40)));
                float caustic = pow(max(0.0,sin(angle*2.7+t*.58)),14.0);
                energy = core*(.55+.73*caustic)+secondary*.44;
                glow = exp(-11.0*abs(d-.30))*.29;
                glass = exp(-9.0*abs(d-.30))*.18;
            """.trimIndent(),
            // Twin Glass Rings
            """
                float2 a = p-float2(.085*cos(t*.33),.045*sin(t*.33));
                float2 b = p+float2(.10*cos(t*.27+.9),.07*sin(t*.27+.9));
                float ar = length(a*float2(1.0,1.20));
                float br = length(b*float2(1.11,1.0));
                float front = .5+.5*sin(angle*2.0-t*.55);
                float ringA = exp(-94.0*abs(ar-.285));
                float ringB = exp(-98.0*abs(br-.305));
                energy = ringA*(.32+.86*front)+ringB*(1.15-.67*front);
                glow = exp(-12.0*abs(ar-.285))*.13+exp(-12.0*abs(br-.305))*.18;
                glass = exp(-5.0*(ar+br))*.17;
            """.trimIndent(),
            // Caustic Ring
            """
                float d = length(p*float2(1.0,1.26));
                float warped = d-.305-.014*sin(angle*5.0+t*.30);
                float rim = exp(-106.0*abs(warped));
                float caustic = pow(max(0.0,cos(angle*2.3-t*(.45+.23*u_charging))),16.0);
                float charge = .24+.65*u_charging;
                energy = rim*(.55+caustic*(.44+charge));
                glow = exp(-11.0*abs(warped))*(.19+.24*u_charging);
                glass = exp(-7.0*abs(d-.28))*.12;
            """.trimIndent(),
            // Eclipse Lens
            """
                float2 a = p-float2(.09*sin(t*.29),-.035);
                float2 b = p+float2(.115*cos(t*.24),.02);
                float da = length(a);
                float db = length(b);
                float outer = exp(-93.0*abs(da-.33));
                float front = exp(-94.0*abs(db-.238));
                float crossing = exp(-9.0*abs(da-db));
                float pulse = .24+.76*u_notification;
                energy = outer*.63+front*(.46+.54*crossing)+pulse*exp(-104.0*abs(r-(.18+.25*(1.0-u_notification))))*.49;
                glow = exp(-12.0*abs(da-.33))*.24+crossing*.10;
                glass = exp(-4.0*db)*.14;
            """.trimIndent(),
            // Prism Orbit
            """
                float2 a = float2(p.x*.86+p.y*.50,-p.x*.50+p.y*.86);
                float2 b = float2(p.x*.95-p.y*.31,p.x*.31+p.y*.95);
                float d1 = length(a*float2(1.0,1.38));
                float d2 = length(b*float2(1.27,1.0));
                float prism1 = exp(-101.0*abs(d1-.285-.019*sin(t*.40)));
                float prism2 = exp(-97.0*abs(d2-.32+.027*cos(t*.32)));
                float focus = pow(max(0.0,sin(angle*2.2+t*.56)),12.0);
                energy = prism1*(.49+.68*focus)+prism2*(.68-.2*focus);
                glow = (exp(-11.0*abs(d1-.285))+exp(-11.0*abs(d2-.32)))*.17;
                glass = exp(-5.0*r)*.13;
            """.trimIndent(),
            // Crystal Orbit
            """
                float2 q=p;
                float d1=length(q*float2(1.0,1.32));
                float d2=length(float2(q.x*.89+q.y*.46,q.y*.89-q.x*.46)*float2(1.0,1.45));
                float d3=length(float2(q.x*.95-q.y*.30,q.y*.95+q.x*.30)*float2(1.0,1.18));
                float c1=exp(-107.0*abs(d1-.29));
                float c2=exp(-97.0*abs(d2-.31));
                float c3=exp(-112.0*abs(d3-.34));
                float touchHighlight=exp(-7.0*length(q-u_touch))*(.10+.95*u_touchForce);
                energy=c1*.75+c2*.42+c3*.36+touchHighlight*.44;
                glow=(exp(-13.0*abs(d1-.29))+exp(-12.0*abs(d3-.34)))*.14;
                glass=touchHighlight*.17;
            """.trimIndent(),
            // Orbital Bloom
            """
                float2 q = p-float2(.025*cos(t*.33),.023*sin(t*.28));
                float a=atan(q.y,q.x);
                float radius=.29+.048*sin(t*.63)+.025*cos(a*3.0-t*.23);
                float d=length(q);
                float outer=exp(-90.0*abs(d-radius));
                float inner=exp(-102.0*abs(d-(radius-.085)));
                float breath=.68+.32*sin(t*.63);
                energy=outer*(.65+.38*breath)+inner*.40;
                glow=exp(-11.0*abs(d-radius))*(.20+.10*breath);
                glass=exp(-3.8*d)*.16;
            """.trimIndent(),
            // Glass Nebula Ring
            """
                float2 q=p;
                float d=length(q*float2(1.0,1.18));
                float mist=sin(q.x*8.0+t*.19+sin(q.y*6.0-t*.11))*cos(q.y*8.0-t*.15);
                float orbit=.315+.03*mist;
                float rim=exp(-90.0*abs(d-orbit));
                float night=.55+.45*cos((u_dayphase-.50)*6.2831853);
                float cloud=pow(max(0.0,mist*.5+.5),3.0)*exp(-4.5*abs(d-.28));
                energy=rim*(.50+.25*night)+cloud*.34;
                glow=exp(-9.0*abs(d-orbit))*(.12+.22*night)+cloud*.13;
                glass=exp(-4.0*d)*(.07+.06*night);
            """.trimIndent(),
            // Luminous Orbit
            """
                float d=length(p*float2(1.0,1.22));
                float ring=exp(-109.0*abs(d-.285));
                float outer=exp(-82.0*abs(d-.365));
                float wave=.5+.5*sin(angle*2.1-t*(.48+.30*u_music));
                float specular=pow(max(0.0,wave),12.0);
                energy=ring*(.48+.81*specular+u_music*.30)+outer*(.30+.35*u_music);
                glow=exp(-11.0*abs(d-.285))*(.16+.25*u_music);
                glass=exp(-7.0*abs(d-.30))*.13;
            """.trimIndent()
        )

        private val SOURCE = """
uniform float2 u_resolution;
uniform float u_time;
uniform float u_speed;
uniform float u_intensity;
uniform float u_palette;
uniform float3 u_customColor;
uniform float u_variant;
uniform float u_offset;
uniform float2 u_gyro;
uniform float2 u_touch;
uniform float u_quadEnabled;
uniform float3 u_quadOne;
uniform float3 u_quadTwo;
uniform float3 u_quadThree;
uniform float3 u_quadFour;
uniform float u_charging;
uniform float u_notification;
uniform float u_touchForce;
uniform float u_dayphase;
uniform float u_music;

float3 toneMap(float3 x) {
    x = max(x, float3(0.0));
    return clamp((x*(2.51*x+float3(.03))) /
        (x*(2.43*x+float3(.59))+float3(.14)), float3(0.0), float3(1.0));
}

half4 main(float2 frag) {
    float2 uv = frag/u_resolution;
    float aspect = u_resolution.x/u_resolution.y;
    float2 p = float2((uv.x-.5)*aspect,uv.y-.5);
    p -= u_gyro*float2(.045,.040);
    p.x -= (u_offset-.5)*.08;
    float r = length(p);
    float angle = atan(p.y,p.x);
    float t = u_time*(.38+.58*u_speed);
    float3 tint = float3(.38,.78,1.0);
    if (u_palette>.5 && u_palette<1.5) tint=float3(.73,.45,1.0);
    else if (u_palette>1.5 && u_palette<2.5) tint=float3(1.0,.57,.35);
    else if (u_palette>2.5 && u_palette<3.5) tint=float3(1.0,.48,.69);
    else if (u_palette>3.5 && u_palette<4.5) tint=float3(.40,1.0,.76);
    else if (u_palette>4.5) tint=u_customColor;

    float3 quad = mix(
        mix(u_quadOne,u_quadTwo,clamp(uv.x,0.0,1.0)),
        mix(u_quadThree,u_quadFour,clamp(uv.x,0.0,1.0)),
        clamp(uv.y,0.0,1.0)
    );
    if (u_quadEnabled>.5) tint=quad;
    float3 partner=mix(tint,tint.zxy,.37);
    float3 pearl=mix(tint,float3(.985,.99,1.0),.72);

    float energy=0.0;
    float glow=0.0;
    float glass=0.0;
    /* GLASS_ORBIT_EXPRESSION */

    float atmosphere=exp(-3.3*r);
    float vignette=1.0-smoothstep(.30,.94,r);
    float3 background=float3(.004,.007,.017)+
        partner*(.020+.024*atmosphere)+tint*.009*vignette;
    float signal=max(0.0,energy)*u_intensity;
    float scatter=max(0.0,glow)*u_intensity;
    float3 col=background+
        mix(partner,tint,clamp(signal*.52+.24,0.0,1.0))*(signal*.68+scatter*.25);
    col+=pearl*(signal*signal/(1.0+signal*signal))*.72;
    col+=tint*(scatter*.23+glass*.32);
    col+=pearl*glass*.11;
    if (u_quadEnabled>.5) col+=quad*.017*atmosphere;
    col=toneMap(col*1.26);
    col=pow(col,float3(.92));
    return half4(half3(col),1.0);
}
""".trimIndent()
    }

    private val shaderCache = object : LinkedHashMap<Int, RuntimeShader>(8, .75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Int, RuntimeShader>?
        ): Boolean = size > 8
    }
    private val invalidScenes = mutableSetOf<Int>()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val ellipsePath = Path()

    fun resetHardwareState() {
        shaderCache.clear()
        invalidScenes.clear()
    }

    fun draw(
        canvas: Canvas, state: MotionState, time: Float,
        touchX: Float = .5f, touchY: Float = .5f,
        offsetX: Float = .5f, gyro: ParallaxPose = ParallaxPose(),
        touchForce: Float = 0f, reactions: SceneSignals = SceneSignals()
    ) {
        val index = state.preset - FIRST
        if (index !in 0 until COUNT) return
        val width = canvas.width.coerceAtLeast(1).toFloat()
        val height = canvas.height.coerceAtLeast(1).toFloat()
        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated &&
            index !in invalidScenes) {
            try {
                val shader = shaderCache[index] ?: RuntimeShader(
                    SOURCE.replace(EXPRESSION_MARKER, EXPRESSIONS[index])
                ).also { shaderCache[index] = it }
                shader.setFloatUniform("u_resolution", width, height)
                shader.setFloatUniform("u_time", time)
                shader.setFloatUniform("u_speed", state.speed)
                shader.setFloatUniform("u_intensity", state.intensity)
                shader.setFloatUniform("u_palette", state.palette.toFloat())
                shader.setFloatUniform("u_customColor",
                    Color.red(state.customColor)/255f,
                    Color.green(state.customColor)/255f,
                    Color.blue(state.customColor)/255f)
                shader.setFloatUniform("u_variant", index.toFloat())
                shader.setFloatUniform("u_offset", if (state.parallax) offsetX else .5f)
                shader.setFloatUniform("u_gyro", gyro.x, gyro.y)
                shader.setFloatUniform("u_touch",
                    if (state.touch) touchX else .5f,
                    if (state.touch) touchY else .5f)
                QuadColorSystem.setShaderUniforms(shader, state)
                when (index) {
                    3 -> shader.setFloatUniform("u_charging", reactions.charging)
                    4 -> shader.setFloatUniform("u_notification", reactions.notification)
                    6 -> shader.setFloatUniform("u_touchForce", touchForce)
                    8 -> shader.setFloatUniform("u_dayphase", reactions.dayPhase)
                    9 -> shader.setFloatUniform("u_music", reactions.music)
                }
                fillPaint.shader = shader
                fillPaint.alpha = 255
                canvas.drawRect(0f, 0f, width, height, fillPaint)
                fillPaint.shader = null
                return
            } catch (e: RuntimeException) {
                fillPaint.shader = null
                shaderCache.remove(index)
                invalidScenes.add(index)
                Log.e("HALYN_GLASS_ORBIT",
                    "Scene failed: " + SCENES[index].name + ". Using glass-ring fallback.", e)
            }
        }
        drawFallback(canvas, state, time, gyro, reactions, index, width, height)
    }

    private fun drawFallback(
        canvas: Canvas, state: MotionState, seconds: Float,
        gyro: ParallaxPose, reactions: SceneSignals, index: Int, w: Float, h: Float
    ) {
        val selected = if (state.fourColorsEnabled) QuadColorSystem.colors(state)
            else intArrayOf(
                Color.rgb(40, 104, 149), Color.rgb(75, 175, 225),
                Color.rgb(136, 219, 248), Color.rgb(218, 241, 255)
            )
        fillPaint.shader = null
        fillPaint.color = Color.rgb(3, 7, 18)
        canvas.drawColor(Color.rgb(3, 7, 18))
        val m = min(w, h)
        val t = seconds*(.40f + state.speed*.55f)
        val x = w*.5f + gyro.x*w*.045f + sin(t*.23f+index)*m*.025f
        val y = h*.5f + gyro.y*h*.040f
        for (ring in 0..2) {
            val rr = m*(.27f+ring*.105f+.019f*sin(t*(.40f+ring*.11f)+index))
            val a = 1f+(index%3)*.10f
            val b = .88f+(index%4)*.055f
            ellipsePath.reset()
            ellipsePath.addOval(
                x-rr*a, y-rr*b, x+rr*a, y+rr*b, Path.Direction.CW
            )
            val color = selected[(index+ring)%selected.size]
            for (pass in 0..2) {
                ringPaint.shader = null
                ringPaint.color = color
                ringPaint.alpha = when (pass) { 0 -> 25; 1 -> 72; else -> 220 }
                ringPaint.strokeWidth = m*
                    when (pass) { 0 -> .055f; 1 -> .023f; else -> .005f }
                canvas.drawPath(ellipsePath, ringPaint)
            }
        }
        if (index == 9 && reactions.music > .5f) {
            ringPaint.color = selected[3]
            ringPaint.alpha = 180
            ringPaint.strokeWidth = m*.012f
            canvas.drawCircle(x, y, m*(.20f+.04f*sin(t)), ringPaint)
        }
        ringPaint.alpha = 255
        QuadColorSystem.drawOverlay(canvas, state, .12f)
    }
}
