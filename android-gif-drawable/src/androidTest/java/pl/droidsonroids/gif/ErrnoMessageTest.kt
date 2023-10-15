package pl.droidsonroids.gif

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import pl.droidsonroids.gif.GifIOException
import java.io.File

@RunWith(AndroidJUnit4::class)
class ErrnoMessageTest {

    @get:Rule
    var mExpectedException = ExpectedException.none()

    @get:Rule
    var mTemporaryFolder = TemporaryFolder()

    @Test
    fun errnoMessageAppendedToOpenFailed() {
        mExpectedException.expect(GifIOException::class.java)

        mExpectedException.expectMessage("GifError 101: Failed to open given input: No such file or directory")

        val nonExistentFile = File(mTemporaryFolder.root, "non-existent")

        GifDrawable(nonExistentFile)
    }

    @Test
    fun errnoMessageAppendedToReadFailed() {
        mExpectedException.expect(GifIOException::class.java)

        mExpectedException.expectMessage("GifError 102: Failed to read from given input: Is a directory")

        GifDrawable(mTemporaryFolder.root)
    }
}