package com.oddf0x.halynmotion

import android.content.res.AssetManager
import android.graphics.Color
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.view.Surface

/**
 * Explicit GPU backend selection. System Canvas remains the fully compatible
 * path; the direct APIs are for the ray-marched scenes with no clock overlay.
 * No fake Vulkan labels or runtime API redirection.
 */
object RaymarchScenes {
    const val FIRST = 72
    const val LAST = 74
    val NAMES = listOf("Glass Horizon", "Liquid Mirror", "Prism Monolith")
    fun supports(preset: Int) = preset in FIRST..LAST
}

object VulkanBridge {
    val loaded: Boolean by lazy {
        try {
            System.loadLibrary("halyn-vulkan")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private external fun nativeSupported(): Boolean
    private external fun nativeCreate(surface: Surface, assets: AssetManager): Long
    private external fun nativeDraw(
        handle: Long, time: Float, variant: Int, quality: Int,
        red: Float, green: Float, blue: Float, intensity: Float,
        offsetX: Float, offsetY: Float, depth: Float, gloss: Float
    ): Boolean
    private external fun nativeDestroy(handle: Long)

    fun supported(): Boolean = loaded && runCatching { nativeSupported() }.getOrDefault(false)
    fun create(surface: Surface, assets: AssetManager): Long =
        if (supported()) runCatching { nativeCreate(surface, assets) }.getOrDefault(0L) else 0L

    fun draw(handle: Long, time: Float, state: MotionState, offsetX: Float, offsetY: Float): Boolean {
        val color = if (state.palette == 5) state.customColor
            else HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0, 4)]
        return runCatching {
            nativeDraw(handle, time, state.preset - RaymarchScenes.FIRST,
                state.shaderQuality,
                Color.red(color) / 255f, Color.green(color) / 255f,
                Color.blue(color) / 255f, state.intensity,
                offsetX, offsetY, state.shadesDepth, state.shadesGloss)
        }.getOrDefault(false)
    }

    fun destroy(handle: Long) {
        if (handle != 0L && loaded) runCatching { nativeDestroy(handle) }
    }
}

/** OpenGL ES 3.0 owns the WallpaperService Surface only while it is active. */
class RaymarchOpenGl {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var vertexArray = IntArray(1)
    private var width = 1
    private var height = 1

    fun start(surface: Surface): Boolean {
        close()
        val success = try { initialize(surface) } catch (_: RuntimeException) { false }
        if (!success) close()
        return success
    }

