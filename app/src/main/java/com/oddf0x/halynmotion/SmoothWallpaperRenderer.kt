package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log

/**
 * 30 extra HALYN ORIGINAL-STYLE procedural shaders.
 *
 * The entire optical colour pipeline is taken directly from HALYN's restored
 * original AGSL source: shared dark canvas, colour palettes, bright emission,
 * soft secondary glow, metal/glass accent, filmic tone map and fine dither.
 *
 * Each of the 30 additions owns its own v/glow shape field and motion equation.
 * Unlike v0.17 there is NO separate physical-material/surface-normal engine.
 * Existing originals (0..11, 13..16, 52..63) remain in HalynRenderer unchanged.
 */
class SmoothWallpaperRenderer {
    data class Scene(
        val name: String,
        val category: String,
        val description: String
    )

    companion object {
        const val FIRST = 75
        const val COUNT = 30
        const val LAST = FIRST + COUNT - 1
        val SCENES = listOf(
        Scene("Ripple Cadence", "Fluid ribbons", "Three travelling ripple sources alternate between constructive and destructive interference."),
        Scene("Helix Undertow", "Fluid ribbons", "Two independently twisting strands exchange front and back lighting along their length."),
        Scene("Viscous Portal", "Fluid ribbons", "A viscous spiral curls inward, accelerating toward the centre of a dark portal."),
        Scene("Mirror Interference", "Fluid ribbons", "Symmetric counter-propagating arcs create travelling mirror-like moiré patterns."),
        Scene("Pressure Lines", "Fluid ribbons", "Five streamlines shear around a moving pressure front, forming bright compressed edges."),
        Scene("Current Fold", "Fluid ribbons", "A folded translucent sheet rolls over itself, and thin luminous creases chase the fold."),
        Scene("Glass Caustics", "Optical light", "Moving translucent lenses focus rippling light into narrow sweeping caustic curves."),
        Scene("Refractive Helix", "Optical light", "A glass-like double helix rotates with changing focus where its threads cross."),
        Scene("Prism Diffusion", "Optical light", "Rotating diagonal light sheets diffract through one another at distinct speeds."),
        Scene("Phase Lens", "Optical light", "A breathing elliptical lens sends focused rings outward and recaptures them."),
        Scene("Lumen Chamber", "Optical light", "Seven curved aperture blades slide open while a thin rim retains its light."),
        Scene("Chroma Shell", "Optical light", "Two translucent floating shells overlap, their rims pulsing where they intersect."),
        Scene("Accretion Band", "Cosmic formations", "A dark core pulls thin elliptical light streams into different orbital speeds."),
        Scene("Void Echo", "Cosmic formations", "Expanding light shocks leave fading echoes around a nearly black central void."),
        Scene("Distant Bloom", "Cosmic formations", "Volumetric-looking wisps extend into several drifting luminous branches."),
        Scene("Event Ribbons", "Cosmic formations", "Gravitationally bent ribbons pass around the centre on different curved tracks."),
        Scene("Parallax Nebula", "Cosmic formations", "Three smoothly drifting cloud layers produce changing depth and shadow."),
        Scene("Cosmic Aperture", "Cosmic formations", "A nine-bladed glowing iris opens and closes around a dark gravitational centre."),
        Scene("Ion Threads", "Energy fields", "Charged strands fork sideways and bright pulses run through their intersections."),
        Scene("Plasma Convergence", "Energy fields", "Seven curved plasma streams converge toward a bright pulsing central knot."),
        Scene("Flux Cascade", "Energy fields", "Curved energy threads stream downward in a cascading sequence, never as pixels."),
        Scene("Electric Lacing", "Energy fields", "Two crossed sheets of fine lightning threads illuminate successive moving knots."),
        Scene("Arc Reservoir", "Energy fields", "A crescent-shaped electric reservoir charges, flashes and cools repeatedly."),
        Scene("Magnetic Pulse", "Energy fields", "Two opposing magnetic poles push travelling luminosity around elongated field loops."),
        Scene("Gyroid Passage", "Geometry & motion", "Continuous gyroid contours appear to rotate through an illuminated passage."),
        Scene("Elastic Membrane", "Geometry & motion", "A luminous membrane stretches, rebounds and releases rings from the last touch."),
        Scene("Sonic Contours", "Geometry & motion", "Three resonant harmonics trace bright nodal curves in changing combinations."),
        Scene("Folded Continuum", "Geometry & motion", "Continuous luminous planes fold and invert, revealing separate interior edges."),
        Scene("Morphing Torus", "Geometry & motion", "A changing toroidal loop pinches into lobes before smoothly returning to a ring."),
        Scene("Infinite Weave", "Geometry & motion", "Two figure-eight curves exchange over-under highlights as they rotate across the screen.")
        )
        fun isSmooth(value: Int): Boolean = value in FIRST..LAST
        fun mechanicDescription(preset: Int): String? =
            if (isSmooth(preset)) SCENES[preset - FIRST].description else null

        private val AGSL = """

uniform float2 u_resolution;
uniform float2 u_touch;
uniform float u_time;
uniform float u_variant;
uniform float u_palette;
uniform float3 u_customColor;
uniform float u_intensity;
uniform float u_speed;
uniform float u_quality;
uniform float u_offset;
uniform float2 u_gyro;

float noise21(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}
// Smoothed density, sampled only when Cinematic detail is selected.
float valueNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(noise21(i), noise21(i + float2(1.0, 0.0)), f.x);
    float b = mix(noise21(i + float2(0.0, 1.0)),
                  noise21(i + float2(1.0, 1.0)), f.x);
    return mix(a, b, f.y);
}
// Filmic shoulder: preserves glints without clipping them to pure white.
float3 toneMap(float3 x) {
    x = max(x, float3(0.0));
    return clamp(
        (x * (2.51 * x + float3(0.03))) /
        (x * (2.43 * x + float3(0.59)) + float3(0.14)),
        float3(0.0), float3(1.0));
}
// Interleaved gradient noise is stable between frames (no shimmering grain).
float stableDither(float2 pixel) {
    return fract(52.9829189 * fract(pixel.x * 0.06711056 +
                pixel.y * 0.00583715)) - 0.5;
}
half4 main(float2 frag) {
    float2 uv = frag / u_resolution;
    float aspect = u_resolution.x / u_resolution.y;
    float2 p = float2((uv.x - 0.5) * aspect, uv.y - 0.5);
    p.x -= (u_offset - 0.5) * 0.10;
    float radialDepth = smoothstep(0.0, .65, length(p));
    p -= u_gyro * float2(.021 + .037 * radialDepth, .012 + .025 * radialDepth);
    float2 touch = float2((u_touch.x - 0.5) * aspect, u_touch.y - 0.5);
    float nearTouch = exp(-13.0 * length(p - touch));
    float t = u_time * (0.35 + u_speed * 0.9);
    float k = u_intensity;
    float3 accent = float3(0.32, 0.91, 1.0);
    if (u_palette > 0.5 && u_palette < 1.5) {
        accent = float3(0.68, 0.42, 1.0);
    } else if (u_palette > 1.5 && u_palette < 2.5) {
        accent = float3(1.0, 0.48, 0.22);
    } else if (u_palette > 2.5 && u_palette < 3.5) {
        accent = float3(1.0, 0.35, 0.66);
    } else if (u_palette > 3.5) {
        accent = float3(0.30, 0.96, 0.64);
    }
    if (u_palette > 4.5) {
        accent = u_customColor;
    }
    float3 companion = mix(accent, accent.zxy, 0.46) * 0.72;
    float3 highlight = mix(accent, float3(0.97, 0.985, 1.0), 0.59);
    float r = length(p);
    float v = 0.0;
    float glow = 0.0;
    if (u_variant < 0.5) {
        // Ripple Cadence: Three travelling ripple sources alternate between constructive and destructive interference.
        float2 a=p-float2(-.27+.025*sin(t*.17),-.21);
        float2 b=p-float2(.29,.17+.035*cos(t*.13));
        float2 c=p-float2(.05*sin(t*.09),.31);
        float w1=sin(length(a)*29.0-t*1.14);
        float w2=cos(length(b)*24.0+t*.79);
        float w3=sin(length(c)*35.0-t*.61);
        float interference=w1*.54+w2*.34+w3*.26;
        float envelope=exp(-1.7*r);
        v=pow(max(0.0,interference),9.0)*envelope*1.25;
        glow=pow(abs(interference),2.0)*envelope*.32;
    }     else if (u_variant < 1.5) {
        // Helix Undertow: Two independently twisting strands exchange front and back lighting along their length.
        float lane=sin(p.y*8.6-t*.53)*.23;
        float2 qa=p-float2(lane,.0);
        float2 qb=p+float2(lane,.0);
        float over=.5+.5*sin(p.y*9.1+t*.46);
        float threadA=exp(-98.0*abs(qa.x))*(.35+.65*over);
        float threadB=exp(-98.0*abs(qb.x))*(1.0-.64*over);
        float crossing=pow(max(0.0,cos(p.y*9.1+t*.46)),18.0);
        v=(threadA+threadB*.93)*exp(-.8*abs(p.y))+.23*crossing*exp(-23.0*abs(p.x));
        glow=(exp(-15.0*abs(qa.x))+exp(-15.0*abs(qb.x)))*.20;
    }     else if (u_variant < 2.5) {
        // Viscous Portal: A viscous spiral curls inward, accelerating toward the centre of a dark portal.
        float2 q=p-float2(.045*sin(t*.14),-.02*cos(t*.16));
        float radius=max(length(q),.018);
        float a=atan(q.y,q.x);
        float spiral=sin(5.0*a+33.0*radius+t*.52+1.4/(radius+.32));
        float curtain=exp(-1.85*radius)*pow(max(0.0,spiral),13.0);
        float hollow=1.0-smoothstep(.08,.22,radius);
        v=curtain*(1.0-.82*hollow)+exp(-24.0*radius)*.21;
        glow=exp(-2.2*radius)*pow(max(0.0,spiral),3.0)*.33;
    }     else if (u_variant < 3.5) {
        // Mirror Interference: Symmetric counter-propagating arcs create travelling mirror-like moiré patterns.
        float side=abs(p.x);
        float flowA=sin(side*31.0+3.1*sin(p.y*4.0+t*.32)-t*.71);
        float flowB=cos(side*26.0-2.5*sin(p.y*6.0-t*.24)+t*.51);
        float crest=flowA*.64+flowB*.36;
        float focus=exp(-3.2*side)*(1.0-smoothstep(.12,.68,abs(p.y)));
        v=pow(max(0.0,crest),15.0)*focus*1.23;
        glow=pow(max(0.0,abs(crest)),3.0)*focus*.31;
    }     else if (u_variant < 4.5) {
        // Pressure Lines: Five streamlines shear around a moving pressure front, forming bright compressed edges.
        float q=p.x+.18*sin(p.y*3.5-t*.27)+.035*sin(p.y*12.0+t*.19);
        float pressure=sin(p.y*7.0-t*.43);
        float lines=sin(q*32.0+pressure*1.4);
        float wave=sin(q*12.0+p.y*5.0+t*.26);
        float compression=exp(-2.0*abs(p.x+.10*sin(p.y*2.0-t*.14)));
        v=pow(max(0.0,lines),26.0)*compression*.96+.18*pow(max(0.0,wave),12.0)*compression;
        glow=pow(abs(lines),3.0)*compression*.29;
    }     else if (u_variant < 5.5) {
        // Current Fold: A folded translucent sheet rolls over itself, and thin luminous creases chase the fold.
        float fold=p.x+.29*sin(p.y*3.8-t*.31);
        float crease=sin(19.0*fold+2.3*sin(p.y*6.0+t*.27));
        float back=sin(12.0*fold-p.y*8.0+t*.34);
        float envelope=exp(-3.5*abs(fold));
        float sweep=sin(p.y*3.0-t*.2)*.5+.5;
        v=(pow(abs(crease),23.0)*.9+pow(max(0.0,back),16.0)*.24)*envelope*(.7+.3*sweep);
        glow=envelope*(pow(abs(crease),4.0)*.25+.08*sweep);
    }     else if (u_variant < 6.5) {
        // Glass Caustics: Moving translucent lenses focus rippling light into narrow sweeping caustic curves.
        float2 q=p+float2(.10*sin(p.y*6.0+t*.18),.07*cos(p.x*5.0-t*.22));
        float lensA=length(q-float2(-.16,.07));
        float lensB=length(q-float2(.22,-.12));
        float ray=sin(lensA*36.0-t*.65+1.4*sin(q.y*7.0));
        float focus=cos(lensB*29.0+t*.43);
        float caustic=pow(max(0.0,ray*focus),8.0);
        float envelope=exp(-2.0*length(q));
        v=caustic*envelope*1.48;
        glow=(pow(max(0.0,ray),3.0)+pow(abs(focus),9.0))*.17*envelope;
    }     else if (u_variant < 7.5) {
        // Refractive Helix: A glass-like double helix rotates with changing focus where its threads cross.
        float travel=p.y*9.2-t*.47;
        float left=p.x-.18*sin(travel);
        float right=p.x+.18*sin(travel);
        float sharpA=exp(-118.0*abs(left));
        float sharpB=exp(-118.0*abs(right));
        float focus=.46+.54*cos(travel*2.0);
        float refr=sin(p.x*24.0+p.y*6.0+t*.18);
        v=(sharpA*(.45+.55*focus)+sharpB*(1.0-.48*focus))*1.12
            +pow(max(0.0,refr),24.0)*exp(-5.0*abs(p.x))*.16;
        glow=(exp(-15.0*abs(left))+exp(-15.0*abs(right)))*.18;
    }     else if (u_variant < 8.5) {
        // Prism Diffusion: Rotating diagonal light sheets diffract through one another at distinct speeds.
        float ca=cos(t*.12),sa=sin(t*.12);
        float2 q=float2(p.x*ca-p.y*sa,p.x*sa+p.y*ca);
        float sheetA=sin(q.x*18.0+2.0*sin(q.y*5.0)-t*.47);
        float sheetB=cos(q.y*20.0-1.2*sin(q.x*7.0)+t*.31);
        float luminous=pow(max(0.0,sheetA*.7+sheetB*.3),16.0);
        float spread=exp(-2.8*abs(q.x+q.y*.4));
        v=luminous*spread*1.05+.16*pow(max(0.0,sheetB),19.0)*spread;
        glow=pow(abs(sheetA),4.0)*spread*.23;
    }     else if (u_variant < 9.5) {
        // Phase Lens: A breathing elliptical lens sends focused rings outward and recaptures them.
        float2 q=p-float2(.025*sin(t*.15),.024*cos(t*.12));
        float radius=length(q*float2(1.28,.84));
        float breathing=.31+.046*sin(t*.36);
        float rim=exp(-90.0*abs(radius-breathing));
        float secondary=exp(-55.0*abs(radius-(breathing+.08)));
        float focus=pow(max(0.0,cos(atan(q.y,q.x)*5.0+t*.35)),12.0);
        v=rim*(.56+.7*focus)+secondary*.36;
        glow=exp(-12.0*abs(radius-breathing))*.34+exp(-5.0*radius)*.07;
    }     else if (u_variant < 10.5) {
        // Lumen Chamber: Seven curved aperture blades slide open while a thin rim retains its light.
        float a=atan(p.y,p.x),rad=length(p);
        float blade=sin(a*7.0+3.3*rad+t*.21);
        float opening=.235+.05*sin(t*.37)+.045*blade;
        float edge=exp(-97.0*abs(rad-opening));
        float sweep=pow(max(0.0,cos(a*7.0-t*.32)),19.0);
        float interior=exp(-18.0*rad)*.28;
        v=edge*(.48+.72*sweep)+interior;
        glow=exp(-12.0*abs(rad-opening))*.31+interior*.2;
    }     else if (u_variant < 11.5) {
        // Chroma Shell: Two translucent floating shells overlap, their rims pulsing where they intersect.
        float2 a=p-float2(-.18+.07*sin(t*.17),.09*cos(t*.12));
        float2 b=p-float2(.20+.06*cos(t*.19),-.12+.07*sin(t*.14));
        float ra=length(a*float2(1.1,.83)),rb=length(b*float2(.84,1.13));
        float rimA=exp(-98.0*abs(ra-.25));
        float rimB=exp(-103.0*abs(rb-.285));
        float crossing=rimA*rimB;
        v=rimA*.8+rimB*.85+crossing*.75;
        glow=exp(-13.0*abs(ra-.25))*.16+exp(-13.0*abs(rb-.285))*.19;
    }     else if (u_variant < 12.5) {
        // Accretion Band: A dark core pulls thin elliptical light streams into different orbital speeds.
        float2 q=p*float2(.88,1.38);
        float radius=max(length(q),.05),a=atan(q.y,q.x);
        float shear=a+1.4/(radius+.27)-t*.18/(radius+.32);
        float ribbon=sin(5.0*shear+20.0*radius);
        float ring=exp(-33.0*abs(radius-.34-.023*sin(a*8.0+t*.31)));
        float jet=pow(max(0.0,ribbon),14.0)*exp(-3.9*radius);
        v=ring*.65+jet*.83;
        glow=exp(-8.0*abs(radius-.34))*.22+jet*.15;
    }     else if (u_variant < 13.5) {
        // Void Echo: Expanding light shocks leave fading echoes around a nearly black central void.
        float q=max(length(p),.01);
        float wave=sin(q*35.0-t*1.21);
        float echo=sin(q*21.0-t*.59+1.6*sin(q*5.0));
        float boundary=1.0-smoothstep(.09,.21,q);
        float spread=exp(-3.0*q)*(1.0-boundary*.9);
        v=(pow(max(0.0,wave),23.0)*.91+pow(max(0.0,echo),18.0)*.43)*spread;
        glow=pow(abs(wave),3.0)*spread*.24+exp(-20.0*q)*.08;
    }     else if (u_variant < 14.5) {
        // Distant Bloom: Volumetric-looking wisps extend into several drifting luminous branches.
        float2 q=p*4.2;
        q.x+=.9*sin(q.y*.8-t*.14);
        q.y+=.55*cos(q.x*.7+t*.19);
        float tendril=sin(q.x*2.2+sin(q.y*2.0-t*.3))
            *cos(q.y*1.6+cos(q.x*2.7+t*.17));
        float motion=sin(q.x*.36-q.y*.43+t*.09);
        float density=max(0.0,tendril*.56+motion*.24+.20);
        float bloom=exp(-2.1*r);
        v=pow(density,3.3)*bloom*.83;
        glow=pow(density,1.6)*bloom*.25+exp(-9.0*r)*.10;
    }     else if (u_variant < 15.5) {
        // Event Ribbons: Gravitationally bent ribbons pass around the centre on different curved tracks.
        float2 q=p;
        float rr=max(length(q),.04),angle=atan(q.y,q.x);
        float streamA=sin(angle*4.0+19.0*rr+t*.36/(rr+.38));
        float streamB=cos(angle*3.0-22.0*rr-t*.21/(rr+.29));
        float phase=sin(angle*8.0-rr*11.0+t*.25);
        float near=exp(-1.9*rr);
        v=(pow(max(0.0,streamA),17.0)*.63
            +pow(max(0.0,streamB),20.0)*.57)*near*(.6+.4*abs(phase));
        glow=(pow(abs(streamA),4.0)+pow(abs(streamB),6.0))*.14*near;
    }     else if (u_variant < 16.5) {
        // Parallax Nebula: Three smoothly drifting cloud layers produce changing depth and shadow.
        float2 q=p*3.7;
        float2 nearQ=q+float2(t*.11,-t*.08);
        float2 farQ=q*.51+float2(-t*.047,t*.032);
        float a=sin(nearQ.x*2.2+sin(nearQ.y*1.9))
            *cos(nearQ.y*2.6+cos(nearQ.x*1.3));
        float b=cos(farQ.x*3.3-t*.12)*sin(farQ.y*2.8+t*.09);
        float wisps=valueNoise(nearQ*.73+float2(t*.07,0.0));
        float mass=max(0.0,a*.42+b*.28+wisps*.45-.09);
        v=pow(mass,3.1)*exp(-2.2*r)*.98;
        glow=pow(max(0.0,a*.5+b*.5),3.0)*exp(-1.9*r)*.15;
    }     else if (u_variant < 17.5) {
        // Cosmic Aperture: A nine-bladed glowing iris opens and closes around a dark gravitational centre.
        float angle=atan(p.y,p.x),rad=length(p);
        float petals=sin(angle*9.0+rad*4.1+t*.26);
        float aperture=.25+.045*sin(t*.51)+.031*petals;
        float edge=exp(-96.0*abs(rad-aperture));
        float blade=pow(max(0.0,petals),21.0)*exp(-5.0*rad);
        float hollow=1.0-smoothstep(.08,.16,rad);
        v=edge*(.60+.46*blade)+blade*.40*(1.0-hollow);
        glow=exp(-11.0*abs(rad-aperture))*.31+exp(-8.0*rad)*.08;
    }     else if (u_variant < 18.5) {
        // Ion Threads: Charged strands fork sideways and bright pulses run through their intersections.
        float wave=p.y*10.0-t*.66;
        float center=.18*sin(wave)+.045*sin(p.y*27.0+t*.4);
        float branch=p.x-center-.17*sin(p.y*6.0+t*.28);
        float fork=p.x+center+.14*cos(p.y*8.0-t*.37);
        float connection=sin(p.y*17.0-p.x*7.0-t*.71);
        float pulse=.36+.64*pow(max(0.0,connection),9.0);
        v=(exp(-128.0*abs(branch))+exp(-118.0*abs(fork))*.79)*pulse;
        glow=(exp(-16.0*abs(branch))+exp(-13.0*abs(fork)))*.17;
    }     else if (u_variant < 19.5) {
        // Plasma Convergence: Seven curved plasma streams converge toward a bright pulsing central knot.
        float a=atan(p.y,p.x),rad=max(length(p),.01);
        float bend=sin(a*7.0+rad*11.0-t*.46+1.4*sin(rad*8.0));
        float rays=pow(max(0.0,bend),24.0)*exp(-3.7*rad);
        float core=exp(-21.0*rad)*(.75+.25*sin(t*1.6));
        float charge=pow(max(0.0,sin(t*1.15-rad*20.0)),4.0);
        v=rays*(.54+.46*charge)+core;
        glow=rays*.20+exp(-8.0*rad)*.24;
    }     else if (u_variant < 20.5) {
        // Flux Cascade: Curved energy threads stream downward in a cascading sequence, never as pixels.
        float2 q=p;
        float shear=sin(q.y*7.0+t*.38)*.13;
        float flux=sin((q.x+shear)*26.0+sin(q.y*13.0-t*.63)*1.4);
        float ribbon=pow(max(0.0,flux),25.0);
        float falling=.40+.60*pow(max(0.0,sin(q.y*15.0+t*.84)),4.0);
        float envelope=exp(-2.4*abs(q.x));
        v=ribbon*falling*envelope*1.05;
        glow=pow(abs(flux),5.0)*envelope*.23;
    }     else if (u_variant < 21.5) {
        // Electric Lacing: Two crossed sheets of fine lightning threads illuminate successive moving knots.
        float2 q=p;
        float a=sin((q.x+q.y*.50)*19.0+t*.39+sin(q.y*8.0)*1.1);
        float b=cos((q.y-q.x*.65)*18.0-t*.47+sin(q.x*7.0)*1.1);
        float traceA=pow(max(0.0,a),24.0);
        float traceB=pow(max(0.0,b),23.0);
        float knot=pow(max(0.0,a*b),9.0);
        float pulse=.54+.46*sin(t*.88+q.y*8.0-q.x*7.0);
        v=(traceA*.48+traceB*.56+knot*.66)*exp(-2.7*r)*(.7+.3*pulse);
        glow=(pow(abs(a),5.0)+pow(abs(b),5.0))*exp(-2.7*r)*.11;
    }     else if (u_variant < 22.5) {
        // Arc Reservoir: A crescent-shaped electric reservoir charges, flashes and cools repeatedly.
        float2 q=p-float2(.02*sin(t*.13),-.06);
        float angle=atan(q.y,q.x),radius=length(q*float2(.90,1.15));
        float rim=exp(-118.0*abs(radius-.30));
        float gap=smoothstep(-.30,.06,sin(angle+t*.22));
        float charge=pow(max(0.0,sin(t*1.27)),3.0);
        float spark=pow(max(0.0,cos(angle*16.0-t*.74)),19.0);
        v=rim*(.26+.82*charge)*gap+spark*rim*.44;
        glow=exp(-12.0*abs(radius-.30))*(.11+.26*charge);
    }     else if (u_variant < 23.5) {
        // Magnetic Pulse: Two opposing magnetic poles push travelling luminosity around elongated field loops.
        float2 a=p-float2(-.17,0.0),b=p-float2(.17,0.0);
        float field=length(a)-length(b);
        float ellipse=length(p*float2(.75,1.35));
        float lines=sin(field*43.0+ellipse*11.0-t*.91);
        float shaped=exp(-3.3*ellipse);
        float wave=pow(max(0.0,lines),22.0)*shaped;
        float polar=exp(-25.0*length(a))+exp(-25.0*length(b));
        v=wave*.95+polar*.38;
        glow=pow(abs(lines),4.0)*shaped*.20+polar*.14;
    }     else if (u_variant < 24.5) {
        // Gyroid Passage: Continuous gyroid contours appear to rotate through an illuminated passage.
        float cs=cos(t*.14),sn=sin(t*.14);
        float2 q=float2(p.x*cs-p.y*sn,p.x*sn+p.y*cs)*11.0;
        float gyroid=sin(q.x)*cos(q.y)+sin(q.y)*cos(q.x*.61);
        float warp=sin(q.x+q.y*.42+t*.11)*.24;
        float shell=exp(-8.0*abs(gyroid+warp*.5));
        float focus=exp(-2.0*r);
        v=pow(max(0.0,1.0-abs(gyroid+warp)),19.0)*focus*.90;
        glow=shell*focus*.19;
    }     else if (u_variant < 25.5) {
        // Elastic Membrane: A luminous membrane stretches, rebounds and releases rings from the last touch.
        float distance=length(p-touch);
        float distortion=sin(distance*27.0-t*1.58)*exp(-4.0*distance);
        float wave=sin(p.x*17.0+2.5*distortion+t*.23)
            *cos(p.y*12.0-distortion*3.0-t*.17);
        float envelope=exp(-2.5*length(p));
        v=pow(max(0.0,wave*.68+.32*distortion),12.0)*envelope;
        glow=(pow(abs(wave),3.0)*.17+abs(distortion)*.24)*envelope;
    }     else if (u_variant < 26.5) {
        // Sonic Contours: Three resonant harmonics trace bright nodal curves in changing combinations.
        float x=sin(p.x*17.0+t*.43)*sin(p.y*6.0-t*.19);
        float y=cos(p.x*7.0-t*.29)*cos(p.y*21.0+t*.47);
        float z=sin((p.x-p.y)*11.0+t*.21);
        float mode=x*.47+y*.34+z*.19;
        float contour=exp(-33.0*abs(mode-.12));
        float envelope=exp(-2.0*r);
        v=contour*envelope*.97;
        glow=exp(-8.0*abs(mode-.12))*envelope*.25;
    }     else if (u_variant < 27.5) {
        // Folded Continuum: Continuous luminous planes fold and invert, revealing separate interior edges.
        float fold=p.x+.19*sin(p.y*5.6-t*.28);
        float inversion=sin(p.y*4.1+t*.17);
        float face=sin(26.0*abs(fold)+2.1*inversion);
        float seam=exp(-65.0*abs(fold));
        float inner=pow(max(0.0,face),19.0)*exp(-2.7*abs(fold));
        v=inner*.83+seam*(.27+.35*max(0.0,inversion));
        glow=pow(abs(face),4.0)*exp(-2.2*abs(fold))*.22+seam*.12;
    }     else if (u_variant < 28.5) {
        // Morphing Torus: A changing toroidal loop pinches into lobes before smoothly returning to a ring.
        float angle=atan(p.y,p.x),radius=length(p);
        float squeeze=sin(t*.41);
        float lobes=sin(angle*6.0-t*.31);
        float circle=.31+.055*lobes+.04*squeeze*sin(angle*3.0);
        float body=exp(-90.0*abs(radius-circle));
        float reflection=pow(max(0.0,cos(angle*3.0+t*.66)),19.0);
        float inner=exp(-70.0*abs(radius-(circle-.075)));
        v=body*(.56+.61*reflection)+inner*.28;
        glow=exp(-11.0*abs(radius-circle))*.23+exp(-6.0*radius)*.06;
    }     else if (u_variant < 29.5) {
        // Infinite Weave: Two figure-eight curves exchange over-under highlights as they rotate across the screen.
        float co=cos(t*.13),si=sin(t*.13);
        float2 q=float2(p.x*co-p.y*si,p.x*si+p.y*co);
        float a=q.x*q.x-q.y*q.y;
        float b=2.0*q.x*q.y;
        float lemniscate=sin(31.0*(a*.58+b*.42)+t*.37);
        float path=exp(-95.0*abs(a*.42+b*.58-.045*sin(t*.18)));
        float strands=pow(max(0.0,lemniscate),22.0)*exp(-3.0*length(q));
        float over=.5+.5*sin(q.y*12.0+t*.42);
        v=path*(.30+.76*over)+strands*.49;
        glow=exp(-13.0*abs(a*.42+b*.58))*.22;
    } else {
        v = 0.0;
        glow = 0.0;
    }
    // Scene-dependent material response: reflections are not nebula light.
    float metal = 0.0;
    if ((u_variant > 5.5 && u_variant < 11.5) ||
        (u_variant > 23.5 && u_variant < 24.5) ||
        (u_variant > 27.5 && u_variant < 29.5)) {
        metal = 1.0;
    }
    float atmospheric = 0.0;
    if (u_variant > 11.5 && u_variant < 17.5) {
        atmospheric = 1.0;
    }
    // Tinted shadow, midground haze and a separate far-light source.
    float horizon = 0.5 + 0.5 * sin(p.y * 2.2 + p.x * 1.25 + u_variant * .35);
    float vignette = 1.0 - smoothstep(0.25, 0.92, r);
    float fog = exp(-3.3 * length(p - float2(0.08, -0.12)));
    float3 inkBlack = float3(0.0035, 0.006, 0.014);
    float3 ambientTint = mix(companion, accent, .36) * (.065 + .045 * horizon);
    float3 col = inkBlack + ambientTint * (.5 + .5 * vignette);

    // Balanced: animated light gradients, without expensive noise samples.
    if (u_quality > 0.5) {
        float lightFlow = sin(p.x * 4.4 - t * .087 + u_variant * .3)
            * cos(p.y * 3.9 + t * .061);
        col += companion * (.017 + .011 * lightFlow) * vignette;
        col += accent * (.023 + .032 * atmospheric) * fog;
    }
    // Cinematic: smooth moving turbulence, calculated only when enabled.
    if (u_quality > 1.5) {
        float f = valueNoise(p * 5.1 + float2(t * .054, u_variant * .16));
        col += companion * (.040 * f * f) * (.35 + .65 * vignette);
    }
    float energy = max(0.0, v) * k;
    float halo = max(0.0, glow) * k;
    float broadLight = energy * (.56 - .10 * metal) +
        halo * (.25 + .24 * atmospheric);
    float3 emission = mix(companion, accent, clamp(energy * .56 + .35, 0.0, 1.0));
    col += emission * broadLight;
    col += companion * halo * (.17 + .18 * atmospheric);
    // Metal and refractive glass receive a bright, focused specular lobe.
    float specular = energy * energy / (1.0 + energy * energy);
    col += highlight * specular * (.33 + .70 * metal);
    col += accent * nearTouch * .07 * k;
    col += companion * .034 * vignette;
    col = toneMap(col * 1.22);
    col = pow(col, float3(.92));
    // Fixed sub-LSB grain reduces gradient banding without temporal shimmer.
    col = clamp(col + float3(stableDither(floor(frag)) / 255.0),
                float3(0.0), float3(1.0));
    return half4(half3(col), 1.0);
}
""".trimIndent()
    }

