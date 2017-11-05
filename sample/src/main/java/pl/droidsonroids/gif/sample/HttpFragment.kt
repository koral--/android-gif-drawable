package pl.droidsonroids.gif.sample

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import pl.droidsonroids.gif.GifTextureView
import pl.droidsonroids.gif.InputSource
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class HttpFragment : BaseFragment(), View.OnClickListener {

	private var gifTextureView: GifTextureView? = null
	private val executorService = Executors.newSingleThreadExecutor()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		gifTextureView = inflater.inflate(R.layout.http, container, false) as GifTextureView
		downloadGif()
		return gifTextureView
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		if (!gifTextureView!!.isHardwareAccelerated) {
			Snackbar.make(gifTextureView!!, R.string.gif_texture_view_stub_acceleration, Snackbar.LENGTH_LONG).show()
		}
	}

	override fun onDestroy() {
		executorService.shutdownNow()
		super.onDestroy()
	}

	internal fun onGifDownloaded(buffer: ByteBuffer) =
			gifTextureView!!.setInputSource(InputSource.DirectByteBufferSource(buffer))

	internal fun onDownloadFailed(e: Exception) {
		gifTextureView!!.setOnClickListener(this@HttpFragment)
		if (isDetached) {
			return
		}
		val message = getString(R.string.gif_texture_view_loading_failed, e.message)
		Snackbar.make(gifTextureView!!, message, Snackbar.LENGTH_LONG).show()

	}

	private fun downloadGif() {
		executorService.submit(GifLoadTask(this))
	}

	override fun onClick(v: View) = downloadGif()
}
