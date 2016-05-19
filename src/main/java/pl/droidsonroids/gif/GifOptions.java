package pl.droidsonroids.gif;

import android.support.annotation.IntRange;

import pl.droidsonroids.gif.annotations.Beta;

/**
 * Options controlling various GIF parameters similar to
 * {@link android.graphics.BitmapFactory.Options}
 */
@Beta
public class GifOptions {
	/**
	 * If set to a value &gt; 1, requests the decoder to subsample the original
	 * frames, returning a smaller frame buffer to save memory. The sample size is
	 * the number of pixels in either dimension that correspond to a single
	 * pixel in the decoded bitmap. For example, inSampleSize == 4 returns
	 * an image that is 1/4 the width/height of the original, and 1/16 the
	 * number of pixels. Values outside range {@code <1, 65635>} are treated as 1.
	 * Unlike {@link android.graphics.BitmapFactory.Options#inSampleSize}
	 * values which are not powers of 2 are also supported.
	 *
	 * @param inSampleSize the sample size
	 */
	public void setInSampleSize(@IntRange(from = 1, to = 0xffff) int inSampleSize) {
		this.inSampleSize = inSampleSize;
	}

	/**
	 * Indicates whether the content is opaque. GIF that is known to be opaque can
	 * take a faster drawing case than non-opaque one. See {@link GifTextureView#setOpaque(boolean)}
	 * for more information.<br>
	 * Default value is {@code false}, meaning that content can be transparent.
	 *
	 * @param inIsOpaque whether the content is opaque
	 */
	public void setInIsOpaque(boolean inIsOpaque) {
		this.inIsOpaque = inIsOpaque;
	}

	int inSampleSize = 1;
	boolean inIsOpaque = false;
}
