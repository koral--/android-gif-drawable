package pl.droidsonroids.gif;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.lang.ref.WeakReference;

class InvalidationHandler extends Handler {
    private final WeakReference<GifDrawable> mDrawableRef;

    public InvalidationHandler(GifDrawable gifDrawable) {
        super(Looper.getMainLooper());
        mDrawableRef = new WeakReference<>(gifDrawable);
    }

    @Override
    public void handleMessage(Message msg) {
        final GifDrawable gifDrawable = mDrawableRef.get();
        if (gifDrawable != null) {
            gifDrawable.invalidateSelf();
        }
    }
}
