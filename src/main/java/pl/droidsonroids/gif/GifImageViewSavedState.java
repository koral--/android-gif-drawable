package pl.droidsonroids.gif;

import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;

class GifImageViewSavedState extends View.BaseSavedState {

    final int mSrcPosition, mBackgroundPosition;

    GifImageViewSavedState(Parcelable superState, Drawable source, Drawable background) {
        super(superState);
        if (source instanceof GifDrawable)
            mSrcPosition = ((GifDrawable) source).getCurrentPosition();
        else
            mSrcPosition = -1;
        if (background instanceof GifDrawable)
            mBackgroundPosition = ((GifDrawable) background).getCurrentPosition();
        else
            mBackgroundPosition = -1;
    }

    GifImageViewSavedState(Parcel in) {
        super(in);
        mSrcPosition = in.readInt();
        mBackgroundPosition = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mSrcPosition);
        dest.writeInt(mBackgroundPosition);
    }

    public static final Creator<GifImageViewSavedState> CREATOR = new Creator<GifImageViewSavedState>() {

        public GifImageViewSavedState createFromParcel(Parcel in) {
            return new GifImageViewSavedState(in);
        }

        public GifImageViewSavedState[] newArray(int size) {
            return new GifImageViewSavedState[size];
        }
    };
}
