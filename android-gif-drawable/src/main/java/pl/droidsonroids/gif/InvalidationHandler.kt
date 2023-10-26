package pl.droidsonroids.gif

import android.os.Handler
import android.os.Looper
import android.os.Message
import java.lang.ref.WeakReference

class InvalidationHandler(gifDrawable: GifDrawable) : Handler(Looper.getMainLooper()) {
    private val mDrawableRef: WeakReference<GifDrawable>

    init {
        mDrawableRef = WeakReference(gifDrawable)
    }

    override fun handleMessage(msg: Message) {
        val gifDrawable = mDrawableRef.get() ?: return
        if (msg.what == MSG_TYPE_INVALIDATION) {
            gifDrawable.invalidateSelf()
        } else {
            for (listener in gifDrawable.mListeners) {
                listener.onAnimationCompleted(msg.what)
            }
        }
    }

    companion object {
        const val MSG_TYPE_INVALIDATION = -1
    }
}