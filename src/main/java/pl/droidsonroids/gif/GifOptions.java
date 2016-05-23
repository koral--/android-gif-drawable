package pl.droidsonroids.gif;

import android.support.annotation.IntRange;
import android.support.annotation.Nullable;

import pl.droidsonroids.gif.annotations.Beta;

/**
 * Options controlling various GIF parameters similar to
 * {@link android.graphics.BitmapFactory.Options}
 */
@Beta
public class GifOptions {
	static final int UINT16_MAX = 0xFFFF;

	int inSampleSize;
	boolean inIsOpaque;

	public GifOptions() {
		reset();
	}

	private void reset() {
		inSampleSize = 1;
		inIsOpaque = false;
	}

	/**
	 * If set to a value {@code > 1}, requests the decoder to subsample the original
	 * frames, returning a smaller frame buffer to save memory. The sample size is
	 * the number of pixels in either dimension that correspond to a single
	 * pixel in the decoded bitmap. For example, inSampleSize == 4 returns
	 * an image that is 1/4 the width/height of the original, and 1/16 the
	 * number of pixels. Values outside range {@code <1, 65635>} are treated as 1.
	 * Unlike {@link android.graphics.BitmapFactory.Options#inSampleSize}
	 * values which are not powers of 2 are also supported.
	 * Default value is 1.
	 *
	 * @param inSampleSize the sample size
	 */
	public void setInSampleSize(@IntRange(from = 1, to = UINT16_MAX) int inSampleSize) {
		this.inSampleSize = inSampleSize;
	}

	/**
	 * Indicates whether the content is opaque. GIF that is known to be opaque can
	 * take a faster drawing case than non-opaque one, since alpha channel processing may be skipped.
	 * <p>
	 * Common usage is setting this to true when view where GIF is displayed is known to be non-transparent
	 * and its background is irrelevant.In such case even if GIF contains transparent areas,
	 * they will appear black.
	 * <p>
	 * See also {@link GifTextureView#setOpaque(boolean)}.
	 * Default value is {@code false}, which means that content can be transparent.
	 *
	 * @param inIsOpaque whether the content is opaque
	 */
	public void setInIsOpaque(boolean inIsOpaque) {
		this.inIsOpaque = inIsOpaque;
	}

	void setFrom(@Nullable GifOptions source) {
		if (source == null) {
			reset();
		} else {
			inIsOpaque = source.inIsOpaque;
			inSampleSize = source.inSampleSize;
		}
	}
}
