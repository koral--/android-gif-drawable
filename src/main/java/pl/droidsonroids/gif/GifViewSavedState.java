package pl.droidsonroids.gif;

import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.view.View;

class GifViewSavedState extends View.BaseSavedState {

    final long[][] mStates;

    GifViewSavedState(Parcelable superState, Drawable... drawables) {
        super(superState);
        mStates = new long[drawables.length][];
        for (int i = 0; i < drawables.length; i++) {
            Drawable drawable = drawables[i];
            if (drawable instanceof GifDrawable) {
                mStates[i] = ((GifDrawable) drawable).mNativeInfoHandle.getSavedState();
            } else {
                mStates[i] = null;
            }
        }
    }

    private GifViewSavedState(Parcel in) {
        super(in);
        mStates = new long[in.readInt()][];
        for (int i=0;i<mStates.length;i++)
            mStates[i]=in.createLongArray();
    }

    public GifViewSavedState(Parcelable superState, long[] savedState) {
        super(superState);
        mStates = new long[1][];
        mStates[0] = savedState;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mStates.length);
        for (long[] mState : mStates)
            dest.writeLongArray(mState);
    }

    public static final Creator<GifViewSavedState> CREATOR = new Creator<GifViewSavedState>() {

        public GifViewSavedState createFromParcel(Parcel in) {
            return new GifViewSavedState(in);
        }

        public GifViewSavedState[] newArray(int size) {
            return new GifViewSavedState[size];
        }
    };

    void restoreState(Drawable drawable, int i) {
        if (mStates[i] != null && drawable instanceof GifDrawable) {
            final GifDrawable gifDrawable = (GifDrawable) drawable;
            gifDrawable.startAnimation(gifDrawable.mNativeInfoHandle.restoreSavedState(mStates[i], gifDrawable.mBuffer));
        }
    }
}
