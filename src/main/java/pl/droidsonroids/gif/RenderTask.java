package pl.droidsonroids.gif;

import java.util.concurrent.TimeUnit;

class RenderTask extends SafeRunnable {

    RenderTask(GifDrawable gifDrawable) {
        super(gifDrawable);
    }

    final Runnable mNotifyListenersTask = new Runnable() {
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
        mGifDrawable.nextFrameRenderTime = System.currentTimeMillis() + invalidationDelay;
        if ((int) (renderResult & 1L) == 1 && !mGifDrawable.mListeners.isEmpty()) {
            mGifDrawable.scheduleSelf(mNotifyListenersTask, 0L);
        }
        if (invalidationDelay >= 0) {
            if (mGifDrawable.isVisible() && mGifDrawable.mIsRunning) {
                mGifDrawable.unscheduleSelf(mGifDrawable.mInvalidateTask);
                mGifDrawable.scheduleSelf(mGifDrawable.mInvalidateTask, 0L);
            }
        }
    }
}
