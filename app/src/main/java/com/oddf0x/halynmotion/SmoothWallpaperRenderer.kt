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

        /**
         * A unique motion field per scene. These are shader mechanisms,
         * not palette variants: every effect displaces, phases and emits
         * light through a different equation and interaction response.
         */
        data class Mechanic(
            val name: String,
            val explanation: String,
            val materialModel: Int,
            val playbackRate: Float,
            val code: String
        )
        val MECHANICS = listOf(
        Mechanic("Ripple Cadence", "Two travelling wavefronts form and cancel moving interference rings.", 0, 0.88f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float2 a=p-float2(-.20,.12),b=p-float2(.26,-.16);\nfloat ra=max(length(a),.001),rb=max(length(b),.001);\nfloat pulse=sin(22.0*ra-t*1.7)+.7*sin(19.0*rb+t*1.2);\nfloat2 dir=a/ra+b/rb;\nfloat impulse=force*exp(-12.0*length(p-tap))*sin(t*7.0);\nreturn float4(dir*.014*pulse+dir*.04*impulse,sin(ra*9.0-rb*4.0)*.12,.21*abs(pulse)+.22*abs(impulse)); }"),
        Mechanic("Helix Undertow", "Counter-rotating strands braid, shear and exchange their depth.", 0, 0.7f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float y=p.y*6.4-t*.48;\nfloat strand=sin(y),twist=cos(y);\nfloat2 displacement=float2(.11*strand*cos(t*.12),.045*twist);\nfloat crossing=sin(y*2.0+t*.17);\nfloat attract=force*exp(-15.0*length(p-tap));\nreturn float4(displacement+float2(-attract*.05,attract*.08),crossing*.33,.23*pow(max(0.0,crossing),4.0)); }"),
        Mechanic("Viscous Portal", "An inward vortex advects thick ribbons toward its moving core.", 0, 0.61f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float2 q=p+float2(.02*sin(t*.11),-.01*cos(t*.09));\nfloat radius=max(length(q),.03);\nfloat angular=t*.15+1.3/(.28+radius);\nfloat2 rotational=float2(-q.y,q.x)*(.12/(.3+radius))*sin(angular);\nfloat suction=-q*(.11/(.5+radius));\nfloat touch=force*exp(-8.0*length(p-tap));\nreturn float4(rotational+suction+touch*float2(.08,-.03),angular*.14,.12/(1.0+radius)); }"),
        Mechanic("Mirror Interference", "Opposing waves reflect across a central mirror and phase-cancel.", 0, 1.07f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float edge=abs(p.x);\nfloat arriving=sin(19.0*edge-t*.73+p.y*5.0);\nfloat departing=sin(19.0*edge+t*.73-p.y*5.0);\nfloat standing=arriving+departing;\nfloat signX=p.x/sqrt(p.x*p.x+.0004);\nfloat touch=force*exp(-16.0*length(p-tap));\nreturn float4(signX*.028*standing+touch*.025,.025*(arriving-departing),sin(standing)*.18,.18*abs(standing)); }"),
        Mechanic("Pressure Lines", "Two shear layers deform streamlines without a flat texture overlay.", 0, 0.64f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float2 a=p+float2(.17*sin(p.y*3.0+t*.14),0.0);\nfloat curl=sin(a.y*7.0-t*.4)*cos(a.x*4.0);\nfloat2 field=float2(.08*curl,-.045*sin(a.x*7.0+t*.3));\nfloat push=force*exp(-9.0*length(p-tap));\nreturn float4(field+push*(p-tap)*.11,curl*.21,.16*abs(curl)); }"),
        Mechanic("Current Fold", "A continuous sheet folds over itself with a travelling crease.", 0, 0.75f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float folded=sin(p.y*3.8-t*.32);\nfloat flap=sin(p.x*8.2+folded*2.6+t*.13);\nfloat fold=exp(-12.0*abs(flap));\nfloat touch=force*exp(-12.0*length(p-tap));\nreturn float4(.09*folded+.055*fold,touch*.1+.025*flap,fold*.3,.24*fold); }"),
        Mechanic("Glass Caustics", "Moving curved lenses focus refracted light into drifting caustics.", 1, 0.68f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float2 wave=float2(sin(p.y*7.0+t*.32),cos(p.x*6.0-t*.27));\nfloat focus=sin(p.x*14.0+wave.x*1.7)*cos(p.y*13.0+wave.y*1.4);\nfloat taplens=force*exp(-13.0*length(p-tap));\nreturn float4(.045*wave+.04*taplens*(p-tap),focus*.11,.40*pow(max(focus,0.0),5.0)+.1*taplens); }"),
        Mechanic("Refractive Helix", "A rotating optical helix bends the background through its depth.", 1, 0.83f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float radius=max(length(p),.05),a=atan(p.y,p.x);\nfloat phase=a*4.0+radius*18.0-t*.58;\nfloat twist=sin(phase);\nfloat2 tangent=float2(-p.y,p.x)/radius;\nfloat proximity=force*exp(-9.0*length(p-tap));\nreturn float4(tangent*.075*twist+proximity*.025,.18*cos(phase),.22*pow(abs(twist),4.0)); }"),
        Mechanic("Prism Diffusion", "Spectral bands separate and recombine as a prism slowly rotates.", 1, 0.58f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float angle=t*.1;\nfloat2 axis=float2(cos(angle),sin(angle));\nfloat projection=dot(p,axis);\nfloat dispersion=sin(projection*19.0+t*.38);\nfloat split=sin((p.x*axis.y-p.y*axis.x)*13.0-t*.24);\nfloat forceLens=force*exp(-17.0*length(p-tap));\nreturn float4(axis*.065*dispersion+float2(-axis.y,axis.x)*.03*split,\n    forceLens*.13,.3*pow(max(0.0,dispersion*split),3.0)); }"),
        Mechanic("Phase Lens", "A breathing lens alternates focus and defocus with a soft meniscus.", 1, 0.66f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float2 q=p-float2(.03*sin(t*.12),.03*cos(t*.11));\nfloat radius=max(length(q),.03),breath=sin(t*.44);\nfloat focus=(.045+.035*breath)/(radius+.2);\nfloat disturb=force*exp(-10.0*length(p-tap));\nreturn float4(-q*focus+disturb*q*.10,.12*breath,.34*exp(-22.0*abs(radius-.35-.03*breath))); }"),
        Mechanic("Lumen Chamber", "An iris-like chamber opens in timed cycles and reflects its rim.", 1, 0.53f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float2 q=p;\nfloat a=atan(q.y,q.x),r=length(q);\nfloat shutter=.5+.5*sin(t*.35);\nfloat blade=sin(a*8.0+t*.12);\nfloat opening=.26+.09*shutter+.025*blade;\nfloat rim=exp(-17.0*abs(r-opening));\nreturn float4(-q*.055*(1.0-shutter)+force*(p-tap)*.03,.24*(1.0-shutter),.32*rim); }"),
        Mechanic("Chroma Shell", "Two floating refractive bubbles orbit, approach and separate.", 1, 0.72f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float2 a=float2(-.19+.10*sin(t*.21),.08*cos(t*.23));\nfloat2 b=float2(.20+.11*cos(t*.18),.11*sin(t*.19));\nfloat da=length(p-a),db=length(p-b);\nfloat influence=exp(-10.0*da)+exp(-10.0*db);\nfloat2 bend=float2(sin(da*15.0),cos(db*14.0));\nreturn float4(.045*bend*influence+force*(p-tap)*.07,.16*(da-db),.30*influence); }"),
        Mechanic("Accretion Band", "Inner orbital layers rotate faster than outer material.", 2, 0.58f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float2 q=p;\nfloat radius=max(length(q),.10),a=atan(q.y,q.x);\nfloat orbit=t*.40/(.38+radius*1.5);\nfloat2 tang=float2(-q.y,q.x)*(.09/(radius+.24));\nfloat hotspot=exp(-11.0*abs(radius-.4));\nreturn float4(tang*sin(orbit+a),orbit*.22+force*.09,.22*hotspot*(.5+.5*sin(a*5.0-orbit))); }"),
        Mechanic("Void Echo", "Concentric shock fronts expand, fade and repeat into darkness.", 2, 0.81f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float r=length(p),wave=sin(17.0*r-t*1.1);\nfloat envelope=exp(-1.9*r);\nfloat2 radial=p/sqrt(r*r+.002);\nfloat touchRing=force*exp(-20.0*abs(length(p-tap)-.23));\nreturn float4(radial*.045*wave*envelope,wave*.16,.32*pow(max(wave,0.0),6.0)*envelope+.2*touchRing); }"),
        Mechanic("Distant Bloom", "A volumetric-looking cloud blooms through two independently evolving fields.", 2, 0.44f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float a=snoise(p*3.4+float2(t*.025,-t*.019));\nfloat b=snoise(p*6.3+float2(-t*.021,t*.014));\nfloat grow=.5+.5*sin(t*.25);\nfloat2 drift=float2(a-.5,b-.5)*.085*grow;\nfloat halo=exp(-2.0*length(p))*a*b;\nreturn float4(drift,grow*.23,.22*pow(max(halo,0.0),2.0)+force*.1*exp(-10.0*length(p-tap))); }"),
        Mechanic("Event Ribbons", "Ribbons bend around a gravitational focus with different trajectories.", 2, 0.73f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float2 q=p-float2(.02,-.07);\nfloat r=max(length(q),.08),a=atan(q.y,q.x);\nfloat2 tangent=float2(-q.y,q.x)/r;\nfloat bend=.06/(.12+r*r);\nfloat spiral=sin(6.0*a+13.0*r-t*.42);\nfloat response=force*exp(-13.0*length(p-tap));\nreturn float4(tangent*bend*spiral+q*.02*response,spiral*.17,.19*exp(-5.0*r)*abs(spiral)); }"),
        Mechanic("Parallax Nebula", "Three cloud depths drift independently, with gyro-driven perspective.", 2, 0.57f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float n1=snoise(p*2.6+float2(t*.018,-t*.024));\nfloat n2=snoise(p*4.2+float2(-t*.023,t*.015));\nfloat n3=snoise(p*7.6+float2(t*.011,t*.008));\nfloat2 offset=float2(n1-n2,n3-n1)*.11;\nfloat swirl=n2*.4+n3*.6;\nreturn float4(offset,swirl*.21,.20*pow(max(n1*n2,0.0),2.0)+force*.06); }"),
        Mechanic("Cosmic Aperture", "A rotating nine-bladed aperture contracts and reveals deep layers.", 2, 0.62f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float r=length(p),a=atan(p.y,p.x);\nfloat petals=sin(a*9.0+t*.23);\nfloat shutter=.5+.5*sin(t*.34);\nfloat size=.27+.09*shutter+.05*petals;\nfloat edge=exp(-24.0*abs(r-size));\nreturn float4(p*.045*(1.0-shutter),petals*.18,.28*edge+.05*force); }"),
        Mechanic("Ion Threads", "Charged filaments branch and flicker at changing junctions.", 3, 1.18f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float a=sin(p.y*15.0-t*.83),b=cos(p.x*9.0+t*.61);\nfloat fil=sin(p.x*20.0+2.0*a+b);\nfloat branch=exp(-20.0*abs(fil));\nfloat snap=.5+.5*sin(t*12.0+p.y*7.0);\nreturn float4(.034*a,.031*b,fil*.14,.42*branch*snap+.30*force*exp(-8.0*length(p-tap))); }"),
        Mechanic("Plasma Convergence", "Seven plasma streams focus energy into a pulsing central core.", 3, 0.88f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float r=max(length(p),.04),a=atan(p.y,p.x);\nfloat rays=pow(max(0.0,cos(7.0*a-r*6.0+t*.36)),12.0);\nfloat converge=.5+.5*cos(t*.78);\nfloat2 inward=-p*(.085/(r+.4))*converge;\nreturn float4(inward,rays*.1,.39*rays*exp(-3.0*r)+.30*converge*exp(-8.0*r)); }"),
        Mechanic("Flux Cascade", "Charged current drops through a descending cascade of vortex eddies.", 3, 0.79f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float2 q=p+float2(t*.013,-t*.081);\nfloat vortex=snoise(q*float2(3.0,6.8));\nfloat2 fall=float2(.055*sin(q.y*8.0+vortex*3.0),-.09+.04*cos(q.x*7.0));\nfloat pulse=.5+.5*sin(t*2.5+p.y*17.0);\nreturn float4(fall,.15*vortex,.25*pulse*pow(vortex,3.0)+.12*force); }"),
        Mechanic("Electric Lacing", "A woven arc network transfers light between crossings in sequences.", 3, 0.94f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float2 q=p*float2(10.0,11.0);\nfloat node=sin(q.x+t*.55)*sin(q.y-t*.41);\nfloat conduction=.5+.5*sin((q.x+q.y)*.7-t*1.1);\nfloat crossing=pow(max(0.0,node),5.0);\nfloat2 weave=float2(.025*sin(q.y+t*.2),.025*cos(q.x-t*.16));\nreturn float4(weave,node*.16,.42*crossing*conduction+.10*force); }"),
        Mechanic("Arc Reservoir", "A resonant arc ring charges, discharges and recovers cyclically.", 3, 0.65f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float r=length(p*float2(.84,1.17)),a=atan(p.y,p.x);\nfloat charge=pow(.5+.5*sin(t*1.35),2.0);\nfloat ring=exp(-25.0*abs(r-.33));\nfloat filament=sin(a*16.0-t*.39);\nreturn float4(p*.018*(1.0-charge),.2*charge,.52*ring*charge*(.5+.5*filament)+.2*force*ring); }"),
        Mechanic("Magnetic Pulse", "Moving magnetic field lines compress and release in successive waves.", 3, 0.87f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float r=length(p),angle=atan(p.y,p.x);\nfloat polarity=cos(angle*4.0+t*.19);\nfloat compression=sin(27.0*r-t*1.25);\nfloat2 radial=p/sqrt(r*r+.001);\nfloat touch=force*exp(-17.0*length(p-tap));\nreturn float4(radial*.05*compression*polarity+touch*.03,compression*.12,.27*pow(max(0.0,compression),6.0)); }"),
        Mechanic("Gyroid Passage", "A three-axis gyroid lattice appears to rotate through a tunnel.", 4, 0.62f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float2 q=p;\nfloat angle=t*.19;\nfloat2 rotate=float2(q.x*cos(angle)-q.y*sin(angle),q.x*sin(angle)+q.y*cos(angle));\nfloat lattice=sin(rotate.x*8.0)*cos(rotate.y*8.0);\nfloat tunnel=exp(-1.5*length(q));\nreturn float4((rotate-q)*.33,lattice*.15,.23*tunnel*pow(max(lattice,0.0),5.0)); }"),
        Mechanic("Elastic Membrane", "A tensioned membrane propagates damped impact waves from touch.", 4, 0.73f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float r=length(p-tap);\nfloat phase=r*25.0-t*3.0;\nfloat ripple=force*exp(-r*3.1)*sin(phase);\nfloat2 radial=(p-tap)/sqrt(r*r+.003);\nfloat oscillation=sin(p.x*7.0+t*.49)*cos(p.y*5.0-t*.22);\nreturn float4(radial*.12*ripple,.14*oscillation+.21*ripple,.24*abs(ripple)); }"),
        Mechanic("Sonic Contours", "Intersecting standing-wave harmonics create wandering nodal contours.", 4, 0.97f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float modeA=sin(p.x*12.0)*sin(t*.78+p.y*5.0);\nfloat modeB=cos(p.y*16.0)*cos(t*.53-p.x*3.0);\nfloat beat=modeA+modeB;\nfloat node=exp(-15.0*abs(beat));\nreturn float4(.031*modeA,.029*modeB,.18*beat,.30*node); }"),
        Mechanic("Folded Continuum", "Space reflects across alternating fold boundaries as they travel.", 4, 0.49f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float fold=sin(p.x*7.0+t*.23)+.3*cos(p.y*6.0-t*.17);\nfloat mirrored=abs(fold);\nfloat crease=exp(-12.0*abs(fold));\nfloat2 warp=float2(.06*(mirrored-.4),.035*sin(p.y*6.0+t*.19));\nreturn float4(warp,crease*.22,.30*crease+.13*force); }"),
        Mechanic("Morphing Torus", "A toroidal ring breathes and pinches around travelling lobes.", 4, 0.64f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float r=length(p*float2(1.13,.89)),a=atan(p.y,p.x);\nfloat breath=.5+.5*sin(t*.65);\nfloat pinch=sin(a*6.0-t*.34);\nfloat ring=exp(-18.0*abs(r-.33-.05*pinch-.035*breath));\nreturn float4(p*.07*pinch,.18*breath+.12*pinch,.28*ring); }"),
        Mechanic("Infinite Weave", "Two looped strands exchange over-under depth at successive crossings.", 4, 0.76f, "float4 dynamics(float2 p,float t,float2 tap,float force) { float2 q=p;\nfloat a=atan(q.y,q.x),r=length(q);\nfloat over=sin(2.0*a+t*.35),under=sin(3.0*a-t*.26);\nfloat crossing=sin(22.0*r+over*2.0-under*1.5);\nfloat knot=exp(-9.0*abs(over-under));\nreturn float4(float2(-q.y,q.x)*.04*(over-under),crossing*.14,.27*knot*pow(max(crossing,0.0),2.0)); }")
        )
        init {
            require(SCENES.size == COUNT && MECHANICS.size == COUNT)
            require(SCENES.indices.all { SCENES[it].name == MECHANICS[it].name })
            require(MECHANICS.map { it.code }.distinct().size == COUNT)
        }
        fun mechanicDescription(preset: Int): String? =
            if (isSmooth(preset)) MECHANICS[preset - FIRST].explanation else null
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
             touchX: Float, touchY: Float, offsetX: Float, pose: ParallaxPose,
             touchForce: Float = 0f) {
        val index = (state.preset - FIRST).coerceIn(0, COUNT - 1)
        val spec = SCENES[index]
        val mechanic = MECHANICS[index]
        val w = canvas.width.coerceAtLeast(1).toFloat()
        val h = canvas.height.coerceAtLeast(1).toFloat()
        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated && index !in failed) {
            try {
                val shader = shaderCache[index] ?: RuntimeShader(PREFIX + mechanic.code + spec.field + SUFFIX).also {
                    shaderCache[index] = it
                }
                shader.setFloatUniform("u_resolution", w, h)
                shader.setFloatUniform(
                    "u_time", time * mechanic.playbackRate * (.33f + state.speed * .67f)
                )
                shader.setFloatUniform("u_model", mechanic.materialModel.toFloat())
                shader.setFloatUniform("u_touch_force", touchForce.coerceIn(0f, 1f))
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
uniform float u_model;
uniform float u_touch_force;

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
    float2 touch=(u_touch-.5)*res/size;
    float4 motion=dynamics(p,t,touch,u_touch_force);
    float2 warped=p+motion.xy;
    float phase=t+motion.z;
    float4 s=surface(warped,phase);
    float eps=max(1.0/size,.0028);
    if (u_quality < .5) eps*=1.65;
    float2 slope=float2(
        surface(warped+float2(eps,0.0),phase).x-s.x,
        surface(warped+float2(0.0,eps),phase).x-s.x)/eps;
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
    float touchGlow=exp(-12.0*length(p-touch))*.12*u_touch_force;
    float heightFade=smoothstep(-.72,.75,p.y);
    float atmosphere=exp(-3.0*length(p-float2(.14,-.18)));
    float3 bg=u_sky*(.65+.31*heightFade)+u_light*(.045+.082*atmosphere);
    float3 material=mix(u_base,u_second,clamp(s.w,0.0,1.0));
    float3 ambient=mix(u_light,u_tint,.09);
    float coverage=clamp(s.y,0.0,1.0);
    float3 lit=material*(.27+diffuse*.64+bounce*.19)*shadow;
    lit+=ambient*(.11*diffuse+.043*bounce);
    float3 reflection=mix(u_light,u_second,.34)*spec*(.48+.72*u_metallic);
    reflection+=u_light*other*.13;
    lit+=reflection*(.65+.45*u_gloss);
    lit+=u_second*fresnel*(.16+.44*u_metallic);
    float3 col=mix(bg,lit,coverage);

    // Each collection has a distinct material response; motion.glow
    // originates inside the scene-specific mechanics shader.
    if (u_model < .5) {
        // Fluid: diffused inner light and broad liquid highlights.
        col+=u_second*pow(max(0.0,diffuse),4.0)*.07*coverage;
        col+=u_light*motion.w*.22;
    } else if (u_model < 1.5) {
        // Optics: environment refraction and focused caustics.
        float lens=clamp(length(motion.xy)*6.0,0.0,1.0);
        col=mix(col,bg+u_second*.16,lens*.28);
        col+=u_light*(.16*fresnel+.42*motion.w)*(1.0-rough*.42);
    } else if (u_model < 2.5) {
        // Space: depth-fog scattering and independent luminous strata.
        col=mix(col,bg+material*.34,.18*(1.0-coverage));
        col+=u_second*motion.w*.68;
        col*=.88+.12*atmosphere;
    } else if (u_model < 3.5) {
        // Energy: arc emission and charge flashes, not a generic glow.
        col=col*.77+u_second*(motion.w*.85)+u_light*motion.w*.45;
        col+=u_base*pow(max(0.0,diffuse),8.0)*.14;
    } else {
        // Geometry: differentiated sharp highlights and soft bevels.
        col+=u_light*(.24*spec+.16*fresnel)*coverage;
        col=mix(bg*.91,col,coverage);
        col+=u_second*motion.w*.18;
    }
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
