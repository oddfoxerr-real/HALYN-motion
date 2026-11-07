package com.oddf0x.halynmotion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build

/**
 * HALYN SHADES — a separate procedural material renderer.
 *
 * Each material defines a height field, surface response and colour profile.
 * Normals are reconstructed from the procedural height field at runtime.
 * Lighting combines two directional sources, a broad ambient term, Fresnel
 * edge response, and an analytical emission/halo layer. It is not a PBR
 * ray tracer, path tracer, or multipass real-time reflection system.
 *
 * The renderer never uses screen capture, camera input or downloaded assets.
 * RuntimeShader objects are rebuilt when the wallpaper surface is recreated.
 */
class ShadesRenderer {
    companion object {
        const val FIRST = 64
        const val COUNT = 8
        const val LAST = FIRST + COUNT - 1

        val SCENES = listOf(
            "Obsidian Current",
            "Mercury Fold",
            "Opaline Depth",
            "Aurora Satin",
            "Crystal Facets",
            "Abyssal Tide",
            "Molten Veins",
            "Prismatic Rift"
        )

        fun isShades(preset: Int): Boolean = preset in FIRST..LAST

        private val AGSL = """
uniform float2 u_resolution;
uniform float2 u_touch;
uniform float2 u_parallax;
uniform float3 u_accent;
uniform float u_time;
uniform float u_variant;
uniform float u_quality;
uniform float u_energy;
uniform float u_depth;
uniform float u_gloss;
uniform float u_glow;

float hash21(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float noise2(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(hash21(i), hash21(i + float2(1.0, 0.0)), f.x);
    float b = mix(hash21(i + float2(0.0, 1.0)),
                  hash21(i + float2(1.0, 1.0)), f.x);
    return mix(a, b, f.y);
}

float fbm(float2 p, float quality) {
    float n = noise2(p) * .56;
    n += noise2(p * 2.13 + float2(7.3, 4.7)) * .29;
    if (quality > 1.5) {
        n += noise2(p * 4.27 - float2(5.8, 8.2)) * .15;
    }
    return n;
}

// A family of smoothly deforming surface height fields, in world space.
float heightField(float2 p, float t, float material, float quality) {
    float2 q = p;
    if (quality > 1.5) {
        float warp = fbm(q * 2.0 + float2(t * .11, -t * .075), quality);
        q += .095 * float2(warp - .5, .5 - warp);
    }

    if (material < .5) {
        // Black glass: twisting channels and thick viscous ridges.
        float wave = sin(q.x * 9.7 + 2.0 * sin(q.y * 3.6 - t * .38));
        return .50 + .31 * wave + .12 * cos(q.y * 8.2 + t * .28);
    }
    if (material < 1.5) {
        // Liquid metal: concentric folds distorted by moving currents.
        float2 s = q + .11 * float2(sin(q.y * 5.8 - t * .6),
                                   cos(q.x * 7.0 + t * .43));
        return .49 + .32 * sin(21.0 * length(s + float2(.13, -.24))
            - t * 1.45 + sin(s.y * 9.0)) + .09 * cos(s.x * 16.0);
    }
    if (material < 2.5) {
        // Glass/opal: suspended fluid lenses, with caustic ridges.
        float2 s = q + .13 * float2(sin(q.y * 4.2 + t * .22),
                                   cos(q.x * 5.1 - t * .31));
        float lenses = sin(length(s * float2(1.2, .8)) * 18.0 - t * .53);
        return .53 + .25 * lenses + .17 * sin(s.x * 8.0 + s.y * 5.0);
    }
    if (material < 3.5) {
        // Satin folds: tall, continuous ribbons with asymmetrical pleats.
        float fold = q.x * 15.0 + 2.5 * sin(q.y * 4.8 - t * .43);
        return .52 + .32 * sin(fold) + .10 * sin(fold * .52 + q.y * 7.0);
    }
    if (material < 4.5) {
        // Angular crystal lattice: faceted cells with different heights.
        float2 cell = q * 6.3;
        float2 id = floor(cell);
        float2 local = abs(fract(cell) - .5);
        float facet = 1.0 - max(local.x, local.y) * 2.0;
        float ridge = abs(local.x - local.y);
        return .22 + .51 * facet + .15 * hash21(id) - .12 * ridge;
    }
    if (material < 5.5) {
        // Deep ocean: long rolling swells with superimposed micro-ripples.
        float swell = sin(q.y * 7.0 + 1.5 * sin(q.x * 3.5 - t * .32));
        float ripple = sin(q.y * 37.0 - t * 1.2 + q.x * 10.0);
        return .49 + .33 * swell + .10 * ripple;
    }
    if (material < 6.5) {
        // Volcanic marble: pressure-folded geology and luminous seams.
        float2 s = q + .16 * float2(sin(q.y * 4.0 - t * .2),
                                   cos(q.x * 5.0 + t * .15));
        return .49 + .26 * sin(s.x * 11.0 + sin(s.y * 7.0) * 2.1)
                   + .15 * cos(s.y * 10.0 - t * .22);
    }
    // Iridescent rift: layered folds that diverge around an off-axis seam.
    float ang = atan(q.y + .17, q.x - .08);
    float rad = length(q - float2(.08, -.17));
    return .49 + .29 * sin(ang * 7.0 + rad * 19.0 - t * .63)
               + .13 * cos(q.x * 12.0 - q.y * 8.0 + t * .24);
}

float3 filmic(float3 x) {
    x = max(x, float3(0.0));
    return clamp((x * (2.51 * x + float3(.03))) /
                 (x * (2.43 * x + float3(.59)) + float3(.14)),
                 float3(0.0), float3(1.0));
}

half4 main(float2 frag) {
    float2 res = max(u_resolution, float2(1.0));
    float shortSide = min(res.x, res.y);
    float2 uv = frag / res;
    float2 p = (frag - res * .5) / shortSide;
    p -= u_parallax * float2(.085, .11);
    float t = u_time;
    float mat = u_variant;
    float quality = u_quality;
    float3 accent = clamp(u_accent, float3(0.0), float3(1.0));
    float3 complementary = mix(accent.zxy, accent, .31);
    float3 hot = mix(float3(.85, .93, 1.0), accent, .30);
    float3 deep = mix(float3(.003, .008, .022), accent, .038);

    // Far-field atmosphere is independently parallaxed from material surface.
    float2 far = p * .66 + u_parallax * .027;
    float radial = length(far);
    float mist = exp(-2.1 * length(far + float2(.19, -.27)));
    float3 col = deep + accent * (.036 + .08 * mist);
    col += complementary * .042 * (1.0 - smoothstep(.1, 1.6, radial));

    // The material's height function drives both the geometry and lighting.
    float h = heightField(p, t, mat, quality);
    float epsilon = quality < .5 ? .016 : .008;
    float hx = heightField(p + float2(epsilon, 0.0), t, mat, quality);
    float hy = heightField(p + float2(0.0, epsilon), t, mat, quality);
    float2 slope = float2((hx - h) / epsilon, (hy - h) / epsilon);
    float3 n = normalize(float3(-slope * u_depth * .34, 1.0));
    float3 view = normalize(float3(-p.x * .22, -p.y * .22, 1.7));
    float3 keyLight = normalize(float3(-.55, -.67, 1.0));
    float3 fillLight = normalize(float3(.72, .22, .82));
    float diffuse = max(dot(n, keyLight), 0.0);
    float fill = max(dot(n, fillLight), 0.0);
    float3 halfKey = normalize(keyLight + view);
    float3 halfFill = normalize(fillLight + view);
    float roughness = clamp(1.25 - u_gloss * .50, .16, 1.0);
    float power = mix(14.0, 110.0, 1.0 - roughness);
    float keySpec = pow(max(dot(n, halfKey), 0.0), power);
    float fillSpec = pow(max(dot(n, halfFill), 0.0), power * .62);
    float fresnel = pow(1.0 - clamp(dot(n, view), 0.0, 1.0), 5.0);
    float occlusion = clamp(.84 + n.z * .16 - length(slope) * .018, .38, 1.0);

    float metallic = 0.0;
    if (mat < 1.5 || (mat > 3.5 && mat < 4.5) || (mat > 5.5 && mat < 6.5)) {
        metallic = 1.0;
    }
    float translucent = 0.0;
    if ((mat > 1.5 && mat < 2.5) || (mat > 4.5 && mat < 5.5) || mat > 6.5) {
        translucent = 1.0;
    }

    float light = (.11 + .68 * diffuse + .23 * fill) * occlusion;
    float3 surface = mix(complementary * .20, accent * .64, smoothstep(.15, .90, h));
    surface = mix(surface, accent * (.29 + h * .40), metallic * .55);
    col += surface * light * (u_energy * .85);

    // Simulated self-shadow along the key-light direction. This remains
    // analytical (no expensive secondary raymarch).
    float shadow = smoothstep(-.45, .55, dot(normalize(slope + float2(.0001)),
                                               float2(-.62, -.78)));
    col *= .75 + .25 * shadow;

    // Reflection is two lobes, rim Fresnel and a broad tinted environment.
    float3 specColor = mix(hot, accent, metallic * .68);
    col += specColor * (keySpec * (1.18 - roughness * .34) +
                         fillSpec * .38) * u_gloss * (.4 + .6 * u_energy);
    col += mix(complementary, hot, .55) * fresnel *
           (.17 + .32 * metallic + .27 * translucent);
    col += hot * pow(max(n.z, 0.0), 6.0) * .065;

    // Material-specific emissive structure. No generic circles/icons.
    float emission = 0.0;
    if (mat < .5) {
        emission = exp(-23.0 * abs(h - .68)) * .44;
    } else if (mat < 1.5) {
        emission = exp(-21.0 * abs(h - .55)) * .24;
    } else if (mat < 2.5) {
        emission = exp(-26.0 * abs(h - .68)) * .56;
    } else if (mat < 3.5) {
        emission = pow(max(0.0, diffuse), 7.0) * .20;
    } else if (mat < 4.5) {
        emission = exp(-16.0 * abs(h - .42)) * .21;
    } else if (mat < 5.5) {
        emission = exp(-31.0 * abs(h - .73)) * .26;
    } else if (mat < 6.5) {
        emission = exp(-30.0 * abs(h - .52)) * .92;
    } else {
        emission = exp(-28.0 * abs(h - .64)) * .63;
    }
    float2 touch = (u_touch - .5) * res / shortSide;
    float touchLight = exp(-9.0 * length(p - touch)) * .11;
    col += (accent * .84 + hot * .16) * (emission * u_glow + touchLight) * u_energy;

    // High-quality grade: weak caustics and fine procedural density in glass.
    if (quality > 1.5) {
        float density = fbm(p * 4.2 + float2(t * .035, -t * .045), quality);
        col += complementary * density * .045 * (.4 + .6 * translucent);
        float caustic = pow(max(0.0, 1.0 - abs(h - .67) * 5.0), 5.0);
        col += hot * caustic * .11 * translucent;
    }

    // Gentle atmospheric absorption at the edges for spatial depth.
    float edge = 1.0 - smoothstep(.42, 1.6, length(p));
    col *= .68 + .32 * edge;
    col = filmic(col * 1.58);
    col = pow(col, float3(.94));

    // A fixed sub-LSB ordered dither helps gradients without animated grain.
    float dither = fract(52.9829189 *
        fract(frag.x * .06711056 + frag.y * .00583715)) - .5;
    col = clamp(col + float3(dither / 255.0), float3(0.0), float3(1.0));
    return half4(half3(col), 1.0);
}
""".trimIndent()
    }

