package pl.droidsonroids.gif;

import android.content.res.Resources;
import android.graphics.Bitmap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import pl.droidsonroids.gif.test.R;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Characterizes decoding of a real-world animated GIF, compressed and composed by an image editor.
 */
@RunWith(AndroidJUnit4.class)
public class AnimatedGifTest {

	private byte[] gif;
	private DecodedGif decoded;

	@Before
	public void setUp() throws Exception {
		gif = readRawResource(R.raw.test);
		decoded = DecodedGif.decode(gif);
	}

	@Test
	public void metadata() {
		assertEquals(278, decoded.width);
		assertEquals(183, decoded.height);
		assertEquals(0, decoded.loopCount);
		assertEquals(1000, decoded.duration);
		assertTrue(decoded.comment.startsWith("http://www.rtlsoft.com/animagic\n\nCreated with Animagic GIF V 1.06b"));
		assertArrayEquals(new int[]{250, 250, 250, 250}, decoded.frameDurations);
	}

	@Test
	public void frames() {
		assertArrayEquals(new long[]{97545333L, 1897542300L, 230902770L, 4075987651L}, decoded.frameChecksums);
	}

	@Test
	public void seekingDirectlyToFrameGivesSameResultAsSequentialDecoding() throws Exception {
		for (int frame = 0; frame < decoded.numberOfFrames; frame++) {
			final GifDecoder decoder = new GifDecoder(new InputSource.ByteArraySource(gif));
			final Bitmap buffer = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888);
			decoder.seekToFrame(frame, buffer);
			assertEquals("frame " + frame, decoded.frameChecksums[frame], DecodedGif.checksum(buffer));
			decoder.recycle();
		}
	}

	@Test
	public void seekingBackwardsGivesSameResultAsSequentialDecoding() throws Exception {
		final GifDecoder decoder = new GifDecoder(new InputSource.ByteArraySource(gif));
		final Bitmap buffer = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888);
		for (int frame = decoded.numberOfFrames - 1; frame >= 0; frame--) {
			decoder.seekToFrame(frame, buffer);
			assertEquals("frame " + frame, decoded.frameChecksums[frame], DecodedGif.checksum(buffer));
		}
		decoder.recycle();
	}

	@Test
	public void seekingToTimeShowsFrameDisplayedAtThatTime() throws Exception {
		final GifDecoder decoder = new GifDecoder(new InputSource.ByteArraySource(gif));
		final Bitmap buffer = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888);
		int frameStart = 0;
		for (int frame = 0; frame < decoded.numberOfFrames; frame++) {
			for (final int time : new int[]{frameStart, frameStart + decoded.frameDurations[frame] - 1}) {
				decoder.seekToTime(time, buffer);
				assertEquals("time " + time, decoded.frameChecksums[frame], DecodedGif.checksum(buffer));
			}
			frameStart += decoded.frameDurations[frame];
		}
		decoder.recycle();
	}

	private static byte[] readRawResource(final int id) throws IOException {
		final Resources resources = InstrumentationRegistry.getInstrumentation().getContext().getResources();
		final InputStream stream = resources.openRawResource(id);
		try {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			final byte[] buffer = new byte[8192];
			int read;
			while ((read = stream.read(buffer)) != -1) {
				bytes.write(buffer, 0, read);
			}
			return bytes.toByteArray();
		} finally {
			stream.close();
		}
	}
}
