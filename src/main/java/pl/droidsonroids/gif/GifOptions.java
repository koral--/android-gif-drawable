package pl.droidsonroids.gif;

import android.support.annotation.IntRange;

import pl.droidsonroids.gif.annotations.Beta;

/**
 * TODO
 */
@Beta
public class GifOptions {
	public void setInSampleSize(@IntRange(from = 1, to = 0xffff) int inSampleSize) {
		this.inSampleSize = inSampleSize;
	}

	public void setInIsOpaque(boolean inIsOpaque) {
		this.inIsOpaque = inIsOpaque;
	}

	int inSampleSize = 1;
	boolean inIsOpaque = false;
}
