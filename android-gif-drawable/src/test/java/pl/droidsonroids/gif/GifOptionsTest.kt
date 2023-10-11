package pl.droidsonroids.gif;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GifOptionsTest {

	private GifOptions gifOptions;

	@Before
	public void setUp() {
		gifOptions = new GifOptions();
	}

	@Test
	public void testInitialValues() {
		assertThat(gifOptions.inSampleSize).isEqualTo((char) 1);
		assertThat(gifOptions.inIsOpaque).isFalse();
	}

	@Test
	public void setInSampleSize() {
		gifOptions.setInSampleSize(2);
		assertThat(gifOptions.inSampleSize).isEqualTo((char) 2);
	}

	@Test
	public void setInIsOpaque() {
		gifOptions.setInIsOpaque(true);
		assertThat(gifOptions.inIsOpaque).isTrue();
	}

	@Test
	public void copyFromNonNull() {
		GifOptions source = new GifOptions();
		source.setInIsOpaque(false);
		source.setInSampleSize(8);
		gifOptions.setFrom(source);
		assertThat(gifOptions).isEqualToComparingFieldByField(source);
	}

	@Test
	public void copyFromNull() {
		GifOptions defaultOptions = new GifOptions();
		gifOptions.setInIsOpaque(false);
		gifOptions.setInSampleSize(8);
		gifOptions.setFrom(null);
		assertThat(gifOptions).isEqualToComparingFieldByField(defaultOptions);
	}
}