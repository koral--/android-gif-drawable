package pl.droidsonroids.gif.sample

import android.opengl.GLES20.*

import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import pl.droidsonroids.gif.GifOptions
import pl.droidsonroids.gif.GifTexImage2D
import pl.droidsonroids.gif.InputSource
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GifTexImage2DFragment : BaseFragment() {

	private val VERTEX_SHADER_CODE =
			"attribute vec4 position;" +
					"uniform mediump mat4 texMatrix;" +
					"attribute vec4 coordinate;" +
					"varying vec2 textureCoordinate;" +
					"void main()" +
					"{" +
					"    gl_Position = position;" +
					"    mediump vec4 outCoordinate = texMatrix * coordinate;" +
					"    textureCoordinate = vec2(outCoordinate.s, 1.0 - outCoordinate.t);" +
					"}"

	private val FRAGMENT_SHADER_CODE =
			"varying mediump vec2 textureCoordinate;" +
					"uniform sampler2D texture;" +
					"void main() { " +
					"    gl_FragColor = texture2D(texture, textureCoordinate);" +
					"}"

	private var gifTexImage2D: GifTexImage2D? = null

	private fun loadShader(shaderType: Int, source: String): Int {
		val shader = glCreateShader(shaderType)
		glShaderSource(shader, source)
		glCompileShader(shader)
		return shader
	}

	private fun createFloatBuffer(floats: FloatArray): Buffer {
		return ByteBuffer
				.allocateDirect(floats.size * 4)
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer()
				.put(floats)
				.rewind()
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		if (!isOpenGLES2Supported) {
			Snackbar.make(container!!, R.string.gles2_not_supported, Snackbar.LENGTH_LONG).show()
			return null
		}
		val options = GifOptions()
		options.setInIsOpaque(true)
		gifTexImage2D = GifTexImage2D(InputSource.ResourcesSource(resources, R.drawable.anim_flag_chile), options)

		val view = inflater.inflate(R.layout.opengl, container, false) as GLSurfaceView
		view.setEGLContextClientVersion(2)
		view.setRenderer(Renderer())
		view.holder.setFixedSize(gifTexImage2D!!.width, gifTexImage2D!!.height)
		gifTexImage2D!!.startDecoderThread()
		return view
	}

	override fun onDetach() {
		super.onDetach()
		gifTexImage2D?.recycle()
	}

	private inner class Renderer : GLSurfaceView.Renderer {
		private var mTexMatrixLocation = 0

		internal val mTexMatrix = FloatArray(16)

		override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
			val texNames = intArrayOf(0)
			glGenTextures(1, texNames, 0)
			glBindTexture(GL_TEXTURE_2D, texNames[0])
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

			val vertexShader = loadShader(GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
			val pixelShader = loadShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
			val program = glCreateProgram()
			glAttachShader(program, vertexShader)
			glAttachShader(program, pixelShader)
			glLinkProgram(program)
			glDeleteShader(pixelShader)
			glDeleteShader(vertexShader)
			val positionLocation = glGetAttribLocation(program, "position")
			val textureLocation = glGetUniformLocation(program, "texture")
			mTexMatrixLocation = glGetUniformLocation(program, "texMatrix")
			val coordinateLocation = glGetAttribLocation(program, "coordinate")
			glUseProgram(program)

			val textureBuffer = createFloatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
			val verticesBuffer = createFloatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
			glVertexAttribPointer(coordinateLocation, 2, GL_FLOAT, false, 0, textureBuffer)
			glEnableVertexAttribArray(coordinateLocation)
			glUniform1i(textureLocation, 0)
			glVertexAttribPointer(positionLocation, 2, GL_FLOAT, false, 0, verticesBuffer)
			glEnableVertexAttribArray(positionLocation)
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, gifTexImage2D!!.width, gifTexImage2D!!.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
		}

		override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
			val scaleX = width.toFloat() / gifTexImage2D!!.width
			val scaleY = height.toFloat() / gifTexImage2D!!.height
			Matrix.setIdentityM(mTexMatrix, 0)
			Matrix.scaleM(mTexMatrix, 0, scaleX, scaleY, 1f)
			Matrix.translateM(mTexMatrix, 0, 1 / scaleX / 2 - 0.5f, 1 / scaleY / 2 - 0.5f, 0f)
			glUniformMatrix4fv(mTexMatrixLocation, 1, false, mTexMatrix, 0)
		}

		override fun onDrawFrame(gl: GL10) {
			gifTexImage2D!!.glTexSubImage2D(GL_TEXTURE_2D, 0)
			glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
		}
	}

	private val isOpenGLES2Supported: Boolean
		get() {
			val features = context?.packageManager?.systemAvailableFeatures
			features?.filter { it.name == null }
					?.forEach { return it.reqGlEsVersion and 0xffff0000.toInt() shr 16 >= 2 }
			return false
		}
}
