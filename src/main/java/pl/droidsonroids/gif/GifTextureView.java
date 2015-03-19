package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;

import java.io.IOException;

/**
 * TODO javadoc and example
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class GifTextureView extends TextureView {

    private RenderThread mThread;
    private int mSavedPosition;
    private GifDrawableBuilder.Source mSource;
    private boolean freezesAnimation;
    private final SurfaceTextureListener mCallback = new SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mThread = new RenderThread(surface, mSource, mSavedPosition);
            mThread.start();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            mSavedPosition = mThread.getPosition();
            mThread.interrupt();
            return true;
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

    private void init(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        if (attrs != null && !isInEditMode()) {
            final TypedArray surfaceViewAttributes = getContext().obtainStyledAttributes(attrs, R.styleable.GifTextureView, defStyleAttr, defStyleRes);
            int resourceId = surfaceViewAttributes.getResourceId(R.styleable.GifTextureView_srcId, 0);
            String assetName = surfaceViewAttributes.getString(R.styleable.GifTextureView_srcAsset);
            String path = surfaceViewAttributes.getString(R.styleable.GifTextureView_srcPath);
            if (assetName != null) {
                mSource = new GifDrawableBuilder.AssetSource(getContext().getAssets(), assetName);
            } else if (path != null) {
                mSource = new GifDrawableBuilder.FileSource(path);
            } else {
                mSource = new GifDrawableBuilder.FileDescriptorSource(getContext().getResources(), resourceId);
            }
            surfaceViewAttributes.recycle();
            freezesAnimation = GifViewUtils.isFreezingAnimation(this, attrs, defStyleAttr, defStyleRes);
            setOpaque(surfaceViewAttributes.getBoolean(R.styleable.GifTextureView_isOpaque, true));
        }
        setSurfaceTextureListener(mCallback);
    }

    private class RenderThread extends Thread {
        private ImageView.ScaleType mScaleType = ImageView.ScaleType.CENTER_CROP;
        private final GifDrawableBuilder.Source mSource;
        private final SurfaceTexture mSurfaceTexture;
        private GifInfoHandle mGifInfoHandle;
        private int mPosition;
        private IOException mIOException;

        RenderThread(SurfaceTexture surfaceTexture, GifDrawableBuilder.Source source, int startPosition) {
            mSurfaceTexture = surfaceTexture;
            mSource = source;
            mPosition = startPosition;
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

            updateTextureViewSize();

            Surface surface = new Surface(mSurfaceTexture);
            mGifInfoHandle.bindSurface(surface, mPosition);
            synchronized (this) {
                mPosition = mGifInfoHandle.getCurrentPosition();
                mGifInfoHandle.recycle();
            }
        }

        synchronized int getPosition() {
            if (mGifInfoHandle == null || mGifInfoHandle.isRecycled())
                return mPosition;
            else {
                mGifInfoHandle.saveRemainder();
                return mGifInfoHandle.getCurrentPosition();
            }
        }

        IOException getException() {
            if (mGifInfoHandle == null)
                return mIOException;
            else
                return new GifIOException(mGifInfoHandle.getNativeErrorCode());
        }

        private void updateTextureViewSize() { //TODO support more scaletypes

            float pivotPointX = 0;
            float pivotPointY = 0;
            final float viewWidth = getWidth();
            final float viewHeight = getHeight();

            float scaleX = 1.0f;
            float scaleY = 1.0f;
            final Matrix transform = getTransform(null);

            switch (mScaleType) {
                case CENTER:
                    pivotPointX = viewWidth / 2;
                    pivotPointY = viewHeight / 2;
                    scaleX = mGifInfoHandle.width / viewWidth;
                    scaleY = mGifInfoHandle.height / viewHeight;
                    transform.setScale(scaleX, scaleY, pivotPointX, pivotPointY);
                    break;
                case CENTER_CROP:
                    pivotPointX = viewWidth / 2;
                    pivotPointY = viewHeight / 2;
                    scaleX = mGifInfoHandle.width / viewWidth;
                    scaleY = mGifInfoHandle.height / viewHeight;
                    float refScale = 1 / Math.min(scaleX, scaleY);
                    transform.setScale(refScale * scaleX, refScale * scaleY, pivotPointX, pivotPointY);
                    break;
                case CENTER_INSIDE:
                    break;
                case FIT_CENTER:
                    break;
                case FIT_END:
                    break;
                case FIT_START:
                    break;
                case FIT_XY:
                    return;
                case MATRIX:
                    break;
            }

            post(new Runnable() {
                @Override
                public void run() {
                    setTransform(transform);
                }
            });
        }
    }

    /**
     * Sets the source of the animation.
     *
     * @param source new animation source
     */
    public synchronized void setSource(GifDrawableBuilder.Source source) {
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
        if (mThread != null && mThread.mGifInfoHandle != null)
            mThread.mGifInfoHandle.setSpeedFactor(factor);
    }

    /**
     * Returns {@link IOException} occurred during loading or playing GIF (in such case only {@link GifIOException}
     * can be returned. Null is returned when source is not set or surface was not yet created.
     * In case of no error {@link GifIOException} with {@link GifError#NO_ERROR} code is returned.
     *
     * @return exception occurred during loading or playing GIF or null
     */
    public IOException getIOException() {
        if (mThread != null)
            return mThread.getException();
        return null;
    }

    public void setScaleType(ImageView.ScaleType scaleType) {
        Matrix transform = getTransform(null);
        transform.postScale(0.2f, 0.2f);
        setTransform(transform);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final int position = mThread.getPosition();
        GifViewSavedState gifViewSavedState = new GifViewSavedState(super.onSaveInstanceState(), freezesAnimation ? position : 0);
        mSavedPosition = position;
        return gifViewSavedState;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        GifViewSavedState ss = (GifViewSavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mSavedPosition = ss.mPositions[0];
    }
}
