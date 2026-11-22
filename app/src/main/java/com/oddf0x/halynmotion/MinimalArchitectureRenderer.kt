package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build

/**
 * Minimal Architecture: one original, dimensional graphite monolith.
 *
 * AGSL performs an analytic ray/box intersection per pixel, with light transport,
 * face-specific roughness and contact lighting. There is no ray-marching loop,
 * particle system, random shape generator, downloaded asset or endless gradient.
 *
 * The scene moves slowly as an object: the slab itself rotates slightly with
 * time and gyro; only the lighting changes. A recognizable geometric Canvas
 * fallback is used if AGSL is unavailable or fails on a particular driver.
 */
class MinimalArchitectureRenderer {
    companion object {
        const val SCENE_INDEX = 182
        const val SCENE_NAME = "Monolith Light"
        const val DESCRIPTION =
            "Sculptural graphite architecture. Slow viewpoint motion, real face lighting, and matte stone."
        private val AGSL = """
uniform float2 u_resolution;
uniform float u_time;
uniform float2 u_tilt;
uniform float u_intensity;
uniform float u_speed;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}
float3 rotateY(float3 p, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return float3(c*p.x + s*p.z, p.y, -s*p.x + c*p.z);
}
float3 lightColor(float3 p) {
    return float3(0.73,0.78,0.84) * max(0.0, p.z) +
        float3(0.12,0.15,0.21) * max(0.0, -p.z);
}
half4 main(float2 frag) {
    float2 res = max(u_resolution,float2(1.0,1.0));
    float2 uv = (frag - res * .5) / res.y;
    float t = u_time * u_speed;
    float2 q = uv;
    float dist = length(q - float2(-.16,-.08));
    float spot = exp(-4.5*dist*dist);
    float horizon = smoothstep(-.80, .14, uv.y);
    float3 col = float3(.011,.013,.017) +
        float3(.028,.032,.043) * spot +
        float3(.007,.009,.012) * horizon;
    // Deterministic, sub-LSB spatial grain; no temporal flicker.
    float dither = hash(floor(frag)) - .5;
    float3 rayOrigin = float3(0.0, 0.06, 3.4);
    float3 ray = normalize(float3(uv.x*1.28, uv.y*1.28, -2.10));
    float angle = .14 + .13*sin(t*.13) + u_tilt.x*.15;
    rayOrigin = rotateY(rayOrigin, -angle);
    ray = rotateY(ray, -angle);
    float3 halfSize = float3(.59, 1.04, .24);
    float3 invRay = 1.0 / (ray + sign(ray) * .00001);
    float3 nearPlane = (-halfSize - rayOrigin) * invRay;
    float3 farPlane = (halfSize - rayOrigin) * invRay;
    float3 lower = min(nearPlane, farPlane);
    float3 upper = max(nearPlane, farPlane);
    float enter = max(lower.x, max(lower.y, lower.z));
    float leave = min(upper.x, min(upper.y, upper.z));
    float hit = step(enter, leave) * step(.01, leave) * step(enter, 9.0);
    if (hit > .5) {
        float3 p = rayOrigin + ray*max(enter,.01);
        float3 axis = abs(p / halfSize);
        float3 n = axis.x > axis.y && axis.x > axis.z ?
            float3(sign(p.x),0,0) :
            (axis.y > axis.z ? float3(0,sign(p.y),0) :
                float3(0,0,sign(p.z)));
        float3 worldNormal = rotateY(n, angle);
        float3 lightDir = normalize(float3(-.65, .94, 1.15));
        float diffuse = max(0.0, dot(worldNormal, lightDir));
        float edge = min(halfSize.x-abs(p.x), halfSize.y-abs(p.y));
        float bevel = 1.0 - smoothstep(0.0,.037,edge);
        float stoneGrain = hash(floor(float2(p.x*320.0,p.y*420.0)));
        float fineVein = sin(p.y*67.0 + sin(p.x*16.0)*.7)*.5+.5;
        float body = .032 + diffuse*.19 + .016*stoneGrain + .009*fineVein;
        float3 stone = float3(body*.88,body*.92,body);
        float side = .06 + .12*max(0.0, worldNormal.x);
        stone = mix(stone, float3(side*.72,side*.8,side*.9),
            1.0-step(.5,abs(n.z)));
        stone += bevel * float3(.065,.077,.089);
        // Wide softbox reflection, sharper on the narrow engineered edge.
        float3 viewDir = normalize(-ray);
        float3 halfDir = normalize(lightDir+viewDir);
        float spec = pow(max(0.0,dot(worldNormal,halfDir)),18.0);
        stone += float3(.20,.24,.28)*spec*.38;
        // Restraint: the front face has a barely visible central scored line.
        float seam = (1.0-smoothstep(.0015,.004,abs(p.x+.17))) *
            step(.95,abs(n.z));
        stone *= 1.0-seam*.28;
        // Ground-adjacent vignette keeps the monolith rooted rather than floating.
        stone *= .84+.16*smoothstep(-1.04,-.65,p.y);
        col = mix(col,stone*clamp(u_intensity,.5,1.6),hit);
    } else {
        // Analytical contact shadow on a dark floor, not a synthetic halo.
        float floorT = (-1.04-rayOrigin.y)/min(ray.y,-.0001);
        float3 floorPos = rayOrigin+ray*floorT;
        float onFloor = step(.0,floorT)*step(floorT,9.0)*step(uv.y,-.33);
        float shadow = exp(-3.0*(floorPos.x*floorPos.x+floorPos.z*floorPos.z));
        float floorLight = .016+.018*exp(-1.5*floorPos.x*floorPos.x);
        col = mix(col,float3(floorLight)*(.65-.48*shadow),onFloor*.63);
    }
    col = col / (1.0+col*.22);
    col = pow(max(col,float3(0.0)),float3(.91));
    col = clamp(col + dither/255.0,float3(0.0),float3(1.0));
    return half4(half3(col),1.0);
}
""".trimIndent()
    }

