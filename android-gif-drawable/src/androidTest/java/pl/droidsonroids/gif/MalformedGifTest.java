package pl.droidsonroids.gif;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static pl.droidsonroids.gif.DecodedGif.indices;
import static pl.droidsonroids.gif.DecodedGif.palette;
import static pl.droidsonroids.gif.GifBuilder.DISPOSAL_NONE;
import static pl.droidsonroids.gif.GifBuilder.NO_TRANSPARENCY;
import static pl.droidsonroids.gif.GifBuilder.gif;

/**
 * Characterizes malformed GIFs, many of them reproducing bugs reported in the past. Each one is read through every
 * {@link GifSource} and must give the same, expected outcome.
 */
@RunWith(AndroidJUnit4.class)
public class MalformedGifTest {

	private static final int[] RGBW = palette("RGBW");
	private static final String SINGLE_RED_PIXEL = "1x1 loops=1 duration=100 comment=null frames=[R] errors=NO_ERROR,NO_ERROR";

	@Test
	public void notGifFile() {
		assertOutcome("GifIOException NOT_GIF_FILE", new GifBuilder().raw(0x89).raw("PNG").raw(0x0D, 0x0A, 0x1A, 0x0A).raw(0, 0, 0, 0, 0, 0, 0).build());
	}

	@Test
	public void versionIsNotChecked() {
		for (final String header : new String[]{"GIF87a", "GIF88a", "GIFxyz"}) {
			assertOutcome(SINGLE_RED_PIXEL, new GifBuilder().raw(header).screen(1, 1, RGBW, 0).image(0, 0, 1, 1, 0).trailer().build());
		}
	}

	@Test
	public void zeroScreenWidthOrHeight() {
		assertOutcome("GifIOException INVALID_SCR_DIMS", gif(0, 1, RGBW).image(0, 0, 1, 1, 0).trailer().build());
		assertOutcome("GifIOException INVALID_SCR_DIMS", gif(1, 0, RGBW).image(0, 0, 1, 1, 0).trailer().build());
	}

	@Test
	public void noImages() {
		assertOutcome("GifIOException NO_FRAMES", gif(1, 1, RGBW).netscapeLoop(0).comment("no images").trailer().build());
	}

	@Test
	public void trailingGarbageIsIgnored() {
		assertOutcome("2x1 loops=1 duration=100 comment=null frames=[GG] errors=NO_ERROR,NO_ERROR", gif(2, 1, RGBW).image(0, 0, 2, 1, 1).trailer().raw("garbage").raw(0x2C, 0x21, 0).build());
	}

	/**
	 * #667, files written by Giffle contained a stray 0x00 between blocks.
	 */
	@Test
	public void unknownRecordTypeIsSkipped() {
		assertOutcome("2x1 loops=1 duration=300 comment=null frames=[RR, GR, GB] errors=WRONG_RECORD,WRONG_RECORD", gif(2, 1, RGBW)
				.image(0, 0, 2, 1, 0)
				.raw(0)
				.image(0, 0, 1, 1, 1)
				.raw(0x42)
				.image(1, 0, 1, 1, 2)
				.trailer()
				.build());
	}

	/**
	 * #433, NETSCAPE2.0 identifier without the loop count sub-block.
	 * <p>
	 * Current behavior, likely a bug: the block terminator is consumed twice, so the next block is misread and no frame is found.
	 */
	@Test
	public void netscapeExtensionWithoutLoopCount() {
		assertOutcome("GifIOException NO_FRAMES", gif(1, 1, RGBW)
				.raw(0x21, 0xFF, 11).raw("NETSCAPE2.0").raw(0)
				.image(0, 0, 1, 1, 0)
				.image(0, 0, 1, 1, 1)
				.trailer()
				.build());
	}

	@Test
	public void netscapeExtensionWithShortSubBlock() {
		assertOutcome("1x1 loops=1 duration=200 comment=null frames=[R, G] errors=NO_ERROR,NO_ERROR", gif(1, 1, RGBW)
				.raw(0x21, 0xFF, 11).raw("NETSCAPE2.0").raw(1, 1, 0)
				.image(0, 0, 1, 1, 0)
				.image(0, 0, 1, 1, 1)
				.trailer()
				.build());
	}

	/**
	 * Current behavior: a single malformed graphic control extension rejects the whole file.
	 */
	@Test
	public void graphicControlExtensionWithWrongSize() {
		assertOutcome("GifIOException NO_FRAMES", gif(1, 1, RGBW)
				.raw(0x21, 0xF9, 2, 0, 5, 0)
				.image(0, 0, 1, 1, 0)
				.image(0, 0, 1, 1, 1)
				.trailer()
				.build());
	}

