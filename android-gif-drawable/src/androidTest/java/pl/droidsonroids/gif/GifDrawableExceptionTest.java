package pl.droidsonroids.gif;

import android.content.res.Resources;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import pl.droidsonroids.gif.test.R;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class GifDrawableExceptionTest {
	private GifDrawable gifDrawable;

	@Before
	public void setUp() throws Exception {
		final Resources resources = InstrumentationRegistry.getInstrumentation().getContext().getResources();
		gifDrawable = new GifDrawable(resources, R.raw.test);
	}

	@After
	public void tearDown() {
		gifDrawable.recycle();
	}

	@Test
	public void frameIndexOutOfBoundsMessageContainsRange() {
		final int numberOfFrames = gifDrawable.getNumberOfFrames();
		final int invalidFrameIndex = numberOfFrames + 10;
		try {
			gifDrawable.getFrameDuration(invalidFrameIndex);
			fail("Expected IndexOutOfBoundsException to be thrown");
		} catch (IndexOutOfBoundsException exception) {
			assertNotNull(exception.getMessage());
			assertTrue(exception.getMessage().contains(Integer.toString(numberOfFrames)));
		}
	}

	@Test
	public void exceptionThrownWhenPixelsArrayTooSmall() {
		try {
			gifDrawable.getPixels(new int[0]);
			fail("Expected ArrayIndexOutOfBoundsException to be thrown");
		} catch (ArrayIndexOutOfBoundsException exception) {
			// expected
		}
	}

	@Test
	public void exceptionThrownWhenPixelCoordinateXOutOfRange() {
		try {
			gifDrawable.getPixel(gifDrawable.getIntrinsicWidth(), 0);
			fail("Expected IllegalArgumentException to be thrown");
		} catch (IllegalArgumentException exception) {
			// expected
		}
	}

	@Test
	public void exceptionThrownWhenPixelCoordinateYOutOfRange() {
		try {
			gifDrawable.getPixel(0, gifDrawable.getIntrinsicHeight());
			fail("Expected IllegalArgumentException to be thrown");
		} catch (IllegalArgumentException exception) {
			// expected
		}
	}
}
