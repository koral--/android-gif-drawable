package pl.droidsonroids.gif.sample;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.RawRes;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ToggleButton;

import java.io.IOException;

import pl.droidsonroids.gif.AnimationListener;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

public class AnimationControlFragment extends BaseFragment implements View.OnClickListener, AnimationListener {

	private FrameLimitingGifDrawable gifDrawable;
	private ToggleButton toggleButton;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.animation_control, container, false);
		final GifImageView gifImageView = (GifImageView) view.findViewById(R.id.gifImageView);
		view.findViewById(R.id.btn_reset).setOnClickListener(this);
		toggleButton = (ToggleButton) view.findViewById(R.id.btn_toggle);
		toggleButton.setOnClickListener(this);
		try {
			gifDrawable = new FrameLimitingGifDrawable(getResources(), R.drawable.led7);
		} catch (IOException e) {
			e.printStackTrace();
		}
		gifDrawable.setLastFrameIndex(8);
		gifImageView.setImageDrawable(gifDrawable);

		resetAnimation();
		return view;
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.btn_toggle) {
			toggleAnimation();
		} else {
			resetAnimation();
		}
	}

	private void resetAnimation() {
		gifDrawable.stop();
		gifDrawable.setLoopCount(4);
		gifDrawable.seekToFrameAndGet(5);
		toggleButton.setChecked(false);
	}

	private void toggleAnimation() {
		if (gifDrawable.isPlaying()) {
			gifDrawable.stop();
		} else {
			gifDrawable.start();
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		gifDrawable.addAnimationListener(this);
	}

	@Override
	public void onDestroyView() {
		gifDrawable.removeAnimationListener(this);
		super.onDestroyView();
	}

	@Override
	public void onAnimationCompleted(final int loopNumber) {
		final View view = getView();
		if (view != null) {
			Snackbar.make(view, getString(R.string.animation_loop_completed, loopNumber), Snackbar.LENGTH_SHORT).show();
		}
	}

	static class FrameLimitingGifDrawable extends GifDrawable {

		int lastFrameIndex;

		public FrameLimitingGifDrawable(@NonNull Resources res, @RawRes @DrawableRes int id) throws Resources.NotFoundException, IOException {
			super(res, id);
			lastFrameIndex = getNumberOfFrames() - 1;
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			super.draw(canvas);
			if (getCurrentFrameIndex() >= lastFrameIndex) {
				stop();
			}
		}

		public void setLastFrameIndex(int lastFrameIndex) {
			this.lastFrameIndex = lastFrameIndex;
		}
	}
}
