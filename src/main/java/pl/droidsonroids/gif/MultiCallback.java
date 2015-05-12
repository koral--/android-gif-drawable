package pl.droidsonroids.gif;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.Callback;
import android.support.annotation.NonNull;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link Callback} which allows single {@link Drawable} to be associated with multiple {@link View}s.
 * If drawable needs to be redrawn all its views are invalidated.
 * @author koral--
 * @author Doctoror
 */
public class MultiCallback implements Callback {

    private final Set<ViewWeakReference> mViewSet = new LinkedHashSet<>();

    @Override
    public void invalidateDrawable(final Drawable who) {
        final Iterator<ViewWeakReference> iterator = mViewSet.iterator();
        while (iterator.hasNext()) {
            final View view = iterator.next().get();
            if (view != null) {
                view.invalidate();
            } else {
                // Always remove null references to reduce Set size
                iterator.remove();
            }
        }
    }

    @Override
    public void scheduleDrawable(final Drawable who, final Runnable what, final long when) {
        final Iterator<ViewWeakReference> iterator = mViewSet.iterator();
        while (iterator.hasNext()) {
            final View view = iterator.next().get();
            if (view != null) {
                view.scheduleDrawable(who, what, when);
            } else {
                // Always remove null references to reduce Set size
                iterator.remove();
            }
        }
    }

    @Override
    public void unscheduleDrawable(final Drawable who, final Runnable what) {
        final Iterator<ViewWeakReference> iterator = mViewSet.iterator();
        while (iterator.hasNext()) {
            final View view = iterator.next().get();
            if (view != null) {
                view.unscheduleDrawable(who, what);
            } else {
                // Always remove null references to reduce Set size
                iterator.remove();
            }
        }
    }

    /**
     * Associates given {@link View}. If view has been already added, nothing happens.
     * @param view View to be associated
     */
    public void addView(@NonNull final View view) {
        final Iterator<ViewWeakReference> iterator = mViewSet.iterator();
        while (iterator.hasNext()) {
            final View item = iterator.next().get();
            if (item == null) {
                // Always remove null references to reduce Set size
                iterator.remove();
            } else if (item == view) {
                // Return when found or if loop end reached, add it.
                return;
            }
        }
        mViewSet.add(new ViewWeakReference(view));
    }

    /**
     * Disassociates given {@link View}. If view is not associated, nothing happens.
     * @param view View to be disassociated
     */
    public void removeView(final View view) {
        final Iterator<ViewWeakReference> iterator = mViewSet.iterator();
        while (iterator.hasNext()) {
            final View item = iterator.next().get();
            if (item == null || item == view) {
                // Always remove null references to reduce Set size
                iterator.remove();
            }
        }
    }

    private static final class ViewWeakReference extends WeakReference<View>{
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