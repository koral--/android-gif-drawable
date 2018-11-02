package pl.droidsonroids.gif;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

final class GifViewUtils {
	static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
	static final List<String> SUPPORTED_RESOURCE_TYPE_NAMES = Arrays.asList("raw", "drawable", "mipmap");

	private GifViewUtils() {
	}

	static GifImageViewAttributes initImageView(ImageView view, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		if (attrs != null && !view.isInEditMode()) {
			final GifImageViewAttributes viewAttributes = new GifImageViewAttributes(view, attrs, defStyleAttr, defStyleRes);
			final int loopCount = viewAttributes.mLoopCount;
			if (loopCount >= 0) {
				applyLoopCount(loopCount, view.getDrawable());
				applyLoopCount(loopCount, view.getBackground());
			}
			return viewAttributes;
		}
		return new GifImageViewAttributes();
	}

	static void applyLoopCount(final int loopCount, final Drawable drawable) {
		if (drawable instanceof GifDrawable) {
			((GifDrawable) drawable).setLoopCount(loopCount);
		}
	}

	@SuppressWarnings("deprecation")
	static boolean setResource(ImageView view, boolean isSrc, int resId) {
		Resources res = view.getResources();
		if (res != null) {
			try {
				final String resourceTypeName = res.getResourceTypeName(resId);
				if (!SUPPORTED_RESOURCE_TYPE_NAMES.contains(resourceTypeName)) {
					return false;
				}
				GifDrawable d = new GifDrawable(res, resId);
				if (isSrc) {
					view.setImageDrawable(d);
				} else {
					view.setBackground(d);
				}
				return true;
			} catch (IOException | Resources.NotFoundException ignored) {
				//ignored
			}
		}
		return false;
	}

	static boolean setGifImageUri(ImageView imageView, Uri uri) {
		if (uri != null) {
			try {
				imageView.setImageDrawable(new GifDrawable(imageView.getContext().getContentResolver(), uri));
				return true;
			} catch (IOException ignored) {
				//ignored
			}
		}
		return false;
	}

	static float getDensityScale(@NonNull Resources res, @DrawableRes @RawRes int id) {
		final TypedValue value = new TypedValue();
		res.getValue(id, value, true);
		final int resourceDensity = value.density;
		final int density;
		if (resourceDensity == TypedValue.DENSITY_DEFAULT) {
			density = DisplayMetrics.DENSITY_DEFAULT;
		} else if (resourceDensity != TypedValue.DENSITY_NONE) {
			density = resourceDensity;
		} else {
			density = 0;
		}
		final int targetDensity = res.getDisplayMetrics().densityDpi;

		if (density > 0 && targetDensity > 0) {
			return (float) targetDensity / density;
		}
		return 1f;
	}

	static class GifViewAttributes {
		boolean freezesAnimation;
		final int mLoopCount;

		GifViewAttributes(final View view, final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
			final TypedArray gifViewAttributes = view.getContext().obtainStyledAttributes(attrs, R.styleable.GifView, defStyleAttr, defStyleRes);
			freezesAnimation = gifViewAttributes.getBoolean(R.styleable.GifView_freezesAnimation, false);
			mLoopCount = gifViewAttributes.getInt(R.styleable.GifView_loopCount, -1);
			gifViewAttributes.recycle();
		}

		GifViewAttributes() {
			freezesAnimation = false;
			mLoopCount = -1;
		}
	}

	static class GifImageViewAttributes extends GifViewAttributes {
		final int mSourceResId;
		final int mBackgroundResId;

		GifImageViewAttributes(final ImageView view, final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
			super(view, attrs, defStyleAttr, defStyleRes);
			mSourceResId = getResourceId(view, attrs, true);
			mBackgroundResId = getResourceId(view, attrs, false);
		}

		GifImageViewAttributes() {
			super();
			mSourceResId = 0;
			mBackgroundResId = 0;
		}

		private static int getResourceId(ImageView view, AttributeSet attrs, final boolean isSrc) {
			final int resId = attrs.getAttributeResourceValue(ANDROID_NS, isSrc ? "src" : "background", 0);
			if (resId > 0) {
				final String resourceTypeName = view.getResources().getResourceTypeName(resId);
				if (SUPPORTED_RESOURCE_TYPE_NAMES.contains(resourceTypeName) && !setResource(view, isSrc, resId)) {
					return resId;
				}
			}
			return 0;
		}

	}
}
