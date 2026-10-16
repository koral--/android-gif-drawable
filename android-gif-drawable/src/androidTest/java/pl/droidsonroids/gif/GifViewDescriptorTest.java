package pl.droidsonroids.gif;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.test.platform.app.InstrumentationRegistry;
import android.view.LayoutInflater;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import pl.droidsonroids.gif.test.R;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class GifViewDescriptorTest {

	private static final int IMAGE_BUTTON_LOOP_COUNT = 2;
	private static final int IMAGE_VIEW_LOOP_COUNT = 3;
	private static final int TEXT_VIEW_LOOP_COUNT = 4;
	private View rootView;

	@Before
	public void setUp() {
		final Context context = InstrumentationRegistry.getInstrumentation().getContext();
		rootView = LayoutInflater.from(context).inflate(R.layout.attrributes, null, false);
	}

	@Test
	public void loopCountSetOnGifImageButton() {
		final GifImageButton view = rootView.findViewById(R.id.imageButton);
		assertLoopCount(IMAGE_BUTTON_LOOP_COUNT, view.getBackground());
		assertLoopCount(IMAGE_BUTTON_LOOP_COUNT, view.getDrawable());
	}

	@Test
	public void loopCountSetOnGifImageView() {
		final GifImageView view = rootView.findViewById(R.id.imageView);
		assertLoopCount(IMAGE_VIEW_LOOP_COUNT, view.getBackground());
		assertLoopCount(IMAGE_VIEW_LOOP_COUNT, view.getDrawable());
	}

	@Test
	public void loopCountSetOnGifTextView() {
		final GifTextView view = rootView.findViewById(R.id.textView);

		assertLoopCount(TEXT_VIEW_LOOP_COUNT, view.getBackground());
		for (final Drawable drawable : view.getCompoundDrawablesRelative()) {
			assertLoopCount(TEXT_VIEW_LOOP_COUNT, drawable);
		}
	}

	private static void assertLoopCount(final int expectedLoopCount, final Drawable drawable) {
		assertTrue(drawable instanceof GifDrawable);
		assertEquals(expectedLoopCount, ((GifDrawable) drawable).getLoopCount());
	}
}
