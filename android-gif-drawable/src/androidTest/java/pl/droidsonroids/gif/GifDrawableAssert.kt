package pl.droidsonroids.gif

import android.graphics.drawable.Drawable
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions
import pl.droidsonroids.gif.GifDrawable

internal class GifDrawableAssert private constructor(actual: GifDrawable) :
    AbstractAssert<GifDrawableAssert?, GifDrawable?>(actual, GifDrawableAssert::class.java) {
    fun hasLoopCountEqualTo(loopCount: Int): GifDrawableAssert {
        Assertions.assertThat(actual!!.loopCount).isEqualTo(loopCount)
        return this
    }

    companion object {
        fun assertThat(actual: Drawable): GifDrawableAssert {
            Assertions.assertThat(actual).isInstanceOf(GifDrawable::class.java)
            return GifDrawableAssert(actual as GifDrawable)
        }
    }
}