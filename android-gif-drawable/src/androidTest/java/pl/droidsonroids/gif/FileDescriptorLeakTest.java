package pl.droidsonroids.gif;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertTrue;
import static pl.droidsonroids.gif.DecodedGif.palette;
import static pl.droidsonroids.gif.GifBuilder.gif;

/**
 * Descriptors passed to or opened by the library are closed exactly once, also when opening fails (#747, #845, #870).
 */
@RunWith(AndroidJUnit4.class)
public class FileDescriptorLeakTest {

	private static final int REPETITIONS = 200;
	private static final int TOLERANCE = 10;

	@Test
	public void descriptorsAreClosedAfterRecycle() throws Exception {
		final byte[] gif = gif(1, 1, palette("RG")).image(0, 0, 1, 1, 0).image(0, 0, 1, 1, 1).trailer().build();
		for (final GifSource source : GifSource.values()) {
			final int before = openDescriptorCount();
			for (int i = 0; i < REPETITIONS; i++) {
				final GifDrawable drawable = source.open(gif);
				drawable.seekToFrameAndGet(1).recycle();
				drawable.recycle();
			}
			assertDescriptorCountNotIncreased(source, before);
		}
	}

	@Test
	public void descriptorsAreClosedWhenOpeningFails() throws Exception {
		final byte[] notGif = "not a GIF file".getBytes("US-ASCII");
		for (final GifSource source : GifSource.values()) {
			final int before = openDescriptorCount();
			for (int i = 0; i < REPETITIONS; i++) {
				try {
					source.open(notGif);
					throw new AssertionError("Expected GifIOException");
				} catch (GifIOException expected) {
					// expected
				}
			}
			assertDescriptorCountNotIncreased(source, before);
		}
	}

	private static void assertDescriptorCountNotIncreased(final GifSource source, final int before) {
		final int after = openDescriptorCount();
		assertTrue(source + ": " + before + " descriptors open before, " + after + " after", after <= before + TOLERANCE);
	}

	private static int openDescriptorCount() {
		final String[] descriptors = new File("/proc/self/fd").list();
		if (descriptors == null) {
			throw new IllegalStateException("Cannot list /proc/self/fd");
		}
		return descriptors.length;
	}
}
