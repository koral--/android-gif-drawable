package pl.droidsonroids.gif;

import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.view.View;

class GifViewSavedState extends View.BaseSavedState {

    final int[] mPositions;

    GifViewSavedState(Parcelable superState, Drawable... drawables) {
        super(superState);
        mPositions = new int[drawables.length];
        for (int i = 0; i < drawables.length; i++) {
            Drawable drawable = drawables[i];
            if (drawable instanceof GifDrawable) {
                mPositions[i] = ((GifDrawable) drawable).getCurrentPosition();
            } else {
                mPositions[i] = -1;
            }
        }
    }

    private GifViewSavedState(Parcel in) {
        super(in);
        mPositions = new int[in.readInt()];
        in.readIntArray(mPositions);
    }

    GifViewSavedState(Parcelable superState, int savedPosition) {
        super(superState);
        mPositions = new int[1];
        mPositions[0] = savedPosition;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
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

    void setPosition(Drawable drawable, int i) {
        if (drawable instanceof GifDrawable && mPositions[i] >= 0) {
            ((GifDrawable) drawable).seekTo(mPositions[i]);
        }
    }
}
