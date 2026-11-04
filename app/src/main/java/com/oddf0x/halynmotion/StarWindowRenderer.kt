package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RuntimeShader

/**
 * Fixed-world star sphere. Rays are rotated with the rotation-vector sensor
 * and originate at the bounded inertial position. Three concentric shells
 * create actual view-dependent parallax as the virtual camera translates.
 *
 * Stars are deterministic in spherical world coordinates, so they do not
 * follow the phone when its attitude changes. No camera, Unity or ARCore.
 */
class StarWindowRenderer {
    private val shader = try { RuntimeShader(AGSL) } catch (_: IllegalArgumentException) { null }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
        if (this@StarWindowRenderer.shader != null) this.shader = this@StarWindowRenderer.shader
        else color = Color.rgb(3, 6, 17)
    }

    fun draw(
        canvas: Canvas,
        state: MotionState,
        seconds: Float,
        pose: StarPose = StarPose()
    ) {
        canvas.drawColor(Color.rgb(3, 5, 15))
        if (canvas.isHardwareAccelerated && shader != null) {
            val color = if (state.palette == 5) state.customColor
                else HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0, 4)]
            shader.setFloatUniform("u_resolution",
                canvas.width.coerceAtLeast(1).toFloat(),
                canvas.height.coerceAtLeast(1).toFloat())
            shader.setFloatUniform("u_time", seconds)
            shader.setFloatUniform("u_right", pose.right[0], pose.right[1], pose.right[2])
            shader.setFloatUniform("u_up", pose.up[0], pose.up[1], pose.up[2])
            shader.setFloatUniform("u_out", pose.outward[0], pose.outward[1], pose.outward[2])
            shader.setFloatUniform("u_origin", pose.origin[0], pose.origin[1], pose.origin[2])
            shader.setFloatUniform("u_accent",
                Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f)
            shader.setFloatUniform("u_density", when (state.starDensity) {
                0 -> .75f
                2 -> 1.5f
                else -> 1f
            })
            shader.setFloatUniform("u_lines", if (state.starLines) 1f else 0f)
        }
        canvas.drawRect(0f, 0f,
            canvas.width.coerceAtLeast(1).toFloat(),
            canvas.height.coerceAtLeast(1).toFloat(), paint)
    }

    companion object {
        const val SCENE_INDEX = 12
        private val AGSL = """
uniform float2 u_resolution;
uniform float u_time;
uniform float3 u_right;
uniform float3 u_up;
uniform float3 u_out;
uniform float3 u_origin;
uniform float3 u_accent;
uniform float u_density;
uniform float u_lines;

float hash21(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
}
float2 hash22(float2 p) {
    return float2(hash21(p), hash21(p + float2(37.719, 61.317)));
}
// Each layer is a fixed spherical map around the virtual world's origin.
// Sampling the ray intersection gives position-dependent parallax.
float4 starLayer(float3 origin, float3 dir, float distance, float density, float seed) {
    float b = dot(origin, dir);
    float c = dot(origin, origin) - distance * distance;
    float t = -b + sqrt(max(0.001, b * b - c));
    float3 surface = normalize(origin + t * dir);
    float2 lonLat = float2(atan(surface.x, surface.z) / 6.2831853 + .5,
        asin(clamp(surface.y, -1.0, 1.0)) / 3.1415927 + .5);
    float2 p = lonLat * float2(density, density * .5);
    float2 cell = floor(p);
    float2 local = fract(p);
    float rnd = hash21(cell + float2(seed, seed * 2.13));
    float2 center = .12 + .76 * hash22(cell + float2(seed * 4.7, seed));
    float diameter = .035 + .048 * hash21(cell + float2(seed, 8.71));
    float star = (1.0 - smoothstep(diameter, diameter + .022, length(local - center)))
        * smoothstep(.78, .985, rnd);
    float glow = exp(-17.0 * length(local - center)) * pow(max(0.0, rnd - .90), 1.4) * 1.7;
    float twinkle = .87 + .13 * sin(u_time * (0.45 + rnd * 1.15) + rnd * 30.0);
    return float4(star * twinkle, glow * twinkle, rnd, star);
}
float segmentGlow(float3 ray, float3 start, float3 end) {
    float3 delta = end - start;
    float ratio = clamp(dot(ray - start, delta) / max(dot(delta, delta), .0001), 0.0, 1.0);
    float3 nearest = normalize(start + ratio * delta);
    float d = length(ray - nearest);
    float line = (1.0 - smoothstep(.0015, .0045, d)) * .30;
    float vertex = (1.0 - smoothstep(.003, .008, length(ray - start))) * .8;
    return line + vertex;
}
half4 main(float2 frag) {
    float2 uv = frag / u_resolution;
    float aspect = u_resolution.x / u_resolution.y;
    float2 view = float2((uv.x - .5) * aspect, .5 - uv.y);
    float3 dir = normalize(view.x * u_right + view.y * u_up - u_out * .91);
    float3 origin = u_origin;

    float4 nearStars = starLayer(origin, dir, 4.5, 115.0 * u_density, 2.4);
    float4 midStars  = starLayer(origin, dir, 15.0, 178.0 * u_density, 6.1);
    float4 farStars  = starLayer(origin, dir, 75.0, 265.0 * u_density, 10.5);

    float3 white = float3(.93, .96, 1.0);
    float3 tint = mix(float3(.45, .64, .96), u_accent, .48);
    float3 color = float3(.006, .009, .022);
    color += white * (farStars.x * .40 + midStars.x * .66 + nearStars.x * .95);
    color += tint * (farStars.y * .12 + midStars.y * .28 + nearStars.y * .40);
    color += tint * (farStars.x * farStars.z * .10 + nearStars.x * nearStars.z * .20);

    // Volumetric-looking haze is still a faint deterministic world direction
    // function, not a drifting screen-space overlay.
    float haloA = exp(-12.0 * length(dir - normalize(float3(.51, -.15, -.84))));
    float haloB = exp(-6.0 * length(dir - normalize(float3(-.50, .31, -.81))));
    color += tint * haloA * .036 + float3(.12, .16, .27) * haloB * .03;

    if (u_lines > .5) {
        // Hand-authored decorative celestial geometry, not an astronomy catalog.
        float3 a = normalize(float3(-.42, .32, -.85));
        float3 b = normalize(float3(-.24, .41, -.88));
        float3 c = normalize(float3(-.10, .18, -.97));
        float3 d = normalize(float3(.14, .29, -.94));
        float3 e = normalize(float3(.36, .11, -.92));
        float3 f = normalize(float3(.49, .36, -.79));
        float tracing = segmentGlow(dir,a,b) + segmentGlow(dir,b,c)
            + segmentGlow(dir,c,d) + segmentGlow(dir,d,e)
            + segmentGlow(dir,e,f);
        color += tint * tracing * .55;
    }

    float edge = length(float2((uv.x - .5) * .9, (uv.y - .5) * .65));
    color *= 1.0 - smoothstep(.34, .83, edge) * .17;
    return half4(half3(max(color, float3(0.0))), 1.0);
}
""".trimIndent()
    }
}
