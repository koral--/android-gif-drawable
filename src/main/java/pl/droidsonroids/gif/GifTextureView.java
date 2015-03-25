package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView.ScaleType;

import java.io.IOException;

/**
 * <p>{@link TextureView} which can display animated GIFs. Available on API level 14
 * ({@link Build.VERSION_CODES#ICE_CREAM_SANDWICH}) and above. GifTextureView can only be used in a
 * hardware accelerated window. When rendered in software, GifTextureView will draw nothing.</p>
 * <p>GIF source can be specified in XML or by calling {@link #setSource(GifDrawableBuilder.Source)}</p>
 * <pre>
 *     &lt;pl.droidsonroids.gif.GifTextureView
 *          xmlns:app="http://schemas.android.com/apk/res-auto"
 *          android:id="@+id/gif_texture_view"
 *          android:scaleType="fitEnd"
 *          app:src="@drawable/animation"
 *          android:layout_width="match_parent"
 *          android:layout_height="match_parent" /&gt;
 * </pre>
 * Note that <b>src</b> attribute comes from app namespace (you can call it whatever you want) not from
 * android one. Drawable, raw resources and assets can be specified through XML. If value is a string
 * (from resources or entered directly) it will be treated as an asset.
 * <p>Unlike {@link TextureView} GifTextureView is transparent by default, but it can be changed by
 * {@link #setOpaque(boolean)}.
 * You can use scale types the same way as in {@link android.widget.ImageView}.</p>
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class GifTextureView extends TextureView {

    private ScaleType mScaleType = ScaleType.FIT_CENTER;
    private final Matrix mTransform = new Matrix();
    private GifDrawableBuilder.Source mSource;
    private boolean mFreezesAnimation;

    private RenderThread mRenderThread = new RenderThread();
    private float mSpeedFactor = 1f;

    public GifTextureView(Context context) {
        super(context);
    }

    public GifTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0, 0);
    }

    public GifTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public GifTextureView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs, defStyleAttr, defStyleRes);
    }

    private static final ScaleType[] sScaleTypeArray = {ScaleType.MATRIX, ScaleType.FIT_XY, ScaleType.FIT_START, ScaleType.FIT_CENTER, ScaleType.FIT_END, ScaleType.CENTER, ScaleType.CENTER_CROP, ScaleType.CENTER_INSIDE};

    private void init(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        if (attrs != null && !isInEditMode()) {
            final int scaleTypeIndex = attrs.getAttributeIntValue(GifViewUtils.ANDROID_NS, "scaleType", -1);
            if (scaleTypeIndex >= 0 && scaleTypeIndex < sScaleTypeArray.length) {
                mScaleType = sScaleTypeArray[scaleTypeIndex];
            }
            final TypedArray textureViewAttributes = getContext().obtainStyledAttributes(attrs, R.styleable.GifTextureView, defStyleAttr, defStyleRes);
            mSource = findSource(textureViewAttributes);
            setOpaque(textureViewAttributes.getBoolean(R.styleable.GifTextureView_isOpaque, false));
            textureViewAttributes.recycle();
            mFreezesAnimation = GifViewUtils.isFreezingAnimation(this, attrs, defStyleAttr, defStyleRes);
        } else {
            setOpaque(false);
        }

        if (mSource != null) {
            mRenderThread.start();
        }
    }

    /**
     * Always throws {@link UnsupportedOperationException}. Changing {@link SurfaceTextureListener}
     * is not supported.
     *
     * @param listener ignored
     */
    @Override
    public void setSurfaceTextureListener(SurfaceTextureListener listener) {
        throw new UnsupportedOperationException("Changing SurfaceTextureListener is not supported");
    }

    /**
     * Always returns null since changing {@link SurfaceTextureListener} is not supported.
     *
     * @return always null
     */
    @Override
    public SurfaceTextureListener getSurfaceTextureListener() {
        return null;
    }

    /**
     * Always throws {@link UnsupportedOperationException}. Changing {@link SurfaceTexture} is not
     * supported.
     *
     * @param surfaceTexture ignored
     */
    @Override
    public void setSurfaceTexture(SurfaceTexture surfaceTexture) {
        throw new UnsupportedOperationException("Changing SurfaceTexture is not supported");
    }

    private static GifDrawableBuilder.Source findSource(final TypedArray textureViewAttributes) {
        final TypedValue value = new TypedValue();
        if (!textureViewAttributes.getValue(R.styleable.GifTextureView_src, value)) {
            return null;
        }

        if (value.resourceId != 0) {
            final String type = textureViewAttributes.getResources().getResourceTypeName(value.resourceId);
            if ("drawable".equals(type) || "raw".equals(type)) {
                return new GifDrawableBuilder.ResourcesSource(textureViewAttributes.getResources(), value.resourceId);
            } else if (!"string".equals(type)) {
                throw new IllegalArgumentException("Expected string, drawable or raw resource, type " + type + " cannot be converted to GIF");
            }
        }
        return new GifDrawableBuilder.AssetSource(textureViewAttributes.getResources().getAssets(), value.string.toString());
    }

    private class RenderThread extends Thread implements SurfaceTextureListener {
        private int mStartPosition;
        private GifInfoHandle mGifInfoHandle = GifInfoHandle.NULL_INFO;
        private IOException mIOException;

        @Override
        public void run() {
            try {
                mGifInfoHandle = mSource.open();
            } catch (IOException ex) {
                mIOException = ex;
                return;
            }
            GifTextureView.super.setSurfaceTextureListener(this);

            post(new Runnable() {
                @Override
                public void run() {
                    updateTextureViewSize(mGifInfoHandle);
                }
            });
            mGifInfoHandle.setSpeedFactor(mSpeedFactor);
            RENDER_LOOP:
            while (!isInterrupted()) {
                SurfaceTexture surfaceTexture = getSurfaceTexture();
                while (surfaceTexture == null) {
                    try {
                        synchronized (this) {
                            wait();
                        }
                    } catch (InterruptedException e) {
                        break RENDER_LOOP;
                    }
                    surfaceTexture = getSurfaceTexture();
                }

                final Surface surface = new Surface(surfaceTexture);
                mGifInfoHandle.reset();
                try {
                    mGifInfoHandle.bindSurface(surface, mStartPosition);
                } finally {
                    surface.release();
                }
            }
            mGifInfoHandle.recycle();
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            synchronized (this) {
                notify();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            mStartPosition = mGifInfoHandle.postUnbindSurface();
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }

        void dispose() {
            GifTextureView.super.setSurfaceTextureListener(null);
            mGifInfoHandle.postUnbindSurface();
            mRenderThread.interrupt();
            final boolean isCallerInterrupted = Thread.currentThread().isInterrupted();
            if (isCallerInterrupted)
                interrupted();
            try {
                mRenderThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (isCallerInterrupted)
                Thread.currentThread().interrupt();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mRenderThread.dispose();
        super.onDetachedFromWindow();
        final SurfaceTexture surfaceTexture = getSurfaceTexture();
        if (surfaceTexture != null)
            surfaceTexture.release();
    }

    /**
     * Sets the source of the animation. Pass null to remove current source.
     *
     * @param source new animation source, may be null
     */
    public synchronized void setSource(@Nullable GifDrawableBuilder.Source source) {
        mRenderThread.dispose();
        mSource = source;
        mRenderThread = new RenderThread();
        if (source != null)
            mRenderThread.start();
    }

    /**
     * @param factor new speed factor, eg. 0.5f means half speed, 1.0f - normal, 2.0f - double speed
     * @throws IllegalArgumentException if factor&lt;=0
     * @see GifDrawable#setSpeed(float)
     */
    public void setSpeed(float factor) {
        mSpeedFactor = factor;
        mRenderThread.mGifInfoHandle.setSpeedFactor(factor);
    }

    /**
     * Returns last {@link IOException} occurred during loading or playing GIF (in such case only {@link GifIOException}
     * can be returned. Null is returned when source is not, surface was not yet created or no error
     * occurred.
     *
     * @return exception occurred during loading or playing GIF or null
     */
    @Nullable
    public IOException getIOException() {
        if (mRenderThread.mIOException != null)
            return mRenderThread.mIOException;
        else {
            return GifIOException.fromCode(mRenderThread.mGifInfoHandle.getNativeErrorCode());
        }
    }

    /**
     * Controls how the image should be resized or moved to match the size
     * of this ImageView.
     *
     * @param scaleType The desired scaling mode.
     */
    public void setScaleType(@NonNull ScaleType scaleType) {
        mScaleType = scaleType;
        updateTextureViewSize(mRenderThread.mGifInfoHandle);
    }

    /**
     * @return the current scale type in use by this View.
     * @see ScaleType
     */
    public ScaleType getScaleType() {
        return mScaleType;
    }

    private void updateTextureViewSize(final GifInfoHandle gifInfoHandle) {
        final Matrix transform = new Matrix();
        final float viewWidth = getWidth();
        final float viewHeight = getHeight();
        final float scaleRef;
        final float scaleX = gifInfoHandle.width / viewWidth;
        final float scaleY = gifInfoHandle.height / viewHeight;
        RectF src = new RectF(0, 0, gifInfoHandle.width, gifInfoHandle.height);
        RectF dst = new RectF(0, 0, viewWidth, viewHeight);
        switch (mScaleType) {
            case CENTER:
                transform.setScale(scaleX, scaleY, viewWidth / 2, viewHeight / 2);
                break;
            case CENTER_CROP:
                scaleRef = 1 / Math.min(scaleX, scaleY);
                transform.setScale(scaleRef * scaleX, scaleRef * scaleY, viewWidth / 2, viewHeight / 2);
                break;
            case CENTER_INSIDE:
                if (gifInfoHandle.width <= viewWidth && gifInfoHandle.height <= viewHeight) {
                    scaleRef = 1.0f;
                } else {
                    scaleRef = Math.min(1 / scaleX, 1 / scaleY);
                }
                transform.setScale(scaleRef * scaleX, scaleRef * scaleY, viewWidth / 2, viewHeight / 2);
                break;
            case FIT_CENTER:
                transform.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER);
                transform.preScale(scaleX, scaleY);
                break;
            case FIT_END:
                transform.setRectToRect(src, dst, Matrix.ScaleToFit.END);
                transform.preScale(scaleX, scaleY);
                break;
            case FIT_START:
                transform.setRectToRect(src, dst, Matrix.ScaleToFit.START);
                transform.preScale(scaleX, scaleY);
                break;
            case FIT_XY:
                return;
            case MATRIX:
                transform.set(mTransform);
                transform.preScale(scaleX, scaleY);
                break;
        }
        super.setTransform(transform);
    }

    /**
     * {@link #setTransform(Matrix)} equivalent.
     *
     * @param matrix The transform to apply to the content of this view.
     */
    public void setImageMatrix(Matrix matrix) {
        setTransform(matrix);
    }

    /**
     * Works like {@link TextureView#setTransform(Matrix)} but transform will take effect only if
     * scale type is set to {@link ScaleType#MATRIX} through XML attribute or via {@link #setScaleType(ScaleType)}
     *
     * @param transform The transform to apply to the content of this view.
     */
    @Override
    public void setTransform(Matrix transform) {
        mTransform.set(transform);
        updateTextureViewSize(mRenderThread.mGifInfoHandle);
    }

    @Override
    public Matrix getTransform(Matrix transform) {
        if (transform == null) {
            transform = new Matrix();
        }
        transform.set(mTransform);
        return transform;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final int position;
        if (mFreezesAnimation) {
            position = mRenderThread.mGifInfoHandle.postUnbindSurface();
            mRenderThread.mStartPosition = position;
        } else
            position = 0;
        return new GifViewSavedState(super.onSaveInstanceState(), position);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        GifViewSavedState ss = (GifViewSavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mRenderThread.mStartPosition = ss.mPositions[0];
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
