package pl.droidsonroids.gif;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertTrue;
import static pl.droidsonroids.gif.DecodedGif.indices;
import static pl.droidsonroids.gif.DecodedGif.palette;
import static pl.droidsonroids.gif.GifBuilder.DISPOSAL_BACKGROUND;
import static pl.droidsonroids.gif.GifBuilder.DISPOSAL_NONE;
import static pl.droidsonroids.gif.GifBuilder.NO_TRANSPARENCY;

/**
 * Characterizes GIFs cut off at every possible byte, as in incomplete downloads, read through every kind of source.
 */
@RunWith(AndroidJUnit4.class)
public class TruncatedGifTest {

	private static final String FIRST_FRAME = "RGB/BWR";
	private static final String SECOND_FRAME = "RGB/BRW";

	private final byte[] gif;
	private final int screenEnd;
	private final int firstImageDescriptorEnd;
	private final int firstFrameEnd;
	private final int secondImageDescriptorEnd;
	private final int secondFrameEnd;

	public TruncatedGifTest() {
		final GifBuilder builder = new GifBuilder()
				.raw("GIF89a")
				.screen(3, 2, palette("RGBW"), 0);
		screenEnd = builder.size();
		builder.netscapeLoop(2)
				.comment("hi")
				.graphicControl(DISPOSAL_NONE, 5, NO_TRANSPARENCY)
				.imageDescriptor(0, 0, 3, 2, null, false);
		firstImageDescriptorEnd = builder.size();
		builder.raw(2).subBlocks(GifBuilder.lzw(indices("RGBW", "RGB", "BWR"), 2, true));
		firstFrameEnd = builder.size();
		builder.graphicControl(DISPOSAL_BACKGROUND, 7, 1)
				.imageDescriptor(1, 0, 2, 2, palette("GBRW"), true);
		secondImageDescriptorEnd = builder.size();
		builder.raw(2).subBlocks(GifBuilder.lzw(GifBuilder.interlace(indices("GBRW", "GB", "RW"), 2, 2), 2, true));
		secondFrameEnd = builder.size();
		gif = builder.trailer().build();
	}

	@Test
	public void completeFileIsDecoded() {
		for (final GifSource source : GifSource.values()) {
			assertOutcome(source, gif.length,
					"3x2 loops=3 duration=120 comment=hi frames=[" + FIRST_FRAME + ", " + SECOND_FRAME + "] errors=NO_ERROR,NO_ERROR");
		}
	}

	@Test
	public void missingTrailerIsReportedAfterDecodingAllFrames() {
		for (final GifSource source : GifSource.values()) {
			assertOutcome(source, secondFrameEnd,
					"3x2 loops=3 duration=120 comment=hi frames=[" + FIRST_FRAME + ", " + SECOND_FRAME + "] errors=READ_FAILED,READ_FAILED");
		}
	}

	@Test
	public void emptyInputCannotBeRead() {
		for (final GifSource source : GifSource.values()) {
			final GifError expected = source == GifSource.DIRECT_BYTE_BUFFER ? GifError.INVALID_BYTE_BUFFER : GifError.READ_FAILED;
			assertOutcome(source, 0, "GifIOException " + expected);
		}
	}

	@Test
	public void truncatedHeaderCannotBeRead() {
		assertOutcomeInRange(1, 6, "GifIOException " + GifError.READ_FAILED);
	}

	@Test
	public void truncatedScreenDescriptorOrGlobalColorTable() {
		assertOutcomeInRange(6, screenEnd, "GifIOException " + GifError.NO_SCRN_DSCR);
	}

	@Test
	public void truncatedBeforeFirstImageData() {
		assertOutcomeInRange(screenEnd, firstImageDescriptorEnd, "GifIOException " + GifError.NO_FRAMES);
	}

	@Test
	public void truncatedBeforeSecondImageData() {
		assertOutcomeInRange(firstFrameEnd, secondImageDescriptorEnd,
				"3x2 loops=3 duration=50 comment=hi frames=[" + FIRST_FRAME + "] errors=READ_FAILED,READ_FAILED");
	}

	/**
	 * Pixels of a frame cut off in its image data are not specified, they currently depend on uninitialized memory.
	 */
	@Test
	public void truncatedInImageDataDoesNotCrash() {
		final List<Integer> lengths = new ArrayList<>();
		for (int length = firstImageDescriptorEnd; length < firstFrameEnd; length++) {
			lengths.add(length);
		}
		for (int length = secondImageDescriptorEnd; length < secondFrameEnd; length++) {
			lengths.add(length);
		}
		for (final GifSource source : GifSource.values()) {
			for (final int length : lengths) {
				final GifSource.Outcome outcome = source.decodeAll(Arrays.copyOf(gif, length));
				final String message = source + " truncated to " + length + ": " + outcome;
				if (outcome.openError != null) {
					assertTrue(message, outcome.openError == GifError.NO_FRAMES && length < firstFrameEnd);
				} else {
					assertTrue(message, outcome.unexpectedException == null);
					assertTrue(message, outcome.width == 3 && outcome.height == 2 && outcome.loopCount == 3 && "hi".equals(outcome.comment));
					final int framesBefore = length < firstFrameEnd ? 0 : 1;
					assertTrue(message, outcome.frames.length == framesBefore || outcome.frames.length == framesBefore + 1);
					assertTrue(message, outcome.errorAfterDecoding != GifError.NO_ERROR);
				}
			}
		}
	}

	private void assertOutcomeInRange(final int fromLength, final int toLengthExclusive, final String expected) {
		assertTrue("empty range", fromLength < toLengthExclusive);
		for (final GifSource source : GifSource.values()) {
			for (int length = fromLength; length < toLengthExclusive; length++) {
				assertOutcome(source, length, expected);
			}
		}
	}

	private void assertOutcome(final GifSource source, final int length, final String expected) {
		final String actual = source.decodeAll(Arrays.copyOf(gif, length)).toString();
		assertTrue(source + " truncated to " + length + "\nexpected: " + expected + "\nactual:   " + actual, expected.equals(actual));
	}
}
