package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;

import java.io.IOException;

final class GifViewUtils {
    static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private GifViewUtils() {
    }

    static InitResult initImageView(ImageView view, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        int sourceResId = 0;
        int backgroundResId = 0;
        boolean freezesAnimation = false;
        Resources res = view.getResources();
        if (attrs != null && res != null && !view.isInEditMode()) {
            int resId = attrs.getAttributeResourceValue(ANDROID_NS, "src", -1);
            if (resId > 0 && "drawable".equals(res.getResourceTypeName(resId))) {
                if (!setResource(view, true, resId)) {
                    sourceResId = resId;
                }
            }

            resId = attrs.getAttributeResourceValue(ANDROID_NS, "background", -1);
            if (resId > 0 && "drawable".equals(res.getResourceTypeName(resId))) {
                if (!setResource(view, true, resId)) {
                    backgroundResId = resId;
                }
            }
            freezesAnimation = isFreezingAnimation(view, attrs, defStyleAttr, defStyleRes);
        }
        return new InitResult(sourceResId, backgroundResId, freezesAnimation);
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
        final TypedArray gifViewAttributes = view.getContext().obtainStyledAttributes(attrs, R.styleable.GifSurfaceView, defStyleAttr, defStyleRes);
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

    static Pair<GifDrawableBuilder.Source, Boolean> initSurfaceView(View view, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        GifDrawableBuilder.Source source;
        boolean freezesAnimation;
        if (attrs != null && !view.isInEditMode()) {
            final TypedArray surfaceViewAttributes = view.getContext().obtainStyledAttributes(attrs, R.styleable.GifSurfaceView, defStyleAttr, defStyleRes);
            int resourceId = surfaceViewAttributes.getResourceId(R.styleable.GifSurfaceView_srcId, 0);
            String assetName = surfaceViewAttributes.getString(R.styleable.GifSurfaceView_srcAsset);
            String path = surfaceViewAttributes.getString(R.styleable.GifSurfaceView_srcPath);
            if (assetName != null) {
                source = new GifDrawableBuilder.AssetSource(view.getContext().getAssets(), assetName);
            } else if (path != null) {
                source = new GifDrawableBuilder.FileSource(path);
            } else {
                source = new GifDrawableBuilder.FileDescriptorSource(view.getContext().getResources(), resourceId);
            }
            surfaceViewAttributes.recycle();
            freezesAnimation = isFreezingAnimation(view, attrs, defStyleAttr, defStyleRes);
        } else {
            source = null;
            freezesAnimation = false;
        }
        return Pair.create(source, freezesAnimation);
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
