package pl.droidsonroids.gif;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.fakes.RoboAttributeSet;
import org.robolectric.res.Attribute;
import org.robolectric.res.ResName;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(RobolectricGradleTestRunner.class)
@Config(sdk = Build.VERSION_CODES.M, constants = BuildConfig.class)
public class GifViewUtilsTest {

	@Test
	public void testFreezesAnimationAttribute() {
		final Context context = RuntimeEnvironment.application;
		final String packageName = GifImageView.class.getPackage().getName();

		final ResName resName = new ResName(packageName, "attr", "freezesAnimation");
		final Attribute attribute = new Attribute(resName, "true", packageName);
		final AttributeSet attributeSet = RoboAttributeSet.create(context, attribute);
		final GifImageView gifImageView = new GifImageView(context, attributeSet);
		assertThat(GifViewUtils.isFreezingAnimation(gifImageView, attributeSet, 0, 0)).isTrue();
	}
}
