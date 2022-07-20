package pl.droidsonroids.gif.sample

import android.content.res.AssetFileDescriptor
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import pl.droidsonroids.gif.GifTextureView
import pl.droidsonroids.gif.InputSource
import java.io.BufferedInputStream

class TexturePlaceholderFragment : Fragment(), GifTextureView.PlaceholderDrawListener {

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val view = GifTextureView(inflater.context)
		val assetFileDescriptor = inflater.context.assets.openFd("Animated-Flag-Delaware.gif")
		view.setInputSource(InputSource.InputStreamSource(SlowLoadingInputStream(assetFileDescriptor)), this)
		return view
	}

	override fun onDrawPlaceholder(canvas: Canvas) {
		val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher)
		canvas.drawBitmap(bitmap, 0f, 0f, null)
		bitmap.recycle()
	}
}

private class SlowLoadingInputStream
constructor(private val assetFileDescriptor: AssetFileDescriptor) : BufferedInputStream(assetFileDescriptor.createInputStream(), assetFileDescriptor.length.toInt()) {
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
		assetFileDescriptor.close()
	}
}
