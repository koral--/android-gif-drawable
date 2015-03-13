package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
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
        final Pair<GifDrawableBuilder.Source, Boolean> initResult = GifViewUtils.initSurfaceView(this, attrs, defStyleAttr, defStyleRes);
        freezesAnimation = initResult.second;
        mSource = initResult.first;
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
            } catch (IOException e) {
                return;
            }
            if (mStartPosition > 0) {
                //TODO mGifInfoHandle.seekToTime(mStartPosition, mBitmap);
            }
            Surface surface = new Surface(mSurfaceTexture);
            while (!Thread.currentThread().isInterrupted()) {
                int sleepTime = (int) (mGifInfoHandle.renderSurface(surface) >> 1);
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
            surface.release();
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
