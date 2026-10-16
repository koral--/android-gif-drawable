package pl.droidsonroids.gif;

import android.content.res.AssetFileDescriptor;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import pl.droidsonroids.gif.test.R;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class InputStreamTest {

	@Test
	public void gifDrawableCreatedFromInputStream() throws Exception {
		final AssetFileDescriptor assetFileDescriptor = InstrumentationRegistry.getInstrumentation()
				.getContext().getResources().openRawResourceFd(R.raw.test);
		final byte[] buffer = new byte[(int) assetFileDescriptor.getDeclaredLength()];
		final FileInputStream inputStream = assetFileDescriptor.createInputStream();
		final int bufferedByteCount = inputStream.read(buffer);
		inputStream.close();
		assetFileDescriptor.close();
		assertEquals(buffer.length, bufferedByteCount);

		final InputStream responseStream = new ByteArrayInputStream(buffer);

		final GifDrawable gifDrawable = new GifDrawable(responseStream);
		assertEquals(GifError.NO_ERROR, gifDrawable.getError());
		assertEquals(278, gifDrawable.getIntrinsicWidth());
		assertEquals(183, gifDrawable.getIntrinsicHeight());
	}
}
