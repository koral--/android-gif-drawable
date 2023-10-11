package pl.droidsonroids.gif

import android.os.SystemClock
import java.util.concurrent.TimeUnit

internal class RenderTask(gifDrawable: GifDrawable?) : SafeRunnable(gifDrawable!!) {
    override fun doWork() {
        val invalidationDelay = mGifDrawable.mNativeInfoHandle.renderFrame(mGifDrawable.mBuffer)
        if (invalidationDelay >= 0) {
            mGifDrawable.mNextFrameRenderTime = SystemClock.uptimeMillis() + invalidationDelay
            if (mGifDrawable.isVisible && mGifDrawable.mIsRunning && !mGifDrawable.mIsRenderingTriggeredOnDraw) {
                mGifDrawable.mExecutor.remove(this)
                mGifDrawable.mRenderTaskSchedule =
                    mGifDrawable.mExecutor.schedule(this, invalidationDelay, TimeUnit.MILLISECONDS)
            }
            if (!mGifDrawable.mListeners.isEmpty() && mGifDrawable.currentFrameIndex == mGifDrawable.mNativeInfoHandle.numberOfFrames - 1) {
                mGifDrawable.mInvalidationHandler.sendEmptyMessageAtTime(
                    mGifDrawable.currentLoop,
                    mGifDrawable.mNextFrameRenderTime
                )
            }
        } else {
            mGifDrawable.mNextFrameRenderTime = Long.MIN_VALUE
            mGifDrawable.mIsRunning = false
        }
        if (mGifDrawable.isVisible && !mGifDrawable.mInvalidationHandler.hasMessages(
                InvalidationHandler.MSG_TYPE_INVALIDATION
            )
        ) {
            mGifDrawable.mInvalidationHandler.sendEmptyMessageAtTime(
                InvalidationHandler.MSG_TYPE_INVALIDATION,
                0
            )
        }
    }
}