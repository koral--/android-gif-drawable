package pl.droidsonroids.gif

import org.assertj.core.api.Java6Assertions
import org.junit.Test

class GifDrawableBuilderTest {
    @Test
    @Throws(Exception::class)
    fun testOptionsAndSampleSizeConflict() {
        val builder = GifDrawableBuilder()
        val options = GifOptions()
        builder.options(options)
        builder.sampleSize(3)
        Java6Assertions.assertThat(options.inSampleSize).isEqualTo(1.toChar())
    }
}