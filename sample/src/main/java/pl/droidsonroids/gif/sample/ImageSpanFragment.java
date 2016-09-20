package pl.droidsonroids.gif.sample;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import pl.droidsonroids.gif.GifDrawable;

public class ImageSpanFragment extends BaseFragment implements Drawable.Callback {

	private TextView mTextView;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mTextView = new TextView(getActivity());
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			mTextView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
			mTextView.setTextIsSelectable(true);
		}
		final GifDrawable gifDrawable = GifDrawable.createFromResource(getResources(), R.drawable.anim_flag_england);
		final SpannableStringBuilder ssb = new SpannableStringBuilder("test\ufffc");
		assert gifDrawable != null;
		gifDrawable.setBounds(0, 0, gifDrawable.getIntrinsicWidth(), gifDrawable.getIntrinsicHeight());
		gifDrawable.setCallback(this);
		ssb.setSpan(new ImageSpan(gifDrawable), ssb.length() - 1, ssb.length(), 0);
		mTextView.setText(ssb);
		return mTextView;
	}

	@Override
	public void invalidateDrawable(Drawable who) {
		mTextView.invalidate();
	}

	@Override
	public void scheduleDrawable(Drawable who, Runnable what, long when) {
		mTextView.postDelayed(what, when);
	}

	@Override
	public void unscheduleDrawable(Drawable who, Runnable what) {
		mTextView.removeCallbacks(what);
	}
}