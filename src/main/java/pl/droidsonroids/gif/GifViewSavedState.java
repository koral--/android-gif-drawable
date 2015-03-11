package pl.droidsonroids.gif;

import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;

class GifViewSavedState extends View.BaseSavedState {

    final int[] mPositions;

    GifViewSavedState(Parcelable superState, Drawable... drawables) {
        super(superState);
        mPositions = new int[drawables.length];
        for (int i = 0; i < drawables.length; i++) {
            Drawable drawable = drawables[i];
            if (drawable instanceof GifDrawable)
                mPositions[i] = ((GifDrawable) drawable).getCurrentPosition();
            else
                mPositions[i] = -1;
        }
    }

    GifViewSavedState(Parcelable superState, GifInfoHandle gifInfoHandle, Drawable background)
    {
        super(superState);
        mPositions = new int[2];
        mPositions[0] = gifInfoHandle!=null?gifInfoHandle.getCurrentPosition():-1;
        if (background instanceof GifDrawable)
            mPositions[1] = ((GifDrawable) background).getCurrentPosition();
        else
            mPositions[1] = -1;
    }

    GifViewSavedState(Parcel in) {
        super(in);
        mPositions = new int[in.readInt()];
        in.readIntArray(mPositions);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mPositions.length);
        dest.writeIntArray(mPositions);
    }

    public static final Creator<GifViewSavedState> CREATOR = new Creator<GifViewSavedState>() {

        public GifViewSavedState createFromParcel(Parcel in) {
            return new GifViewSavedState(in);
        }

        public GifViewSavedState[] newArray(int size) {
            return new GifViewSavedState[size];
        }
    };

    void setPostion(Drawable drawable, int i) {
        if (drawable instanceof GifDrawable && mPositions[i] >= 0)
            ((GifDrawable) drawable).seekTo(mPositions[i]);
    }
}
