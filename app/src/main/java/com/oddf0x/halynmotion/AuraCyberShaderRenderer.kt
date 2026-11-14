package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log

/**
 * GPU compositor replacing the flat v0.25.0 Canvas artwork.
 *
 * Each wallpaper (156..167) has its own short AGSL material program:
 * curved volumetric energy, optical interference, restrained network
 * surfaces, flowing refractive fields, and real direction-aligned hits.
 *
 * The old AuraCyberRenderer is retained ONLY as a recovery renderer if
 * hardware acceleration or an individual GPU shader is unavailable.
 *
 * All shader sources are generated from independent expressions at compile
 * time and cached lazily; no bitmap textures, AI-generated images or model.
 */
class AuraCyberShaderRenderer {
    companion object {
        private const val PLACEHOLDER = "/* AURA_CYBER_SCENE */"
        private val DEFAULT_TINT = arrayOf(
            floatArrayOf(.30f,.62f,1.0f),
            floatArrayOf(.56f,.43f,1.0f),
            floatArrayOf(.33f,.74f,.94f),
            floatArrayOf(.52f,.38f,.96f),
            floatArrayOf(.27f,.84f,.94f),
            floatArrayOf(.40f,.66f,1.0f),
            floatArrayOf(.32f,.77f,.72f),
            floatArrayOf(.26f,.72f,.80f),
            floatArrayOf(.37f,.56f,.95f),
            floatArrayOf(.32f,.81f,.75f),
            floatArrayOf(.49f,.55f,.88f),
            floatArrayOf(.29f,.67f,.77f)
        )
        private val EXPRESSIONS = arrayOf(
            // 156: Aura Strike
            """
                float2 q = p - float2(.0, .02);
                float a = n2(q * float2(6.0, 4.5) + float2(t*.22,-t*.31));
                float b = n2(q * float2(12.0,7.0)+float2(-t*.14,-t*.49));
                float deformation = (a-.5)*.14 + .036*sin(q.y*15.0-t*.95);
                float plume = exp(-pow((q.x+deformation)/(.17+.06*b),2.0));
                float rise = 1.0-smoothstep(-.43,.41,q.y);
                float petals=pow(max(0.0,sin(q.x*29.0 - q.y*8.0 +a*5.0 -t*1.3)),4.0);
                core=plume*(.32+.48*a)*rise + exp(-23.0*length(q-float2(0.0,.16)))*.38;
                filament=plume*petals*(.11+.31*b)*rise;
                fog=exp(-pow(q.x/.36,2.0)-pow(q.y/.42,2.0))*(.32+.4*a);
                spark=pow(max(0.0, n2(floor((q+float2(0.0,t*.032))*180.0))-.93),5.0)*150000.0*plume*rise;
            """.trimIndent(),
            // 157: Nova Clash
            """
                float2 q=p;
                float left = exp(-pow((q.x+.22+.045*sin(q.y*10.0+t*.8))/.24,2.0)-pow((q.y+.03)/.40,2.0));
                float right = exp(-pow((q.x-.23+.045*cos(q.y*9.0-t*.74))/.23,2.0)-pow((q.y-.04)/.41,2.0));
                float seam = q.x-.035*sin(q.y*10.0+t*.65)-.022*sin(t*.4);
                float turbulence=n2(q*10.0+float2(t*.19,-t*.31));
                core=(left+right)*(.45+.26*turbulence);
                filament=exp(-90.0*abs(seam))*(.35+.43*turbulence)*(1.0-smoothstep(.15,.48,abs(q.y)));
                fog=(left+right)*(.19+.19*sin(q.y*20.0-t*.8)*sin(q.x*15.0+t*.27));
                spark=exp(-25.0*abs(seam))*pow(max(0.0,sin(q.y*64.0-t*3.0)),10.0)*.18;
            """.trimIndent(),
            // 158: Impact Veil
            """
                float2 q=p;
                float fold=q.y+q.x*.56+.058*sin(q.x*11.0-t*.67);
                float layer1=exp(-28.0*abs(fold+.20+.044*sin(t*.61)));
                float layer2=exp(-31.0*abs(fold-.07+.048*sin(t*.45+q.x*3.0)));
                float layer3=exp(-32.0*abs(fold-.27-.025*cos(t*.75)));
                float sweep=pow(max(0.0,sin(fold*23.0-t*.81)),7.0);
                core=layer1*.21+layer2*.32+layer3*.22;
                filament=layer1*.52+layer2*.62+layer3*.48;
                fog=(layer1+layer2+layer3)*.18+exp(-4.5*length(q))*.11;
                spark=sweep*(layer1+layer2+layer3)*.14;
            """.trimIndent(),
            // 159: Void Rush
            """
                float2 q=p;
                q.x+=.042*sin(t*.34)+u_gyro.x*.052;
                float ang=atan(q.y,q.x);
                float d=length(q*float2(1.0,1.17));
                float depth=1.0/(d+.12);
                float concentric=abs(sin(depth*4.5-t*1.1+ang*.26));
                float band=pow(max(0.0,1.0-concentric),10.0);
                float shaft=pow(max(0.0,.5+.5*cos(ang*7.0+t*.41)),14.0);
                float envelope=1.0-smoothstep(.12,.86,d);
                core=band*.20*envelope;
                filament=band*.51*envelope+shaft*(.10+.31*d)*envelope;
                fog=exp(-6.0*d)*.29+exp(-11.0*abs(d-.24))* .13;
                spark=band*shaft*.15;
            """.trimIndent(),
            // 160: Energy Break
            """
                float2 q=p;
                float warp = .085*sin(q.x*9.0+t*.71)+.038*sin(q.x*20.0-t*.51);
                float membrane=q.y-warp;
                float ripples=sin(q.x*17.0+membrane*7.0-t*.66);
                float sheet=exp(-5.0*abs(membrane))*(.55+.45*n2(q*6.0+float2(-t*.17,t*.14)));
                float edge=exp(-62.0*abs(membrane+.045*sin(q.x*13.0+t)));
                core=sheet*.19;
                filament=edge*(.52+.25*max(0.0,ripples));
                fog=sheet*.19+exp(-3.0*length(q))*.09;
                spark=pow(max(0.0,ripples),12.0)*edge*.21;
            """.trimIndent(),
            // 161: Pulse Slash
            """
                float2 q=p;
                float filamentA=abs(q.x-.18*sin(q.y*5.0-t*.64)-.08);
                float filamentB=abs(q.x+.20*sin(q.y*4.1+t*.53)+.12);
                float filamentC=abs(q.x-.12*cos(q.y*7.4+t*.80)+.22);
                float fade=1.0-smoothstep(.27,.57,abs(q.y));
                float a=exp(-90.0*filamentA);
                float b=exp(-94.0*filamentB);
                float c=exp(-105.0*filamentC);
                core=(a+b+c)*.11*fade;
                filament=(a*.62+b*.46+c*.41)*fade;
                fog=exp(-4.0*length(q))*.18;
                spark=pow(max(0.0,sin(q.y*55.0-t*1.7)),15.0)*(a+b+c)*.17;
            """.trimIndent(),
            // 162: Cipher Veil
            """
                float2 q=p;
                float2 sheet1=abs(q-float2(-.035,-.08))-float2(.22,.23);
                float2 sheet2=abs(q-float2(.045,.11))-float2(.19,.19);
                float sd1=length(max(sheet1,float2(0.0)))+min(max(sheet1.x,sheet1.y),0.0)-.026;
                float sd2=length(max(sheet2,float2(0.0)))+min(max(sheet2.x,sheet2.y),0.0)-.022;
                float border=exp(-165.0*abs(sd1))+exp(-160.0*abs(sd2));
                float path=abs(sin(q.y*47.0+sin(q.x*8.0)*.4));
                float trace=pow(max(0.0,1.0-path),20.0)*exp(-pow(q.x/.30,4.0));
                float sweep=exp(-58.0*abs(q.y+.50-.90*fract(t*.065)));
                core=border*.13;
                filament=border*.23+trace*.11+sweep*.09;
                fog=(1.0-smoothstep(-.24,.05,sd1))*.045+
                    (1.0-smoothstep(-.19,.06,sd2))*.031;
                spark=pow(max(0.0,n2(floor(q*44.0+float2(t*.27,0.0)))-.95),4.0)*9000.0*.06;
            """.trimIndent(),
            // 163: Ghost Trace
            """
                float2 q=p;
                float2 grid=q*float2(11.0,13.0);
                float2 cell=floor(grid);
                float2 local=fract(grid)-.5;
                float present=step(.37,n2(cell));
                float routeV=exp(-130.0*abs(local.x+.12*sin(cell.y*1.8)));
                float routeH=exp(-105.0*abs(local.y-.11*sin(cell.x*2.2)));
                float node=exp(-58.0*dot(local,local));
                float travel=fract(t*.13+n2(cell)*.77);
                float packet=exp(-130.0*abs(local.y-travel+.5))*routeV;
                core=0.0;
                filament=(routeV*.095+routeH*.060+node*.23)*present;
                fog=exp(-3.8*length(q))*.023;
                spark=packet*.36*present;
            """.trimIndent(),
            // 164: Vector Breach
            """
                float2 q=p;
                float shifted=q.x*.77+q.y*.53;
                float seam = abs(fract(shifted*6.0+.25*sin(t*.24))-.5);
                float slit=exp(-140.0*seam);
                float2 slab=abs(q-float2(.05*sin(t*.29),0.0))-float2(.26,.34);
                float panel=length(max(slab,float2(0.0)))+min(max(slab.x,slab.y),0.0);
                float edge=exp(-115.0*abs(panel));
                float charge=u_signal.y;
                float interference=pow(max(0.0,sin(q.y*40.0+t*.58)),14.0);
                core=edge*.055;
                filament=slit*(.10+.09*charge)+edge*.14;
                fog=(1.0-smoothstep(-.27,.10,panel))*.032;
                spark=slit*interference*(.07+.13*charge);
            """.trimIndent(),
            // 165: Packet Drift
            """
                float2 q=p;
                float touchDist=length(q-pointer);
                float deflect=u_pointerDown*.05*exp(-30.0*touchDist*touchDist);
                float curved=q.y +.048*sin(q.x*8.0 +t*.32)-deflect;
                float channels=curved*15.0;
                float band=exp(-160.0*abs(fract(channels)-.5));
                float moving=fract(q.x*4.5-t*(.24+.16*u_signal.x));
                float packet=exp(-70.0*abs(moving-.5))*band;
                core=0.0;
                filament=band*.09;
                fog=exp(-3.1*length(q))*.029;
                spark=packet*(.28+.17*u_signal.x);
            """.trimIndent(),
            // 166: Zero Grid
            """
                float2 q=p;
                float2 g=abs(fract(q*float2(18.0,26.0))-.5);
                float thin=1.0-smoothstep(.008,.042,min(g.x,g.y));
                float sweep=exp(-75.0*abs(q.y+.45-.90*fract(t*.065)));
                float activation=exp(-24.0*length(q-pointer))*(.13+.28*u_pointerDown);
                float2 tile=floor(q*float2(18.0,26.0));
                float chosen=step(.94,n2(tile+floor(t*.32)));
                core=0.0;
                filament=thin*(.032+.056*sweep)+activation*.20;
                fog=exp(-4.6*length(q))*.018;
                spark=chosen*thin*.09;
            """.trimIndent(),
            // 167: Signal Intrusion
            """
                float2 q=p;
                float row=floor(q.y*115.0);
                float wobble=.015*sin(row*.71+t*.33);
                float2 r=q+float2(wobble,0.0);
                float thin=exp(-95.0*abs(fract(r.y*49.0)-.5));
                float window=smoothstep(-.33,-.15,r.x)*(1.0-smoothstep(.16,.40,r.x));
                float weak=sin(row*2.4 +t*.19)*.5+.5;
                float band=exp(-66.0*abs(q.y+.47-.94*fract(t*.11)));
                float notification=u_signal.z;
                core=0.0;
                filament=thin*(.046+.086*weak)*window+band*.14;
                fog=exp(-3.5*length(q))*.023;
                spark=thin*band*(.06+.18*notification);
            """.trimIndent()
        )

        private val SOURCE = """
uniform float2 u_resolution;
uniform float u_time;
uniform float u_speed;
uniform float u_intensity;
uniform float u_quality;
uniform float2 u_gyro;
uniform float u_offset;
uniform float u_palette;
uniform float3 u_customColor;
uniform float u_quadEnabled;
uniform float3 u_quadOne;
uniform float3 u_quadTwo;
uniform float3 u_quadThree;
uniform float3 u_quadFour;
uniform float3 u_defaultTint;
uniform float3 u_signal;
uniform float2 u_pointer;
uniform float u_pointerDown;
uniform float u_burstAge;
uniform float u_burstPower;
uniform float2 u_burstCenter;
uniform float2 u_burstDirection;

float hash21(float2 p) {
    return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453);
}
float n2(float2 p) {
    float2 i=floor(p);
    float2 f=fract(p);
    f=f*f*(3.0-2.0*f);
    float n00=hash21(i);
    float n10=hash21(i+float2(1.0,0.0));
    float n01=hash21(i+float2(0.0,1.0));
    float n11=hash21(i+float2(1.0,1.0));
    return mix(mix(n00,n10,f.x),mix(n01,n11,f.x),f.y);
}
float3 filmic(float3 x) {
    x=max(x,float3(0.0));
    return clamp((x*(2.51*x+float3(.03)))/
        (x*(2.43*x+float3(.59))+float3(.14)),float3(0.0),float3(1.0));
}
half4 main(float2 frag) {
    float2 uv=frag/u_resolution;
    float aspect=u_resolution.x/u_resolution.y;
    float2 p=float2((uv.x-.5)*aspect,uv.y-.5);
    p-=u_gyro*float2(.034,.026);
    p.x-=(u_offset-.5)*.065;
    float2 pointer=float2((u_pointer.x-.5)*aspect,u_pointer.y-.5);
    float t=u_time*(.40+.64*u_speed);
    float r=length(p);
    float3 accent=u_defaultTint;
    if(u_palette>.5 && u_palette<1.5) accent=float3(.72,.44,1.0);
    else if(u_palette>1.5 && u_palette<2.5) accent=float3(1.0,.56,.29);
    else if(u_palette>2.5 && u_palette<3.5) accent=float3(1.0,.38,.66);
    else if(u_palette>3.5 && u_palette<4.5) accent=float3(.38,.95,.66);
    else if(u_palette>4.5) accent=u_customColor;

    float3 quad=mix(
        mix(u_quadOne,u_quadTwo,clamp(uv.x,0.0,1.0)),
        mix(u_quadThree,u_quadFour,clamp(uv.x,0.0,1.0)),
        clamp(uv.y,0.0,1.0));
    if(u_quadEnabled>.5) accent=quad;
    float3 secondary=mix(accent,accent.zxy,.46);
    float3 highlight=mix(accent,float3(.975,.987,1.0),.77);
    float core=0.0;
    float filament=0.0;
    float fog=0.0;
    float spark=0.0;
    /* AURA_CYBER_SCENE */

    float strength=u_intensity;
    float background=exp(-3.5*r);
    float3 col=float3(.0015,.003,.009)+
        secondary*(.022+.016*background);
    col+=secondary*fog*.45*strength;
    col+=accent*core*.57*strength;
    col+=highlight*(filament*.68+spark*.55)*strength;
    float ambientSignal=(u_signal.x*.016+u_signal.y*.014+u_signal.z*.029);
    col+=secondary*ambientSignal*background;
    float nearTouch=exp(-21.0*length(p-pointer));
    col+=accent*nearTouch*u_pointerDown*.026;
    if(u_quadEnabled>.5) col+=quad*.008*background;
    if(u_quality>1.5) {
        float film=n2(p*5.0+float2(t*.04,-t*.025));
        col+=secondary*(film-.5)*.014*background;
    }

    float active=(1.0-smoothstep(.03,1.0,u_burstAge))*u_burstPower;
    float2 b=float2((u_burstCenter.x-.5)*aspect,u_burstCenter.y-.5);
    float2 delta=p-b;
    float2 direction=u_burstDirection;
    float along=dot(delta,direction);
    float across=dot(delta,float2(-direction.y,direction.x));
    float ringRadius=.045+u_burstAge*.43;
    float impactRing=exp(-104.0*abs(length(delta)-ringRadius));
    float lane=fract(across*34.0+.08*sin(along*30.0))-.5;
    float inkStreak=exp(-175.0*lane*lane)*
        smoothstep(-.46,-.11,along)*
        (1.0-smoothstep(.07,.58,along));
    float oval=exp(-6.0*length(delta));
    float strengthBurst=active*strength;
    /* BATTLE_OR_CYBER_BURST */
    col=filmic(col*1.25);
    col=pow(col,float3(.92));
    // Stable sub-LSB dithering prevents static banding on AMOLED panels.
    float dither=fract(52.9829189*fract(frag.x*.06711056+frag.y*.00583715))-.5;
    col=clamp(col+float3(dither/255.0),float3(0.0),float3(1.0));
    return half4(half3(col),1.0);
}
""".trimIndent()
    }

