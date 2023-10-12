package pl.droidsonroids.gif

import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test

class GifOptionsTest {

    private lateinit var gifOptions: GifOptions

    @Before
    fun setUp() {
        gifOptions = GifOptions()
    }

    @Test
    fun testInitialValues() {
        Assertions.assertThat(gifOptions.inSampleSize).isEqualTo(1.toChar())
        Assertions.assertThat(gifOptions.inIsOpaque).isFalse
    }

    @Test
    fun setInSampleSize() {
        gifOptions.setInSampleSize(2)
        Assertions.assertThat(gifOptions.inSampleSize).isEqualTo(2.toChar())
    }

    @Test
    fun setInIsOpaque() {
        gifOptions.inIsOpaque = true
        Assertions.assertThat(gifOptions.inIsOpaque).isTrue
    }

    @Test
    fun copyFromNonNull() {
        val source = GifOptions()
        source.inIsOpaque = false
        source.setInSampleSize(8)
        gifOptions.setFrom(source)
        Assertions.assertThat(gifOptions).isEqualToComparingFieldByField(source)
    }

    @Test
    fun copyFromNull() {
        val defaultOptions = GifOptions()
        gifOptions.inIsOpaque = false
        gifOptions.setInSampleSize(8)
        gifOptions.setFrom(null)
        Assertions.assertThat(gifOptions).isEqualToComparingFieldByField(defaultOptions)
    }
}