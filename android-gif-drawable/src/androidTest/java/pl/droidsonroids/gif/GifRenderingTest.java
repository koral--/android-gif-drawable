package pl.droidsonroids.gif;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Random;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static pl.droidsonroids.gif.DecodedGif.decode;
import static pl.droidsonroids.gif.DecodedGif.indices;
import static pl.droidsonroids.gif.DecodedGif.palette;
import static pl.droidsonroids.gif.DecodedGif.rows;
import static pl.droidsonroids.gif.GifBuilder.DISPOSAL_BACKGROUND;
import static pl.droidsonroids.gif.GifBuilder.DISPOSAL_NONE;
import static pl.droidsonroids.gif.GifBuilder.DISPOSAL_PREVIOUS;
import static pl.droidsonroids.gif.GifBuilder.DISPOSAL_UNSPECIFIED;
import static pl.droidsonroids.gif.GifBuilder.NO_TRANSPARENCY;
import static pl.droidsonroids.gif.GifBuilder.gif;

/**
 * Characterizes how well-formed GIFs, including the less common features, are decoded and composed.
 */
@RunWith(AndroidJUnit4.class)
public class GifRenderingTest {

	private static final int[] RGBW = palette("RGBW");

	@Test
	public void singleFrameWithoutExtensions() throws Exception {
		final byte[] gif = gif(2, 2, palette("RG"))
				.image(0, 0, 2, 2, null, false, indices("RG", "RG", "GR"))
				.trailer()
				.build();

		final DecodedGif decoded = decode(gif);

		assertEquals(2, decoded.width);
		assertEquals(2, decoded.height);
		assertEquals(1, decoded.numberOfFrames);
		assertEquals(1, decoded.loopCount);
		assertEquals(100, decoded.duration);
		assertNull(decoded.comment);
		assertArrayEquals(new String[]{rows("RG", "GR")}, decoded.frames);
	}

	@Test
	public void frameDelaysBelowTwoCentisecondsDefaultTo100Milliseconds() throws Exception {
		final GifBuilder builder = gif(1, 1, RGBW);
		for (final int delay : new int[]{0, 1, 2, 7, 65535}) {
			builder.graphicControl(DISPOSAL_NONE, delay, NO_TRANSPARENCY).image(0, 0, 1, 1, 0);
		}

		final DecodedGif decoded = decode(builder.trailer().build());

		assertArrayEquals(new int[]{100, 100, 20, 70, 655350}, decoded.frameDurations);
		assertEquals(100 + 100 + 20 + 70 + 655350, decoded.duration);
	}

	@Test
	public void frameWithoutGraphicControlExtensionLastsDefaultDuration() throws Exception {
		final byte[] gif = gif(1, 1, RGBW)
				.graphicControl(DISPOSAL_NONE, 5, NO_TRANSPARENCY)
				.image(0, 0, 1, 1, 0)
				.image(0, 0, 1, 1, 1)
				.trailer()
				.build();

		assertArrayEquals(new int[]{50, 100}, decode(gif).frameDurations);
	}

	@Test
	public void loopCountIsOneWithoutNetscapeExtension() throws Exception {
		assertEquals(1, decode(twoFrames(null)).loopCount);
	}

	@Test
	public void netscapeLoopCountZeroMeansInfinite() throws Exception {
		assertEquals(0, decode(twoFrames(0)).loopCount);
	}

	@Test
	public void netscapeLoopCountIsNumberOfRepetitionsAfterFirstPlay() throws Exception {
		assertEquals(2, decode(twoFrames(1)).loopCount);
		assertEquals(6, decode(twoFrames(5)).loopCount);
		assertEquals(65536, decode(twoFrames(65535)).loopCount);
	}

	@Test
	public void commentSubBlocksAndExtensionsAreConcatenated() throws Exception {
		final byte[] gif = gif(1, 1, RGBW)
				.comment("Hello", ", ")
				.image(0, 0, 1, 1, 0)
				.comment("World")
				.trailer()
				.build();

		assertEquals("Hello, World", decode(gif).comment);
	}

	@Test
	public void commentSubBlocksExceedingLimitAreSkipped() throws Exception {
		final String subBlock = repeat('x', 255);
		final String[] subBlocks = new String[10];
		for (int i = 0; i < subBlocks.length; i++) {
			subBlocks[i] = subBlock;
		}
		final byte[] gif = gif(1, 1, RGBW)
				.comment(subBlocks)
				.comment("tail")
				.image(0, 0, 1, 1, 0)
				.trailer()
				.build();

		assertEquals(repeat('x', 255 * 8) + "tail", decode(gif).comment);
	}

