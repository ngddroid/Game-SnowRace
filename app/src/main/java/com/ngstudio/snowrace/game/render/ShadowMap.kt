package com.ngstudio.snowrace.game.render

import android.opengl.GLES30
import com.ngstudio.snowrace.core.Mat4
import kotlin.math.floor

/** Directional-light shadow map (depth texture with hardware compare for PCF). */
class ShadowMap(val size: Int) {
    val texture: Int = Gl.genTexture()
    private val fbo: Int = Gl.genFbo()
    val lightView = FloatArray(16)
    val lightProj = FloatArray(16)
    val lightViewProj = FloatArray(16)
    var ok = true
        private set

    init {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24, size, size, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_COMPARE_REF_TO_TEXTURE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_FUNC, GLES30.GL_LEQUAL)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, texture, 0)
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
        GLES30.glReadBuffer(GLES30.GL_NONE)
        ok = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    /**
     * Fits an orthographic light frustum around [cx,cy,cz] with half-extent [radius].
     * The center is snapped to shadow texels so shadows don't shimmer while the camera moves.
     */
    fun update(sunX: Float, sunY: Float, sunZ: Float, cx: Float, cy: Float, cz: Float, radius: Float) {
        val dist = radius * 2.5f
        Mat4.lookAt(lightView, cx + sunX * dist, cy + sunY * dist, cz + sunZ * dist, cx, cy, cz, 0f, 1f, 0f)
        // snap translation to texel grid in light space
        val texel = radius * 2f / size
        lightView[12] = floor(lightView[12] / texel) * texel
        lightView[13] = floor(lightView[13] / texel) * texel
        Mat4.ortho(lightProj, -radius, radius, -radius, radius, 0.5f, dist * 2f + radius)
        Mat4.mul(lightViewProj, lightProj, lightView)
    }

    fun begin() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(0, 0, size, size)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(2.0f, 3.0f)
    }

    fun end(viewW: Int, viewH: Int) {
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, viewW, viewH)
    }

    fun release() {
        GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        TextureUtil.delete(texture)
    }
}