    private fun initialize(surface: Surface): Boolean {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return false
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return false
            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, 0x0040, // EGL_OPENGL_ES3_BIT_KHR
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0,
                    configs, 0, 1, count, 0) || count[0] == 0) return false
            val config = configs[0] ?: return false
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            if (context == EGL14.EGL_NO_CONTEXT) return false
            eglSurface = EGL14.eglCreateWindowSurface(display, config, surface,
                intArrayOf(EGL14.EGL_NONE), 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) return false
            if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) return false
            val vertex = compile(GLES30.GL_VERTEX_SHADER, VERTEX)
            val fragment = compile(GLES30.GL_FRAGMENT_SHADER, FRAGMENT)
            if (vertex == 0 || fragment == 0) {
                if (vertex != 0) GLES30.glDeleteShader(vertex)
                if (fragment != 0) GLES30.glDeleteShader(fragment)
                return false
            }
            program = GLES30.glCreateProgram()
            GLES30.glAttachShader(program, vertex)
            GLES30.glAttachShader(program, fragment)
            GLES30.glLinkProgram(program)
            GLES30.glDeleteShader(vertex)
            GLES30.glDeleteShader(fragment)
            val status = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
            if (status[0] != GLES30.GL_TRUE) return false
            GLES30.glGenVertexArrays(1, vertexArray, 0)
            val value = IntArray(1)
            EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_WIDTH, value, 0)
            width = value[0].coerceAtLeast(1)
            EGL14.eglQuerySurface(display, eglSurface, EGL14.EGL_HEIGHT, value, 0)
            height = value[0].coerceAtLeast(1)
            return true
    }

    private fun compile(type: Int, source: String): Int {
        val result = GLES30.glCreateShader(type)
        if (result == 0) return 0
        GLES30.glShaderSource(result, source)
        GLES30.glCompileShader(result)
        val status = IntArray(1)
        GLES30.glGetShaderiv(result, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES30.GL_TRUE) {
            android.util.Log.e("HALYN_GPU", "OpenGL shader: " + GLES30.glGetShaderInfoLog(result))
            GLES30.glDeleteShader(result)
            return 0
        }
        return result
    }

    fun render(time: Float, state: MotionState, offsetX: Float, offsetY: Float): Boolean {
        if (program == 0 || display == EGL14.EGL_NO_DISPLAY) return false
        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) return false
        val color = if (state.palette == 5) state.customColor
            else HalynRenderer.PALETTE_COLORS[state.palette.coerceIn(0, 4)]
        GLES30.glViewport(0, 0, width, height)
        GLES30.glUseProgram(program)
        fun one(name: String, value: Float) {
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, name), value)
        }
        one("u_time", time * (.35f + state.speed * .65f))
        one("u_scene", (state.preset - RaymarchScenes.FIRST).toFloat())
        one("u_quality", state.shaderQuality.toFloat())
        one("u_intensity", state.intensity)
        one("u_depth", state.shadesDepth)
        one("u_gloss", state.shadesGloss)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "u_resolution"),
            width.toFloat(), height.toFloat())
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "u_offset"),
            offsetX, offsetY)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "u_accent"),
            Color.red(color) / 255f, Color.green(color) / 255f,
            Color.blue(color) / 255f)
        GLES30.glBindVertexArray(vertexArray[0])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glBindVertexArray(0)
        return EGL14.eglSwapBuffers(display, eglSurface)
    }

    fun close() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (context != EGL14.EGL_NO_CONTEXT && eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)
                if (program != 0) GLES30.glDeleteProgram(program)
                if (vertexArray[0] != 0) GLES30.glDeleteVertexArrays(1, vertexArray, 0)
            }
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
        program = 0
        vertexArray[0] = 0
    }

    companion object {
        private val VERTEX = """
            #version 300 es
            precision highp float;
            void main() {
                vec2 pos = vec2(
                    (gl_VertexID == 1) ? 3.0 : -1.0,
                    (gl_VertexID == 2) ? 3.0 : -1.0
                );
                gl_Position = vec4(pos, 0.0, 1.0);
            }
        """.trimIndent()

        private val FRAGMENT = """
            #version 300 es
            precision highp float;
            uniform vec2 u_resolution;
            uniform vec2 u_offset;
            uniform vec3 u_accent;
            uniform float u_time;
            uniform float u_scene;
            uniform float u_quality;
            uniform float u_intensity;
            uniform float u_depth;
            uniform float u_gloss;
            out vec4 outColor;

            float sceneSdf(vec3 p) {
                if (u_scene < .5) {
                    p.x += .12 * sin(p.y * 3.1 + u_time * .37);
                    float sph = length(p * vec3(1.0, .87, 1.11)) - 1.02;
                    return sph + .055 * sin(p.x * 12.0 + p.y * 9.0 - u_time);
                }
                if (u_scene < 1.5) {
                    vec2 q = vec2(length(p.xy) - 1.02, p.z);
                    float torus = length(q) - .38;
                    return torus + .035 * sin(p.x * 13.0 - p.y * 8.0 + u_time);
                }
                p.xy *= mat2(cos(u_time * .12), -sin(u_time * .12),
                              sin(u_time * .12), cos(u_time * .12));
                vec3 d = abs(p) - vec3(.75, 1.12, .73);
                return length(max(d, 0.0)) + min(max(d.x, max(d.y,d.z)), 0.0) - .13;
            }
            vec2 march(vec3 ro, vec3 rd, int maxSteps) {
                float distanceTravelled = 0.0;
                for (int i = 0; i < 48; ++i) {
                    if (i >= maxSteps || distanceTravelled > 12.0) break;
                    float stepDistance = sceneSdf(ro + rd * distanceTravelled);
                    if (stepDistance < .008) return vec2(distanceTravelled, 1.0);
                    distanceTravelled += max(stepDistance * .82, .012);
                }
                return vec2(distanceTravelled, 0.0);
            }
            vec3 normalAt(vec3 p) {
                float eps = .012;
                return normalize(vec3(
                    sceneSdf(p + vec3(eps, 0.0, 0.0)) - sceneSdf(p - vec3(eps, 0.0, 0.0)),
                    sceneSdf(p + vec3(0.0, eps, 0.0)) - sceneSdf(p - vec3(0.0, eps, 0.0)),
                    sceneSdf(p + vec3(0.0, 0.0, eps)) - sceneSdf(p - vec3(0.0, 0.0, eps))
                ));
            }
            vec3 environment(vec3 dir) {
                float haze = pow(max(0.0, 1.0 - abs(dir.y + .2)), 5.0);
                float stripe = pow(max(0.0, sin(dir.x * 18.0 + dir.y * 11.0 + u_time * .18)), 13.0);
                return vec3(.009, .015, .037) + u_accent * (haze * .28 + stripe * .16);
            }
            vec3 filmic(vec3 v) {
                return clamp(v * (2.51 * v + .03) / (v * (2.43 * v + .59) + .14), 0.0, 1.0);
            }
            void main() {
                vec2 p = (gl_FragCoord.xy - u_resolution * .5) /
                          min(u_resolution.x, u_resolution.y);
                p -= u_offset * .07;
                vec3 ro = vec3(0.0, 0.0, -4.0);
                vec3 rd = normalize(vec3(p * 1.55, 1.7));
                int steps = u_quality > 1.5 ? 42 : (u_quality > .5 ? 30 : 20);
                vec2 hit = march(ro, rd, steps);
                vec3 col = environment(rd);
                if (hit.y > .5) {
                    vec3 position = ro + rd * hit.x;
                    vec3 n = normalAt(position);
                    vec3 keyLight = normalize(vec3(-.6, .8, -1.2));
                    vec3 view = -rd;
                    float diffuse = max(dot(n, keyLight), 0.0);
                    float fresnel = pow(1.0 - max(dot(n, view), 0.0), 5.0);
                    float ambient = .14 + .45 * max(n.y, 0.0);
                    float roughness = clamp(1.0 - u_gloss * .40, .17, 1.0);
                    float spec = pow(max(dot(reflect(-keyLight, n), view), 0.0),
                                     mix(14.0, 108.0, 1.0 - roughness));
                    vec3 reflected = reflect(rd, n);
                    vec2 bounce = march(position + n * .046, reflected,
                                        u_quality > 1.5 ? 15 : 8);
                    vec3 reflection = environment(reflected);
                    if (bounce.y > .5) {
                        vec3 secondaryN = normalAt(position + n * .046 + reflected * bounce.x);
                        reflection = environment(reflect(reflected, secondaryN)) +
                                     u_accent * .18;
                    }
                    col = u_accent * (ambient + diffuse * .34) +
                          reflection * (.43 + fresnel * .7) +
                          vec3(.94, .97, 1.0) * spec * (.40 + .48 * u_gloss);
                    col *= .62 + .38 * clamp(u_depth, .4, 2.0);
                    col += u_accent * fresnel * .44;
                }
                col *= max(.5, u_intensity);
                col = pow(filmic(col * 1.27), vec3(.95));
                float grain = fract(52.9829189 *
                    fract(gl_FragCoord.x * .06711056 + gl_FragCoord.y * .00583715)) - .5;
                outColor = vec4(clamp(col + vec3(grain / 255.0), 0.0, 1.0), 1.0);
            }
        """.trimIndent()
    }
}
