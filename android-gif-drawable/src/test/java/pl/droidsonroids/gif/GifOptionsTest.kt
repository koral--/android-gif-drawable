package pl.droidsonroids.gif

import org.assertj.core.api.Assertions.assertThat
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
        assertThat(gifOptions.inSampleSize).isEqualTo(1.toChar())
        assertThat(gifOptions.inIsOpaque).isFalse
    }

    @Test
    fun setInSampleSize() {
        gifOptions.setInSampleSize(2)

        assertThat(gifOptions.inSampleSize).isEqualTo(2.toChar())
    }

    @Test
    fun setInIsOpaque() {
        gifOptions.inIsOpaque = true

        assertThat(gifOptions.inIsOpaque).isTrue
    }

    @Test
    fun copyFromNonNull() {
        val source = GifOptions()

        source.inIsOpaque = false
        source.setInSampleSize(8)
        gifOptions.setFrom(source)

        assertThat(gifOptions).isEqualToComparingFieldByField(source)
    }

    @Test
    fun copyFromNull() {
        val defaultOptions = GifOptions()

        gifOptions.inIsOpaque = false
        gifOptions.setInSampleSize(8)
        gifOptions.setFrom(null)

        assertThat(gifOptions).isEqualToComparingFieldByField(defaultOptions)
    }
}