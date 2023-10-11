package pl.droidsonroids.gif;

import org.junit.Test;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class GifDrawableBuilderTest {

	@Test
	public void testOptionsAndSampleSizeConflict() throws Exception {
		GifDrawableBuilder builder = new GifDrawableBuilder();
		GifOptions options = new GifOptions();
		builder.options(options);
		builder.sampleSize(3);
		assertThat(options.inSampleSize).isEqualTo((char) 1);
	}

}