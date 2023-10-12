package pl.droidsonroids.gif

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.assertj.core.api.Assertions
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.InputStream

@RunWith(AndroidJUnit4::class)
class InputStreamTest {

    @Test
    fun gifDrawableCreatedFromInputStream() {
        val assetFileDescriptor = InstrumentationRegistry.getInstrumentation()
            .context.resources.openRawResourceFd(pl.droidsonroids.gif.test.R.raw.test)
        val buffer = ByteArray(assetFileDescriptor.declaredLength.toInt())
        val inputStream = assetFileDescriptor.createInputStream()
        val bufferedByteCount = inputStream.read(buffer)
        inputStream.close()
        assetFileDescriptor.close()
        Assertions.assertThat(bufferedByteCount).isEqualTo(buffer.size)
        val responseStream: InputStream = ByteArrayInputStream(buffer)
        val gifDrawable = GifDrawable(responseStream)
        Assertions.assertThat(gifDrawable.error).isEqualTo(GifError.NO_ERROR)
        Assertions.assertThat(gifDrawable.intrinsicWidth).isEqualTo(278)
        Assertions.assertThat(gifDrawable.intrinsicHeight).isEqualTo(183)
    }
}