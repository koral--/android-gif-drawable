package pl.droidsonroids.gif.sample

import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import androidx.annotation.DrawableRes
import org.xmlpull.v1.XmlPullParser
import pl.droidsonroids.gif.GifDrawable

internal object AnimatedSelectorDrawableGenerator {
	private const val NAMESPACE = "http://schemas.android.com/apk/res/android"

	fun getDrawable(resources: Resources, resourceParser: XmlResourceParser): Drawable {
		val stateListDrawable = StateListDrawable()
		resourceParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)

		var eventType = resourceParser.eventType
		do {
			if (eventType == XmlPullParser.START_TAG && "item" == resourceParser.name) {
				@DrawableRes
				val resourceId = resourceParser.getAttributeResourceValue(NAMESPACE, "drawable", 0)
				val statePressed = resourceParser.getAttributeBooleanValue(NAMESPACE, "state_pressed", false)
				val stateSet = if (statePressed) intArrayOf(android.R.attr.state_pressed) else IntArray(0)
				stateListDrawable.addState(stateSet, GifDrawable.createFromResource(resources, resourceId))
			}
			eventType = resourceParser.next()
		} while (eventType != XmlPullParser.END_DOCUMENT)

		return stateListDrawable
	}
}
