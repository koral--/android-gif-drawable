package pl.droidsonroids.gif;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes GIF files block by block, so tests can describe edge cases and malformed files precisely.
 * Blocks are written in the order the methods are called, nothing is validated.
 */
final class GifBuilder {

	static final int DISPOSAL_UNSPECIFIED = 0;
	static final int DISPOSAL_NONE = 1;
	static final int DISPOSAL_BACKGROUND = 2;
	static final int DISPOSAL_PREVIOUS = 3;
	static final int NO_TRANSPARENCY = -1;

	private static final int MAX_LZW_CODE = 4095;
	private static final Charset ASCII = Charset.forName("US-ASCII");

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	/**
	 * GIF89a header and a logical screen descriptor with an optional global color table.
	 *
	 * @param globalColors 0xRRGGBB colors, padded with black to a power of two, or null for no global color table
	 */
	static GifBuilder gif(final int width, final int height, final int[] globalColors) {
		return new GifBuilder().raw("GIF89a").screen(width, height, globalColors, 0);
	}

	GifBuilder raw(final String ascii) {
		final byte[] bytes = ascii.getBytes(ASCII);
		out.write(bytes, 0, bytes.length);
		return this;
	}

	GifBuilder raw(final int... bytes) {
		for (final int b : bytes) {
			out.write(b);
		}
		return this;
	}

	GifBuilder screen(final int width, final int height, final int[] globalColors, final int backgroundIndex) {
		short16(width);
		short16(height);
		if (globalColors == null) {
			raw(0, backgroundIndex, 0);
		} else {
			final int tableBits = colorTableBits(globalColors.length);
			raw(0x80 | (tableBits - 1) << 4 | (tableBits - 1), backgroundIndex, 0);
			colorTable(globalColors, tableBits);
		}
		return this;
	}

	/**
	 * NETSCAPE2.0 application extension.
	 *
	 * @param loopCount number of repetitions stored in the file, 0 means infinite
	 */
	GifBuilder netscapeLoop(final int loopCount) {
		raw(0x21, 0xFF, 11).raw("NETSCAPE2.0");
		raw(3, 1);
		short16(loopCount);
		return raw(0);
	}

	GifBuilder comment(final String... subBlocks) {
		raw(0x21, 0xFE);
		for (final String subBlock : subBlocks) {
			final byte[] bytes = subBlock.getBytes(ASCII);
			out.write(bytes.length);
			out.write(bytes, 0, bytes.length);
		}
		return raw(0);
	}

	/**
	 * @param delayCentiseconds as stored in the file, in units of 10 ms
	 */
	GifBuilder graphicControl(final int disposal, final int delayCentiseconds, final int transparentIndex) {
		final int transparentFlag = transparentIndex == NO_TRANSPARENCY ? 0 : 1;
		raw(0x21, 0xF9, 4, disposal << 2 | transparentFlag);
		short16(delayCentiseconds);
		return raw(transparentFlag == 0 ? 0 : transparentIndex, 0);
	}

	/**
	 * Image descriptor and LZW compressed pixels.
	 *
	 * @param localColors 0xRRGGBB colors of a local color table, or null to use the global one
	 * @param indices     color indices, row by row, top to bottom (not interlaced)
	 */
	GifBuilder image(final int left, final int top, final int width, final int height,
	                 final int[] localColors, final boolean interlaced, final byte[] indices) {
		final int codeSize = minCodeSize(localColors, indices);
		imageDescriptor(left, top, width, height, localColors, interlaced);
		final byte[] ordered = interlaced ? interlace(indices, width, height) : indices;
		raw(codeSize);
		return subBlocks(lzw(ordered, codeSize, true));
	}

	/**
	 * Image filled with a single color index, using the global color table.
	 */
	GifBuilder image(final int left, final int top, final int width, final int height, final int colorIndex) {
		final byte[] indices = new byte[width * height];
		Arrays.fill(indices, (byte) colorIndex);
		return image(left, top, width, height, null, false, indices);
	}

	GifBuilder imageDescriptor(final int left, final int top, final int width, final int height,
	                           final int[] localColors, final boolean interlaced) {
		raw(0x2C);
		short16(left);
		short16(top);
		short16(width);
		short16(height);
		final int interlaceFlag = interlaced ? 0x40 : 0;
		if (localColors == null) {
			raw(interlaceFlag);
		} else {
			final int tableBits = colorTableBits(localColors.length);
			raw(0x80 | interlaceFlag | (tableBits - 1));
			colorTable(localColors, tableBits);
		}
		return this;
	}