	@Test
	public void canvasIsFilledWithBackgroundColorWhenFirstFrameIsOpaque() throws Exception {
		final byte[] gif = new GifBuilder().raw("GIF89a")
				.screen(3, 1, RGBW, 2)
				.image(0, 0, 1, 1, 0)
				.trailer()
				.build();

		assertEquals("RBB", decode(gif).frames[0]);
	}

	@Test
	public void canvasIsTransparentWhenFirstFrameHasTransparentColor() throws Exception {
		final byte[] gif = new GifBuilder().raw("GIF89a")
				.screen(3, 1, RGBW, 2)
				.graphicControl(DISPOSAL_NONE, 10, 3)
				.image(0, 0, 2, 1, null, false, indices("RGBW", "RW"))
				.trailer()
				.build();

		assertEquals("R..", decode(gif).frames[0]);
	}

	@Test
	public void transparentPixelsShowPreviousFrame() throws Exception {
		final byte[] gif = gif(3, 1, RGBW)
				.graphicControl(DISPOSAL_NONE, 10, NO_TRANSPARENCY)
				.image(0, 0, 3, 1, null, false, indices("RGBW", "RRR"))
				.graphicControl(DISPOSAL_NONE, 10, 3)
				.image(0, 0, 3, 1, null, false, indices("RGBW", "GWB"))
				.trailer()
				.build();

		assertArrayEquals(new String[]{"RRR", "GRB"}, decode(gif).frames);
	}

	@Test
	public void disposalNoneAndUnspecifiedKeepFrame() throws Exception {
		assertArrayEquals(new String[]{"RRR", "RRG"}, decode(disposalOfFullFrame(DISPOSAL_NONE)).frames);
		assertArrayEquals(new String[]{"RRR", "RRG"}, decode(disposalOfFullFrame(DISPOSAL_UNSPECIFIED)).frames);
	}

	@Test
	public void reservedDisposalValuesKeepFrame() throws Exception {
		for (int disposal = 4; disposal <= 7; disposal++) {
			assertArrayEquals("disposal " + disposal, new String[]{"RRR", "RRG"}, decode(disposalOfFullFrame(disposal)).frames);
		}
	}

	@Test
	public void disposalBackgroundClearsFrameAreaToTransparent() throws Exception {
		assertArrayEquals(new String[]{"RRR", "..G"}, decode(disposalOfFullFrame(DISPOSAL_BACKGROUND)).frames);
	}

	@Test
	public void disposalPreviousOfFirstFrameClearsToTransparent() throws Exception {
		assertArrayEquals(new String[]{"RRR", "..G"}, decode(disposalOfFullFrame(DISPOSAL_PREVIOUS)).frames);
	}

	@Test
	public void disposalPreviousRestoresCanvasFromBeforeFrame() throws Exception {
		final byte[] gif = gif(3, 1, RGBW)
				.graphicControl(DISPOSAL_NONE, 10, NO_TRANSPARENCY)
				.image(0, 0, 3, 1, 0)
				.graphicControl(DISPOSAL_PREVIOUS, 10, NO_TRANSPARENCY)
				.image(0, 0, 1, 1, 1)
				.graphicControl(DISPOSAL_NONE, 10, NO_TRANSPARENCY)
				.image(2, 0, 1, 1, 2)
				.trailer()
				.build();

		assertArrayEquals(new String[]{"RRR", "GRR", "RRB"}, decode(gif).frames);
	}

	@Test
	public void disposalIsSkippedWhenNextFrameIsOpaqueAndCoversPreviousOne() throws Exception {
		final byte[] gif = gif(3, 1, RGBW)
				.graphicControl(DISPOSAL_NONE, 10, NO_TRANSPARENCY)
				.image(0, 0, 3, 1, 0)
				.graphicControl(DISPOSAL_BACKGROUND, 10, NO_TRANSPARENCY)
				.image(1, 0, 1, 1, 1)
				.graphicControl(DISPOSAL_NONE, 10, NO_TRANSPARENCY)
				.image(0, 0, 2, 1, 2)
				.trailer()
				.build();

		assertArrayEquals(new String[]{"RRR", "RGR", "BBR"}, decode(gif).frames);
	}

	@Test
	public void interlacedImageIsDeinterlaced() throws Exception {
		final String[] image = {"R", "G", "B", "W", "R", "G", "B", "W", "R", "G"};
		final byte[] interlaced = gif(1, image.length, RGBW)
				.image(0, 0, 1, image.length, null, true, indices("RGBW", image))
				.trailer()
				.build();

		assertEquals(rows(image), decode(interlaced).frames[0]);
	}

