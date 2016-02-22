package pl.droidsonroids.gif;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.AttributeSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.res.Attribute;
import org.robolectric.res.ResourceLoader;
import org.robolectric.shadows.RoboAttributeSet;
import org.robolectric.shadows.ShadowResources;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(RobolectricGradleTestRunner.class)
@Config(sdk = Build.VERSION_CODES.LOLLIPOP, constants = BuildConfig.class)
public class GifViewUtilsTest {

	@Test
	public void testFreezesAnimationAttribute() {
		final Context context = RuntimeEnvironment.application;
		final String packageName = GifImageView.class.getPackage().getName();
		final Resources resources = context.getResources();
		final ResourceLoader resourceLoader = ((ShadowResources) ShadowExtractor.extract(resources)).getResourceLoader();

		final Attribute attribute = new Attribute(packageName + ":attr/freezesAnimation", "true", packageName);
		final AttributeSet attributeSet = new RoboAttributeSet(Collections.singletonList(attribute), resourceLoader);
		final GifImageView gifImageView = new GifImageView(context, attributeSet);
		assertThat(GifViewUtils.isFreezingAnimation(gifImageView, attributeSet, 0, 0)).isTrue();
	}
}
