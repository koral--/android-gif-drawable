package pl.droidsonroids.gif.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import pl.droidsonroids.gif.GifTextureView
import pl.droidsonroids.gif.InputSource
import java.nio.ByteBuffer

class HttpFragment : Fragment(), View.OnClickListener {

    private var gifTextureView: GifTextureView? = null
    private val gifDownloader = GifDownloader(this)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        gifTextureView = inflater.inflate(R.layout.http, container, false) as GifTextureView
        gifDownloader.load()
        return gifTextureView
    }

    override fun onDestroy() {
        gifDownloader.destroy()
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

    override fun onClick(v: View) = gifDownloader.load()
}
