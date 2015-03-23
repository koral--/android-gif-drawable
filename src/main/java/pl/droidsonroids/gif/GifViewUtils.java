package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import java.io.IOException;

final class GifViewUtils {
    static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private GifViewUtils() {
    }

    static InitResult initImageView(ImageView view, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        if (attrs != null && !view.isInEditMode()) {
            final int sourceResId = getResourceId(view, attrs, true);
            final int backgroundResId = getResourceId(view, attrs, false);
            final boolean freezesAnimation = isFreezingAnimation(view, attrs, defStyleAttr, defStyleRes);
            return new InitResult(sourceResId, backgroundResId, freezesAnimation);
        }
        return new InitResult(0, 0, false);
    }

    private static int getResourceId(ImageView view, AttributeSet attrs, final boolean isSrc) {
        final int resId = attrs.getAttributeResourceValue(ANDROID_NS, isSrc ? "src" : "background", 0);
        if (resId > 0) {
            final String resourceTypeName = view.getResources().getResourceTypeName(resId);
            if ("drawable".equals(resourceTypeName) || "raw".equals(resourceTypeName)) {
                if (!setResource(view, isSrc, resId)) {
                    return resId;
                }
            }
        }
        return 0;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @SuppressWarnings("deprecation")
    static boolean setResource(ImageView view, boolean isSrc, int resId) {
        Resources res = view.getResources();
        if (res != null) {
            try {
                GifDrawable d = new GifDrawable(res, resId);
                if (isSrc) {
                    view.setImageDrawable(d);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    view.setBackground(d);
                } else {
                    view.setBackgroundDrawable(d);
                }
                return true;
            } catch (IOException | Resources.NotFoundException ignored) {
                //ignored
            }
        }
        return false;
    }

    static boolean isFreezingAnimation(View view, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        final TypedArray gifViewAttributes = view.getContext().obtainStyledAttributes(attrs, R.styleable.GifView, defStyleAttr, defStyleRes);
        boolean freezesAnimation = gifViewAttributes.getBoolean(R.styleable.GifView_freezesAnimation, false);
        gifViewAttributes.recycle();
        return freezesAnimation;
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

    static class InitResult {
        final int mSourceResId;
        final int mBackgroundResId;
        final boolean mFreezesAnimation;

        InitResult(int sourceResId, int backgroundResId, boolean freezesAnimation) {

            mSourceResId = sourceResId;
            mBackgroundResId = backgroundResId;
            mFreezesAnimation = freezesAnimation;
        }
    }
}