    private var shader: RuntimeShader? = null
    private var failed = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private var fallback: LinearGradient? = null
    private var fallbackHeight = -1
    private var fallbackColor = 0
    private var fallbackVariant = -1

    fun resetHardwareState() {
        shader = null
        failed = false
    }

    fun draw(
        canvas: Canvas,
        state: MotionState,
        time: Float,
        touchX: Float,
        touchY: Float,
        offsetX: Float,
        gyro: ParallaxPose
    ) {
        val w = canvas.width.coerceAtLeast(1).toFloat()
        val h = canvas.height.coerceAtLeast(1).toFloat()
        val accent = if (state.palette == 5) state.customColor
            else HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0, 4)]
        if (canvas.isHardwareAccelerated && Build.VERSION.SDK_INT >= 33 && !failed) {
            try {
                val active = shader ?: RuntimeShader(AGSL).also { shader = it }
                active.setFloatUniform("u_resolution", w, h)
                active.setFloatUniform("u_time", time * (.35f + .65f * state.speed))
                active.setFloatUniform("u_variant", (state.preset - FIRST).toFloat())
                active.setFloatUniform("u_quality", state.shaderQuality.toFloat())
                active.setFloatUniform("u_energy", state.intensity)
                active.setFloatUniform("u_depth", state.shadesDepth)
                active.setFloatUniform("u_gloss", state.shadesGloss)
                active.setFloatUniform("u_glow", state.shadesGlow)
                active.setFloatUniform(
                    "u_accent",
                    Color.red(accent) / 255f,
                    Color.green(accent) / 255f,
                    Color.blue(accent) / 255f
                )
                active.setFloatUniform(
                    "u_parallax",
                    gyro.x + if (state.parallax) (offsetX - .5f) * .35f else 0f,
                    gyro.y
                )
                active.setFloatUniform(
                    "u_touch",
                    if (state.touch) touchX.coerceIn(0f, 1f) else .5f,
                    if (state.touch) touchY.coerceIn(0f, 1f) else .5f
                )
                paint.shader = active
                canvas.drawRect(0f, 0f, w, h, paint)
                return
            } catch (_: RuntimeException) {
                failed = true
                shader = null
                paint.shader = null
            } catch (_: IllegalArgumentException) {
                failed = true
                shader = null
                paint.shader = null
            }
        }
        drawFallback(canvas, accent, state.preset)
    }

    private fun drawFallback(canvas: Canvas, accent: Int, variant: Int) {
        val h = canvas.height.coerceAtLeast(1)
        if (fallback == null || fallbackHeight != h ||
            fallbackColor != accent || fallbackVariant != variant) {
            val colorOffset = (variant - FIRST) * 7
            val upper = Color.rgb(
                (Color.red(accent) / 8 + colorOffset).coerceIn(0, 255),
                (Color.green(accent) / 8 + colorOffset / 2).coerceIn(0, 255),
                (Color.blue(accent) / 5 + 12).coerceIn(0, 255)
            )
            val mid = Color.rgb(
                (Color.red(accent) / 3).coerceIn(0, 255),
                (Color.green(accent) / 3).coerceIn(0, 255),
                (Color.blue(accent) / 3).coerceIn(0, 255)
            )
            fallback = LinearGradient(0f, 0f, 0f, h.toFloat(),
                upper, mid, Shader.TileMode.CLAMP)
            fallbackHeight = h
            fallbackColor = accent
            fallbackVariant = variant
        }
        fallbackPaint.shader = fallback
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), h.toFloat(), fallbackPaint)
    }
}
