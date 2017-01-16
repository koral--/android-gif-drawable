package pl.droidsonroids.gif.sample;

import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.RawRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import pl.droidsonroids.gif.GifOptions;
import pl.droidsonroids.gif.WebpInfoHandle;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_FRAGMENT_SHADER;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.GL_VERTEX_SHADER;
import static android.opengl.GLES20.glAttachShader;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glCompileShader;
import static android.opengl.GLES20.glCreateProgram;
import static android.opengl.GLES20.glCreateShader;
import static android.opengl.GLES20.glDeleteShader;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glGetAttribLocation;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glLinkProgram;
import static android.opengl.GLES20.glShaderSource;
import static android.opengl.GLES20.glTexImage2D;
import static android.opengl.GLES20.glTexParameteri;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;

public class WebPFragment extends BaseFragment {

	private static final String VERTEX_SHADER_CODE =
			"attribute vec4 position;" +
					"uniform mediump mat4 texMatrix;" +
					"attribute vec4 coordinate;" +
					"varying vec2 textureCoordinate;" +
					"void main()" +
					"{" +
					"    gl_Position = position;" +
					"    mediump vec4 outCoordinate = texMatrix * coordinate;" +
					"    textureCoordinate = vec2(outCoordinate.s, 1.0 - outCoordinate.t);" +
					"}";

	private static final String FRAGMENT_SHADER_CODE =
			"varying mediump vec2 textureCoordinate;" +
					"uniform sampler2D texture;" +
					"void main() { " +
					"    gl_FragColor = texture2D(texture, textureCoordinate);" +
					"}";

	private WebpInfoHandle mWebpInfoHandle;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		try {
			GifOptions options = new GifOptions();
			options.setInIsOpaque(true);
			@RawRes @DrawableRes final int resourceId = R.drawable.blend;
			mWebpInfoHandle = new WebpInfoHandle(getResources().openRawResourceFd(resourceId));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		final GLSurfaceView view = (GLSurfaceView) inflater.inflate(R.layout.opengl, container, false);
		view.setEGLContextClientVersion(2);
		view.setRenderer(new Renderer());
		view.getHolder().setFixedSize(mWebpInfoHandle.getWidth(), mWebpInfoHandle.getHeight());
		return view;
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mWebpInfoHandle.recycle();
	}

	private class Renderer implements GLSurfaceView.Renderer {

		private int mTexMatrixLocation;
		final float[] mTexMatrix = new float[16];

		@Override
		public void onSurfaceCreated(GL10 gl, EGLConfig config) {
			final int[] texNames = {0};
			glGenTextures(1, texNames, 0);
			glBindTexture(GL_TEXTURE_2D, texNames[0]);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

			final int vertexShader = loadShader(GL_VERTEX_SHADER, VERTEX_SHADER_CODE);
			final int pixelShader = loadShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE);
			final int program = glCreateProgram();
			glAttachShader(program, vertexShader);
			glAttachShader(program, pixelShader);
			glLinkProgram(program);
			glDeleteShader(pixelShader);
			glDeleteShader(vertexShader);
			final int positionLocation = glGetAttribLocation(program, "position");
			final int textureLocation = glGetUniformLocation(program, "texture");
			mTexMatrixLocation = glGetUniformLocation(program, "texMatrix");
			final int coordinateLocation = glGetAttribLocation(program, "coordinate");
			glUseProgram(program);

			Buffer textureBuffer = createFloatBuffer(new float[]{0, 0, 1, 0, 0, 1, 1, 1});
			Buffer verticesBuffer = createFloatBuffer(new float[]{-1, -1, 1, -1, -1, 1, 1, 1});
			glVertexAttribPointer(coordinateLocation, 2, GL_FLOAT, false, 0, textureBuffer);
			glEnableVertexAttribArray(coordinateLocation);
			glUniform1i(textureLocation, 0);
			glVertexAttribPointer(positionLocation, 2, GL_FLOAT, false, 0, verticesBuffer);
			glEnableVertexAttribArray(positionLocation);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mWebpInfoHandle.getWidth(), mWebpInfoHandle.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
		}

		@Override
		public void onSurfaceChanged(GL10 gl, int width, int height) {
			final float scaleX = (float) width / mWebpInfoHandle.getWidth();
			final float scaleY = (float) height / mWebpInfoHandle.getHeight();
			Matrix.setIdentityM(mTexMatrix, 0);
			Matrix.scaleM(mTexMatrix, 0, scaleX, scaleY, 1);
			Matrix.translateM(mTexMatrix, 0, (1 / scaleX) / 2 - 0.5f, (1 / scaleY) / 2 - 0.5f, 0);
			glUniformMatrix4fv(mTexMatrixLocation, 1, false, mTexMatrix, 0);
		}

		@Override
		public void onDrawFrame(GL10 gl) {
			mWebpInfoHandle.glTexSubImage2D(GL_TEXTURE_2D, 0);
			glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
		}
	}

	private static int loadShader(int shaderType, String source) {
		final int shader = glCreateShader(shaderType);
		glShaderSource(shader, source);
		glCompileShader(shader);
		return shader;
	}

	private static Buffer createFloatBuffer(float[] floats) {
		return ByteBuffer
				.allocateDirect(floats.length * 4)
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer()
				.put(floats)
				.rewind();
	}
}
