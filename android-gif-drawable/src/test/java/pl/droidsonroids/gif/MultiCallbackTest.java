package pl.droidsonroids.gif;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.M, constants = BuildConfig.class, manifest = Config.NONE)
public class MultiCallbackTest {

	@Mock View view;
	@Spy Drawable drawable;
	private MultiCallback simpleMultiCallback;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		simpleMultiCallback = new MultiCallback();
	}

	@Test
	public void testInvalidateDrawable() {
		simpleMultiCallback.addView(view);
		drawable.setCallback(simpleMultiCallback);
		drawable.invalidateSelf();
		verify(view).invalidateDrawable(drawable);
	}

	@Test
	public void testScheduleDrawable() {
		simpleMultiCallback.addView(view);
		drawable.setCallback(simpleMultiCallback);
		drawable.scheduleSelf(null, 0);
		verify(view).scheduleDrawable(drawable, null, 0);
	}

	@Test
	public void testUnscheduleDrawable() {
		simpleMultiCallback.addView(view);
		drawable.setCallback(simpleMultiCallback);
		drawable.unscheduleSelf(null);
		verify(view).unscheduleDrawable(drawable, null);
	}

	@Test
	public void testViewRemoval() {
		simpleMultiCallback.addView(view);
		drawable.setCallback(simpleMultiCallback);
		drawable.invalidateSelf();
		simpleMultiCallback.removeView(view);
		drawable.invalidateSelf();
		verify(view).invalidateDrawable(drawable);
	}

	@Test
	public void testViewInvalidate() {
		final MultiCallback viewInvalidateMultiCallback = new MultiCallback(true);
		viewInvalidateMultiCallback.addView(view);
		drawable.setCallback(viewInvalidateMultiCallback);
		drawable.invalidateSelf();
		verify(view).invalidate();
	}
}