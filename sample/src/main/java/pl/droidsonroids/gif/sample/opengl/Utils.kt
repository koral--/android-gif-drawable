package pl.droidsonroids.gif.sample.opengl

import android.content.Context
import android.opengl.GLES20.*
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

val Context?.isOpenGLES2Supported: Boolean
    get() {
        val features = this?.packageManager?.systemAvailableFeatures
        features?.filter { it.name == null }
            ?.forEach { return it.reqGlEsVersion and 0xffff0000.toInt() shr 16 >= 2 }
        return false
    }


fun loadShader(shaderType: Int, source: String): Int {
    val shader = glCreateShader(shaderType)
    glShaderSource(shader, source)
    glCompileShader(shader)
    return shader
}

fun FloatArray.toFloatBuffer(): Buffer {
    return ByteBuffer.allocateDirect(size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(this)
        .rewind()
}