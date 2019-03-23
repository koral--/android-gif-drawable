package pl.droidsonroids.gif;

import android.graphics.Bitmap;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

/**
 * GifDecoder allows lightweight access to GIF frames, without wrappers like Drawable or View.
 * {@link Bitmap} with size equal to or greater than size of the GIF is needed.
 * For access only metadata (size, number of frames etc.) without pixels see {@link GifAnimationMetaData}.
 */
public class GifDecoder {
	//TODO extract common container
	private final GifInfoHandle mGifInfoHandle;

	/**
	 * Constructs new GifDecoder.
	 * Equivalent of {@link #GifDecoder(InputSource, GifOptions)} with null {@code options}
	 *
	 * @param inputSource source
	 * @throws IOException when creation fails
	 */
	public GifDecoder(@NonNull final InputSource inputSource) throws IOException {
		this(inputSource, null);
	}

	/**
	 * Constructs new GifDecoder
	 *
	 * @param inputSource source
	 * @param options     null-ok; options controlling subsampling and opacity
	 * @throws IOException when creation fails
	 */
	public GifDecoder(@NonNull final InputSource inputSource, @Nullable final GifOptions options) throws IOException {
		mGifInfoHandle = inputSource.open();
		if (options != null) {
			mGifInfoHandle.setOptions(options.inSampleSize, options.inIsOpaque);
		}
	}

	/**
	 * See {@link GifDrawable#getComment()}
	 *
	 * @return GIF comment
	 */
	public String getComment() {
		return mGifInfoHandle.getComment();
	}

	/**
	 * See {@link GifDrawable#getLoopCount()}
	 *
	 * @return loop count, 0 means that animation is infinite
	 */
	public int getLoopCount() {
		return mGifInfoHandle.getLoopCount();
	}

	/**
	 * See {@link GifDrawable#getInputSourceByteCount()}
	 *
	 * @return number of bytes backed by input source or -1 if it is unknown
	 */
	public long getSourceLength() {
		return mGifInfoHandle.getSourceLength();
	}

	/**
	 * See {@link GifDrawable#seekTo(int)}
	 *
	 * @param position position to seek to in milliseconds
	 * @param buffer   the frame buffer
	 * @throws IllegalArgumentException if {@code position < 0 }or {@code buffer} is recycled
	 */
	public void seekToTime(@IntRange(from = 0, to = Integer.MAX_VALUE) final int position, @NonNull final Bitmap buffer) {
		checkBuffer(buffer);
		mGifInfoHandle.seekToTime(position, buffer);
	}

	/**
	 * See {@link GifDrawable#seekToFrame(int)}
	 *
	 * @param frameIndex position to seek to in milliseconds
	 * @param buffer     the frame buffer
	 * @throws IllegalArgumentException if {@code frameIndex < 0} or {@code buffer} is recycled
	 */
	public void seekToFrame(@IntRange(from = 0, to = Integer.MAX_VALUE) final int frameIndex, @NonNull final Bitmap buffer) {
		checkBuffer(buffer);
		mGifInfoHandle.seekToFrame(frameIndex, buffer);
	}

	/**
	 * See {@link GifDrawable#getAllocationByteCount()}
	 *
	 * @return possible size of the memory needed to store pixels of this object
	 */
	public long getAllocationByteCount() {
		return mGifInfoHandle.getAllocationByteCount();
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
	 * See {@link GifDrawable#getDuration()}
	 *
	 * @return duration of of one loop the animation in milliseconds. Result is always multiple of 10.
	 */
	public int getDuration() {
		return mGifInfoHandle.getDuration();
	}

	/**
	 * @return width od the GIF canvas in pixels
	 */
	public int getWidth() {
		return mGifInfoHandle.getWidth();
	}

	/**
	 * @return height od the GIF canvas in pixels
	 */
	public int getHeight() {
		return mGifInfoHandle.getHeight();
	}

	/**
	 * @return number of frames in GIF, at least one
	 */
	public int getNumberOfFrames() {
		return mGifInfoHandle.getNumberOfFrames();
	}

	/**
	 * @return true if GIF is animated (has at least 2 frames and positive duration), false otherwise
	 */
	public boolean isAnimated() {
		return mGifInfoHandle.getNumberOfFrames() > 1 && getDuration() > 0;
	}

	/**
	 * See {@link GifDrawable#recycle()}
	 */
	public void recycle() {
		mGifInfoHandle.recycle();
	}

	private void checkBuffer(final Bitmap buffer) {
		if (buffer.isRecycled()) {
			throw new IllegalArgumentException("Bitmap is recycled");
		}
		if (buffer.getWidth() < mGifInfoHandle.getWidth() || buffer.getHeight() < mGifInfoHandle.getHeight()) {
			throw new IllegalArgumentException("Bitmap ia too small, size must be greater than or equal to GIF size");
		}
		if (buffer.getConfig() != Bitmap.Config.ARGB_8888) {
			throw new IllegalArgumentException("Only Config.ARGB_8888 is supported. Current bitmap config: " + buffer.getConfig());
		}
	}
}