	/**
	 * 0×0 frames like in the CVE-2019-11932 exploit (#673, #692), the double free they caused is no longer reachable.
	 * <p>
	 * Current behavior, likely a bug: decoding stops at the first 0×0 frame, so the following frames are never drawn.
	 * This comes from the {@code newRasterSize == 0} guard in DDGifSlurp.
	 */
	@Test
	public void zeroSizedFrames() {
		assertOutcome("2x1 loops=1 duration=400 comment=null frames=[RR, RR, RR, RR] errors=NO_ERROR,NO_ERROR", gif(2, 1, RGBW)
				.image(0, 0, 2, 1, 0)
				.image(0, 0, 0, 0, null, false, new byte[0])
				.image(0, 0, 0, 0, null, false, new byte[0])
				.image(0, 0, 1, 1, 1)
				.trailer()
				.build());
	}

	/**
	 * Current behavior, likely a bug: decoding stops at the 0×0 frame, so the following frame is never drawn.
	 */
	@Test
	public void zeroSizedFirstFrame() {
		assertOutcome("2x1 loops=1 duration=200 comment=null frames=[RR, RR] errors=NO_ERROR,NO_ERROR", gif(2, 1, RGBW)
				.image(0, 0, 0, 0, null, false, new byte[0])
				.image(0, 0, 2, 1, 1)
				.trailer()
				.build());
	}

	/**
	 * #678, a huge malformed frame after a valid one caused a heap buffer overflow.
	 */
	@Test
	public void hugeMalformedFrameAfterValidOne() {
		assertOutcome("2x1 loops=1 duration=100 comment=null frames=[GG] errors=IMG_NOT_CONFINED,IMG_NOT_CONFINED", gif(2, 1, RGBW)
				.image(0, 0, 2, 1, 1)
				.imageDescriptor(0, 0, 65535, 65535, null, false)
				.raw(44)
				.subBlocks(new byte[]{1, 2, 3})
				.trailer()
				.build());
	}

	/**
	 * #709, #243, #250, #801, frames not confined in the logical screen caused overflows and huge allocations.
	 */
	@Test
	public void frameExceedingScreenInOneDimension() {
		assertOutcome("3x2 loops=1 duration=100 comment=null frames=[RRR/RRR] errors=IMG_NOT_CONFINED,IMG_NOT_CONFINED", gif(3, 2, RGBW)
				.image(0, 0, 3, 2, 0)
				.image(2, 0, 2, 1, 1)
				.image(0, 0, 1, 1, 2)
				.trailer()
				.build());
	}

	@Test
	public void frameAtHugeOffset() {
		assertOutcome("3x2 loops=1 duration=100 comment=null frames=[RRR/RRR] errors=IMG_NOT_CONFINED,IMG_NOT_CONFINED", gif(3, 2, RGBW)
				.image(0, 0, 3, 2, 0)
				.image(26020, 28016, 1, 1, 1)
				.trailer()
				.build());
	}

	/**
	 * Current behavior: a first frame not confined in the screen rejects the whole file, even with valid frames after it.
	 */
	@Test
	public void firstFrameExceedingScreen() {
		assertOutcome("GifIOException NO_FRAMES", gif(2, 2, RGBW)
				.image(0, 0, 2, 3, 0)
				.image(0, 0, 1, 1, 1)
				.trailer()
				.build());
	}

	@Test
	public void lzwMinimumCodeSizeAbove8() {
		assertOutcome("2x1 loops=1 duration=100 comment=null frames=[RR] errors=READ_FAILED,READ_FAILED", gif(2, 1, RGBW)
				.image(0, 0, 2, 1, 0)
				.imageDescriptor(0, 0, 2, 1, null, false)
				.raw(9)
				.subBlocks(GifBuilder.lzw(new byte[]{1, 1}, 9, true))
				.trailer()
				.build());
	}

	@Test
	public void lzwMinimumCodeSizeBelow2() {
		assertOutcome("2x1 loops=1 duration=100 comment=null frames=[GR] errors=NO_ERROR,NO_ERROR", gif(2, 1, RGBW)
				.imageDescriptor(0, 0, 2, 1, null, false)
				.raw(1)
				.subBlocks(GifBuilder.lzw(new byte[]{1, 0}, 1, true))
				.trailer()
				.build());
	}

