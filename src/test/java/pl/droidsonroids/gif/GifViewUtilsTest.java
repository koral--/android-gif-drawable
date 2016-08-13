package pl.droidsonroids.gif;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.M, constants = BuildConfig.class, manifest = Config.NONE)
public class GifViewUtilsTest {

	@Test
	public void testFreezesAnimationAttribute() {
		final Context context = RuntimeEnvironment.application;
		final AttributeSet attributeSet = Robolectric.buildAttributeSet()
				.addAttribute(R.attr.freezesAnimation, "true")
				.build();

		final GifImageView gifImageView = new GifImageView(context, attributeSet);
		assertThat(GifViewUtils.isFreezingAnimation(gifImageView, attributeSet, 0, 0)).isTrue();
	}
}
