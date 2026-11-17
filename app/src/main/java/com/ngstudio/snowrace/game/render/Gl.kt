package com.ngstudio.snowrace.game.render

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer

object Gl {
    fun floatBuffer(n: Int): FloatBuffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    fun intBuffer(n: Int): IntBuffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
    fun shortBuffer(n: Int): ShortBuffer = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
    fun byteBuffer(n: Int): ByteBuffer = ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder())

    fun genBuffer(): Int { val a = IntArray(1); GLES30.glGenBuffers(1, a, 0); return a[0] }
    fun genVao(): Int { val a = IntArray(1); GLES30.glGenVertexArrays(1, a, 0); return a[0] }
    fun genTexture(): Int { val a = IntArray(1); GLES30.glGenTextures(1, a, 0); return a[0] }
    fun genFbo(): Int { val a = IntArray(1); GLES30.glGenFramebuffers(1, a, 0); return a[0] }

    fun check(tag: String) {
        var e = GLES30.glGetError()
        while (e != GLES30.GL_NO_ERROR) {
            Log.e("GL", "$tag: error 0x${Integer.toHexString(e)}")
            e = GLES30.glGetError()
        }
    }
}

/** Compiled GLSL program with cached uniform locations. */
class ShaderProgram(name: String, vs: String, fs: String) {
    val id: Int
    private val locs = HashMap<String, Int>()

    init {
        val v = compile(GLES30.GL_VERTEX_SHADER, vs, name)
        val f = compile(GLES30.GL_FRAGMENT_SHADER, fs, name)
        id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, v)
        GLES30.glAttachShader(id, f)
        GLES30.glLinkProgram(id)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(id)
            Log.e("Shader", "Link failed [$name]: $log")
            throw RuntimeException("Link failed [$name]: $log")
        }
        GLES30.glDeleteShader(v)
        GLES30.glDeleteShader(f)
    }

    private fun compile(type: Int, src: String, name: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(s)
            Log.e("Shader", "Compile failed [$name ${if (type == GLES30.GL_VERTEX_SHADER) "VS" else "FS"}]: $log")
            throw RuntimeException("Compile failed [$name]: $log")
        }
        return s
    }

    fun use() = GLES30.glUseProgram(id)

    fun loc(name: String): Int = locs.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

    fun mat4(name: String, m: FloatArray) { val l = loc(name); if (l >= 0) GLES30.glUniformMatrix4fv(l, 1, false, m, 0) }
    fun vec2(name: String, x: Float, y: Float) { val l = loc(name); if (l >= 0) GLES30.glUniform2f(l, x, y) }
    fun vec3(name: String, x: Float, y: Float, z: Float) { val l = loc(name); if (l >= 0) GLES30.glUniform3f(l, x, y, z) }
    fun vec3(name: String, v: FloatArray) { val l = loc(name); if (l >= 0) GLES30.glUniform3f(l, v[0], v[1], v[2]) }
    fun vec4(name: String, x: Float, y: Float, z: Float, w: Float) { val l = loc(name); if (l >= 0) GLES30.glUniform4f(l, x, y, z, w) }
    fun f(name: String, v: Float) { val l = loc(name); if (l >= 0) GLES30.glUniform1f(l, v) }
    fun i(name: String, v: Int) { val l = loc(name); if (l >= 0) GLES30.glUniform1i(l, v) }

    fun delete() { GLES30.glDeleteProgram(id) }
}
