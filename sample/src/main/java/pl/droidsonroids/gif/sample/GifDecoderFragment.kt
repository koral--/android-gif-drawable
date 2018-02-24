package pl.droidsonroids.gif.sample

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.decoder.*
import pl.droidsonroids.gif.GifDecoder
import pl.droidsonroids.gif.InputSource

class GifDecoderFragment : BaseFragment() {
    private val frames = mutableListOf<Bitmap>()
    private val durations = mutableListOf<Int>()
    private val handler = Handler()
    private var currentFrameIndex = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.decoder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Thread {
            val decoder = GifDecoder(InputSource.ResourcesSource(resources, R.drawable.anim_flag_ok_large))
            for (i in 0 until decoder.numberOfFrames) {
                val frame = Bitmap.createBitmap(decoder.width, decoder.height, Bitmap.Config.ARGB_8888)
                decoder.seekToFrame(i, frame)
                frames += frame
                durations += decoder.getFrameDuration(i)
            }
            decoder.recycle()
            handler.post {
                startAnimation()
                decoderLoadingTextView.visibility = View.GONE
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        if (frames.isNotEmpty()) {
            startAnimation()
        }
    }

    private fun startAnimation() {
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