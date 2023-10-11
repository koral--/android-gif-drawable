package pl.droidsonroids.gif

import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.annotation.RequiresApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import pl.droidsonroids.gif.GifViewUtils.getDensityScale

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
@RunWith(
    MockitoJUnitRunner::class
)
class GifViewUtilsDensityTest {
    @Mock
    lateinit var resources: Resources
    private fun getMockedResources(resourceDensity: Int, displayDensity: Int): Resources {
        Mockito.doAnswer { invocation ->
            val outValue = invocation.getArgument<TypedValue>(1)
            outValue.density = resourceDensity
            outValue
        }.`when`(resources)?.getValue(
            ArgumentMatchers.anyInt(), ArgumentMatchers.any(
                TypedValue::class.java
            ), ArgumentMatchers.anyBoolean()
        )
        val displayMetrics = DisplayMetrics()
        displayMetrics.densityDpi = displayDensity
        Mockito.`when`(resources.displayMetrics).thenReturn(displayMetrics)
        return resources
    }

    @Test
    @Throws(Exception::class)
    fun testHighResourceDensities() {
        assertThat(
            getDensityScale(
                getMockedResources(
                    DisplayMetrics.DENSITY_HIGH,
                    DisplayMetrics.DENSITY_HIGH
                ), 0
            )
        ).isEqualTo(1.0f)
        assertThat(
            getDensityScale(
                getMockedResources(
                    DisplayMetrics.DENSITY_HIGH,
                    DisplayMetrics.DENSITY_LOW
                ), 0
            )
        ).isEqualTo(0.5f)
        assertThat(
            getDensityScale(
                getMockedResources(
                    DisplayMetrics.DENSITY_HIGH,
                    DisplayMetrics.DENSITY_MEDIUM
                ), 0
            )
        ).isEqualTo(2f / 3)
        assertThat(
            getDensityScale(
                getMockedResources(
                    DisplayMetrics.DENSITY_HIGH,
                    DisplayMetrics.DENSITY_XHIGH
                ), 0
            )
        ).isEqualTo(4f / 3)
        assertThat(
            getDensityScale(
                getMockedResources(
                    DisplayMetrics.DENSITY_HIGH,
                    DisplayMetrics.DENSITY_XXHIGH
                ), 0
            )
        ).isEqualTo(2.0f)
        assertThat(
            getDensityScale(
                getMockedResources(
                    DisplayMetrics.DENSITY_HIGH,
                    DisplayMetrics.DENSITY_XXXHIGH
                ), 0
            )
        ).isEqualTo(8f / 3)
        assertThat(
            getDensityScale(
                getMockedResources(
                    DisplayMetrics.DENSITY_HIGH,
                    DisplayMetrics.DENSITY_TV
                ), 0
            )
        ).isEqualTo(213f / 240)
    }

    @Test
    @Throws(Exception::class)
    fun testLowHighDensity() {
        assertThat(
            getDensityScale(
                getMockedResources(
                    DisplayMetrics.DENSITY_LOW,
                    DisplayMetrics.DENSITY_HIGH
                ), 0
            )
        ).isEqualTo(2.0f)
    }

    @Test
    @Throws(Exception::class)
    fun testNoneResourceDensities() {
        assertThat(
            getDensityScale(
                getMockedResources(
                    TypedValue.DENSITY_NONE,
                    DisplayMetrics.DENSITY_LOW
                ), 0
            )
        ).isEqualTo(1.0f)
        assertThat(
            getDensityScale(
                getMockedResources(
                    TypedValue.DENSITY_NONE,
                    DisplayMetrics.DENSITY_MEDIUM
                ), 0
            )
        ).isEqualTo(1.0f)
        assertThat(
            getDensityScale(
                getMockedResources(
                    TypedValue.DENSITY_NONE,
                    DisplayMetrics.DENSITY_DEFAULT
                ), 0
            )
        ).isEqualTo(1.0f)
        assertThat(
            getDensityScale(
                getMockedResources(
                    TypedValue.DENSITY_NONE,
                    DisplayMetrics.DENSITY_TV
                ), 0
            )
        ).isEqualTo(1.0f)
        assertThat(
            getDensityScale(
                getMockedResources(
                    TypedValue.DENSITY_NONE,
                    DisplayMetrics.DENSITY_HIGH
                ), 0
            )
        ).isEqualTo(1)
        assertThat(
            getDensityScale(
                getMockedResources(
                    TypedValue.DENSITY_NONE,
                    DisplayMetrics.DENSITY_XXHIGH
                ), 0
            )
        ).isEqualTo(1)
        assertThat(
            getDensityScale(
                getMockedResources(
                    TypedValue.DENSITY_NONE,
                    DisplayMetrics.DENSITY_XXXHIGH
                ), 0
            )
        ).isEqualTo(1)
    }

    @Test
    @Throws(Exception::class)
    fun testNoneDisplayDensities() {
        assertThat(
            getDensityScale(
                getMockedResources(
                    DisplayMetrics.DENSITY_LOW,
                    Bitmap.DENSITY_NONE
                ), 0
            )
        ).isEqualTo(1.0f)
        assertThat(
            getDensityScale(
                getMockedResources(
                    DisplayMetrics.DENSITY_MEDIUM,
                    Bitmap.DENSITY_NONE
                ), 0
            )
        ).isEqualTo(1.0f)
        assertThat(
            getDensityScale(
                getMockedResources(
                    TypedValue.DENSITY_DEFAULT,
                    Bitmap.DENSITY_NONE
                ), 0
            )
        ).isEqualTo(1.0f)
        assertThat(
            getDensityScale(
                getMockedResources(
                    DisplayMetrics.DENSITY_HIGH,
                    Bitmap.DENSITY_NONE
                ), 0
            )
        ).isEqualTo(1.0f)
        assertThat(
            getDensityScale(
                getMockedResources(
                    DisplayMetrics.DENSITY_XXHIGH,
                    Bitmap.DENSITY_NONE
                ), 0
            )
        ).isEqualTo(1.0f)
    }

    @Test
    @Throws(Exception::class)
    fun testInvalidDensities() {
        assertThat(
            getDensityScale(
                getMockedResources(DisplayMetrics.DENSITY_HIGH, -1),
                0
            )
        ).isEqualTo(1.0f)
        assertThat(
            getDensityScale(
                getMockedResources(-1, DisplayMetrics.DENSITY_HIGH),
                0
            )
        ).isEqualTo(1.0f)
        assertThat(getDensityScale(getMockedResources(-1, -1), 0)).isEqualTo(1.0f)
        assertThat(
            getDensityScale(
                getMockedResources(DisplayMetrics.DENSITY_HIGH, 0),
                0
            )
        ).isEqualTo(1.0f)
    }
}