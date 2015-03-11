package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.widget.ImageView;

import java.io.IOException;
import java.util.Arrays;

/**
 * An {@link ImageView} which tries treating background and src as {@link GifDrawable}
 *
 * @author koral--
 */
public class GifImageView extends ImageView {
    static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /**
     * A corresponding superclass constructor wrapper.
     *
     * @param context
     * @see ImageView#ImageView(Context)
     */
    public GifImageView(Context context) {
        super(context);
    }

    /**
     * Like equivalent from superclass but also try to interpret src and background
     * attributes as {@link GifDrawable}.
     *
     * @param context
     * @param attrs
     * @see ImageView#ImageView(Context, AttributeSet)
     */
    public GifImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        trySetGifDrawable(attrs);
    }

    /**
     * Like equivalent from superclass but also try to interpret src and background
     * attributes as GIFs.
     *
     * @param context
     * @param attrs
     * @param defStyle
     * @see ImageView#ImageView(Context, AttributeSet, int)
     */
    public GifImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        trySetGifDrawable(attrs);
    }

    /**
     * Like equivalent from superclass but also try to interpret src and background
     * attributes as GIFs.
     *
     * @param context
     * @param attrs
     * @param defStyle
     * @param defStyleRes
     * @see ImageView#ImageView(Context, AttributeSet, int, int)
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public GifImageView(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
        super(context, attrs, defStyle, defStyleRes);
        trySetGifDrawable(attrs);
    }

    private boolean shouldSaveSource;
    private boolean shouldSaveBackground;

    void trySetGifDrawable(AttributeSet attrs) {
        Resources res = getResources();
        if (attrs != null && res != null && !isInEditMode()) {
            int resId = attrs.getAttributeResourceValue(ANDROID_NS, "src", -1);
            if (resId > 0 && "drawable".equals(res.getResourceTypeName(resId)))
                setResource(true, resId);

            resId = attrs.getAttributeResourceValue(ANDROID_NS, "background", -1);
            if (resId > 0 && "drawable".equals(res.getResourceTypeName(resId)))
                setResource(false, resId);
        }
        shouldSaveSource = true;
        shouldSaveBackground = true;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @SuppressWarnings("deprecation")
    void setResource(boolean isSrc, int resId) {
        Resources res = getResources();
        if (res != null) {
            try {
                GifDrawable d = new GifDrawable(res, resId);
                if (isSrc)
                    setImageDrawable(d);
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                    setBackground(d);
                else
                    setBackgroundDrawable(d);
                return;
            } catch (IOException | NotFoundException ignored) {
                //ignored
            }
        }
        if (isSrc)
            super.setImageResource(resId);
        else
            super.setBackgroundResource(resId);
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
        if (uri != null)
            try {
                setImageDrawable(new GifDrawable(getContext().getContentResolver(), uri));
                return;
            } catch (IOException ignored) {
                //ignored
            }
        super.setImageURI(uri);
    }

    @Override
    public void setImageResource(int resId) {
        setResource(true, resId);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        shouldSaveSource = false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setBackgroundDrawable(Drawable background) {
        super.setBackgroundDrawable(background);
        shouldSaveBackground = false;
    }

    @Override
    public void setBackground(Drawable background) {
        super.setBackground(background);
        shouldSaveBackground = false;
    }

    @Override
    public void setBackgroundResource(int resId) {
        setResource(false, resId);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Drawable source = shouldSaveSource ? getDrawable() : null;
        Drawable background = shouldSaveBackground ? getBackground() : null;
        return new GifViewSavedState(super.onSaveInstanceState(), source, background);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        GifViewSavedState ss = (GifViewSavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        ss.setPostion(getDrawable(), 0);
        ss.setPostion(getBackground(), 1);
    }
}