    private var shader: RuntimeShader? = null
    private var shaderFailed = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
        color = Color.rgb(5, 8, 18)
    }
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private var fallback: LinearGradient? = null
    private var fallbackHeight = -1
    private var fallbackColor = Int.MIN_VALUE

    fun resetHardwareState() {
        shader = null
        shaderFailed = false
    }

    fun draw(
        canvas: Canvas,
        state: MotionState,
        time: Float,
        touchX: Float,
        touchY: Float,
        offsetX: Float,
        pose: ParallaxPose,
        touchForce: Float = 0f
    ) {
        val width = canvas.width.coerceAtLeast(1).toFloat()
        val height = canvas.height.coerceAtLeast(1).toFloat()

        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated && !shaderFailed) {
            try {
                val active = shader ?: RuntimeShader(AGSL).also { shader = it }
                // Same uniforms, palette interpretation and shader detail as
                // the original HALYN AGSL. No extra pixel or material filter.
                active.setFloatUniform("u_resolution", width, height)
                active.setFloatUniform("u_variant", (state.preset - FIRST).toFloat())
                active.setFloatUniform("u_palette", state.palette.toFloat())
                active.setFloatUniform("u_customColor",
                    Color.red(state.customColor) / 255f,
                    Color.green(state.customColor) / 255f,
                    Color.blue(state.customColor) / 255f)
                active.setFloatUniform("u_intensity",
                    (state.intensity + touchForce * .12f).coerceIn(.35f, 2.3f))
                active.setFloatUniform("u_speed", state.speed)
                active.setFloatUniform("u_quality", state.shaderQuality.toFloat())
                active.setFloatUniform("u_time", time)
                active.setFloatUniform("u_touch",
                    if (state.touch) touchX else .5f,
                    if (state.touch) touchY else .5f)
                active.setFloatUniform("u_offset", if (state.parallax) offsetX else .5f)
                active.setFloatUniform("u_gyro", pose.x, pose.y)
                paint.shader = active
                canvas.drawRect(0f, 0f, width, height, paint)
                return
            } catch (e: RuntimeException) {
                paint.shader = null
                shader = null
                shaderFailed = true
                Log.e("HALYN_ORIGINAL_STYLE", "Original-style shader compilation failed", e)
            }
        }
        val accent = if (state.palette == 5) state.customColor
            else HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0, 4)]
        if (fallback == null || fallbackHeight != canvas.height || fallbackColor != accent) {
            fallback = LinearGradient(0f, 0f, 0f, height,
                intArrayOf(Color.rgb(3, 5, 14), Color.rgb(
                    (Color.red(accent) * .14f).toInt().coerceIn(0, 255),
                    (Color.green(accent) * .14f).toInt().coerceIn(0, 255),
                    (Color.blue(accent) * .14f).toInt().coerceIn(0, 255)),
                    Color.rgb(3, 5, 14)),
                null, Shader.TileMode.CLAMP)
            fallbackColor = accent
            fallbackHeight = canvas.height
        }
        fallbackPaint.shader = fallback
        canvas.drawRect(0f, 0f, width, height, fallbackPaint)
    }
}
