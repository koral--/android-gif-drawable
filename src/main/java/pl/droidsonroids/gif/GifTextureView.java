package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView.ScaleType;

import java.io.IOException;

/**
 * {@link TextureView} which can display animated GIFs.<br>
 * <pre>
 *     &lt;pl.droidsonroids.gif.GifTextureView
 *          xmlns:app="http://schemas.android.com/apk/res-auto"
 *          android:id="@+id/gif_texture_view"
 *          app:src="@drawable/anim"
 *          android:layout_width="match_parent"
 *          android:layout_height="match_parent" /&gt;
 * </pre>
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class GifTextureView extends TextureView {

    private ScaleType mScaleType = ScaleType.FIT_CENTER;
    private final Matrix mTransform = new Matrix();
    private RenderThread mThread;
    private int mSavedPosition;
    private GifDrawableBuilder.Source mSource;
    private boolean mFreezesAnimation;
    private final SurfaceTextureListener mCallback = new SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mThread = new RenderThread(surface, GifTextureView.this, mSavedPosition);
            mThread.start();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.e("st changed", width+" "+height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            mSavedPosition = mThread.getPosition();
            mThread.interrupt();
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

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

    private static final ScaleType[] sScaleTypeArray = {
            ScaleType.MATRIX,
            ScaleType.FIT_XY,
            ScaleType.FIT_START,
            ScaleType.FIT_CENTER,
            ScaleType.FIT_END,
            ScaleType.CENTER,
            ScaleType.CENTER_CROP,
            ScaleType.CENTER_INSIDE
    };

    private void init(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        if (attrs != null && !isInEditMode()) {
            final int scaleTypeIndex = attrs.getAttributeIntValue(GifViewUtils.ANDROID_NS, "scaleType", -1);
            if (scaleTypeIndex >= 0 && scaleTypeIndex < sScaleTypeArray.length)
                mScaleType = sScaleTypeArray[scaleTypeIndex];
            final TypedArray textureViewAttributes = getContext().obtainStyledAttributes(attrs, R.styleable.GifTextureView, defStyleAttr, defStyleRes);
            mSource = findSource(textureViewAttributes);
            setOpaque(textureViewAttributes.getBoolean(R.styleable.GifTextureView_isOpaque, true));
            textureViewAttributes.recycle();
            mFreezesAnimation = GifViewUtils.isFreezingAnimation(this, attrs, defStyleAttr, defStyleRes);
        }
        setSurfaceTextureListener(mCallback);
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

    private static class RenderThread extends Thread {
        private final GifDrawableBuilder.Source mSource;
        private final SurfaceTexture mSurfaceTexture;
        private GifInfoHandle mGifInfoHandle;
        private int mPosition;
        private IOException mIOException;
        private final GifTextureView mGifTextureView;

        RenderThread(SurfaceTexture surfaceTexture, GifTextureView gifTextureView, int startPosition) {
            mSurfaceTexture = surfaceTexture;
            mSource = gifTextureView.mSource;
            mPosition = startPosition;
            mGifTextureView = gifTextureView;
            setPriority(MAX_PRIORITY);
        }

        @Override
        public void run() {
            if (mSource == null) {
                return;
            }
            try {
                mGifInfoHandle = mSource.open();
            } catch (IOException ex) {
                mIOException = ex;
                return;
            }

            mGifTextureView.post(new Runnable() {
                @Override
                public void run() {
                    mGifTextureView.updateTextureViewSize(mGifInfoHandle);
                }
            });

            Surface surface = new Surface(mSurfaceTexture);
            mGifInfoHandle.bindSurface(surface, mPosition);
            synchronized (this) {
                mPosition = mGifInfoHandle.getCurrentPosition();
                mGifInfoHandle.recycle();
            }
            surface.release();
        }

        synchronized int getPosition() {
            if (mGifInfoHandle == null || mGifInfoHandle.isRecycled()) {
                return mPosition;
            } else {
                mGifInfoHandle.saveRemainder();
                return mGifInfoHandle.getCurrentPosition();
            }
        }

        IOException getException() {
            if (mGifInfoHandle == null) {
                return mIOException;
            } else {
                return new GifIOException(mGifInfoHandle.getNativeErrorCode());
            }
        }
    }

    /**
     * Sets the source of the animation. Pass null to remove current source.
     *
     * @param source new animation source, may be null
     */
    public synchronized void setSource(@Nullable GifDrawableBuilder.Source source) {
        int oldVisibility = getVisibility();
        setVisibility(INVISIBLE); //dirty hack to force surface recreation
        mSource = source;
        mSavedPosition = 0;
        setVisibility(oldVisibility);
    }

    /**
     * Sets new animation speed factor.<br>
     * Note: If animation is in progress ({@link #draw(Canvas)}) was already called)
     * then effects will be visible starting from the next frame. Duration of the currently rendered
     * frame is not affected.
     *
     * @param factor new speed factor, eg. 0.5f means half speed, 1.0f - normal, 2.0f - double speed
     * @throws IllegalArgumentException if factor&lt;=0
     */
    public void setSpeed(float factor) {
        if (mThread != null && mThread.mGifInfoHandle != null) //TODO thread safe
        {
            mThread.mGifInfoHandle.setSpeedFactor(factor);
        }
    }

    /**
     * Returns {@link IOException} occurred during loading or playing GIF (in such case only {@link GifIOException}
     * can be returned. Null is returned when source is not set or surface was not yet created.
     * In case of no error {@link GifIOException} with {@link GifError#NO_ERROR} code is returned.
     *
     * @return exception occurred during loading or playing GIF or null
     */
    @Nullable
    public IOException getIOException() {
        if (mThread != null) {
            return mThread.getException();
        }
        return null;
    }

    /**
     * Controls how the image should be resized or moved to match the size
     * of this ImageView.
     *
     * @param scaleType The desired scaling mode.
     */
    public void setScaleType(@NonNull ScaleType scaleType) {
        mScaleType = scaleType;
        if (mThread != null && mThread.mGifInfoHandle != null) {
            updateTextureViewSize(mThread.mGifInfoHandle);
        }
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

    public void setImageMatrix(Matrix matrix) {
        setTransform(matrix);
    }

    @Override
    public void setTransform(Matrix transform) {
        mTransform.set(transform);
        if (mThread != null && mThread.mGifInfoHandle != null) {
            updateTextureViewSize(mThread.mGifInfoHandle);
        }
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
        final int position = mFreezesAnimation && mThread != null ? mThread.getPosition() : 0;
        GifViewSavedState gifViewSavedState = new GifViewSavedState(super.onSaveInstanceState(), position);
        mSavedPosition = position;
        return gifViewSavedState;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        GifViewSavedState ss = (GifViewSavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mSavedPosition = ss.mPositions[0];
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
