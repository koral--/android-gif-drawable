package pl.droidsonroids.gif;

import java.io.IOException;
import java.nio.Buffer;

import pl.droidsonroids.gif.annotations.Beta;

/**
 * Provides support for animated GIFs in OpenGL.
 * GIF frames are rendered to internal frame buffer according to their durations in a background thread.
 * Calling {@link #glTexImage2D()} will specify 2D texture using current content of internal frame buffer.
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
