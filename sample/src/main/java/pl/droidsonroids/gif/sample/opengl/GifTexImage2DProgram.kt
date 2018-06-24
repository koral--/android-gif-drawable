package pl.droidsonroids.gif.sample.opengl

import android.opengl.GLES20.*
import android.opengl.Matrix
import pl.droidsonroids.gif.GifTexImage2D

class GifTexImage2DProgram(private val gifTexImage2D: GifTexImage2D) {
    val height = gifTexImage2D.height
    val width = gifTexImage2D.width
    val currentFrameDuration = gifTexImage2D.getFrameDuration(gifTexImage2D.currentFrameIndex)

    private var texMatrixLocation = -1
    private val texMatrix = FloatArray(16)
    private var program = 0
    private var positionLocation = -1
    private var textureLocation = -1
    private var coordinateLocation = -1

    private val vertexShaderCode = """
        attribute vec4 position;
        uniform mediump mat4 texMatrix;
        attribute vec4 coordinate;
        varying vec2 textureCoordinate;
        void main() {
            gl_Position = position;
            mediump vec4 outCoordinate = texMatrix * coordinate;
            textureCoordinate = vec2(outCoordinate.s, 1.0 - outCoordinate.t);
        }
        """

    private val fragmentShaderCode = """
        varying mediump vec2 textureCoordinate;
        uniform sampler2D texture;
        void main() {
             gl_FragColor = texture2D(texture, textureCoordinate);
        }
        """

    private val textureBuffer = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f).toFloatBuffer()
    private val verticesBuffer = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f).toFloatBuffer()

    fun initialize() {
        val texNames = intArrayOf(0)
        glGenTextures(1, texNames, 0)
        glBindTexture(GL_TEXTURE_2D, texNames[0])
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

        val vertexShader = loadShader(GL_VERTEX_SHADER, vertexShaderCode)
        val pixelShader = loadShader(GL_FRAGMENT_SHADER, fragmentShaderCode)
        program = glCreateProgram()
        glAttachShader(program, vertexShader)
        glAttachShader(program, pixelShader)
        glLinkProgram(program)
        glDeleteShader(pixelShader)
        glDeleteShader(vertexShader)
        positionLocation = glGetAttribLocation(program, "position")
        textureLocation = glGetUniformLocation(program, "texture")
        texMatrixLocation = glGetUniformLocation(program, "texMatrix")
        coordinateLocation = glGetAttribLocation(program, "coordinate")

        glActiveTexture(GL_TEXTURE0)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, gifTexImage2D.width, gifTexImage2D.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
        glUseProgram(program)
    }

    fun setDimensions(width: Int, height: Int) {
        glEnableVertexAttribArray(coordinateLocation)
        glVertexAttribPointer(coordinateLocation, 2, GL_FLOAT, false, 0, textureBuffer)
        glUniform1i(textureLocation, 0)
        glEnableVertexAttribArray(positionLocation)
        glVertexAttribPointer(positionLocation, 2, GL_FLOAT, false, 0, verticesBuffer)

        val scaleX = width.toFloat() / gifTexImage2D.width
        val scaleY = height.toFloat() / gifTexImage2D.height
        Matrix.setIdentityM(texMatrix, 0)
        Matrix.scaleM(texMatrix, 0, scaleX, scaleY, 1f)
        Matrix.translateM(texMatrix, 0, 1 / scaleX / 2 - 0.5f, 1 / scaleY / 2 - 0.5f, 0f)
        glUniformMatrix4fv(texMatrixLocation, 1, false, texMatrix, 0)
    }

    fun draw() {
        gifTexImage2D.glTexSubImage2D(GL_TEXTURE_2D, 0)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
    }

    fun destroy() = gifTexImage2D.recycle()
}