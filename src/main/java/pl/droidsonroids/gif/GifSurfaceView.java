package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

/**
 * {@link SurfaceView} implementation which can display animated GIFs.
 * TODO XML example
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class GifSurfaceView extends SurfaceView {
    public static final String SUPER_STATE = "super_state";
    public static final String POSITION = "position";
    private RenderThread mThread;
    private int mSavedPosition;
    private GifDrawableBuilder.Source mSource;
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
            if (mThread.mGifInfoHandle != null)
                mSavedPosition = mThread.mGifInfoHandle.getCurrentPosition();
            else
                mSavedPosition = 0;
            mThread.interrupt();
        }
    };

    public GifSurfaceView(Context context) {
        super(context);
    }

    public GifSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public GifSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public GifSurfaceView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        final TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.GifSurfaceView);
        int resourceId = typedArray.getResourceId(R.styleable.GifSurfaceView_srcId, 0);
        String assetName = typedArray.getString(R.styleable.GifSurfaceView_srcAsset);
        String path = typedArray.getString(R.styleable.GifSurfaceView_srcPath);
        if (assetName != null)
            mSource = new GifDrawableBuilder.AssetSource(getContext().getAssets(), assetName);
        else if (path != null)
            mSource = new GifDrawableBuilder.FileSource(path);
        else
            mSource = new GifDrawableBuilder.FileDescriptorSource(getContext().getResources(), resourceId);
        typedArray.recycle();
        getHolder().addCallback(mCallback);
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
            try {
                mGifInfoHandle = mSource.open();
            } catch (IOException e) {
                return;
            }
            mBitmap = Bitmap.createBitmap(mGifInfoHandle.width, mGifInfoHandle.height, Bitmap.Config.ARGB_8888);
            if (mStartPosition > 0)
                mGifInfoHandle.seekToTime(mStartPosition, mBitmap);

            while (true) {
                int sleepTime = (int) (mGifInfoHandle.renderFrame(mBitmap) >> 1);
                long postRenderTime = SystemClock.elapsedRealtime();
                Canvas canvas = mHolder.lockCanvas();
                if (canvas == null) {
                    return;
                }
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                canvas.drawBitmap(mBitmap, null, mDstRect, mPaint);
                mHolder.unlockCanvasAndPost(canvas);
                final long ms = sleepTime - (SystemClock.elapsedRealtime() - postRenderTime);
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
     * Sets the source of the animation. Not that it will not be persisted when saving instance state.
     * @param source new animation source.
     */
    public synchronized void setSource(GifDrawableBuilder.Source source) {
        int oldVisibility = getVisibility();
        setVisibility(INVISIBLE); //dirty hack to force surface recreation
        mSource = source;
        mSavedPosition = 0;
        setVisibility(oldVisibility);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        Bundle bundle = (Bundle) state;
        super.onRestoreInstanceState(bundle.getParcelable(SUPER_STATE));
        mSavedPosition = bundle.getInt(POSITION);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle state = new Bundle();
        state.putParcelable(SUPER_STATE, super.onSaveInstanceState());
        if (mThread.mGifInfoHandle != null)
            mSavedPosition = mThread.mGifInfoHandle.getCurrentPosition();
        else
            mSavedPosition = 0;
        state.putInt(POSITION, mSavedPosition);
        return state;
    }
}