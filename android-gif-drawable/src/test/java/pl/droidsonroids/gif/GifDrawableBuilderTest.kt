package pl.droidsonroids.gif

import org.assertj.core.api.Java6Assertions.assertThat
import org.junit.Test

class GifDrawableBuilderTest {

    @Test
    fun testOptionsAndSampleSizeConflict() {
        val builder = GifDrawableBuilder()

        val options = GifOptions()
        builder.options(options)
        builder.sampleSize(3)

        assertThat(options.inSampleSize).isEqualTo(1.toChar())
    }
}