	/**
	 * Splits data into sub-blocks of at most 255 bytes and writes the block terminator.
	 */
	GifBuilder subBlocks(final byte[] data) {
		for (int offset = 0; offset < data.length; offset += 255) {
			final int length = Math.min(255, data.length - offset);
			out.write(length);
			out.write(data, offset, length);
		}
		return raw(0);
	}

	GifBuilder trailer() {
		return raw(0x3B);
	}

	/**
	 * Number of bytes written so far, the offset of the next block.
	 */
	int size() {
		return out.size();
	}

	byte[] build() {
		return out.toByteArray();
	}

	private void short16(final int value) {
		raw(value & 0xFF, value >> 8 & 0xFF);
	}

	private void colorTable(final int[] colors, final int tableBits) {
		for (int i = 0; i < 1 << tableBits; i++) {
			final int color = i < colors.length ? colors[i] : 0;
			raw(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF);
		}
	}

	private static int colorTableBits(final int colorCount) {
		int bits = 1;
		while (1 << bits < colorCount) {
			bits++;
		}
		return bits;
	}

	private static int minCodeSize(final int[] localColors, final byte[] indices) {
		int maxIndex = localColors != null ? localColors.length - 1 : 0;
		for (final byte index : indices) {
			maxIndex = Math.max(maxIndex, index & 0xFF);
		}
		return Math.max(2, colorTableBits(maxIndex + 1));
	}

	static byte[] interlace(final byte[] indices, final int width, final int height) {
		final byte[] result = new byte[indices.length];
		final int[] offsets = {0, 4, 2, 1};
		final int[] steps = {8, 8, 4, 2};
		int row = 0;
		for (int pass = 0; pass < 4; pass++) {
			for (int y = offsets[pass]; y < height; y += steps[pass]) {
				System.arraycopy(indices, y * width, result, row * width, width);
				row++;
			}
		}
		return result;
	}

	/**
	 * LZW compression as done by giflib's encoder.
	 *
	 * @param clearWhenFull whether a clear code is written when the code table fills up,
	 *                      otherwise encoding continues with the full table ("deferred clear")
	 */
	static byte[] lzw(final byte[] indices, final int minCodeSize, final boolean clearWhenFull) {
		final BitWriter writer = new BitWriter();
		final int clearCode = 1 << minCodeSize;
		final int endCode = clearCode + 1;
		final Map<Integer, Integer> table = new HashMap<>();
		int codeBits = minCodeSize + 1;
		int nextCode = endCode + 1;
		writer.write(clearCode, codeBits);
		if (indices.length > 0) {
			int prefix = indices[0] & 0xFF;
			for (int i = 1; i < indices.length; i++) {
				final int pixel = indices[i] & 0xFF;
				final int key = prefix << 8 | pixel;
				final Integer code = table.get(key);
				if (code != null) {
					prefix = code;
					continue;
				}
				writer.write(prefix, codeBits);
				if (nextCode >= 1 << codeBits && codeBits < 12) {
					codeBits++;
				}
				if (nextCode < MAX_LZW_CODE) {
					table.put(key, nextCode++);
				} else if (clearWhenFull) {
					writer.write(clearCode, codeBits);
					table.clear();
					nextCode = endCode + 1;
					codeBits = minCodeSize + 1;
				}
				prefix = pixel;
			}
			writer.write(prefix, codeBits);
			if (nextCode >= 1 << codeBits && codeBits < 12) {
				codeBits++;
			}
		}
		writer.write(endCode, codeBits);
		return writer.toByteArray();
	}

	private static final class BitWriter {
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		private int buffer;
		private int bufferedBits;

		void write(final int code, final int bits) {
			buffer |= code << bufferedBits;
			bufferedBits += bits;
			while (bufferedBits >= 8) {
				bytes.write(buffer & 0xFF);
				buffer >>>= 8;
				bufferedBits -= 8;
			}
		}

		byte[] toByteArray() {
			if (bufferedBits > 0) {
				bytes.write(buffer & 0xFF);
				buffer = 0;
				bufferedBits = 0;
			}
			return bytes.toByteArray();
		}
	}
}
