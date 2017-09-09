package pl.droidsonroids.gif.sample

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.animated_selector.view.*
import pl.droidsonroids.gif.GifDrawable

class AnimatedSelectorFragment : BaseFragment() {

	@Suppress("DEPRECATION")
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val rootView = inflater.inflate(R.layout.animated_selector, container, false)
		rootView.buttonKotlin.setBackgroundDrawable(kotlinAnimatedBackground)

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
			rootView.buttonXML.setBackgroundDrawable(xmlAnimatedBackground)
		}

		return rootView
	}

	private val xmlAnimatedBackground: Drawable
		@SuppressLint("ResourceType")
		get() {
			val resourceParser = resources.getXml(R.drawable.selector)
			return AnimatedSelectorDrawableGenerator.getDrawable(resources, resourceParser)
		}

	private val kotlinAnimatedBackground: Drawable
		get() {
			val stateListDrawable = StateListDrawable()
			stateListDrawable.addState(intArrayOf(android.R.attr.state_pressed), GifDrawable.createFromResource(resources, R.drawable.anim_flag_chile))
			stateListDrawable.addState(IntArray(0), GifDrawable.createFromResource(resources, R.drawable.anim_flag_england))
			return stateListDrawable
		}

}
