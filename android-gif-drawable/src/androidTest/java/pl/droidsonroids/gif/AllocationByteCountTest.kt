package pl.droidsonroids.gif

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import pl.droidsonroids.gif.test.R

@RunWith(AndroidJUnit4::class)
class AllocationByteCountTest {

    @Test
    fun allocationByteCountIsConsistent() {
        val resources = getInstrumentation().context.resources

        val drawable = GifDrawable(resources, R.raw.test)

        val metaData = GifAnimationMetaData(resources, R.raw.test)

        assertThat(drawable.frameByteCount + metaData.allocationByteCount)
            .isEqualTo(drawable.allocationByteCount)
        assertThat(metaData.getDrawableAllocationByteCount(null, 1))
            .isEqualTo(drawable.allocationByteCount)
        assertThat(metaData.getDrawableAllocationByteCount(drawable, 1))
            .isEqualTo(drawable.allocationByteCount)
    }
}