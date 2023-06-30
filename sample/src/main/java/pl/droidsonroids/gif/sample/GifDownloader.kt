package pl.droidsonroids.gif.sample

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.Channels

private const val GIF_URL =
    "https://raw.githubusercontent.com/koral--/android-gif-drawable-sample/cb2d1f42b3045b2790a886d1574d3e74281de743/sample/src/main/assets/Animated-Flag-Hungary.gif"

class GifDownloader(private val httpFragment: HttpFragment) {
    private val fragmentReference = WeakReference(httpFragment)
    private var loadJob: Job? = null

    fun load() {
        loadJob = httpFragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val buffer = downloadGif()
                runOnUiThread {
                    onGifDownloaded(buffer)
                }
            } catch (e: IOException) {
                runOnUiThread {
                    onDownloadFailed(e)
                }
            }
        }
    }

    private suspend fun runOnUiThread(action: HttpFragment.() -> Unit) {
        withContext(Dispatchers.Main) {
            fragmentReference.get()?.apply {
                action()
            }
        }
    }

    fun destroy() {
        loadJob?.cancel()
    }

    private fun downloadGif(): ByteBuffer {
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
                return buffer
            }
        }
    }
}
