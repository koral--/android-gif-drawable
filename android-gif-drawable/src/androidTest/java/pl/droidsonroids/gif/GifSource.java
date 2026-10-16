package pl.droidsonroids.gif;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Ways of feeding the same bytes to {@link GifDrawable}, each going through a different native reader.
 */
enum GifSource {
	BYTE_ARRAY {
		@Override
		GifDrawableBuilder from(final GifDrawableBuilder builder, final byte[] gif) {
			return builder.from(gif);
		}
	},
	DIRECT_BYTE_BUFFER {
		@Override
		GifDrawableBuilder from(final GifDrawableBuilder builder, final byte[] gif) {
			final ByteBuffer buffer = ByteBuffer.allocateDirect(gif.length);
			buffer.put(gif);
			return builder.from(buffer);
		}
	},
	FILE {
		@Override
		GifDrawableBuilder from(final GifDrawableBuilder builder, final byte[] gif) throws IOException {
			return builder.from(writeToFile(name(), gif));
		}
	},
	ASSET_FILE_DESCRIPTOR {
		@Override
		GifDrawableBuilder from(final GifDrawableBuilder builder, final byte[] gif) throws IOException {
			// The GIF does not start at the beginning of the file, like in an APK, the drawable takes ownership of the descriptor
			final byte[] prefixed = new byte[FILE_OFFSET + gif.length];
			System.arraycopy(gif, 0, prefixed, FILE_OFFSET, gif.length);
			final ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(writeToFile(name(), prefixed), ParcelFileDescriptor.MODE_READ_ONLY);
			return builder.from(new AssetFileDescriptor(descriptor, FILE_OFFSET, gif.length));
		}
	},
	INPUT_STREAM {
		@Override
		GifDrawableBuilder from(final GifDrawableBuilder builder, final byte[] gif) {
			return builder.from(new BufferedInputStream(new ByteArrayInputStream(gif)));
		}
	},
	INPUT_STREAM_READING_ONE_BYTE_AT_A_TIME {
		@Override
		GifDrawableBuilder from(final GifDrawableBuilder builder, final byte[] gif) {
			// Like a slow network stream, each read returns fewer bytes than requested
			return builder.from(new FilterInputStream(new BufferedInputStream(new ByteArrayInputStream(gif))) {
				@Override
				public int read(final byte[] buffer, final int offset, final int length) throws IOException {
					return super.read(buffer, offset, Math.min(1, length));
				}
			});
		}
	};

	private static final int FILE_OFFSET = 7;

	abstract GifDrawableBuilder from(GifDrawableBuilder builder, byte[] gif) throws IOException;

	/**
	 * Builds a drawable which renders frames only when asked to, so the result does not depend on timing.
	 */
	GifDrawable open(final byte[] gif) throws IOException {
		return from(new GifDrawableBuilder().renderingTriggeredOnDraw(true), gif).build();
	}

	/**
	 * Opens the GIF and decodes all its frames, capturing everything observable on the way.
	 */
	Outcome decodeAll(final byte[] gif) {
		final Outcome outcome = new Outcome();
		final GifDrawable drawable;
		try {
			drawable = open(gif);
		} catch (GifIOException exception) {
			outcome.openError = exception.reason;
			return outcome;
		} catch (Exception exception) {
			outcome.unexpectedException = exception;
			return outcome;
		}
		try {
			drawable.stop();
			outcome.width = drawable.getIntrinsicWidth();
			outcome.height = drawable.getIntrinsicHeight();
			outcome.loopCount = drawable.getLoopCount();
			outcome.duration = drawable.getDuration();
			outcome.comment = drawable.getComment();
			outcome.errorAfterOpen = drawable.getError();
			outcome.frames = new String[drawable.getNumberOfFrames()];
			outcome.frameChecksums = new long[outcome.frames.length];
			for (int i = 0; i < outcome.frames.length; i++) {
				final Bitmap frame = drawable.seekToFrameAndGet(i);
				outcome.frames[i] = DecodedGif.render(frame).replace('\n', '/');
				outcome.frameChecksums[i] = DecodedGif.checksum(frame);
				frame.recycle();
			}
			outcome.errorAfterDecoding = drawable.getError();
		} catch (Exception exception) {
			outcome.unexpectedException = exception;
		} finally {
			drawable.recycle();
		}
		return outcome;
	}

	static final class Outcome {
		GifError openError;
		Exception unexpectedException;
		int width;
		int height;
		int loopCount;
		int duration;
		String comment;
		GifError errorAfterOpen;
		GifError errorAfterDecoding;
		String[] frames;
		long[] frameChecksums;

		@Override
		public String toString() {
			if (unexpectedException != null) {
				return "unexpected " + unexpectedException;
			}
			if (openError != null) {
				return "GifIOException " + openError;
			}
			return width + "x" + height + " loops=" + loopCount + " duration=" + duration + " comment=" + comment
					+ " frames=" + Arrays.toString(frames) + " errors=" + errorAfterOpen + "," + errorAfterDecoding;
		}
	}

	private static File writeToFile(final String name, final byte[] gif) throws IOException {
		final File file = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), name + ".gif");
		final FileOutputStream stream = new FileOutputStream(file);
		try {
			stream.write(gif);
		} finally {
			stream.close();
		}
		return file;
	}
}
