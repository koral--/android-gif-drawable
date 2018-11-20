package pl.droidsonroids.gif;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class ErrnoMessageTest {

	@Rule
	public ExpectedException mExpectedException = ExpectedException.none();
	@Rule
	public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

	@Test
	public void errnoMessageAppendedToOpenFailed() throws Exception {
		mExpectedException.expect(GifIOException.class);
		mExpectedException.expectMessage("GifError 101: Failed to open given input: No such file or directory");
		final File nonExistentFile = new File(mTemporaryFolder.getRoot(), "non-existent");
		new GifDrawable(nonExistentFile);
	}

	@Test
	public void errnoMessageAppendedToReadFailed() throws Exception {
		mExpectedException.expect(GifIOException.class);
		mExpectedException.expectMessage("GifError 102: Failed to read from given input: Is a directory");
		new GifDrawable(mTemporaryFolder.getRoot());
	}

}
