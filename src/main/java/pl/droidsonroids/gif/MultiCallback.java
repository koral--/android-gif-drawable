package pl.droidsonroids.gif;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.Callback;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

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

	@Override
	public void invalidateDrawable(final Drawable who) {
		for (int i = 0; i < mCallbacks.size(); i++) {
			final CallbackWeakReference reference = mCallbacks.get(i);
			final Callback callback = reference.get();
			if (callback != null) {
				callback.invalidateDrawable(who);
			} else {
				// Always remove null references to reduce list size
				mCallbacks.remove(reference);
			}
		}
	}

	@Override
	public void scheduleDrawable(final Drawable who, final Runnable what, final long when) {
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
	public void unscheduleDrawable(final Drawable who, final Runnable what) {
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

	private static final class CallbackWeakReference extends WeakReference<Callback> {
		CallbackWeakReference(Callback r) {
			super(r);
		}

		@Override
		public boolean equals(Object o) {
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