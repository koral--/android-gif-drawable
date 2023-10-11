package pl.droidsonroids.gif

import android.graphics.drawable.Drawable
import android.view.View
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [Callback] which allows single [Drawable] to be associated with multiple callbacks,
 * eg. with multiple [View]s.
 * If drawable needs to be redrawn all currently associated callbacks are invoked.
 * If Callback is a View it is then invalidated.
 *
 * @author koral--
 * @author Doctoror
 */
class MultiCallback
/**
 * Equivalent to [.MultiCallback] with `false` value.
 */ @JvmOverloads constructor(private val mUseViewInvalidate: Boolean = false) : Drawable.Callback {
    private val mCallbacks = CopyOnWriteArrayList<CallbackWeakReference>()

    /**
     * Set `useViewInvalidate` to `true` if displayed [Drawable] is not supported by
     * [Drawable.Callback.invalidateDrawable] of the target. For example if it is located inside [android.text.style.ImageSpan]
     * displayed in [TextView].
     *
     * @param mUseViewInvalidate whether [View.invalidate] should be used instead of [Drawable.Callback.invalidateDrawable]
     */
    override fun invalidateDrawable(who: Drawable) {
        for (i in mCallbacks.indices) {
            val reference = mCallbacks[i]
            val callback = reference.get()
            if (callback != null) {
                if (mUseViewInvalidate && callback is View) {
                    callback.invalidate()
                } else {
                    callback.invalidateDrawable(who)
                }
            } else {
                // Always remove null references to reduce list size
                mCallbacks.remove(reference)
            }
        }
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        for (i in mCallbacks.indices) {
            val reference = mCallbacks[i]
            val callback = reference.get()
            callback?.scheduleDrawable(who, what, `when`)
                ?: // Always remove null references to reduce Set size
                mCallbacks.remove(reference)
        }
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        for (i in mCallbacks.indices) {
            val reference = mCallbacks[i]
            val callback = reference.get()
            callback?.unscheduleDrawable(who, what)
                ?: // Always remove null references to reduce list size
                mCallbacks.remove(reference)
        }
    }

    /**
     * Associates given [Callback]. If callback has been already added, nothing happens.
     *
     * @param callback Callback to be associated
     */
    fun addView(callback: Drawable.Callback?) {
        for (i in mCallbacks.indices) {
            val reference = mCallbacks[i]
            val item = reference.get()
            if (item == null) {
                // Always remove null references to reduce list size
                mCallbacks.remove(reference)
            }
        }
        mCallbacks.addIfAbsent(CallbackWeakReference(callback))
    }

    /**
     * Disassociates given [Callback]. If callback is not associated, nothing happens.
     *
     * @param callback Callback to be disassociated
     */
    fun removeView(callback: Drawable.Callback) {
        for (i in mCallbacks.indices) {
            val reference = mCallbacks[i]
            val item = reference.get()
            if (item == null || item === callback) {
                // Always remove null references to reduce list size
                mCallbacks.remove(reference)
            }
        }
    }

    internal class CallbackWeakReference(r: Drawable.Callback?) :
        WeakReference<Drawable.Callback?>(r) {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            return if (other == null || javaClass != other.javaClass) {
                false
            } else get() === (other as CallbackWeakReference).get()
        }

        override fun hashCode(): Int {
            val callback = get()
            return callback?.hashCode() ?: 0
        }
    }
}