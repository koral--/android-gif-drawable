package pl.droidsonroids.gif

import android.view.LayoutInflater
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.droidsonroids.gif.GifDrawableAssert.Companion.assertThat
import pl.droidsonroids.gif.test.R

@RunWith(AndroidJUnit4::class)
class GifViewDescriptorTest {

    private lateinit var rootView: View

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().context
        rootView = LayoutInflater.from(context).inflate(R.layout.attrributes, null, false)
    }

    @Test
    fun loopCountSetOnGifImageButton() {
        val view = rootView.findViewById<GifImageButton>(R.id.imageButton)

        assertThat(view.background).hasLoopCountEqualTo(IMAGE_BUTTON_LOOP_COUNT)
        assertThat(view.drawable).hasLoopCountEqualTo(IMAGE_BUTTON_LOOP_COUNT)
    }

    @Test
    fun loopCountSetOnGifImageView() {
        val view = rootView.findViewById<GifImageView>(R.id.imageView)

        assertThat(view.background).hasLoopCountEqualTo(IMAGE_VIEW_LOOP_COUNT)
        assertThat(view.drawable).hasLoopCountEqualTo(IMAGE_VIEW_LOOP_COUNT)
    }

    @Test
    fun loopCountSetOnGifTextView() {
        val view = rootView.findViewById<GifTextView>(R.id.textView)

        assertThat(view.background).hasLoopCountEqualTo(TEXT_VIEW_LOOP_COUNT)

        for (drawable in view.compoundDrawablesRelative) {
            assertThat(drawable).hasLoopCountEqualTo(TEXT_VIEW_LOOP_COUNT)
        }
    }

    companion object {
        private const val IMAGE_BUTTON_LOOP_COUNT = 2
        private const val IMAGE_VIEW_LOOP_COUNT = 3
        private const val TEXT_VIEW_LOOP_COUNT = 4
    }
}