    private var shader: RuntimeShader? = null
    private var failed = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun resetHardwareState() {
        shader = null
        failed = false
        paint.shader = null
    }

    fun draw(canvas: Canvas, state: MotionState, elapsed: Float, gyro: ParallaxPose) {
        val w = canvas.width.coerceAtLeast(1)
        val h = canvas.height.coerceAtLeast(1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            canvas.isHardwareAccelerated && !failed
        ) {
            try {
                val active = shader ?: RuntimeShader(AGSL).also { shader = it }
                active.setFloatUniform("u_resolution", w.toFloat(), h.toFloat())
                active.setFloatUniform("u_time", elapsed)
                active.setFloatUniform("u_speed", state.speed)
                active.setFloatUniform("u_intensity", state.intensity)
                active.setFloatUniform("u_tilt", gyro.x, gyro.y)
                paint.shader = active
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = null
                QuadColorSystem.drawOverlay(canvas, state, .10f)
                return
            } catch (_: RuntimeException) {
                paint.shader = null
                failed = true
            }
        }
        drawFallback(canvas, state, elapsed, gyro)
        QuadColorSystem.drawOverlay(canvas, state, .10f)
    }

    private fun drawFallback(c: Canvas, state: MotionState, time: Float, gyro: ParallaxPose) {
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        c.drawColor(Color.rgb(9, 11, 15))
        val sway = (kotlin.math.sin(time * state.speed * .13f) * .025f + gyro.x * .03f) * w
        val cx = w * .5f + sway
        val left = cx-w*.28f
        val right = cx+w*.23f
        val top = h*.22f
        val bottom = h*.82f
        val side = w*.09f
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(left, top, right, bottom,
            intArrayOf(Color.rgb(71,76,84), Color.rgb(26,30,38), Color.rgb(10,12,16)),
            null, Shader.TileMode.CLAMP)
        c.drawRect(left, top, right, bottom, paint)
        paint.shader = null
        val edge = Path().apply {
            moveTo(right,top)
            lineTo(right+side,top+h*.017f)
            lineTo(right+side,bottom-h*.03f)
            lineTo(right,bottom)
            close()
        }
        paint.color = Color.rgb(40,45,54)
        c.drawPath(edge,paint)
        paint.color = Color.rgb(97,102,111)
        c.drawRect(left,top,right,top+h*.003f,paint)
    }
}
