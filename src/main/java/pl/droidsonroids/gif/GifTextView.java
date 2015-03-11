package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcelable;
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
        init(attrs);
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
        init(attrs);
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
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public GifTextView(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
        super(context, attrs, defStyle, defStyleRes);
        init(attrs);
    }

    private boolean shouldSaveCompoundDrawables;
    private boolean shouldSaveCompoundDrawablesRelative;
    private boolean shouldSaveBackground;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void init(AttributeSet attrs) {
        if (attrs != null) {
            Drawable left = getGifOrDefaultDrawable(attrs.getAttributeResourceValue(GifImageView.ANDROID_NS, "drawableLeft", 0));
            Drawable top = getGifOrDefaultDrawable(attrs.getAttributeResourceValue(GifImageView.ANDROID_NS, "drawableTop", 0));
            Drawable right = getGifOrDefaultDrawable(attrs.getAttributeResourceValue(GifImageView.ANDROID_NS, "drawableRight", 0));
            Drawable bottom = getGifOrDefaultDrawable(attrs.getAttributeResourceValue(GifImageView.ANDROID_NS, "drawableBottom", 0));
            Drawable start = getGifOrDefaultDrawable(attrs.getAttributeResourceValue(GifImageView.ANDROID_NS, "drawableStart", 0));
            Drawable end = getGifOrDefaultDrawable(attrs.getAttributeResourceValue(GifImageView.ANDROID_NS, "drawableEnd", 0));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                if (getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
                    if (start == null)
                        start = left;
                    if (end == null)
                        end = right;
                } else {
                    if (start == null)
                        start = right;
                    if (end == null)
                        end = left;
                }
                setCompoundDrawablesRelativeWithIntrinsicBounds(start, top, end, bottom);
                setCompoundDrawablesWithIntrinsicBounds(left, top, right, bottom);
            } else {
                setCompoundDrawablesWithIntrinsicBounds(left, top, right, bottom);
            }
            setBackgroundInternal(getGifOrDefaultDrawable(attrs.getAttributeResourceValue(GifImageView.ANDROID_NS, "background", 0)));
        }
        shouldSaveCompoundDrawables = true;
        shouldSaveCompoundDrawablesRelative = true;
        shouldSaveBackground = true;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    // setBackground
    @SuppressWarnings("deprecation")
    // setBackgroundDrawable
    private void setBackgroundInternal(Drawable bg) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            setBackground(bg);
        } else {
            setBackgroundDrawable(bg);
        }
    }

    @Override
    public void setBackgroundResource(int resid) {
        setBackgroundInternal(getGifOrDefaultDrawable(resid));
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP) //Resources#getDrawable(int, Theme)
    @SuppressWarnings("deprecation") //Resources#getDrawable(int)
    private Drawable getGifOrDefaultDrawable(int resId) {
        if (resId == 0) {
            return null;
        }
        final Resources resources = getResources();
        if (!isInEditMode() && "drawable".equals(resources.getResourceTypeName(resId))) {
            try {
                return new GifDrawable(resources, resId);
            } catch (IOException | NotFoundException ignored) {
                // ignored
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            return resources.getDrawable(resId, getContext().getTheme());
        else
            return resources.getDrawable(resId);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void setCompoundDrawablesRelativeWithIntrinsicBounds(int start, int top, int end, int bottom) {
        setCompoundDrawablesRelativeWithIntrinsicBounds(getGifOrDefaultDrawable(start), getGifOrDefaultDrawable(top), getGifOrDefaultDrawable(end), getGifOrDefaultDrawable(bottom));
    }

    @Override
    public void setCompoundDrawablesWithIntrinsicBounds(int left, int top, int right, int bottom) {
        setCompoundDrawablesWithIntrinsicBounds(getGifOrDefaultDrawable(left), getGifOrDefaultDrawable(top), getGifOrDefaultDrawable(right), getGifOrDefaultDrawable(bottom));
    }

    @Override
    public void setCompoundDrawables(Drawable left, Drawable top, Drawable right, Drawable bottom) {
        super.setCompoundDrawables(left, top, right, bottom);
        shouldSaveCompoundDrawables = false;
    }

    @Override
    public void setCompoundDrawablesRelative(Drawable start, Drawable top, Drawable end, Drawable bottom) {
        super.setCompoundDrawablesRelative(start, top, end, bottom);
        shouldSaveCompoundDrawablesRelative = false;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        hideCompoundDrawables(getCompoundDrawables());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            hideCompoundDrawables(getCompoundDrawablesRelative());
        }
    }

    private void hideCompoundDrawables(Drawable[] drawables) {
        for (Drawable d : drawables) {
            if (d != null) {
                d.setVisible(false, false);
            }
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Drawable[] savedDrawables = new Drawable[7];
        if (shouldSaveCompoundDrawables) {
            Drawable[] compoundDrawables = getCompoundDrawables();
            System.arraycopy(compoundDrawables, 0, savedDrawables, 0, compoundDrawables.length);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Drawable[] compoundDrawablesRelative = getCompoundDrawablesRelative();
            savedDrawables[4] = shouldSaveCompoundDrawablesRelative ? compoundDrawablesRelative[0] : null; //start
            savedDrawables[5] = shouldSaveCompoundDrawablesRelative ? compoundDrawablesRelative[2] : null; //end
            if (!shouldSaveCompoundDrawablesRelative) {
                savedDrawables[2] = null;
                savedDrawables[4] = null;
            }
        }
        savedDrawables[6] = shouldSaveBackground ? getBackground() : null;
        return new GifViewSavedState(super.onSaveInstanceState(), savedDrawables);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        GifViewSavedState ss = (GifViewSavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        Drawable[] compoundDrawables = getCompoundDrawables();
        ss.setPostion(compoundDrawables[0], 0);
        ss.setPostion(compoundDrawables[1], 1);
        ss.setPostion(compoundDrawables[2], 2);
        ss.setPostion(compoundDrawables[3], 3);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Drawable[] compoundDrawablesRelative = getCompoundDrawablesRelative();
            ss.setPostion(compoundDrawablesRelative[0], 4);
            ss.setPostion(compoundDrawablesRelative[2], 5);
        }
        ss.setPostion(getBackground(), 6);
    }
}
