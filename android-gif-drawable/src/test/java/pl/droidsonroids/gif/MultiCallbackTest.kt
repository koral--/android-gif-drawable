package pl.droidsonroids.gif

import android.graphics.drawable.Drawable
import android.view.View
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.Spy
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MultiCallbackTest {

    @Mock
    lateinit var view: View

    @Spy
    lateinit var drawable: Drawable

    private lateinit var action: Runnable
    private lateinit var simpleMultiCallback: MultiCallback

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        simpleMultiCallback = MultiCallback()
        action = Runnable {
            //no-op
        }
    }

    @Test
    fun testInvalidateDrawable() {
        simpleMultiCallback.addView(view)
        drawable.callback = simpleMultiCallback

        drawable.invalidateSelf()

        verify(view).invalidateDrawable(drawable)
    }

    @Test
    fun testScheduleDrawable() {
        simpleMultiCallback.addView(view)
        drawable.callback = simpleMultiCallback

        drawable.scheduleSelf(action, 0)

        verify(view).scheduleDrawable(drawable, action, 0)
    }

    @Test
    fun testUnscheduleDrawable() {
        simpleMultiCallback.addView(view)
        drawable.callback = simpleMultiCallback

        drawable.unscheduleSelf(action)

        verify(view).unscheduleDrawable(drawable, action)
    }

    @Test
    fun testViewRemoval() {
        simpleMultiCallback.addView(view)
        drawable.callback = simpleMultiCallback

        drawable.invalidateSelf()
        simpleMultiCallback.removeView(view)
        drawable.invalidateSelf()

        verify(view).invalidateDrawable(drawable)
    }

    @Test
    fun testViewInvalidate() {
        val viewInvalidateMultiCallback = MultiCallback(true)
        viewInvalidateMultiCallback.addView(view)

        drawable.callback = viewInvalidateMultiCallback
        drawable.invalidateSelf()

        verify(view).invalidate()
    }
}