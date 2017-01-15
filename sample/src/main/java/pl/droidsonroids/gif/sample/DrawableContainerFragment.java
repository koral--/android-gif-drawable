package pl.droidsonroids.gif.sample;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import pl.droidsonroids.gif.AnimationListener;
import pl.droidsonroids.gif.GifDrawable;

@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
public class DrawableContainerFragment extends BaseFragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final ImageView imageView = new ImageView(getActivity()) {
			@Override
			protected boolean verifyDrawable(@NonNull Drawable dr) {
				super.verifyDrawable(dr);
				return true;
			}
		};
		final GifDrawable firstDrawable = GifDrawable.createFromResource(getResources(), R.drawable.anim_flag_chile);
		final GifDrawable secondDrawable = GifDrawable.createFromResource(getResources(), R.drawable.anim_flag_iceland);
		final ChainedGifDrawable drawable = new ChainedGifDrawable(firstDrawable, secondDrawable);
		imageView.setImageDrawable(drawable);
		drawable.setAnimationEnabled();
		return imageView;
	}

	public static class ChainedGifDrawable extends Drawable implements AnimationListener {
		private final GifDrawable[] mDrawables;
		private int mCurrentIndex;

		public ChainedGifDrawable(GifDrawable... drawables) {
			mDrawables = drawables;
			for (GifDrawable drawable : drawables) {
				drawable.addAnimationListener(this);
			}
		}

		@Override
		public void onAnimationCompleted(int loopNumber) {
			mCurrentIndex++;
			mCurrentIndex %= mDrawables.length;
			mDrawables[mCurrentIndex].setCallback(getCallback());
			mDrawables[mCurrentIndex].invalidateSelf();
		}

		@Override
		public void invalidateSelf() {
			mDrawables[mCurrentIndex].invalidateSelf();
		}

		@Override
		public void scheduleSelf(@NonNull Runnable what, long when) {
			mDrawables[mCurrentIndex].scheduleSelf(what, when);
		}

		@Override
		public void unscheduleSelf(@NonNull Runnable what) {
			mDrawables[mCurrentIndex].unscheduleSelf(what);
		}

		@Override
		public int getIntrinsicWidth() {
			return mDrawables[mCurrentIndex].getIntrinsicWidth();
		}

		@Override
		public int getIntrinsicHeight() {
			return mDrawables[mCurrentIndex].getIntrinsicHeight();
		}

		@Override
		protected void onBoundsChange(Rect bounds) {
			for (GifDrawable drawable : mDrawables) {
				drawable.setBounds(bounds);
			}
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			mDrawables[mCurrentIndex].draw(canvas);
		}

		@Override
		public void setAlpha(@IntRange(from = 0, to = 255) int alpha) {
			for (GifDrawable drawable : mDrawables) {
				drawable.setAlpha(alpha);
			}
		}

		@Override
		public void setColorFilter(@Nullable ColorFilter colorFilter) {
			for (GifDrawable drawable : mDrawables) {
				drawable.setColorFilter(colorFilter);
			}
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}

		public void setAnimationEnabled() {
			mDrawables[0].setCallback(getCallback());
		}
	}
}