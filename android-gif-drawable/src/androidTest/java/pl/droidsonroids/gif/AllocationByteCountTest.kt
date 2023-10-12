package pl.droidsonroids.gif

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import org.assertj.core.api.Assertions
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllocationByteCountTest {

    @Test
    fun allocationByteCountIsConsistent() {
        val resources = getInstrumentation().context.resources
        val drawable = GifDrawable(resources, pl.droidsonroids.gif.test.R.raw.test)
        val metaData = GifAnimationMetaData(resources, pl.droidsonroids.gif.test.R.raw.test)
        Assertions.assertThat(drawable.frameByteCount + metaData.allocationByteCount)
            .isEqualTo(drawable.allocationByteCount)
        Assertions.assertThat(metaData.getDrawableAllocationByteCount(null, 1))
            .isEqualTo(drawable.allocationByteCount)
        Assertions.assertThat(metaData.getDrawableAllocationByteCount(drawable, 1))
            .isEqualTo(drawable.allocationByteCount)
    }
}