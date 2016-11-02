package pl.droidsonroids.gif;

import android.content.res.Resources;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import pl.droidsonroids.gif.test.R;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(AndroidJUnit4.class)
public class AllocationByteCountTest {

	@Test
	public void testAllocationByteCountConsistency() throws Exception {
		final Resources resources = InstrumentationRegistry.getContext().getResources();
		final GifDrawable drawable = new GifDrawable(resources, R.raw.test);
		final GifAnimationMetaData metaData = new GifAnimationMetaData(resources, R.raw.test);
		assertThat(drawable.getFrameByteCount() + metaData.getAllocationByteCount()).isEqualTo(drawable.getAllocationByteCount());
		assertThat(metaData.getDrawableAllocationByteCount(null, 1)).isEqualTo(drawable.getAllocationByteCount());
		assertThat(metaData.getDrawableAllocationByteCount(drawable.getCurrentFrame(), 1)).isEqualTo(drawable.getAllocationByteCount());
	}
}
