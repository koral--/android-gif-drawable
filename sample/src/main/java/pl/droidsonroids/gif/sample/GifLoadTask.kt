package pl.droidsonroids.gif.sample

import android.os.Build
import android.support.annotation.RequiresApi
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

private const val GIF_URL = "https://raw.githubusercontent.com/koral--/android-gif-drawable-sample/cb2d1f42b3045b2790a886d1574d3e74281de743/sample/src/main/assets/Animated-Flag-Hungary.gif"

@RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
internal class GifLoadTask(httpFragment: HttpFragment) : FutureTask<ByteBuffer>(Callable {
	val urlConnection = URL(GIF_URL).openConnection()
	urlConnection.connect()
	val contentLength = urlConnection.contentLength
	if (contentLength < 0) {
		throw IOException("Content-Length not present")
	}
	urlConnection.getInputStream().use {
		val buffer = ByteBuffer.allocateDirect(contentLength)
		Channels.newChannel(it).use { channel ->
			while (buffer.remaining() > 0) {
				channel.read(buffer)
			}
		}
		return@Callable buffer
	}
}) {
	private val mFragmentReference = WeakReference(httpFragment)

	override fun done() {
		val httpFragment = mFragmentReference.get() ?: return
		try {
			httpFragment.onGifDownloaded(get())
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
		} catch (e: ExecutionException) {
			httpFragment.onDownloadFailed(e)
		}

	}
}
