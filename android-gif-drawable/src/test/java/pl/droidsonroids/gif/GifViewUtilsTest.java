package pl.droidsonroids.gif;

import android.util.AttributeSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
public class GifViewUtilsTest {

	@Test
	public void testFreezesAnimationAttribute() {
		final AttributeSet attributeSet = Robolectric.buildAttributeSet()
				.addAttribute(R.attr.freezesAnimation, "true")
				.build();

		final GifImageView gifImageView = new GifImageView(RuntimeEnvironment.application, attributeSet);
		assertThat(GifViewUtils.isFreezingAnimation(gifImageView, attributeSet, 0, 0)).isTrue();
	}
}