    private val cache = object : LinkedHashMap<Int, RuntimeShader>(8,.75f,true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Int,RuntimeShader>?
        ): Boolean = size>8
    }
    private val failed = mutableSetOf<Int>()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun resetHardwareState() {
        cache.clear()
        failed.clear()
    }

    fun draw(
        canvas: Canvas, state: MotionState, seconds: Float,
        gyro: ParallaxPose, signals: SceneSignals, offsetX: Float,
        impact: AuraCyberRenderer.Impact
    ): Boolean {
        val index=state.preset-AuraCyberRenderer.FIRST
        if (index !in 0 until AuraCyberRenderer.COUNT) return false
        if (Build.VERSION.SDK_INT<33 || !canvas.isHardwareAccelerated ||
            index in failed) return false
        try {
            val shader=cache[index] ?: RuntimeShader(
                SOURCE.replace(PLACEHOLDER, EXPRESSIONS[index])
                    .replace("/* BATTLE_OR_CYBER_BURST */",
                        if(index<6) {
                            """
    col+=accent*(inkStreak*.50+impactRing*.28)*strengthBurst;
    col+=highlight*(inkStreak*.74+impactRing*.20+
        oval*.14*(1.0-u_burstAge))*strengthBurst;
                            """.trimIndent()
                        } else {
                            """
    col+=secondary*(impactRing*.17+inkStreak*.10)*strengthBurst;
    col+=highlight*(impactRing*.21+inkStreak*.18)*strengthBurst;
                            """.trimIndent()
                        })
            ).also {cache[index]=it}
            val width=canvas.width.coerceAtLeast(1).toFloat()
            val height=canvas.height.coerceAtLeast(1).toFloat()
            shader.setFloatUniform("u_resolution",width,height)
            shader.setFloatUniform("u_time",seconds)
            shader.setFloatUniform("u_speed",state.speed)
            shader.setFloatUniform("u_intensity",state.intensity)
            shader.setFloatUniform("u_quality",state.shaderQuality.toFloat())
            shader.setFloatUniform("u_gyro",gyro.x,gyro.y)
            shader.setFloatUniform("u_offset",if(state.parallax) offsetX else .5f)
            shader.setFloatUniform("u_palette",state.palette.toFloat())
            shader.setFloatUniform("u_customColor",
                Color.red(state.customColor)/255f,
                Color.green(state.customColor)/255f,
                Color.blue(state.customColor)/255f)
            shader.setFloatUniform("u_defaultTint",
                DEFAULT_TINT[index][0],DEFAULT_TINT[index][1],DEFAULT_TINT[index][2])
            shader.setFloatUniform("u_signal",
                signals.music,signals.charging,signals.notification)
            shader.setFloatUniform("u_pointer",impact.pointerX,impact.pointerY)
            shader.setFloatUniform("u_pointerDown",if(impact.pointerDown)1f else 0f)
            shader.setFloatUniform("u_burstAge",impact.age)
            shader.setFloatUniform("u_burstPower",impact.power)
            shader.setFloatUniform("u_burstCenter",impact.x,impact.y)
            shader.setFloatUniform("u_burstDirection",impact.directionX,impact.directionY)
            QuadColorSystem.setShaderUniforms(shader,state)
            paint.shader=shader
            paint.color=Color.WHITE
            paint.alpha=255
            canvas.drawRect(0f,0f,width,height,paint)
            paint.shader=null
            return true
        } catch(e: RuntimeException) {
            paint.shader=null
            cache.remove(index)
            failed.add(index)
            Log.e("HALYN_GPU", "Aura/Cyber shader failed: "+
                AuraCyberRenderer.SCENES[index].name+"; reverting to fallback",e)
            return false
        }
    }
}
