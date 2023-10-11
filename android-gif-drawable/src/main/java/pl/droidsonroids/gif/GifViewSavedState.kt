package pl.droidsonroids.gif

import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.view.View

internal class GifViewSavedState : View.BaseSavedState {
    val mStates: Array<LongArray?>

    constructor(superState: Parcelable?, vararg drawables: Drawable?) : super(superState) {
        mStates = arrayOfNulls(drawables.size)
        for (i in drawables.indices) {
            val drawable = drawables[i]
            if (drawable is GifDrawable) {
                mStates[i] = drawable.mNativeInfoHandle.savedState
            } else {
                mStates[i] = null
            }
        }
    }

    private constructor(`in`: Parcel) : super(`in`) {
        mStates = arrayOfNulls(`in`.readInt())
        for (i in mStates.indices) mStates[i] = `in`.createLongArray()
    }

    constructor(superState: Parcelable?, savedState: LongArray?) : super(superState) {
        mStates = arrayOfNulls(1)
        mStates[0] = savedState
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeInt(mStates.size)
        for (mState in mStates) dest.writeLongArray(mState)
    }

    fun restoreState(drawable: Drawable?, i: Int) {
        if (mStates[i] != null && drawable is GifDrawable) {
            drawable.startAnimation(
                drawable.mNativeInfoHandle.restoreSavedState(
                    mStates[i]!!,
                    drawable.mBuffer
                ).toLong()
            )
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Creator<GifViewSavedState> {
        override fun createFromParcel(parcel: Parcel): GifViewSavedState {
            return GifViewSavedState(parcel)
        }

        override fun newArray(size: Int): Array<GifViewSavedState?> {
            return arrayOfNulls(size)
        }
    }
}