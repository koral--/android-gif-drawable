package pl.droidsonroids.gif

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GifDrawableExceptionTest {

    @get:Rule
    var expectedException = ExpectedException.none()

    private lateinit var gifDrawable: GifDrawable

    @Before
    fun setUp() {
        val resources = InstrumentationRegistry.getInstrumentation().context.resources
        gifDrawable = GifDrawable(resources, pl.droidsonroids.gif.test.R.raw.test)
    }

    @After
    fun tearDown() {
        gifDrawable.recycle()
    }

    @Test
    fun frameIndexOutOfBoundsMessageContainsRange() {
        val numberOfFrames = gifDrawable.numberOfFrames
        val invalidFrameIndex = numberOfFrames + 10
        expectedException.expectMessage(CoreMatchers.containsString(Integer.toString(numberOfFrames)))
        expectedException.expect(IndexOutOfBoundsException::class.java)
        gifDrawable.getFrameDuration(invalidFrameIndex)
    }

    @Test
    fun exceptionThrownWhenPixelsArrayTooSmall() {
        expectedException.expect(ArrayIndexOutOfBoundsException::class.java)
        gifDrawable.getPixels(IntArray(0))
    }

    @Test
    fun exceptionThrownWhenPixelCoordinateXOutOfRange() {
        expectedException.expect(IllegalArgumentException::class.java)
        gifDrawable.getPixel(gifDrawable.intrinsicWidth, 0)
    }

    @Test
    fun exceptionThrownWhenPixelCoordinateYOutOfRange() {
        expectedException.expect(IllegalArgumentException::class.java)
        gifDrawable.getPixel(0, gifDrawable.intrinsicHeight)
    }
}