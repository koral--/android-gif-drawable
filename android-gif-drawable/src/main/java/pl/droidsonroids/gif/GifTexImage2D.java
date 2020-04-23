package pl.droidsonroids.gif;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.Buffer;

/**
 * Provides support for animated GIFs in OpenGL.
 * There are 2 possible usages:
 * <ol>
 * <li>Automatic animation according to timing defined in GIF file - {@link #startDecoderThread()} and {@link #stopDecoderThread()}.</li>
 * <li>Manual frame advancing - {@link #seekToFrame(int)}.</li>
 * </ol>
 * Note that call {@link #seekToFrame(int)} while decoder thread is running will cause frame change
 * but it can be immediately changed again by decoder thread.
 * <br>
 * Current frame can be copied to 2D texture when needed. See {@link #glTexImage2D(int, int)} and {@link #glTexSubImage2D(int, int)}.
 */
public class GifTexImage2D {
	private final GifInfoHandle mGifInfoHandle;

	/**
	 * Constructs new GifTexImage2D.
	 * Decoder thread is initially stopped, use {@link #startDecoderThread()} to start it.
	 *
	 * @param inputSource source
	 * @param options     null-ok; options controlling parameters like subsampling and opacity
	 * @throws IOException when creation fails
	 */
	public GifTexImage2D(final InputSource inputSource, @Nullable GifOptions options) throws IOException {
		if (options == null) {
			options = new GifOptions();
		}
		mGifInfoHandle = inputSource.open();
		mGifInfoHandle.setOptions(options.inSampleSize, options.inIsOpaque);
		mGifInfoHandle.initTexImageDescriptor();
	}

	/**
	 * See {@link GifDrawable#getFrameDuration(int)}
	 *
	 * @param index index of the frame
	 * @return duration of the given frame in milliseconds
	 * @throws IndexOutOfBoundsException if {@code index < 0 || index >= <number of frames>}
	 */
	public int getFrameDuration(@IntRange(from = 0) int index) {
		return mGifInfoHandle.getFrameDuration(index);
	}

	/**
	 * Seeks to given frame
	 *
	 * @param index index of the frame
	 * @throws IndexOutOfBoundsException if {@code index < 0 || index >= <number of frames>}
	 */
	public void seekToFrame(@IntRange(from = 0) int index) {
		mGifInfoHandle.seekToFrameGL(index);
	}

	/**
	 * @return number of frames in GIF, at least one
	 */
	public int getNumberOfFrames() {
		return mGifInfoHandle.getNumberOfFrames();
	}

	/**
	 * @return index of recently rendered frame or -1 if this object is recycled
	 */
	public int getCurrentFrameIndex() {
		return mGifInfoHandle.getCurrentFrameIndex();
	}

	/**
	 * Sets new animation speed factor. See {@link GifDrawable#setSpeed(float)}.
	 *
	 * @param factor new speed factor, eg. 0.5f means half speed, 1.0f - normal, 2.0f - double speed
	 * @throws IllegalArgumentException if factor&lt;=0
	 */
	public void setSpeed(@FloatRange(from = 0, fromInclusive = false) final float factor) {
		mGifInfoHandle.setSpeedFactor(factor);
	}

	/**
	 * Equivalent of {@link android.opengl.GLES20#glTexImage2D(int, int, int, int, int, int, int, int, Buffer)}.
	 * Where <code>Buffer</code> contains pixels of the current frame.
	 *
	 * @param level  level-of-detail number
	 * @param target target texture
	 */
	public void glTexImage2D(int target, int level) {
		mGifInfoHandle.glTexImage2D(target, level);
	}

	/**
	 * Equivalent of {@link android.opengl.GLES20#glTexSubImage2D(int, int, int, int, int, int, int, int, Buffer)}.
	 * Where <code>Buffer</code> contains pixels of the current frame.
	 *
	 * @param level  level-of-detail number
	 * @param target target texture
	 */
	public void glTexSubImage2D(int target, int level) {
		mGifInfoHandle.glTexSubImage2D(target, level);
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
		if (mGifInfoHandle != null) {
			mGifInfoHandle.recycle();
		}
	}

	/**
	 * @return width of the GIF canvas, 0 if recycled
	 */
	public int getWidth() {
		return mGifInfoHandle.getWidth();
	}

	/**
	 * @return height of the GIF canvas, 0 if recycled
	 */
	public int getHeight() {
		return mGifInfoHandle.getHeight();
	}

	/**
	 * See {@link GifDrawable#getDuration()}
	 *
	 * @return duration of of one loop the animation in milliseconds. Result is always multiple of 10.
	 */
	public int getDuration() {
		return mGifInfoHandle.getDuration();
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
