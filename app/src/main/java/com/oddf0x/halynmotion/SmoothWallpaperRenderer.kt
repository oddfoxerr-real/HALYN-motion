package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log

/** Thirty new distinct abstract motion shaders alongside all 28 HALYN originals. */
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
        Scene("Ripple Cadence", "Fluid dynamics", 0xFF2B769A.toInt(), 0xFF8FD6E1.toInt(), 0xFF071A32.toInt(), 0xFFD5FBFF.toInt(), 0.32f, 0f, "float4 surface(float2 p,float t) {\nfloat2 a=p-float2(-.22,.10*sin(t*.13)),b=p-float2(.28,-.14*cos(t*.09));\nfloat r1=length(a),r2=length(b);\nfloat phase=sin(22.0*r1-t*.82)+.63*cos(17.0*r2+t*.43);\nfloat rings=exp(-12.0*abs(sin(3.2*r1+1.7*r2-t*.09)));\nreturn float4(.47+.20*phase,.83-.2*smoothstep(.5,.95,r1),.36*rings,.5+.31*phase);\n}"),
        Scene("Helix Undertow", "Fluid dynamics", 0xFF326A81.toInt(), 0xFFB6E8E8.toInt(), 0xFF071727.toInt(), 0xFFE4FFFF.toInt(), 0.26f, 1f, "float4 surface(float2 p,float t) {\nfloat2 q=p; q.x+=.11*sin(q.y*3.0-t*.17);\nfloat left=exp(-18.0*abs(q.x-.23*sin(q.y*7.0-t*.53)));\nfloat right=exp(-18.0*abs(q.x+.23*sin(q.y*7.0-t*.53)));\nfloat crossing=sin(q.y*15.0-t*.74);\nfloat braid=left*(.5+.5*crossing)+right*(.5-.5*crossing);\nreturn float4(.26+.34*braid+.12*sin(q.y*8.0-t*.3),.28+.68*braid,.70*pow(braid,2.0),.2+.7*braid);\n}"),
        Scene("Viscous Portal", "Fluid dynamics", 0xFF273C83.toInt(), 0xFF9699E8.toInt(), 0xFF080E2B.toInt(), 0xFFD4D6FF.toInt(), 0.36f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p+.07*float2(sin(p.y*6.0+t*.24),cos(p.x*5.0-t*.16));\nfloat radius=length(q),angle=atan(q.y,q.x);\nfloat vortex=sin(29.0*radius-5.0*angle-t*.72);\nfloat well=exp(-4.7*radius);\nfloat edge=exp(-19.0*abs(radius-.33-.04*sin(angle*7.0-t*.13)));\nreturn float4(.45+.21*vortex*well+.18*edge,.25+.72*well,.68*edge+.13*well,.18+.68*well);\n}"),
        Scene("Mirror Interference", "Fluid dynamics", 0xFF465A88.toInt(), 0xFFAFD5F5.toInt(), 0xFF08182D.toInt(), 0xFFE8F7FF.toInt(), 0.18f, 1f, "float4 surface(float2 p,float t) {\nfloat2 a=p-float2(.27,.21),b=p+float2(.23,-.19);\nfloat wave=sin(24.0*length(a)-t*.44)*cos(22.0*length(b)+t*.30);\nfloat interference=pow(max(0.0,abs(wave)),5.0);\nfloat horizon=exp(-3.7*length(p));\nreturn float4(.43+.33*wave,.42+.53*horizon,.55*interference*horizon,.23+.6*wave*.5+.23);\n}"),
        Scene("Pressure Lines", "Fluid dynamics", 0xFF31556A.toInt(), 0xFF8DC0CB.toInt(), 0xFF0A1A27.toInt(), 0xFFD6F5F3.toInt(), 0.43f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p+float2(.17*sin(p.y*3.3-t*.2),.08*sin(p.x*6.0+t*.15));\nfloat flow=sin(q.x*18.0+3.1*sin(q.y*5.5-t*.35));\nfloat smoothPressure=cos(q.y*4.1-q.x*5.0+t*.16);\nfloat ridge=exp(-8.0*abs(flow-.14));\nreturn float4(.47+.24*flow+.08*smoothPressure,.97,.31*ridge,.23+.59*(.5+.5*smoothPressure));\n}"),
        Scene("Current Fold", "Fluid dynamics", 0xFF214C5C.toInt(), 0xFF82BDC0.toInt(), 0xFF061623.toInt(), 0xFFD3EDE8.toInt(), 0.64f, 0f, "float4 surface(float2 p,float t) {\nfloat fold=p.x+.26*sin(p.y*3.4-t*.31);\nfloat sheet=sin(9.0*fold+1.5*sin(p.y*6.2+t*.21));\nfloat crease=exp(-13.0*abs(sheet));\nfloat depth=smoothstep(-.6,.44,p.y);\nreturn float4(.37+.32*sheet+.09*cos(p.y*7.0),.36+.58*depth,.31*crease*depth,.17+.64*(.5+.5*sheet));\n}"),
        Scene("Glass Caustics", "Optical structures", 0xFF5576A0.toInt(), 0xFFDBF4FF.toInt(), 0xFF0D1E36.toInt(), 0xFFF1FFFF.toInt(), 0.15f, 0f, "float4 surface(float2 p,float t) {\nfloat2 a=p+float2(.13*sin(t*.17),-.08*cos(t*.11));\nfloat lens=sin(length(a*float2(1.2,.83))*21.0-t*.57);\nfloat refract=sin(a.x*17.0+2.0*sin(a.y*5.0-t*.35));\nfloat hotspot=pow(max(0.0,lens*refract),4.0);\nreturn float4(.48+.21*lens+.17*refract,.51+.43*exp(-1.9*length(a)),.91*hotspot,.2+.64*refract*.5+.22);\n}"),
        Scene("Refractive Helix", "Optical structures", 0xFF52658A.toInt(), 0xFFD1C5F2.toInt(), 0xFF0C1730.toInt(), 0xFFF7EFFF.toInt(), 0.19f, 0f, "float4 surface(float2 p,float t) {\nfloat angle=atan(p.y,p.x),radius=length(p);\nfloat spiral=sin(7.0*angle+25.0*radius-t*.44);\nfloat refr=cos(4.0*angle-18.0*radius+t*.23);\nfloat band=exp(-9.0*abs(spiral-refr*.45));\nreturn float4(.44+.27*spiral+.08*refr,.34+.6*exp(-1.2*radius),.55*band,.28+.61*(.5+.5*refr));\n}"),
        Scene("Prism Diffusion", "Optical structures", 0xFF46738F.toInt(), 0xFFD5F6F9.toInt(), 0xFF081D2C.toInt(), 0xFFF8FFFF.toInt(), 0.28f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=float2(p.x*.83+p.y*.55,-p.x*.55+p.y*.83);\nfloat stripe=sin(q.x*8.0+2.5*cos(q.y*4.4+t*.19));\nfloat split=cos(q.y*12.0+1.7*sin(q.x*3.0-t*.33));\nfloat facet=pow(max(0.0,.5+.5*stripe*split),3.0);\nreturn float4(.33+.27*stripe+.19*split,.90,.38*facet,.2+.67*(.5+.5*stripe));\n}"),
        Scene("Phase Lens", "Optical structures", 0xFF5A6497.toInt(), 0xFFD4CEF9.toInt(), 0xFF0B1332.toInt(), 0xFFF2E8FF.toInt(), 0.13f, 1f, "float4 surface(float2 p,float t) {\nfloat2 q=p-float2(.07*sin(t*.11),-.08*cos(t*.1));\nfloat radius=length(q*float2(.8,1.26));\nfloat core=1.0-smoothstep(.19,.36,radius);\nfloat ring=exp(-21.0*abs(radius-.39-.03*sin(8.0*atan(q.y,q.x)+t*.18)));\nfloat h=.21+.37*core+.22*ring+.07*sin(25.0*radius-t*.39);\nreturn float4(h,.26+.68*core+.38*ring,.64*ring,.25+.64*core);\n}"),
        Scene("Lumen Chamber", "Optical structures", 0xFF667F93.toInt(), 0xFFC8EBED.toInt(), 0xFF081A28.toInt(), 0xFFE6FFFF.toInt(), 0.24f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p+float2(.06*sin(t*.10),.03*cos(t*.12));\nfloat aperture=length(q*float2(1.17,.80));\nfloat wav=sin(34.0*aperture-t*.43+sin(atan(q.y,q.x)*5.0));\nfloat shell=exp(-14.0*abs(aperture-.33));\nfloat inner=1.0-smoothstep(.13,.39,aperture);\nreturn float4(.32+.22*wav+.25*shell,.23+.69*inner+.3*shell,.4*shell+.23*pow(max(wav,0.0),5.0),.25+.68*inner);\n}"),
        Scene("Chroma Shell", "Optical structures", 0xFF42637E.toInt(), 0xFFD3DDEB.toInt(), 0xFF0B1727.toInt(), 0xFFE8FAFF.toInt(), 0.32f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p;\nfloat bubble1=length(q-float2(-.18+.02*sin(t*.1),-.12));\nfloat bubble2=length(q-float2(.21,.18+.02*cos(t*.09)));\nfloat a=1.0-smoothstep(.18,.34,bubble1),b=1.0-smoothstep(.20,.37,bubble2);\nfloat meniscus=exp(-21.0*abs(bubble1-.3))+exp(-18.0*abs(bubble2-.33));\nreturn float4(.19+.35*a+.29*b+.1*meniscus,.22+.72*clamp(a+b,0.0,1.0),.43*meniscus,.2+.62*b);\n}"),
        Scene("Accretion Band", "Space dynamics", 0xFF36567A.toInt(), 0xFFF3B89F.toInt(), 0xFF070E25.toInt(), 0xFFFFE6C9.toInt(), 0.7f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p-float2(.03,-.05);\nfloat radius=length(q*float2(.95,2.3));\nfloat angle=atan(q.y,q.x);\nfloat band=exp(-14.0*abs(radius-.34-.028*sin(angle*5.0+t*.13)));\nfloat disk=.5+.5*sin(angle*9.0-t*.30);\nreturn float4(.24+.33*band+.07*disk,.18+.70*band,.8*band*(.28+.72*disk),.22+.68*disk);\n}"),
        Scene("Void Echo", "Space dynamics", 0xFF31325E.toInt(), 0xFFB3A3E3.toInt(), 0xFF040819.toInt(), 0xFFE5D7FF.toInt(), 0.78f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p+float2(.04,.07);\nfloat radius=length(q);\nfloat dark=1.0-smoothstep(.13,.22,radius);\nfloat echo=exp(-15.0*abs(radius-.40-.022*sin(atan(q.y,q.x)*8.0+t*.09)));\nfloat ring2=exp(-23.0*abs(radius-.53));\nreturn float4(.31+.28*echo+.13*ring2-.1*dark,.21+.61*(echo+ring2),.45*echo+.22*ring2,.16+.61*(.5+.5*cos(radius*9.0)));\n}"),
        Scene("Distant Bloom", "Space dynamics", 0xFF485B89.toInt(), 0xFFA2D3D1.toInt(), 0xFF050E22.toInt(), 0xFFD7F5E6.toInt(), 0.94f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p+float2(.09*sin(t*.025),-.04*cos(t*.021));\nfloat density=snoise(q*3.1+float2(t*.034,-t*.026));\nfloat turbulence=snoise(q*6.7+float2(-t*.019,t*.014));\nfloat cloud=pow(max(0.0,density*.7+turbulence*.3),2.0);\nfloat fade=exp(-2.5*length(q));\nreturn float4(.31+.21*density+.12*turbulence,.22+.58*cloud*fade,.35*pow(cloud,3.0)*fade,.17+.64*cloud);\n}"),
        Scene("Event Ribbons", "Space dynamics", 0xFF355E86.toInt(), 0xFFAFC6EF.toInt(), 0xFF071227.toInt(), 0xFFE8F5FF.toInt(), 0.57f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p+float2(.1*sin(p.y*4.3-t*.15),.05*sin(p.x*3.2+t*.11));\nfloat r=length(q),angle=atan(q.y,q.x);\nfloat curve=sin(3.5*angle+12.0*r-t*.38);\nfloat opposing=sin(-4.5*angle+16.0*r+t*.29);\nfloat ribbon=exp(-14.0*abs(curve))+exp(-16.0*abs(opposing));\nreturn float4(.4+.20*curve+.13*opposing,.24+.55*exp(-1.8*r),.46*ribbon*exp(-1.1*r),.25+.65*(.5+.5*curve));\n}"),
        Scene("Parallax Nebula", "Space dynamics", 0xFF2F6A7F.toInt(), 0xFF89C6CE.toInt(), 0xFF07192D.toInt(), 0xFFBDE6F2.toInt(), 0.92f, 0f, "float4 surface(float2 p,float t) {\nfloat2 layer1=p*2.7+float2(t*.025,-t*.018);\nfloat2 layer2=p*4.2+float2(-t*.016,t*.029);\nfloat n1=snoise(layer1),n2=snoise(layer2);\nfloat w=sin(p.x*4.3+2.0*n1-t*.12);\nfloat density=pow(max(0.0,n1*.67+n2*.33),2.0);\nreturn float4(.4+.21*n1+.10*w,.31+.58*density,.21*density*pow(max(w,0.0),4.0),.17+.69*n1);\n}"),
        Scene("Cosmic Aperture", "Space dynamics", 0xFF3D487F.toInt(), 0xFFC0A8DE.toInt(), 0xFF070B26.toInt(), 0xFFEFD9FF.toInt(), 0.4f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p;\nfloat angle=atan(q.y,q.x),radius=length(q);\nfloat iris=cos(9.0*angle+5.0*sin(radius*4.0-t*.20));\nfloat gate=.33+.055*iris;\nfloat opening=1.0-smoothstep(gate-.03,gate+.05,radius);\nfloat edge=exp(-18.0*abs(radius-gate));\nreturn float4(.37+.27*opening+.19*edge,.21+.69*opening+.35*edge,.59*edge,.25+.59*iris*.5+.30);\n}"),
        Scene("Ion Threads", "Energy & motion", 0xFF29748D.toInt(), 0xFF91E2F1.toInt(), 0xFF061527.toInt(), 0xFFDDFAFF.toInt(), 0.45f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p;\nfloat path1=q.x-.23*sin(q.y*9.0-t*.45+.4*sin(q.y*16.0));\nfloat path2=q.x+.26*cos(q.y*7.0+t*.36);\nfloat line1=exp(-33.0*abs(path1)),line2=exp(-26.0*abs(path2));\nfloat step=sin(q.y*23.0+t*.69);\nreturn float4(.31+.23*line1+.22*line2+.08*step,.19+.8*(line1+line2),.76*line1+.45*line2,.23+.57*step*.5+.3);\n}"),
        Scene("Plasma Convergence", "Energy & motion", 0xFF4C5BA8.toInt(), 0xFFCDD0FF.toInt(), 0xFF060F29.toInt(), 0xFFF2EDFF.toInt(), 0.31f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p+.04*float2(sin(t*.16),cos(t*.13));\nfloat angle=atan(q.y,q.x),radius=length(q);\nfloat beams=pow(max(0.0,cos(angle*7.0+radius*9.0-t*.30)),13.0);\nfloat center=exp(-10.0*radius);\nreturn float4(.31+.30*center+.13*beams,.29+.67*center,.75*beams*center+.35*center,.24+.64*center);\n}"),
        Scene("Flux Cascade", "Energy & motion", 0xFF215E71.toInt(), 0xFF87C8E3.toInt(), 0xFF08192C.toInt(), 0xFFC5F4FF.toInt(), 0.52f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p+float2(.11*sin(p.y*3.8-t*.10),0.0);\nfloat fall=sin(20.0*q.x+2.9*sin(q.y*5.0+t*.38));\nfloat drift=sin(6.3*q.y-t*.54);\nfloat light=exp(-17.0*abs(fall-.12*drift));\nreturn float4(.48+.24*fall+.06*drift,.9,.38*light,.23+.67*(.5+.5*drift));\n}"),
        Scene("Electric Lacing", "Energy & motion", 0xFF4A6188.toInt(), 0xFFB4DFF0.toInt(), 0xFF09172A.toInt(), 0xFFE2FDFF.toInt(), 0.41f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=float2(p.x*.85+p.y*.54,-p.x*.54+p.y*.85);\nfloat braids=sin(q.x*12.0+sin(q.y*6.0-t*.28));\nfloat knots=sin(q.y*15.0+cos(q.x*8.0+t*.23));\nfloat lace=exp(-12.0*abs(braids*knots));\nreturn float4(.42+.20*braids+.16*knots,.72,.47*lace,.23+.64*(.5+.5*braids));\n}"),
        Scene("Arc Reservoir", "Energy & motion", 0xFF315C95.toInt(), 0xFFA7DDF7.toInt(), 0xFF071129.toInt(), 0xFFF1FAFF.toInt(), 0.31f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p-float2(0.0,.13);\nfloat r=length(q*float2(.77,1.24)),a=atan(q.y,q.x);\nfloat arch=exp(-14.0*abs(r-.36-.015*sin(a*9.0-t*.22)));\nfloat spark=sin(a*17.0+t*.59)*cos(r*24.0-t*.24);\nreturn float4(.36+.23*arch+.11*spark,.26+.65*arch,.7*arch*(.5+.5*spark),.2+.64*(.5+.5*spark));\n}"),
        Scene("Magnetic Pulse", "Energy & motion", 0xFF476A91.toInt(), 0xFFB5D6EB.toInt(), 0xFF0B1930.toInt(), 0xFFDCF5FF.toInt(), 0.57f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p+float2(.03*sin(t*.1),-.03*cos(t*.09));\nfloat radius=length(q);\nfloat pulse=sin(24.0*radius-1.8*t+1.6*sin(atan(q.y,q.x)*3.0));\nfloat waves=pow(max(0.0,pulse),5.0);\nfloat limit=1.0-smoothstep(.60,.91,radius);\nreturn float4(.4+.28*pulse,.3+.62*limit,.44*waves*limit,.2+.65*limit);\n}"),
        Scene("Gyroid Passage", "Geometric motion", 0xFF5265A3.toInt(), 0xFFB0BBD9.toInt(), 0xFF0B142D.toInt(), 0xFFEFF0FF.toInt(), 0.34f, 1f, "float4 surface(float2 p,float t) {\nfloat x=p.x*8.0+t*.13,y=p.y*8.0-t*.16;\nfloat gy=sin(x)*cos(y)+sin(y)*cos(x*.65)+sin(x+y)*.26;\nfloat channel=exp(-6.0*abs(gy));\nreturn float4(.4+.26*gy,.88,.37*channel,.26+.56*(.5+.5*gy));\n}"),
        Scene("Elastic Membrane", "Geometric motion", 0xFF3D7686.toInt(), 0xFFB8E8E2.toInt(), 0xFF081A28.toInt(), 0xFFE8FBF6.toInt(), 0.24f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p-float2(.08*sin(t*.16),.06*cos(t*.14));\nfloat stretch=sin(q.x*9.0+3.1*q.y*q.y-t*.32);\nfloat rebound=cos(q.y*7.0+2.6*q.x*q.x+t*.18);\nfloat h=.46+.24*stretch*.7+.18*rebound*.7;\nfloat strain=exp(-14.0*abs(stretch-rebound));\nreturn float4(h,.96,.26*strain,.25+.63*(.5+.5*stretch));\n}"),
        Scene("Sonic Contours", "Geometric motion", 0xFF4A5384.toInt(), 0xFFC7C6EE.toInt(), 0xFF081128.toInt(), 0xFFEEE9FF.toInt(), 0.62f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p*float2(1.3,.85);\nfloat fundamental=sin(q.x*16.0-t*.54)*sin(q.y*5.0+t*.13);\nfloat harmonic=cos(q.x*7.0+t*.23)*cos(q.y*14.0-t*.36);\nfloat node=pow(abs(fundamental+harmonic)*.5,5.0);\nreturn float4(.38+.17*fundamental+.20*harmonic,.88,.39*node,.23+.66*(.5+.5*harmonic));\n}"),
        Scene("Folded Continuum", "Geometric motion", 0xFF335F7D.toInt(), 0xFFA7D7DC.toInt(), 0xFF09192B.toInt(), 0xFFDEFAFA.toInt(), 0.43f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p;\nfloat folding=sin(q.x*7.0+2.3*sin(q.y*3.4+t*.15));\nfloat second=cos(q.y*10.0+1.1*sin(q.x*4.3-t*.32));\nfloat crease=exp(-9.0*abs(folding+second*.52));\nreturn float4(.37+.27*folding+.12*second,.9,.43*crease,.22+.64*(.5+.5*folding));\n}"),
        Scene("Morphing Torus", "Geometric motion", 0xFF426F95.toInt(), 0xFFA7D7F3.toInt(), 0xFF0A1630.toInt(), 0xFFE4F7FF.toInt(), 0.25f, 1f, "float4 surface(float2 p,float t) {\nfloat2 q=p+float2(.06*sin(t*.13),.05*cos(t*.10));\nfloat r=length(q*float2(1.16,.85)),a=atan(q.y,q.x);\nfloat boundary=.34+.05*sin(a*4.0-t*.27)+.025*cos(a*9.0+t*.16);\nfloat ring=exp(-13.0*abs(r-boundary));\nfloat inner=exp(-8.0*r);\nreturn float4(.25+.34*ring+.12*sin(a*3.0+t*.10),.21+.65*ring,.43*ring+.13*inner,.24+.68*(.5+.5*cos(a*4.0)));\n}"),
        Scene("Infinite Weave", "Geometric motion", 0xFF375F89.toInt(), 0xFF9EC4F0.toInt(), 0xFF07132C.toInt(), 0xFFDDE7FF.toInt(), 0.36f, 0f, "float4 surface(float2 p,float t) {\nfloat2 q=p*float2(.82,1.23);\nfloat a=atan(q.y,q.x),r=length(q);\nfloat lem=sin(2.0*a)*r-.20*sin(t*.12);\nfloat weave=sin(22.0*lem+3.0*sin(r*5.0-t*.23));\nfloat local=exp(-5.0*abs(lem));\nreturn float4(.39+.24*weave*local,.25+.68*local,.49*pow(max(0.0,weave),7.0)*local,.22+.66*local);\n}")
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
