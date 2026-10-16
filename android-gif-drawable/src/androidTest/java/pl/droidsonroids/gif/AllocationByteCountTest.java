package pl.droidsonroids.gif;

import android.content.res.Resources;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import pl.droidsonroids.gif.test.R;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class AllocationByteCountTest {

	@Test
	public void allocationByteCountIsConsistent() throws Exception {
		final Resources resources = InstrumentationRegistry.getInstrumentation().getContext().getResources();
		final GifDrawable drawable = new GifDrawable(resources, R.raw.test);
		final GifAnimationMetaData metaData = new GifAnimationMetaData(resources, R.raw.test);

		assertEquals(drawable.getAllocationByteCount(), drawable.getFrameByteCount() + metaData.getAllocationByteCount());
		assertEquals(drawable.getAllocationByteCount(), metaData.getDrawableAllocationByteCount(null, 1));
		assertEquals(drawable.getAllocationByteCount(), metaData.getDrawableAllocationByteCount(drawable, 1));
	}
}