	@Test
	public void localColorTableTakesPrecedence() throws Exception {
		final byte[] gif = gif(2, 1, palette("RR"))
				.image(0, 0, 2, 1, palette("GB"), false, indices("GB", "BG"))
				.graphicControl(DISPOSAL_NONE, 10, NO_TRANSPARENCY)
				.image(0, 0, 1, 1, 0)
				.trailer()
				.build();

		assertArrayEquals(new String[]{"BG", "RG"}, decode(gif).frames);
	}

	@Test
	public void grayscaleColorTableIsUsedWhenThereIsNone() throws Exception {
		final byte[] gif = gif(3, 1, null)
				.image(0, 0, 3, 1, null, false, new byte[]{0, (byte) 0x80, (byte) 0xFF})
				.trailer()
				.build();
		final GifDecoder decoder = new GifDecoder(new InputSource.ByteArraySource(gif));
		final Bitmap buffer = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888);

		decoder.seekToFrame(0, buffer);

		assertEquals(0xFF000000, buffer.getPixel(0, 0));
		assertEquals(0xFF808080, buffer.getPixel(1, 0));
		assertEquals(0xFFFFFFFF, buffer.getPixel(2, 0));
		decoder.recycle();
	}

	@Test
	public void compressedImagesWithAllCodeSizes() throws Exception {
		final Random random = new Random(42);
		for (int colorBits = 1; colorBits <= 8; colorBits++) {
			final int colorCount = 1 << colorBits;
			final int[] colors = new int[colorCount];
			for (int i = 0; i < colorCount; i++) {
				colors[i] = i % 2 == 0 ? DecodedGif.RED : DecodedGif.GREEN;
			}
			final byte[] pixels = new byte[64 * 64];
			for (int i = 0; i < pixels.length; i++) {
				// runs of repeated pixels, so the code table fills up and is cleared
				pixels[i] = (byte) (random.nextInt(4) == 0 ? random.nextInt(colorCount) : pixels[Math.max(0, i - 1)]);
			}
			final StringBuilder expected = new StringBuilder();
			for (int i = 0; i < pixels.length; i++) {
				if (i > 0 && i % 64 == 0) {
					expected.append('\n');
				}
				expected.append((pixels[i] & 0xFF) % 2 == 0 ? 'R' : 'G');
			}

			for (final boolean clearWhenFull : new boolean[]{true, false}) {
				final int codeSize = Math.max(2, colorBits);
				final byte[] gif = gif(64, 64, colors)
						.imageDescriptor(0, 0, 64, 64, null, false)
						.raw(codeSize)
						.subBlocks(GifBuilder.lzw(pixels, codeSize, clearWhenFull))
						.trailer()
						.build();

				assertEquals("color bits: " + colorBits + ", clear when full: " + clearWhenFull,
						expected.toString(), decode(gif).frames[0]);
			}
		}
	}

	@Test
	public void sampleSizeSkipsPixels() throws Exception {
		final byte[] gif = gif(4, 4, RGBW)
				.image(0, 0, 4, 4, null, false, indices("RGBW", "RRGG", "RRGG", "BBWW", "BBWW"))
				.trailer()
				.build();
		final GifOptions options = new GifOptions();
		options.setInSampleSize(2);

		final DecodedGif decoded = decode(new InputSource.ByteArraySource(gif), options);

		assertEquals(2, decoded.width);
		assertEquals(2, decoded.height);
		assertEquals(rows("RG", "BW"), decoded.frames[0]);
	}

	private static byte[] twoFrames(final Integer netscapeLoopCount) {
		final GifBuilder builder = gif(1, 1, RGBW);
		if (netscapeLoopCount != null) {
			builder.netscapeLoop(netscapeLoopCount);
		}
		return builder.image(0, 0, 1, 1, 0)
				.image(0, 0, 1, 1, 1)
				.trailer()
				.build();
	}

	private static byte[] disposalOfFullFrame(final int disposal) {
		return gif(3, 1, RGBW)
				.graphicControl(disposal, 10, NO_TRANSPARENCY)
				.image(0, 0, 3, 1, 0)
				.graphicControl(DISPOSAL_NONE, 10, NO_TRANSPARENCY)
				.image(2, 0, 1, 1, 1)
				.trailer()
				.build();
	}

	private static String repeat(final char c, final int count) {
		final char[] chars = new char[count];
		Arrays.fill(chars, c);
		return new String(chars);
	}
}
