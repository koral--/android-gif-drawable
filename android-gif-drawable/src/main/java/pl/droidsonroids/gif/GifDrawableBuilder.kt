package pl.droidsonroids.gif

/**
 * Builder for [pl.droidsonroids.gif.GifDrawable] which can be used to construct new drawables
 * by reusing old ones.
 */
class GifDrawableBuilder : GifDrawableInit<GifDrawableBuilder>() {

    override fun self(): GifDrawableBuilder = this
}