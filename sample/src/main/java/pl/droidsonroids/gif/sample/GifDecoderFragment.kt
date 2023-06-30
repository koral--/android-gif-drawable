package pl.droidsonroids.gif.sample

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.*
import pl.droidsonroids.gif.GifDecoder
import pl.droidsonroids.gif.InputSource
import kotlin.coroutines.CoroutineContext

class GifDecoderFragment : Fragment(), CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private var frames = emptyList<Bitmap>()
    private var durations = emptyList<Int>()

    private var currentFrameIndex = 0
    private var decoderImageView: ImageView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.decoder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        decoderImageView = view.findViewById(R.id.decoderImageView)
        launch(Dispatchers.IO) {
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
            withContext(Dispatchers.Main){
                this@GifDecoderFragment.frames = frames
                this@GifDecoderFragment.durations = durations
                if (isAdded) {
                    startAnimation(decoderImageView!!)
                    view.findViewById<View>(R.id.decoderLoadingTextView).visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        decoderImageView = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        if (frames.isNotEmpty() && decoderImageView != null) {
            startAnimation(decoderImageView!!)
        }
    }

    private fun startAnimation(decoderImageView: ImageView) {
        decoderImageView.setImageBitmap(frames[currentFrameIndex])
        launch {
            delay(durations[currentFrameIndex].toLong())
            advanceAnimation(decoderImageView)
        }
    }

    override fun onPause() {
        job.cancelChildren()
        super.onPause()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun advanceAnimation(decoderImageView: ImageView){
        currentFrameIndex++
        currentFrameIndex %= frames.size
        decoderImageView.setImageBitmap(frames[currentFrameIndex])
        launch {
            delay(durations[currentFrameIndex].toLong())
            advanceAnimation(decoderImageView)
        }
    }
}
