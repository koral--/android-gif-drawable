package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;

import java.io.IOException;

/**
 * TODO javadoc and example
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class GifTextureView extends TextureView {

    private RenderThread mThread;
    private int mSavedPosition;
    private GifDrawableBuilder.Source mSource;
    private boolean shouldSaveSource;
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
            if (mThread.mGifInfoHandle != null) {
                mSavedPosition = mThread.mGifInfoHandle.getCurrentPosition();
            } else {
                mSavedPosition = 0;
            }
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
            final TypedArray surfaceViewAttributes = getContext().obtainStyledAttributes(attrs, R.styleable.GifView, defStyleAttr, defStyleRes);
            int resourceId = surfaceViewAttributes.getResourceId(R.styleable.GifView_srcId, 0);
            String assetName = surfaceViewAttributes.getString(R.styleable.GifView_srcAsset);
            String path = surfaceViewAttributes.getString(R.styleable.GifView_srcPath);
            if (assetName != null) {
                mSource = new GifDrawableBuilder.AssetSource(getContext().getAssets(), assetName);
            } else if (path != null) {
                mSource = new GifDrawableBuilder.FileSource(path);
            } else {
                mSource = new GifDrawableBuilder.FileDescriptorSource(getContext().getResources(), resourceId);
            }
            surfaceViewAttributes.recycle();
            freezesAnimation = GifViewUtils.isFreezingAnimation(this, attrs, defStyleAttr, defStyleRes);
        }
        setSurfaceTextureListener(mCallback);
        shouldSaveSource = true;
    }

    private static class RenderThread extends Thread {
        private final GifDrawableBuilder.Source mSource;
        private final SurfaceTexture mSurfaceTexture;
        private GifInfoHandle mGifInfoHandle;
        private final int mStartPosition;

        RenderThread(SurfaceTexture surfaceTexture, GifDrawableBuilder.Source source, int startPosition) {
            mSurfaceTexture = surfaceTexture;
            mSource = source;
            mStartPosition = startPosition;
        }

        @Override
        public void run() {
            if (mSource == null) {
                return;
            }
            try {
                mGifInfoHandle = mSource.open();
            } catch (IOException ignored) {
                return;
            }
            Surface surface = new Surface(mSurfaceTexture);
            mGifInfoHandle.bindSurface(surface, mStartPosition);
            mGifInfoHandle.recycle();
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
        shouldSaveSource = false;
    }

    public void setSpeed(float speedFactor) {
        if (mThread != null && mThread.mGifInfoHandle != null)
            mThread.mGifInfoHandle.setSpeedFactor(speedFactor);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final GifInfoHandle gifInfoHandle = freezesAnimation && shouldSaveSource ? mThread.mGifInfoHandle : null;
        GifViewSavedState gifViewSavedState = new GifViewSavedState(super.onSaveInstanceState(), gifInfoHandle);
        mSavedPosition = gifViewSavedState.mPositions[0];
        return gifViewSavedState;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        GifViewSavedState ss = (GifViewSavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mSavedPosition = ss.mPositions[0];
    }
}
