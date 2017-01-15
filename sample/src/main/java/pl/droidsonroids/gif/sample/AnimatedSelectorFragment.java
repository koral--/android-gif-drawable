package pl.droidsonroids.gif.sample;

import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import pl.droidsonroids.gif.GifDrawable;

public class AnimatedSelectorFragment extends BaseFragment {

	@SuppressWarnings("deprecation")
	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		final View rootView = inflater.inflate(R.layout.animated_selector, container, false);
		final View buttonJava = rootView.findViewById(R.id.button_java);
		buttonJava.setBackgroundDrawable(getJavaAnimatedBackground());

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
			final View buttonXML = rootView.findViewById(R.id.button_xml);
			try {
				buttonXML.setBackgroundDrawable(getXMLAnimatedBackground());
			} catch (XmlPullParserException | IOException e) {
				throw new IllegalStateException(e);
			}
		}

		return rootView;
	}

	@SuppressWarnings("ResourceType")
	private Drawable getXMLAnimatedBackground() throws XmlPullParserException, IOException {
		final XmlResourceParser resourceParser = getResources().getXml(R.drawable.selector);
		return AnimatedSelectorDrawableGenerator.getDrawable(getResources(), resourceParser);
	}

	private Drawable getJavaAnimatedBackground() {
		final StateListDrawable stateListDrawable = new StateListDrawable();
		stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, GifDrawable.createFromResource(getResources(), R.drawable.anim_flag_chile));
		stateListDrawable.addState(new int[0], GifDrawable.createFromResource(getResources(), R.drawable.anim_flag_england));
		return stateListDrawable;
	}

}
