package com.ngstudio.snowrace.game.render

import android.content.Context
import android.opengl.GLES30

enum class QualityTier { LOW, MEDIUM, HIGH }

/** Everything GL that lives as long as the EGL context: programs, atlases, shared meshes. */
class RenderResources(val context: Context, val tier: QualityTier) {
    val soft = tier == QualityTier.HIGH
    val lit = ShaderProgram("lit", Shaders.litVs(false), Shaders.litFs(false, soft))
    val outline = ShaderProgram("outline", Shaders.litVs(true), Shaders.litFs(true, false))
    val depth = ShaderProgram("depth", Shaders.depthVs(), Shaders.depthFs())
    val terrain = ShaderProgram("terrain", Shaders.terrainVs(), Shaders.terrainFs(soft))
    val water = ShaderProgram("water", Shaders.waterVs(), Shaders.waterFs(soft))
    val sky = ShaderProgram("sky", Shaders.skyVs(), Shaders.skyFs())
    val ball = ShaderProgram("ball", Shaders.ballVs(), Shaders.ballFs(soft))
    val bb = ShaderProgram("billboard", Shaders.bbVs(), Shaders.bbFs())

    val shadowEnabled = tier != QualityTier.LOW
    val shadow = ShadowMap(when (tier) { QualityTier.HIGH -> 2048; QualityTier.MEDIUM -> 1024; QualityTier.LOW -> 16 })

    val particleAtlas = ParticleAtlas()
    val iconAtlas = IconAtlas(context)

    val chars = CharBatches(
        InstanceBatch(Props.sphere(18), 256), InstanceBatch(Props.cone(), 64), InstanceBatch(Props.cylinder(), 64),
        InstanceBatch(Props.torus(), 64), InstanceBatch(Props.roundedBox(), 64),
    )
    val rig = CharacterRig(chars)
    val ballMesh = Props.snowball()
    val ballCasters = InstanceBatch(Props.sphere(14), 16)

    val bill = BillboardBatch(512)
    val ground = BillboardBatch(128)
    val iconBill = BillboardBatch(32)
    val labelBill = BillboardBatch(32)

    private val skyVao: Int
    val identity = FloatArray(16).also { com.ngstudio.snowrace.core.Mat4.identity(it) }

    init {
        skyVao = Gl.genVao()
        val vbo = Gl.genBuffer()
        GLES30.glBindVertexArray(skyVao)
        val b = Gl.floatBuffer(9)
        b.put(floatArrayOf(-1f, -1f, 0f, 3f, -1f, 0f, -1f, 3f, 0f)).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 36, b, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glBindVertexArray(0)
    }

    var time = 0f
    var width = 1
    var height = 1

    /** Uploads lighting / camera / grading uniforms shared by the lit family of shaders. */
    fun common(p: ShaderProgram, cam: Camera, rig: LightRig, viewProj: FloatArray = cam.viewProj) {
        p.use()
        p.mat4("uViewProj", viewProj)
        p.mat4("uLightVP", shadow.lightViewProj)
        p.f("uTime", time)
        p.vec3("uCamPos", cam.x, cam.y, cam.z)
        p.vec3("uCamPosF", cam.x, cam.y, cam.z)
        p.vec3("uSunDir", rig.sunDir)
        p.vec3("uSunColor", rig.sunColor)
        p.vec3("uAmbSky", rig.ambSky)
        p.vec3("uAmbGround", rig.ambGround)
        p.vec3("uRim", rig.rim)
        p.vec3("uFogColor", rig.fog)
        p.vec2("uFog", rig.fogStart, rig.fogEnd)
        p.vec4("uGrade", rig.saturation, rig.contrast, rig.brightness, rig.vignette)
        p.vec3("uGradeTint", rig.gradeTint)
        p.vec2("uScreen", width.toFloat(), height.toFloat())
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadow.texture)
        p.i("uShadow", 1)
        p.f("uShadowOn", if (shadowEnabled) 1f else 0f)
        p.f("uShadowTexel", 1f / shadow.size)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    fun drawSky(cam: Camera, rig: LightRig) {
        sky.use()
        common(sky, cam, rig)
        sky.mat4("uInvViewProj", cam.invViewProj)
        sky.vec3("uSkyTop", rig.skyTop)
        sky.vec3("uSkyHorizon", rig.skyHorizon)
        sky.f("uNight", rig.night)
        GLES30.glDepthMask(false)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glBindVertexArray(skyVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glDepthMask(true)
        GLES30.glDepthFunc(GLES30.GL_LESS)
    }

    /** Draws a list of instance batches with the lit program (expects [common] already applied). */
    fun drawLit(batches: Iterable<InstanceBatch>) { for (b in batches) b.draw() }

    fun beginOutline(cam: Camera, rig: LightRig, width: Float = 0.026f) {
        common(outline, cam, rig)
        outline.f("uOutline", width)
        outline.vec3("uOutlineColor", rig.outline)
        GLES30.glCullFace(GLES30.GL_FRONT)
    }

    fun endOutline() { GLES30.glCullFace(GLES30.GL_BACK) }

    fun beginShadowPass(): Boolean {
        if (!shadowEnabled || !shadow.ok) return false
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        shadow.begin()
        depth.use()
        depth.mat4("uViewProj", shadow.lightViewProj)
        depth.mat4("uLightVP", shadow.lightViewProj)
        depth.f("uTime", time)
        GLES30.glCullFace(GLES30.GL_BACK)
        return true
    }

    fun endShadowPass() { shadow.end(width, height) }

    fun beginBillboards(cam: Camera, ground: Boolean) {
        bb.use()
        bb.mat4("uViewProj", cam.viewProj)
        bb.vec3("uRight", cam.right[0], cam.right[1], cam.right[2])
        bb.vec3("uUp", cam.up[0], cam.up[1], cam.up[2])
        bb.f("uGround", if (ground) 1f else 0f)
        bb.i("uTex", 0)
    }

    fun release() {
        shadow.release()
        TextureUtil.delete(particleAtlas.texture)
        TextureUtil.delete(iconAtlas.texture)
        for (p in listOf(lit, outline, depth, terrain, water, sky, ball, bb)) p.delete()
    }
}
