package pl.droidsonroids.gif;

import android.graphics.drawable.Drawable;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.robolectric.RobolectricTestRunner;

import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class MultiCallbackTest {

	@Mock View view;
	@Spy Drawable drawable;
	private Runnable action;
	private MultiCallback simpleMultiCallback;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		simpleMultiCallback = new MultiCallback();
		action = new Runnable() {
			@Override
			public void run() {
				//no-op
			}
		};
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
		drawable.scheduleSelf(action, 0);
		verify(view).scheduleDrawable(drawable, action, 0);
	}

	@Test
	public void testUnscheduleDrawable() {
		simpleMultiCallback.addView(view);
		drawable.setCallback(simpleMultiCallback);
		drawable.unscheduleSelf(action);
		verify(view).unscheduleDrawable(drawable, action);
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