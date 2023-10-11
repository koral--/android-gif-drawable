package pl.droidsonroids.gif;

import android.graphics.drawable.Drawable;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

class GifDrawableAssert extends AbstractAssert<GifDrawableAssert, GifDrawable> {

	private GifDrawableAssert(final GifDrawable actual) {
		super(actual, GifDrawableAssert.class);
	}

	static GifDrawableAssert assertThat(final Drawable actual) {
		Assertions.assertThat(actual).isInstanceOf(GifDrawable.class);
		return new GifDrawableAssert((GifDrawable) actual);
	}

	GifDrawableAssert hasLoopCountEqualTo(final int loopCount) {
		Assertions.assertThat(actual.getLoopCount()).isEqualTo(loopCount);
		return this;
	}
}
