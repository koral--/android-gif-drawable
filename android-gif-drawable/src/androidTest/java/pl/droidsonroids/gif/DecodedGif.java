package pl.droidsonroids.gif;

import android.graphics.Bitmap;
import android.text.TextUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Everything observable about a GIF decoded with {@link GifDecoder}, frames rendered as text for readable assertions.
 */
final class DecodedGif {

	static final char TRANSPARENT = '.';
	static final char UNKNOWN_COLOR = '?';
	static final int RED = 0xFF0000;
	static final int GREEN = 0x00FF00;
	static final int BLUE = 0x0000FF;
	static final int WHITE = 0xFFFFFF;
	static final int BLACK = 0x000000;

	private static final String COLOR_CHARS = "RGBWK";
	private static final int[] COLORS = {RED, GREEN, BLUE, WHITE, BLACK};

	final int width;
	final int height;
	final int numberOfFrames;
	final int loopCount;
	final int duration;
	final String comment;
	final int[] frameDurations;
	final String[] frames;
	final long[] frameChecksums;

	private DecodedGif(final GifDecoder decoder) {
		width = decoder.getWidth();
		height = decoder.getHeight();
		numberOfFrames = decoder.getNumberOfFrames();
		loopCount = decoder.getLoopCount();
		duration = decoder.getDuration();
		comment = decoder.getComment();
		frameDurations = new int[numberOfFrames];
		frames = new String[numberOfFrames];
		frameChecksums = new long[numberOfFrames];
		final Bitmap buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		for (int i = 0; i < numberOfFrames; i++) {
			frameDurations[i] = decoder.getFrameDuration(i);
			decoder.seekToFrame(i, buffer);
			frames[i] = render(buffer);
			frameChecksums[i] = checksum(buffer);
		}
		buffer.recycle();
	}

	/**
	 * Decodes with {@link GifDecoder} and checks that {@link GifDrawable} gives the same result from every {@link GifSource}.
	 */
	static DecodedGif decode(final byte[] gif) throws IOException {
		final DecodedGif decoded = decode(new InputSource.ByteArraySource(gif), null);
		final String expected = decoded.width + "x" + decoded.height + " loops=" + decoded.loopCount
				+ " duration=" + decoded.duration + " comment=" + decoded.comment
				+ " frames=" + Arrays.toString(decoded.frames).replace('\n', '/');
		for (final GifSource source : GifSource.values()) {
			final GifSource.Outcome outcome = source.decodeAll(gif);
			final String actual = outcome.toString().replaceFirst(" errors=.*", "");
			if (!expected.equals(actual) || !Arrays.equals(decoded.frameChecksums, outcome.frameChecksums)) {
				throw new AssertionError(source + " differs from GifDecoder\nexpected: " + expected + "\nactual:   " + actual);
			}
		}
		return decoded;
	}

	static DecodedGif decode(final InputSource source, final GifOptions options) throws IOException {
		final GifDecoder decoder = new GifDecoder(source, options);
		try {
			return new DecodedGif(decoder);
		} finally {
			decoder.recycle();
		}
	}

	/**
	 * Renders pixels as rows of characters separated by new lines: R, G, B, W, K for opaque red, green, blue,
	 * white and black, {@value #TRANSPARENT} for fully transparent and {@value #UNKNOWN_COLOR} for anything else.
	 */
	static String render(final Bitmap bitmap) {
		final int width = bitmap.getWidth();
		final int[] pixels = new int[width * bitmap.getHeight()];
		bitmap.getPixels(pixels, 0, width, 0, 0, width, bitmap.getHeight());
		final StringBuilder builder = new StringBuilder();
		for (int i = 0; i < pixels.length; i++) {
			if (i > 0 && i % width == 0) {
				builder.append('\n');
			}
			builder.append(toChar(pixels[i]));
		}
		return builder.toString();
	}

	/**
	 * CRC32 of all pixels, fully transparent ones counted as 0 regardless of their color bits.
	 */
	static long checksum(final Bitmap bitmap) {
		final int width = bitmap.getWidth();
		final int[] pixels = new int[width * bitmap.getHeight()];
		bitmap.getPixels(pixels, 0, width, 0, 0, width, bitmap.getHeight());
		final CRC32 crc = new CRC32();
		for (final int pixel : pixels) {
			final int normalized = pixel >>> 24 == 0 ? 0 : pixel;
			crc.update(normalized >>> 24);
			crc.update(normalized >>> 16);
			crc.update(normalized >>> 8);
			crc.update(normalized);
		}
		return crc.getValue();
	}

	static int[] palette(final String colorChars) {
		final int[] colors = new int[colorChars.length()];
		for (int i = 0; i < colors.length; i++) {
			colors[i] = COLORS[COLOR_CHARS.indexOf(colorChars.charAt(i))];
		}
		return colors;
	}

	/**
	 * Color indices of an image drawn with characters from the given palette, e.g. {@code indices("RG", "RRG")}.
	 */
	static byte[] indices(final String paletteChars, final String... rows) {
		final StringBuilder all = new StringBuilder();
		for (final String row : rows) {
			all.append(row);
		}
		final byte[] indices = new byte[all.length()];
		for (int i = 0; i < indices.length; i++) {
			indices[i] = (byte) paletteChars.indexOf(all.charAt(i));
		}
		return indices;
	}

	static String rows(final String... rows) {
		return TextUtils.join("\n", rows);
	}

	private static char toChar(final int argb) {
		final int alpha = argb >>> 24;
		if (alpha == 0) {
			return TRANSPARENT;
		}
		if (alpha == 0xFF) {
			for (int i = 0; i < COLORS.length; i++) {
				if (COLORS[i] == (argb & 0xFFFFFF)) {
					return COLOR_CHARS.charAt(i);
				}
			}
		}
		return UNKNOWN_COLOR;
	}
}
