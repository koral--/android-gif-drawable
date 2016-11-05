package pl.droidsonroids.gif;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import pl.droidsonroids.gif.test.R;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(AndroidJUnit4.class)
public class InputStreamTest {

	@Rule
	public MockWebServer mMockWebServer = new MockWebServer();

	@Test
	public void testGifDrawableCreationFromInputStream() throws Exception {
		final InputStream originalStream = InstrumentationRegistry.getContext().getResources().openRawResource(R.raw.test);
		mMockWebServer.enqueue(new MockResponse().setChunkedBody(new Buffer().readFrom(originalStream), 1 << 8));

		final URL url = new URL(mMockWebServer.url("/").toString());
		final BufferedInputStream responseStream = new BufferedInputStream(url.openConnection().getInputStream(), 1 << 16);

		final GifDrawable gifDrawable = new GifDrawable(responseStream);
		assertThat(gifDrawable.getError()).isEqualTo(GifError.NO_ERROR);
		assertThat(gifDrawable.getIntrinsicWidth()).isEqualTo(278);
		assertThat(gifDrawable.getIntrinsicHeight()).isEqualTo(183);
	}
}
