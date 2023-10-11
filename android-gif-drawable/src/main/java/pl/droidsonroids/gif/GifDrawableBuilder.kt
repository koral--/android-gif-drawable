package pl.droidsonroids.gif;

/**
 * Builder for {@link pl.droidsonroids.gif.GifDrawable} which can be used to construct new drawables
 * by reusing old ones.
 */
public class GifDrawableBuilder extends  GifDrawableInit<GifDrawableBuilder>{

	@Override
	protected GifDrawableBuilder self() {
		return this;
	}
}
