package pl.droidsonroids.gif.sample

import android.content.res.AssetFileDescriptor
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.support.design.widget.Snackbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import pl.droidsonroids.gif.GifTextureView
import pl.droidsonroids.gif.InputSource
import java.io.BufferedInputStream

class TexturePlaceholderFragment : BaseFragment(), GifTextureView.PlaceholderDrawListener {

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			if (container != null) {
				Snackbar.make(container, R.string.gif_texture_view_stub_api_level, Snackbar.LENGTH_LONG).show()
			}
			return null
		} else {
			val view = GifTextureView(context)
			val assetFileDescriptor = context.assets.openFd("Animated-Flag-Delaware.gif")
			view.setInputSource(InputSource.InputStreamSource(SlowLoadingInputStream(assetFileDescriptor)), this)
			return view
		}
	}

	override fun onDrawPlaceholder(canvas: Canvas) {
		val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher)
		canvas.drawBitmap(bitmap, 0f, 0f, null)
		bitmap.recycle()
	}

	private class SlowLoadingInputStream
	internal constructor(private val mAssetFileDescriptor: AssetFileDescriptor) : BufferedInputStream(mAssetFileDescriptor.createInputStream(), mAssetFileDescriptor.length.toInt()) {
		private var mSleepTimeMillis = 5

		override fun read(buffer: ByteArray): Int {
			SystemClock.sleep(mSleepTimeMillis.toLong())
			return super.read(buffer)
		}

		override fun read(buffer: ByteArray, off: Int, len: Int): Int {
			SystemClock.sleep(mSleepTimeMillis.toLong())
			return super.read(buffer, off, len)
		}

		override fun read(): Int {
			SystemClock.sleep(mSleepTimeMillis.toLong())
			return super.read()
		}

		@Synchronized
		override fun reset() {
			super.reset()
			mSleepTimeMillis = 0
		}

		override fun close() {
			super.close()
			mAssetFileDescriptor.close()
		}
	}
}
