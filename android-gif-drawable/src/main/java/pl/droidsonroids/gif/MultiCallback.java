package pl.droidsonroids.gif;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.Callback;
import android.view.View;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;

/**
 * {@link Callback} which allows single {@link Drawable} to be associated with multiple callbacks,
 * eg. with multiple {@link View}s.
 * If drawable needs to be redrawn all currently associated callbacks are invoked.
 * If Callback is a View it is then invalidated.
 *
 * @author koral--
 * @author Doctoror
 */
public class MultiCallback implements Callback {

	private final CopyOnWriteArrayList<CallbackWeakReference> mCallbacks = new CopyOnWriteArrayList<>();
	private final boolean mUseViewInvalidate;

	/**
	 * Equivalent to {@link #MultiCallback(boolean)} with <code>false</code> value.
	 */
	public MultiCallback() {
		this(false);
	}

	/**
	 * Set <code>useViewInvalidate</code> to <code>true</code> if displayed {@link Drawable} is not supported by
	 * {@link Drawable.Callback#invalidateDrawable(Drawable)} of the target. For example if it is located inside {@link android.text.style.ImageSpan}
	 * displayed in {@link TextView}.
	 *
	 * @param useViewInvalidate whether {@link View#invalidate()} should be used instead of {@link Drawable.Callback#invalidateDrawable(Drawable)}
	 */
	public MultiCallback(final boolean useViewInvalidate) {
		mUseViewInvalidate = useViewInvalidate;
	}

	@Override
	public void invalidateDrawable(@NonNull final Drawable who) {
		for (int i = 0; i < mCallbacks.size(); i++) {
			final CallbackWeakReference reference = mCallbacks.get(i);
			final Callback callback = reference.get();
			if (callback != null) {
				if (mUseViewInvalidate && callback instanceof View) {
					((View) callback).invalidate();
				} else {
					callback.invalidateDrawable(who);
				}
			} else {
				// Always remove null references to reduce list size
				mCallbacks.remove(reference);
			}
		}
	}

	@Override
	public void scheduleDrawable(@NonNull final Drawable who, @NonNull final Runnable what, final long when) {
		for (int i = 0; i < mCallbacks.size(); i++) {
			final CallbackWeakReference reference = mCallbacks.get(i);
			final Callback callback = reference.get();
			if (callback != null) {
				callback.scheduleDrawable(who, what, when);
			} else {
				// Always remove null references to reduce Set size
				mCallbacks.remove(reference);
			}
		}
	}

	@Override
	public void unscheduleDrawable(@NonNull final Drawable who, @NonNull final Runnable what) {
		for (int i = 0; i < mCallbacks.size(); i++) {
			final CallbackWeakReference reference = mCallbacks.get(i);
			final Callback callback = reference.get();
			if (callback != null) {
				callback.unscheduleDrawable(who, what);
			} else {
				// Always remove null references to reduce list size
				mCallbacks.remove(reference);
			}
		}
	}

	/**
	 * Associates given {@link Callback}. If callback has been already added, nothing happens.
	 *
	 * @param callback Callback to be associated
	 */
	public void addView(final Callback callback) {
		for (int i = 0; i < mCallbacks.size(); i++) {
			final CallbackWeakReference reference = mCallbacks.get(i);
			final Callback item = reference.get();
			if (item == null) {
				// Always remove null references to reduce list size
				mCallbacks.remove(reference);
			}
		}
		mCallbacks.addIfAbsent(new CallbackWeakReference(callback));
	}

	/**
	 * Disassociates given {@link Callback}. If callback is not associated, nothing happens.
	 *
	 * @param callback Callback to be disassociated
	 */
	public void removeView(final Callback callback) {
		for (int i = 0; i < mCallbacks.size(); i++) {
			final CallbackWeakReference reference = mCallbacks.get(i);
			final Callback item = reference.get();
			if (item == null || item == callback) {
				// Always remove null references to reduce list size
				mCallbacks.remove(reference);
			}
		}
	}

	static final class CallbackWeakReference extends WeakReference<Callback> {
		CallbackWeakReference(final Callback r) {
			super(r);
		}

		@Override
		public boolean equals(final Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}

			return get() == ((CallbackWeakReference) o).get();
		}

		@Override
		public int hashCode() {
			final Callback callback = get();
			return callback != null ? callback.hashCode() : 0;
		}
	}
}