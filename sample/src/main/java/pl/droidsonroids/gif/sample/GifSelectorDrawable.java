package pl.droidsonroids.gif.sample;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.StateListDrawable;
import android.support.annotation.DrawableRes;
import android.util.AttributeSet;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import pl.droidsonroids.gif.GifDrawable;

public class GifSelectorDrawable extends StateListDrawable {

	private static final String NAMESPACE = "http://schemas.android.com/apk/res/android";

	@Override
	public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
		final XmlResourceParser resourceParser = (XmlResourceParser) parser;
		resourceParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);

		int eventType = resourceParser.getEventType();
		do {
			if (eventType == XmlPullParser.START_TAG && "item".equals(resourceParser.getName())) {
				@DrawableRes
				final int resourceId = resourceParser.getAttributeResourceValue(NAMESPACE, "drawable", 0);
				final boolean state_pressed = resourceParser.getAttributeBooleanValue(NAMESPACE, "state_pressed", false);
				final int[] stateSet = state_pressed ? new int[]{android.R.attr.state_pressed} : new int[0];
				addState(stateSet, GifDrawable.createFromResource(r, resourceId));
			}
			eventType = resourceParser.next();
		} while (eventType != XmlPullParser.END_DOCUMENT);
	}
}
