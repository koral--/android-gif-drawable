package pl.droidsonroids.gif.sample

import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.concurrent.ExecutionException

private const val GIF_URL =
	"https://raw.githubusercontent.com/koral--/android-gif-drawable-sample/cb2d1f42b3045b2790a886d1574d3e74281de743/sample/src/main/assets/Animated-Flag-Hungary.gif"

fun downloadGif(httpFragment: HttpFragment, job: Job) = launch(parent = job) {
	val fragmentReference = WeakReference(httpFragment)
	val urlConnection = URL(GIF_URL).openConnection()
	urlConnection.connect()
	val contentLength = urlConnection.contentLength
	if (contentLength < 0) {
		throw IOException("Content-Length header not present")
	}
	urlConnection.getInputStream().use {
		val buffer = ByteBuffer.allocateDirect(contentLength)
		Channels.newChannel(it).use { channel ->
			while (buffer.remaining() > 0) {
				channel.read(buffer)
			}
		}
		display@ launch(context = UI, parent = job) {
			val fragment = fragmentReference.get() ?: return@display
			try {
				fragment.onGifDownloaded(buffer)
			} catch (e: InterruptedException) {
				Thread.currentThread().interrupt()
			} catch (e: ExecutionException) {
				fragment.onDownloadFailed(e)
			}
		}
	}
}
