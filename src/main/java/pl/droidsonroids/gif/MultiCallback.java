package pl.droidsonroids.gif;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.Callback;
import android.support.annotation.NonNull;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link Callback} which allows single {@link Drawable} to be associated with multiple {@link View}s.
 * If drawable needs to be redrawn all its views are invalidated.
 *
 * @author koral--
 * @author Doctoror
 */
public class MultiCallback implements Callback {

    private final CopyOnWriteArrayList<ViewWeakReference> mViewList = new CopyOnWriteArrayList<>();

    @Override
    public void invalidateDrawable(final Drawable who) {
        for (int i = 0; i < mViewList.size(); i++) {
            final ViewWeakReference reference = mViewList.get(i);
            final View view = reference.get();
            if (view != null) {
                view.invalidateDrawable(who);
            } else {
                // Always remove null references to reduce Set size
                mViewList.remove(reference);
            }
        }
    }

    @Override
    public void scheduleDrawable(final Drawable who, final Runnable what, final long when) {
        for (int i = 0; i < mViewList.size(); i++) {
            final ViewWeakReference reference = mViewList.get(i);
            final View view = reference.get();
            if (view != null) {
                view.scheduleDrawable(who, what, when);
            } else {
                // Always remove null references to reduce Set size
                mViewList.remove(reference);
            }
        }
    }

    @Override
    public void unscheduleDrawable(final Drawable who, final Runnable what) {
        for (int i = 0; i < mViewList.size(); i++) {
            final ViewWeakReference reference = mViewList.get(i);
            final View view = reference.get();
            if (view != null) {
                view.unscheduleDrawable(who);
            } else {
                // Always remove null references to reduce Set size
                mViewList.remove(reference);
            }
        }
    }

    /**
     * Associates given {@link View}. If view has been already added, nothing happens.
     *
     * @param view View to be associated
     */

    public void addView(@NonNull final View view) {
        for (int i = 0; i < mViewList.size(); i++) {
            final ViewWeakReference reference = mViewList.get(i);
            final View item = reference.get();
            if (item == null) {
                // Always remove null references to reduce Set size
                mViewList.remove(reference);
            }
        }
        mViewList.addIfAbsent(new ViewWeakReference(view));
    }

    /**
     * Disassociates given {@link View}. If view is not associated, nothing happens.
     *
     * @param view View to be disassociated
     */
    public void removeView(final View view) {
        for (int i = 0; i < mViewList.size(); i++) {
            final ViewWeakReference reference = mViewList.get(i);
            final View item = reference.get();
            if (item == null || item == view) {
                // Always remove null references to reduce Set size
                mViewList.remove(reference);
            }
        }
    }

    private static final class ViewWeakReference extends WeakReference<View> {
        ViewWeakReference(View r) {
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

            return get() == ((ViewWeakReference) o).get();
        }

        @Override
        public int hashCode() {
            final View view = get();
            return view != null ? view.hashCode() : 0;
        }
    }
}