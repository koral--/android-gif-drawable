package pl.droidsonroids.gif;

import android.os.SystemClock;

import java.util.concurrent.TimeUnit;

class RenderTask extends SafeRunnable {

    RenderTask(GifDrawable gifDrawable) {
        super(gifDrawable);
    }

    private final Runnable mNotifyListenersTask = new Runnable() {
        @Override
        public void run() {
            for (AnimationListener listener : mGifDrawable.mListeners)
                listener.onAnimationCompleted();
        }
    };

    @Override
    public void doWork() {
        final long renderResult = mGifDrawable.mNativeInfoHandle.renderFrame(mGifDrawable.mBuffer);
        final int invalidationDelay = (int) (renderResult >> 1);
        mGifDrawable.mNextFrameRenderTime = SystemClock.elapsedRealtime() + invalidationDelay;
        if ((int) (renderResult & 1L) == 1 && !mGifDrawable.mListeners.isEmpty()) {
            mGifDrawable.scheduleSelf(mNotifyListenersTask, 0L);
        }
        if (invalidationDelay >= 0) {
            if (mGifDrawable.isVisible()) {
                if (mGifDrawable.mIsRunning && !mGifDrawable.mIsRenderingTriggeredOnDraw) {
                    mGifDrawable.mExecutor.schedule(this, invalidationDelay, TimeUnit.MILLISECONDS);
                }
                if (!mGifDrawable.mUiHandler.hasMessages(0)) {
                    mGifDrawable.mUiHandler.sendEmptyMessageAtTime(0, 0);
                }
            }
        } else {
            mGifDrawable.mIsRunning = false;
        }
    }
}