	@Test
	public void lzwCodeNotInTable() {
		// clear code (4), then 7 which is not defined yet, 3 bits each
		assertOutcome("2x1 loops=1 duration=200 comment=null frames=[GG, GG] errors=NO_ERROR,IMAGE_DEFECT", gif(2, 1, RGBW)
				.image(0, 0, 2, 1, 1)
				.imageDescriptor(0, 0, 2, 1, null, false)
				.raw(2)
				.subBlocks(new byte[]{(byte) 0b00_111_100, 0b0_101})
				.trailer()
				.build());
	}

	@Test
	public void lzwDataEndingBeforeAllPixels() {
		assertOutcome("2x2 loops=1 duration=200 comment=null frames=[GG/GG, BW/GG] errors=NO_ERROR,EOF_TOO_SOON", gif(2, 2, RGBW)
				.image(0, 0, 2, 2, 1)
				.imageDescriptor(0, 0, 2, 2, null, false)
				.raw(2)
				.subBlocks(GifBuilder.lzw(new byte[]{2, 3}, 2, true))
				.trailer()
				.build());
	}

	@Test
	public void lzwDataLongerThanImage() {
		assertOutcome("2x1 loops=1 duration=200 comment=null frames=[BW, GW] errors=NO_ERROR,NO_ERROR", gif(2, 1, RGBW)
				.imageDescriptor(0, 0, 2, 1, null, false)
				.raw(2)
				.subBlocks(GifBuilder.lzw(new byte[]{2, 3, 0, 1, 2, 3}, 2, true))
				.image(0, 0, 1, 1, 1)
				.trailer()
				.build());
	}

	@Test
	public void interlacedFrameWithTooFewRows() {
		assertOutcome("1x4 loops=1 duration=200 comment=null frames=[W/W/W/W, R/W/G/W] errors=NO_ERROR,EOF_TOO_SOON", gif(1, 4, RGBW)
				.image(0, 0, 1, 4, 3)
				.imageDescriptor(0, 0, 1, 4, null, true)
				.raw(2)
				.subBlocks(GifBuilder.lzw(new byte[]{0, 1}, 2, true))
				.trailer()
				.build());
	}

	/**
	 * Color tables always have 256 entries, the ones not in the file are black.
	 */
	@Test
	public void backgroundColorIndexOutsideColorTable() {
		assertOutcome("2x1 loops=1 duration=100 comment=null frames=[GK] errors=NO_ERROR,NO_ERROR", new GifBuilder().raw("GIF89a")
				.screen(2, 1, palette("RG"), 200)
				.image(0, 0, 1, 1, 1)
				.trailer()
				.build());
	}

	@Test
	public void transparentColorIndexOutsideColorTable() {
		assertOutcome("2x1 loops=1 duration=100 comment=null frames=[RG] errors=NO_ERROR,NO_ERROR", gif(2, 1, palette("RG"))
				.graphicControl(DISPOSAL_NONE, 10, 200)
				.image(0, 0, 2, 1, null, false, indices("RG", "RG"))
				.trailer()
				.build());
	}

	/**
	 * Color tables always have 256 entries, the ones not in the file are black.
	 */
	@Test
	public void pixelIndexOutsideColorTable() {
		assertOutcome("2x1 loops=1 duration=100 comment=null frames=[GK] errors=NO_ERROR,NO_ERROR", gif(2, 1, palette("RG"))
				.graphicControl(DISPOSAL_NONE, 10, NO_TRANSPARENCY)
				.image(0, 0, 2, 1, null, false, new byte[]{1, 7})
				.trailer()
				.build());
	}

	@Test
	public void inputStreamWithoutMarkSupportIsRejected() throws Exception {
		final byte[] gif = gif(1, 1, RGBW).image(0, 0, 1, 1, 0).trailer().build();
		final InputStream stream = new ByteArrayInputStream(gif) {
			@Override
			public boolean markSupported() {
				return false;
			}
		};
		try {
			new GifDrawable(stream);
			fail("Expected IllegalArgumentException");
		} catch (IllegalArgumentException expected) {
			assertEquals("InputStream does not support marking", expected.getMessage());
		}
	}

	private static void assertOutcome(final String expected, final byte[] gif) {
		final StringBuilder differences = new StringBuilder();
		for (final GifSource source : GifSource.values()) {
			final String actual = source.decodeAll(gif).toString();
			if (!expected.equals(actual)) {
				differences.append('\n').append(source).append(": ").append(actual);
			}
		}
		if (differences.length() > 0) {
			fail("expected: " + expected + differences);
		}
	}
}
