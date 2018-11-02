package pl.droidsonroids.gif;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcelable;
import androidx.annotation.RequiresApi;
import android.util.AttributeSet;
import android.widget.TextView;

import java.io.IOException;

/**
 * A {@link TextView} which handles GIFs as compound drawables. NOTE:
 * {@code android:drawableStart} and {@code android:drawableEnd} from XML are
 * not supported but can be set using
 * {@link #setCompoundDrawablesRelativeWithIntrinsicBounds(int, int, int, int)}
 *
 * @author koral--
 */
public class GifTextView extends TextView {

	private GifViewUtils.GifViewAttributes viewAttributes;

	/**
	 * A corresponding superclass constructor wrapper.
	 *
	 * @param context
	 */
	public GifTextView(Context context) {
		super(context);
	}

	/**
	 * Like equivalent from superclass but also try to interpret compound drawables defined in XML
	 * attributes as GIFs.
	 *
	 * @param context
	 * @param attrs
	 * @see TextView#TextView(Context, AttributeSet)
	 */
	public GifTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(attrs, 0, 0);
	}

	/**
	 * Like equivalent from superclass but also try to interpret compound drawables defined in XML
	 * attributes as GIFs.
	 *
	 * @param context
	 * @param attrs
	 * @param defStyle
	 * @see TextView#TextView(Context, AttributeSet, int)
	 */
	public GifTextView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(attrs, defStyle, 0);
	}

	/**
	 * Like equivalent from superclass but also try to interpret compound drawables defined in XML
	 * attributes as GIFs.
	 *
	 * @param context
	 * @param attrs
	 * @param defStyle
	 * @param defStyleRes
	 * @see TextView#TextView(Context, AttributeSet, int, int)
	 */
	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	public GifTextView(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
		super(context, attrs, defStyle, defStyleRes);
		init(attrs, defStyle, defStyleRes);
	}

	private static void setDrawablesVisible(final Drawable[] drawables, final boolean visible) {
		for (final Drawable drawable : drawables) {
			if (drawable != null) {
				drawable.setVisible(visible, false);
			}
		}
	}

	private void init(AttributeSet attrs, int defStyle, int defStyleRes) {
		if (attrs != null) {
			Drawable left = getGifOrDefaultDrawable(attrs.getAttributeResourceValue(GifViewUtils.ANDROID_NS, "drawableLeft", 0));
			Drawable top = getGifOrDefaultDrawable(attrs.getAttributeResourceValue(GifViewUtils.ANDROID_NS, "drawableTop", 0));
			Drawable right = getGifOrDefaultDrawable(attrs.getAttributeResourceValue(GifViewUtils.ANDROID_NS, "drawableRight", 0));
			Drawable bottom = getGifOrDefaultDrawable(attrs.getAttributeResourceValue(GifViewUtils.ANDROID_NS, "drawableBottom", 0));
			Drawable start = getGifOrDefaultDrawable(attrs.getAttributeResourceValue(GifViewUtils.ANDROID_NS, "drawableStart", 0));
			Drawable end = getGifOrDefaultDrawable(attrs.getAttributeResourceValue(GifViewUtils.ANDROID_NS, "drawableEnd", 0));

			if (getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
				if (start == null) {
					start = left;
				}
				if (end == null) {
					end = right;
				}
			} else {
				if (start == null) {
					start = right;
				}
				if (end == null) {
					end = left;
				}
			}
			setCompoundDrawablesRelativeWithIntrinsicBounds(start, top, end, bottom);
			setBackground(getGifOrDefaultDrawable(attrs.getAttributeResourceValue(GifViewUtils.ANDROID_NS, "background", 0)));
			viewAttributes = new GifViewUtils.GifViewAttributes(this, attrs, defStyle, defStyleRes);
			applyGifViewAttributes();
		}
		viewAttributes = new GifViewUtils.GifViewAttributes();
	}

	private void applyGifViewAttributes() {
		if (viewAttributes.mLoopCount < 0) {
			return;
		}
		for (final Drawable drawable : getCompoundDrawables()) {
			GifViewUtils.applyLoopCount(viewAttributes.mLoopCount, drawable);
		}
		for (final Drawable drawable : getCompoundDrawablesRelative()) {
			GifViewUtils.applyLoopCount(viewAttributes.mLoopCount, drawable);
		}
		GifViewUtils.applyLoopCount(viewAttributes.mLoopCount, getBackground());
	}

	@SuppressWarnings("deprecation") //Resources#getDrawable(int)
	private Drawable getGifOrDefaultDrawable(int resId) {
		if (resId == 0) {
			return null;
		}
		final Resources resources = getResources();
		final String resourceTypeName = resources.getResourceTypeName(resId);
		if (!isInEditMode() && GifViewUtils.SUPPORTED_RESOURCE_TYPE_NAMES.contains(resourceTypeName)) {
			try {
				return new GifDrawable(resources, resId);
			} catch (IOException | NotFoundException ignored) {
				// ignored
			}
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			return resources.getDrawable(resId, getContext().getTheme());
		} else {
			return resources.getDrawable(resId);
		}
	}

	@Override
	public void setCompoundDrawablesWithIntrinsicBounds(int left, int top, int right, int bottom) {
		setCompoundDrawablesWithIntrinsicBounds(getGifOrDefaultDrawable(left), getGifOrDefaultDrawable(top), getGifOrDefaultDrawable(right), getGifOrDefaultDrawable(bottom));
	}

	@Override
	public void setCompoundDrawablesRelativeWithIntrinsicBounds(int start, int top, int end, int bottom) {
		setCompoundDrawablesRelativeWithIntrinsicBounds(getGifOrDefaultDrawable(start), getGifOrDefaultDrawable(top), getGifOrDefaultDrawable(end), getGifOrDefaultDrawable(bottom));
	}

	@Override
	public Parcelable onSaveInstanceState() {
		Drawable[] savedDrawables = new Drawable[7];
		if (viewAttributes.freezesAnimation) {
			Drawable[] compoundDrawables = getCompoundDrawables();
			System.arraycopy(compoundDrawables, 0, savedDrawables, 0, compoundDrawables.length);

            Drawable[] compoundDrawablesRelative = getCompoundDrawablesRelative();
            savedDrawables[4] = compoundDrawablesRelative[0]; //start
            savedDrawables[5] = compoundDrawablesRelative[2]; //end
            savedDrawables[6] = getBackground();
		}
		return new GifViewSavedState(super.onSaveInstanceState(), savedDrawables);
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		if (!(state instanceof GifViewSavedState)) {
			super.onRestoreInstanceState(state);
			return;
		}
		GifViewSavedState ss = (GifViewSavedState) state;
		super.onRestoreInstanceState(ss.getSuperState());

		Drawable[] compoundDrawables = getCompoundDrawables();
		ss.restoreState(compoundDrawables[0], 0);
		ss.restoreState(compoundDrawables[1], 1);
		ss.restoreState(compoundDrawables[2], 2);
		ss.restoreState(compoundDrawables[3], 3);
        Drawable[] compoundDrawablesRelative = getCompoundDrawablesRelative();
        ss.restoreState(compoundDrawablesRelative[0], 4);
        ss.restoreState(compoundDrawablesRelative[2], 5);
        ss.restoreState(getBackground(), 6);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		setCompoundDrawablesVisible(true);
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		setCompoundDrawablesVisible(false);
	}

	@Override
	public void setBackgroundResource(int resId) {
		setBackground(getGifOrDefaultDrawable(resId));
	}

	private void setCompoundDrawablesVisible(final boolean visible) {
		setDrawablesVisible(getCompoundDrawables(), visible);
        setDrawablesVisible(getCompoundDrawablesRelative(), visible);
    }

	/**
	 * Sets whether animation position is saved in {@link #onSaveInstanceState()} and restored
	 * in {@link #onRestoreInstanceState(Parcelable)}. This is applicable to all compound drawables.
	 *
	 * @param freezesAnimation whether animation position is saved
	 */
	public void setFreezesAnimation(boolean freezesAnimation) {
		viewAttributes.freezesAnimation = freezesAnimation;
	}
}
