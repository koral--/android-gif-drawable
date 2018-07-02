package pl.droidsonroids.gif.sample

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.decoder.*
import kotlinx.coroutines.experimental.launch
import pl.droidsonroids.gif.GifDecoder
import pl.droidsonroids.gif.InputSource
import java.lang.ref.WeakReference

class GifDecoderFragment : BaseFragment() {
    var frames = emptyList<Bitmap>()
    var durations = emptyList<Int>()
    private val handler = GifDownloadHandler(this)
    private var currentFrameIndex = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.decoder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        handler.load()
    }

    override fun onResume() {
        super.onResume()
        if (frames.isNotEmpty()) {
            startAnimation()
        }
    }

    fun startAnimation() {
        decoderImageView.setImageBitmap(frames[currentFrameIndex])
        handler.postDelayed(this::advanceAnimation, durations[currentFrameIndex].toLong())
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun advanceAnimation() {
        currentFrameIndex++
        currentFrameIndex %= frames.size
        decoderImageView.setImageBitmap(frames[currentFrameIndex])
        handler.postDelayed(this::advanceAnimation, durations[currentFrameIndex].toLong())
    }
}

class GifDownloadHandler(gifDecoderFragment: GifDecoderFragment) : Handler() {
    private val gifDecoderFragmentReference = WeakReference(gifDecoderFragment)

    fun load() {
        val resources = gifDecoderFragmentReference.get()?.resources
        if (resources != null) {
            launch {
                val frames = mutableListOf<Bitmap>()
                val durations = mutableListOf<Int>()
                val decoder = GifDecoder(InputSource.ResourcesSource(resources, R.drawable.anim_flag_ok_large))
                for (i in 0 until decoder.numberOfFrames) {
                    val frame = Bitmap.createBitmap(decoder.width, decoder.height, Bitmap.Config.ARGB_8888)
                    decoder.seekToFrame(i, frame)
                    frames += frame
                    durations += decoder.getFrameDuration(i)
                }
                decoder.recycle()
                sendMessage(Message.obtain(this@GifDownloadHandler, 0, DecodedGif(frames, durations)))
            }
        }
    }

    override fun handleMessage(msg: Message) {
        gifDecoderFragmentReference.get()?.apply {
            val decodedGif = msg.obj as DecodedGif
            frames = decodedGif.frames
            durations = decodedGif.durations
            if (isAdded) {
                startAnimation()
                decoderLoadingTextView.visibility = View.GONE
            }
        }
    }
}

data class DecodedGif(val frames: List<Bitmap>, val durations: List<Int>)