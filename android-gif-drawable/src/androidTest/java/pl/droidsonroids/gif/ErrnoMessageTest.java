package pl.droidsonroids.gif;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class ErrnoMessageTest {

	@Rule
	public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

	@Test
	public void errnoMessageAppendedToOpenFailed() throws Exception {
		final File nonExistentFile = new File(mTemporaryFolder.getRoot(), "non-existent");
		try {
			new GifDrawable(nonExistentFile);
			fail("Expected GifIOException to be thrown");
		} catch (GifIOException exception) {
			assertNotNull(exception.getMessage());
			assertTrue(exception.getMessage().contains("GifError 101: Failed to open given input: No such file or directory"));
		}
	}

	@Test
	public void errnoMessageAppendedToReadFailed() throws Exception {
		try {
			new GifDrawable(mTemporaryFolder.getRoot());
			fail("Expected GifIOException to be thrown");
		} catch (GifIOException exception) {
			assertNotNull(exception.getMessage());
			assertTrue(exception.getMessage().contains("GifError 102: Failed to read from given input: Is a directory"));
		}
	}

}
