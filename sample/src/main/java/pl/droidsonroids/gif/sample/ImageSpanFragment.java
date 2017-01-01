package pl.droidsonroids.gif.sample;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import pl.droidsonroids.gif.GifDrawable;

public class ImageSpanFragment extends BaseFragment implements Drawable.Callback {

	private EditText mEditText;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mEditText = new EditText(getActivity());
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			mEditText.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
		}
		final GifDrawable gifDrawable = GifDrawable.createFromResource(getResources(), R.drawable.anim_flag_england);
		final SpannableStringBuilder ssb = new SpannableStringBuilder("test\ufffc");
		assert gifDrawable != null;
		gifDrawable.setBounds(0, 0, gifDrawable.getIntrinsicWidth(), gifDrawable.getIntrinsicHeight());
		gifDrawable.setCallback(this);
		ssb.setSpan(new ImageSpan(gifDrawable), ssb.length() - 1, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		mEditText.getText().insert(0, ssb);
		return mEditText;
	}

	@Override
	public void invalidateDrawable(@NonNull Drawable who) {
		mEditText.invalidate();
	}

	@Override
	public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
		mEditText.postDelayed(what, when);
	}

	@Override
	public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
		mEditText.removeCallbacks(what);
	}
}