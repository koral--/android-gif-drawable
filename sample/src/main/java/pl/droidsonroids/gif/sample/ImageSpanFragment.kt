package pl.droidsonroids.gif.sample

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import pl.droidsonroids.gif.GifDrawable

class ImageSpanFragment : Fragment(), Drawable.Callback {

    private var imageSpanTextView: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.image_span, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        imageSpanTextView = view.findViewById(R.id.imageSpanTextView)
        imageSpanTextView?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        val gifDrawable = GifDrawable.createFromResource(resources, R.drawable.anim_flag_england)
        val stringBuilder = SpannableStringBuilder("test\ufffc")
        gifDrawable!!.setBounds(0, 0, gifDrawable.intrinsicWidth, gifDrawable.intrinsicHeight)
        gifDrawable.callback = this
        stringBuilder.setSpan(ImageSpan(gifDrawable), stringBuilder.length - 1, stringBuilder.length, 0)
        imageSpanTextView?.text = stringBuilder
    }

    override fun onDestroyView() {
        imageSpanTextView = null
        super.onDestroyView()
    }

    override fun invalidateDrawable(who: Drawable) {
        imageSpanTextView?.invalidate()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        imageSpanTextView?.postDelayed(what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        imageSpanTextView?.removeCallbacks(what)
    }
}
