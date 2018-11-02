package pl.droidsonroids.gif;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import androidx.annotation.RequiresApi;
import android.util.AttributeSet;
import android.widget.ImageButton;

/**
 * An {@link ImageButton} which tries treating background and src as {@link GifDrawable}
 *
 * @author koral--
 */
public class GifImageButton extends ImageButton {

	private boolean mFreezesAnimation;

	/**
	 * A corresponding superclass constructor wrapper.
	 *
	 * @param context
	 * @see ImageButton#ImageButton(Context)
	 */
	public GifImageButton(Context context) {
		super(context);
	}

	/**
	 * Like equivalent from superclass but also try to interpret src and background
	 * attributes as {@link GifDrawable}.
	 *
	 * @param context
	 * @param attrs
	 * @see ImageButton#ImageButton(Context, AttributeSet)
	 */
	public GifImageButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		postInit(GifViewUtils.initImageView(this, attrs, 0, 0));
	}

	/**
	 * Like equivalent from superclass but also try to interpret src and background
	 * attributes as GIFs.
	 *
	 * @param context
	 * @param attrs
	 * @param defStyle
	 * @see ImageButton#ImageButton(Context, AttributeSet, int)
	 */
	public GifImageButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		postInit(GifViewUtils.initImageView(this, attrs, defStyle, 0));
	}

	/**
	 * Like equivalent from superclass but also try to interpret src and background
	 * attributes as GIFs.
	 *
	 * @param context
	 * @param attrs
	 * @param defStyle
	 * @param defStyleRes
	 * @see ImageButton#ImageButton(Context, AttributeSet, int, int)
	 */
	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	public GifImageButton(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
		super(context, attrs, defStyle, defStyleRes);
		postInit(GifViewUtils.initImageView(this, attrs, defStyle, defStyleRes));
	}

	private void postInit(GifViewUtils.GifImageViewAttributes result) {
		mFreezesAnimation = result.freezesAnimation;
		if (result.mSourceResId > 0) {
			super.setImageResource(result.mSourceResId);
		}
		if (result.mBackgroundResId > 0) {
			super.setBackgroundResource(result.mBackgroundResId);
		}
	}

	/**
	 * Sets the content of this GifImageView to the specified Uri.
	 * If uri destination is not a GIF then {@link android.widget.ImageView#setImageURI(android.net.Uri)}
	 * is called as fallback.
	 * For supported URI schemes see: {@link android.content.ContentResolver#openAssetFileDescriptor(android.net.Uri, String)}.
	 *
	 * @param uri The Uri of an image
	 */
	@Override
	public void setImageURI(Uri uri) {
		if (!GifViewUtils.setGifImageUri(this, uri)) {
			super.setImageURI(uri);
		}
	}

	@Override
	public void setImageResource(int resId) {
		if (!GifViewUtils.setResource(this, true, resId)) {
			super.setImageResource(resId);
		}
	}

	@Override
	public void setBackgroundResource(int resId) {
		if (!GifViewUtils.setResource(this, false, resId)) {
			super.setBackgroundResource(resId);
		}
	}

	@Override
	public Parcelable onSaveInstanceState() {
		Drawable source = mFreezesAnimation ? getDrawable() : null;
		Drawable background = mFreezesAnimation ? getBackground() : null;
		return new GifViewSavedState(super.onSaveInstanceState(), source, background);
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		if (!(state instanceof GifViewSavedState)) {
			super.onRestoreInstanceState(state);
			return;
		}
		GifViewSavedState ss = (GifViewSavedState) state;
		super.onRestoreInstanceState(ss.getSuperState());
		ss.restoreState(getDrawable(), 0);
		ss.restoreState(getBackground(), 1);
	}

	/**
	 * Sets whether animation position is saved in {@link #onSaveInstanceState()} and restored
	 * in {@link #onRestoreInstanceState(Parcelable)}
	 *
	 * @param freezesAnimation whether animation position is saved
	 */
	public void setFreezesAnimation(boolean freezesAnimation) {
		mFreezesAnimation = freezesAnimation;
	}
}
