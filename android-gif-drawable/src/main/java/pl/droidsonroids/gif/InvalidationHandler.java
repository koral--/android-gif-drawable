package pl.droidsonroids.gif;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;

class InvalidationHandler extends Handler {

	static final int MSG_TYPE_INVALIDATION = -1;

	private final WeakReference<GifDrawable> mDrawableRef;

	InvalidationHandler(final GifDrawable gifDrawable) {
		super(Looper.getMainLooper());
		mDrawableRef = new WeakReference<>(gifDrawable);
	}

	@Override
	public void handleMessage(@NonNull final Message msg) {
		final GifDrawable gifDrawable = mDrawableRef.get();
		if (gifDrawable == null) {
			return;
		}
		if (msg.what == MSG_TYPE_INVALIDATION) {
			gifDrawable.invalidateSelf();
		} else {
			for (AnimationListener listener : gifDrawable.mListeners) {
				listener.onAnimationCompleted(msg.what);
			}
		}
	}
}
