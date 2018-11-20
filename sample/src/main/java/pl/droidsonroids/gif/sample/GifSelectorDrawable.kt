package pl.droidsonroids.gif.sample

import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.StateListDrawable
import androidx.annotation.DrawableRes
import android.util.AttributeSet
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import pl.droidsonroids.gif.GifDrawable
import java.io.IOException

private const val NAMESPACE = "http://schemas.android.com/apk/res/android"

class GifSelectorDrawable : StateListDrawable() {

	@Throws(XmlPullParserException::class, IOException::class)
	override fun inflate(r: Resources, parser: XmlPullParser, attrs: AttributeSet, theme: Resources.Theme?) {
		val resourceParser = parser as XmlResourceParser
		resourceParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)

		var eventType = resourceParser.eventType
		do {
			if (eventType == XmlPullParser.START_TAG && "item" == resourceParser.name) {
				@DrawableRes
				val resourceId = resourceParser.getAttributeResourceValue(NAMESPACE, "drawable", 0)
				val statePressed = resourceParser.getAttributeBooleanValue(NAMESPACE, "state_pressed", false)
				val stateSet = if (statePressed) intArrayOf(android.R.attr.state_pressed) else IntArray(0)
				addState(stateSet, GifDrawable.createFromResource(r, resourceId))
			}
			eventType = resourceParser.next()
		} while (eventType != XmlPullParser.END_DOCUMENT)
	}
}
