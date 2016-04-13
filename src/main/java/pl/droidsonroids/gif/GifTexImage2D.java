package pl.droidsonroids.gif;

import android.support.annotation.IntRange;

import java.io.IOException;
import java.nio.Buffer;

import pl.droidsonroids.gif.annotations.Beta;

/**
 * Provides support for animated GIFs in OpenGL.
 * There are 2 possible usages:
 * <ol>
 *     <li>Rendering GIF automatically according to its timing to internal frame buffer in the background thread,
 *     and requesting frame to be copied to 2D texture when needed. See {@link #glTexImage2D()} and {@link #glTexImage2D()}</li>
 *     <li>Manual frame advancing. See {@link #renderFrame(int)}</li>
 * </ol>
 * Note that currently only one of those ways can be used in given {@link GifTexImage2D} instance.
 */
@Beta
public class GifTexImage2D {
	static {
		LibraryLoader.loadLibrary(null, LibraryLoader.SURFACE_LIBRARY_NAME);
	}
	private final GifInfoHandle mGifInfoHandle;

	/**
	 * Constructs new GifTexImage2D.
	 * Decoder thread is initially stopped, use {@link #startDecoderThread()} to start it.
	 * @param inputSource source
	 * @throws IOException when creation fails
	 */
	public GifTexImage2D(final InputSource inputSource) throws IOException {
		mGifInfoHandle = inputSource.open();
	}

	/**
	 * See {@link GifDrawable#getFrameDuration(int)}
	 *
	 * @param index index of the frame
	 * @return duration of the given frame in milliseconds
	 * @throws IndexOutOfBoundsException if index &lt; 0 or index &gt;= number of frames
	 */
	public int getFrameDuration(@IntRange(from = 0) int index) {
		return mGifInfoHandle.getFrameDuration(index);
	}

	/**
	 * Seeks to given frame and then copies its pixels to 2D texture like
	 * {@link android.opengl.GLES20#glTexImage2D(int, int, int, int, int, int, int, int, Buffer)}.
	 * Where <code>target</code> is {@link android.opengl.GLES20#GL_TEXTURE_2D} and <code>Buffer</code> contains pixels of the current frame.
	 * @param index index of the frame
	 * @throws IndexOutOfBoundsException if index &lt; 0 or index &gt;= number of frames
	 */
	public void renderFrame(@IntRange(from = 0) int index){
		mGifInfoHandle.renderGLFrame(index);
	}

	/**
	 * @return number of frames in GIF, at least one
	 */
	public int getNumberOfFrames() {
		return mGifInfoHandle.getNumberOfFrames();
	}

	/**
	 * Equivalent of {@link android.opengl.GLES20#glTexImage2D(int, int, int, int, int, int, int, int, Buffer)}.
	 * Where <code>target</code> is {@link android.opengl.GLES20#GL_TEXTURE_2D} and <code>Buffer</code> contains pixels of the current frame.
	 * Does nothing if decoder thread is not started.
	 */
	public void glTexImage2D() {
		mGifInfoHandle.glTexImage2D();
	}

	/**
	 * Creates frame buffer and starts decoding thread. Does nothing if already started.
	 */
	public void startDecoderThread() {
		mGifInfoHandle.startDecoderThread();
	}

	/**
	 * Stops decoder thread and releases frame buffer. Does nothing if already stopped.
	 */
	public void stopDecoderThread() {
		mGifInfoHandle.stopDecoderThread();
	}

	/**
	 * See {@link GifDrawable#recycle()}. Decoder thread is stopped automatically.
	 */
	public void recycle() {
		stopDecoderThread();
		mGifInfoHandle.recycle();
	}

	@Override
	protected final void finalize() throws Throwable {
		try {
			recycle();
		} finally {
			super.finalize();
		}
	}
}
