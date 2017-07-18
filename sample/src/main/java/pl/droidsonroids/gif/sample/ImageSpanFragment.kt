package pl.droidsonroids.gif.sample

import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import pl.droidsonroids.gif.GifDrawable

class ImageSpanFragment : BaseFragment(), Drawable.Callback {

	private lateinit var textView: TextView

	override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		textView = TextView(activity)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			textView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
			textView.setTextIsSelectable(true)
		}
		val gifDrawable = GifDrawable.createFromResource(resources, R.drawable.anim_flag_england)
		val ssb = SpannableStringBuilder("test\ufffc")
		gifDrawable!!.setBounds(0, 0, gifDrawable.intrinsicWidth, gifDrawable.intrinsicHeight)
		gifDrawable.callback = this
		ssb.setSpan(ImageSpan(gifDrawable), ssb.length - 1, ssb.length, 0)
		textView.text = ssb
		return textView
	}

	override fun invalidateDrawable(who: Drawable) =
			textView.invalidate()

	override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
		textView.postDelayed(what, `when`)
	}

	override fun unscheduleDrawable(who: Drawable, what: Runnable) {
		textView.removeCallbacks(what)
	}
}