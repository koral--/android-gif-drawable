package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Build;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

/**
 * {@link SurfaceView} implementation which can display animated GIFs.
 * TODO XML example
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class GifSurfaceView extends SurfaceView {
    private RenderThread mThread;
    private int mSavedPosition;
    private GifDrawableBuilder.Source mSource;
    private boolean shouldSaveSource;
    private boolean freezesAnimation;
    private final SurfaceHolder.Callback mCallback = new SurfaceHolder.Callback() {
        @Override
        public synchronized void surfaceCreated(SurfaceHolder holder) {
            mThread = new RenderThread(holder, mSource, mSavedPosition);
            mThread.start();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            mThread.mDstRect.set(0, 0, width, height);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (mThread.mGifInfoHandle != null) {
                mSavedPosition = mThread.mGifInfoHandle.getCurrentPosition();
            } else {
                mSavedPosition = 0;
            }
            mThread.interrupt();
        }
    };

    public GifSurfaceView(Context context) {
        super(context);
    }

    public GifSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0, 0);
    }

    public GifSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public GifSurfaceView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs, defStyleAttr, defStyleRes);
    }

    private void init(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        final Pair<GifDrawableBuilder.Source, Boolean> initResult = GifViewUtils.initSurfaceView(this, attrs, defStyleAttr, defStyleRes);
        freezesAnimation = initResult.second;
        mSource = initResult.first;
        getHolder().addCallback(mCallback);
        shouldSaveSource = true;
    }

    private static class RenderThread extends Thread {
        private final SurfaceHolder mHolder;
        private final GifDrawableBuilder.Source mSource;
        private GifInfoHandle mGifInfoHandle;
        private final Rect mDstRect = new Rect();
        protected final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private Bitmap mBitmap;
        private final int mStartPosition;

        RenderThread(SurfaceHolder holder, GifDrawableBuilder.Source source, int startPosition) {
            mHolder = holder;
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
            mBitmap = Bitmap.createBitmap(mGifInfoHandle.width, mGifInfoHandle.height, Bitmap.Config.ARGB_8888);
            if (mStartPosition > 0) {
                mGifInfoHandle.seekToTime(mStartPosition, mBitmap);
            }

            while (!Thread.currentThread().isInterrupted()) {
                long preRenderTimestamp = SystemClock.elapsedRealtime();
                int sleepTime = (int) (mGifInfoHandle.renderFrame(mBitmap) >> 1);
                Canvas canvas = mHolder.lockCanvas();
                if (canvas == null) {
                    return;
                }
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                canvas.drawBitmap(mBitmap, null, mDstRect, mPaint);
                mHolder.unlockCanvasAndPost(canvas);
                final long ms = sleepTime - (SystemClock.elapsedRealtime() - preRenderTimestamp);
                if (ms > 0) {
                    try {
                        Thread.sleep(ms);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
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