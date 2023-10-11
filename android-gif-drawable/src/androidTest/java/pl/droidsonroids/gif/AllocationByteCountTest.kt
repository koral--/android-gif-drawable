package pl.droidsonroids.gif;

import android.content.res.Resources;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import pl.droidsonroids.gif.test.R;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(AndroidJUnit4.class)
public class AllocationByteCountTest {

	@Test
	public void allocationByteCountIsConsistent() throws Exception {
		final Resources resources = InstrumentationRegistry.getInstrumentation().getContext().getResources();
		final GifDrawable drawable = new GifDrawable(resources, R.raw.test);
		final GifAnimationMetaData metaData = new GifAnimationMetaData(resources, R.raw.test);

		assertThat(drawable.getFrameByteCount() + metaData.getAllocationByteCount()).isEqualTo(drawable.getAllocationByteCount());
		assertThat(metaData.getDrawableAllocationByteCount(null, 1)).isEqualTo(drawable.getAllocationByteCount());
		assertThat(metaData.getDrawableAllocationByteCount(drawable, 1)).isEqualTo(drawable.getAllocationByteCount());
	}
}
