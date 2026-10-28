package org.opencarproject.launcher.presentation.reverse

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import org.opencarproject.launcher.core.pure.ReverseImageTuning
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Cleans the analog reverse video on the GPU before it reaches [output] (the preview's
 * TextureView). Pass 1, at camera resolution: blends interlaced lines, a 3×3 edge-preserving
 * (bilateral) denoise, and a motion-adaptive average with the previous frames (temporal denoise,
 * which removes most of the analog grain). Pass 2, at screen size: a light unsharp mask.
 *
 * The camera renders into [input] (handed out through [onInputReady] on the main thread). If GL
 * can't be set up, [onFailed] runs on the main thread and [output] is free again for a direct
 * preview.
 */
class ReverseFilterRenderer(
    private val output: SurfaceTexture,
    outputWidth: Int,
    outputHeight: Int,
    private val inputSize: Size,
    private val onInputReady: (SurfaceTexture) -> Unit,
    private val onFailed: () -> Unit,
) {
    private val thread = HandlerThread("ReverseFilter").apply { start() }
    private val gl = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var outWidth = outputWidth
    @Volatile private var outHeight = outputHeight
    @Volatile private var tuning = ReverseImageTuning.of(denoise = 0.6f, sharpness = 0.3f)

    /** New slider values; applied from the next frame. */
    fun setTuning(value: ReverseImageTuning) {
        tuning = value
    }

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var input: SurfaceTexture? = null
    private var cameraTexture = 0
    private val frameTextures = IntArray(2)
    private val frameBuffers = IntArray(2)
    private var current = 0
    private var hasHistory = false
    private var denoise = 0
    private var sharpen = 0
    private val transform = FloatArray(16)
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(QUAD.size * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply { put(QUAD).position(0) }

    init {
        gl.post {
            runCatching(::setUp).onFailure {
                Log.w(TAG, "GL filter unavailable, falling back to the direct preview", it)
                tearDown()
                main.post(onFailed)
            }
        }
    }

    fun resize(width: Int, height: Int) {
        outWidth = width
        outHeight = height
    }

    fun release() {
        gl.post(::tearDown)
        thread.quitSafely()
    }

    private fun setUp() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE,
        )
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) { "eglChooseConfig" }
        val config = configs[0]
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext" }
        surface = EGL14.eglCreateWindowSurface(display, config, output, intArrayOf(EGL14.EGL_NONE), 0)
        check(surface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface" }
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent" }

        denoise = program(VERTEX, DENOISE)
        sharpen = program(VERTEX, SHARPEN)

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        cameraTexture = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
        linearClamp(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)

        // Two frames at camera resolution, ping-ponged: one is written, the other is the history.
        GLES20.glGenTextures(2, frameTextures, 0)
        GLES20.glGenFramebuffers(2, frameBuffers, 0)
        for (i in 0..1) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTextures[i])
            linearClamp(GLES20.GL_TEXTURE_2D)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, inputSize.width, inputSize.height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, frameTextures[i], 0)
            check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) { "framebuffer" }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        val texture = SurfaceTexture(cameraTexture).apply {
            setDefaultBufferSize(inputSize.width, inputSize.height)
            setOnFrameAvailableListener({ draw() }, gl)
        }
        input = texture
        main.post { onInputReady(texture) }
    }

    private fun draw() {
        val texture = input ?: return
        runCatching {
            texture.updateTexImage()
            texture.getTransformMatrix(transform)
            val history = 1 - current

            // Pass 1: deinterlace + bilateral + temporal, into the current frame buffer.
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[current])
            GLES20.glViewport(0, 0, inputSize.width, inputSize.height)
            GLES20.glUseProgram(denoise)
            bindQuad(denoise)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(denoise, "uTransform"), 1, false, transform, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(denoise, "uCamera"), 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTextures[history])
            GLES20.glUniform1i(GLES20.glGetUniformLocation(denoise, "uHistory"), 1)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(denoise, "uHasHistory"), if (hasHistory) 1f else 0f)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(denoise, "uTexel"), 1f / inputSize.width, 1f / inputSize.height)
            val t = tuning
            GLES20.glUniform1f(GLES20.glGetUniformLocation(denoise, "uStillWeight"), t.stillWeight)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(denoise, "uMotionThreshold"), t.motionThreshold)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(denoise, "uEdgeFalloff"), t.edgeFalloff)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(denoise, "uChroma"), t.chromaSmoothing)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // Pass 2: unsharp mask, to the screen.
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, outWidth, outHeight)
            GLES20.glUseProgram(sharpen)
            bindQuad(sharpen)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(sharpen, "uTransform"), 1, false, IDENTITY, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTextures[current])
            GLES20.glUniform1i(GLES20.glGetUniformLocation(sharpen, "uFrame"), 0)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(sharpen, "uTexel"), 1f / inputSize.width, 1f / inputSize.height)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(sharpen, "uAmount"), t.sharpen)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            EGL14.eglSwapBuffers(display, surface)

            hasHistory = true
            current = history
        }.onFailure { Log.w(TAG, "frame dropped", it) }
    }

    private fun bindQuad(program: Int) {
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val uv = GLES20.glGetAttribLocation(program, "aUv")
        quad.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(position)
        quad.position(2)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(uv)
    }

    private fun tearDown() {
        input?.setOnFrameAvailableListener(null)
        input?.release()
        input = null
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglMakeCurrent(display, surface, surface, context)
                if (cameraTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(cameraTexture), 0)
                GLES20.glDeleteTextures(2, frameTextures, 0)
                GLES20.glDeleteFramebuffers(2, frameBuffers, 0)
                if (denoise != 0) GLES20.glDeleteProgram(denoise)
                if (sharpen != 0) GLES20.glDeleteProgram(sharpen)
            }
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        surface = EGL14.EGL_NO_SURFACE
    }

    private fun linearClamp(target: Int) {
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun program(vertex: String, fragment: String): Int {
        fun shader(type: Int, source: String): Int = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, source)
            GLES20.glCompileShader(it)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(it, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "shader: " + GLES20.glGetShaderInfoLog(it) }
        }
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, vertex))
            GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, fragment))
            GLES20.glLinkProgram(it)
            val ok = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, ok, 0)
            check(ok[0] != 0) { "link: " + GLES20.glGetProgramInfoLog(it) }
        }
    }

    private companion object {
        const val TAG = "ReverseFilter"

        /** Triangle strip: x, y, u, v. */
        val QUAD = floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)
        val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

        const val VERTEX = """
            attribute vec4 aPosition;
            attribute vec4 aUv;
            uniform mat4 uTransform;
            varying vec2 vUv;
            varying vec2 vCamera;
            void main() {
                gl_Position = aPosition;
                vUv = aUv.xy;
                vCamera = (uTransform * aUv).xy;
            }
        """

        const val DENOISE = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uCamera;
            uniform sampler2D uHistory;
            uniform float uHasHistory;
            uniform vec2 uTexel;
            uniform float uStillWeight;
            uniform float uMotionThreshold;
            uniform float uEdgeFalloff;
            uniform float uChroma;
            varying vec2 vUv;
            varying vec2 vCamera;
            const vec3 LUMA = vec3(0.299, 0.587, 0.114);
            vec3 px(float x, float y) { return texture2D(uCamera, vCamera + vec2(x, y) * uTexel).rgb; }
            float lumaWeight(float center, float sample) {
                float d = sample - center;
                return exp(-d * d * uEdgeFalloff * 4.0);
            }
            void main() {
                vec3 s0 = px(0.0, 0.0);
                vec3 s1 = px(0.0, -1.0);
                vec3 s2 = px(0.0, 1.0);
                vec3 s3 = px(-1.0, 0.0);
                vec3 s4 = px(1.0, 0.0);
                vec3 s5 = px(-1.0, -1.0);
                vec3 s6 = px(1.0, -1.0);
                vec3 s7 = px(-1.0, 1.0);
                vec3 s8 = px(1.0, 1.0);
                // Interlaced lines: blend each line with its neighbours (removes combing).
                vec3 center = s0 * 0.5 + (s1 + s2) * 0.25;
                vec3 mean = (s0 + s1 + s2 + s3 + s4 + s5 + s6 + s7 + s8) / 9.0;
                // Luma: edge-preserving (bilateral) average.
                float y0 = dot(center, LUMA);
                float w = 1.0;
                float y = y0;
                float l; float k;
                l = dot(s1, LUMA); k = lumaWeight(y0, l); y += l * k; w += k;
                l = dot(s2, LUMA); k = lumaWeight(y0, l); y += l * k; w += k;
                l = dot(s3, LUMA); k = lumaWeight(y0, l); y += l * k; w += k;
                l = dot(s4, LUMA); k = lumaWeight(y0, l); y += l * k; w += k;
                l = dot(s5, LUMA); k = lumaWeight(y0, l); y += l * k; w += k;
                l = dot(s6, LUMA); k = lumaWeight(y0, l); y += l * k; w += k;
                l = dot(s7, LUMA); k = lumaWeight(y0, l); y += l * k; w += k;
                l = dot(s8, LUMA); k = lumaWeight(y0, l); y += l * k; w += k;
                y /= w;
                // Chroma: analog colour speckles have no edges worth keeping, so average them away.
                vec3 chroma = mix(center - y0, mean - dot(mean, LUMA), uChroma);
                vec3 clean = clamp(vec3(y) + chroma, 0.0, 1.0);
                // Temporal: average with history while still; follow the new frame where luma really moved
                // (measured on the 3x3 mean, so grain alone doesn't count as motion).
                vec3 history = texture2D(uHistory, vUv).rgb;
                float motion = abs(dot(mean, LUMA) - dot(history, LUMA));
                float fresh = mix(uStillWeight, 1.0, smoothstep(uMotionThreshold, uMotionThreshold * 2.5, motion));
                gl_FragColor = vec4(mix(history, clean, uHasHistory > 0.5 ? fresh : 1.0), 1.0);
            }
        """

        const val SHARPEN = """
            precision mediump float;
            uniform sampler2D uFrame;
            uniform vec2 uTexel;
            uniform float uAmount;
            varying vec2 vUv;
            void main() {
                vec3 c = texture2D(uFrame, vUv).rgb;
                vec3 blur = (texture2D(uFrame, vUv + vec2(uTexel.x, 0.0)).rgb
                    + texture2D(uFrame, vUv - vec2(uTexel.x, 0.0)).rgb
                    + texture2D(uFrame, vUv + vec2(0.0, uTexel.y)).rgb
                    + texture2D(uFrame, vUv - vec2(0.0, uTexel.y)).rgb) * 0.25;
                gl_FragColor = vec4(clamp(c + (c - blur) * uAmount, 0.0, 1.0), 1.0);
            }
        """
    }
}
