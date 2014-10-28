package pl.droidsonroids.gif;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.StrictMode;
import android.os.SystemClock;
import android.widget.MediaController.MediaPlayerControl;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A {@link Drawable} which can be used to hold GIF images, especially animations.
 * Basic GIF metadata can be also obtained.
 *
 * @author koral--
 */
public class GifDrawable extends Drawable implements Animatable, MediaPlayerControl {
    static {
        System.loadLibrary("gif");
    }

    /**
     * Decodes a frame if needed.
     *
     * @param buffer   frame destination
     * @param gifFileInPtr GifInfo pointer
     * @return true if loop of the animation is completed
     */
    private native long renderFrame(Bitmap buffer, long gifFileInPtr);

    static native GifInfoHandle openFd(FileDescriptor fd, long offset, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openByteArray(byte[] bytes, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openDirectByteBuffer(ByteBuffer buffer, boolean justDecodeMetaData) throws GifIOException;

    private static native GifInfoHandle openStream(InputStream stream, boolean justDecodeMetaData) throws GifIOException;

    static native GifInfoHandle openFile(String filePath, boolean justDecodeMetaData) throws GifIOException;

    static native void free(long gifFileInPtr);

    private static native void reset(long gifFileInPtr);

    private static native void setSpeedFactor(long gifFileInPtr, float factor);

    private static native String getComment(long gifFileInPtr);

    static native int getLoopCount(long gifFileInPtr);

    static native int getDuration(long gifFileInPtr);

    private static native int getCurrentPosition(long gifFileInPtr);

    private static native void seekToTime(long gifFileInPtr, int pos, Bitmap buffer);

    private static native void seekToFrame(long gifFileInPtr, int frameNr, Bitmap buffer);

    private static native void saveRemainder(long gifFileInPtr);

    private static native void restoreRemainder(long gifFileInPtr);

    private static native long getAllocationByteCount(long gifFileInPtr);

    private static native int getNativeErrorCode(long gifFileInPtr);

    private volatile boolean mIsRunning = true;

    //private final int[] mMetaData = new int[5];//[w,h,imageCount,errorCode,post invalidation time]
    private final long mInputSourceLength;

    private float mSx = 1f;
    private float mSy = 1f;
    private boolean mApplyTransformation;
    private final Rect mDstRect = new Rect();

    /**
     * Paint used to draw on a Canvas
     */
    protected final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    /**
     * Frame buffer, holds current frame.
     */
    private final Bitmap mBuffer;
    private final GifInfoHandle mNativeInfoHandle;
    private final ConcurrentLinkedQueue<AnimationListener> mListeners = new ConcurrentLinkedQueue<>();

    private final Runnable mResetTask = new Runnable() {
        @Override
        public void run() {
            reset(mNativeInfoHandle.gifInfoPtr);
        }
    };

    private final Runnable mStartTask = new Runnable() {
        @Override
        public void run() {
            restoreRemainder(mNativeInfoHandle.gifInfoPtr);
            invalidateSelf();
        }
    };

    private final Runnable mSaveRemainderTask = new Runnable() {
        @Override
        public void run() {
            saveRemainder(mNativeInfoHandle.gifInfoPtr);
        }
    };

    private final Runnable mInvalidateTask = new Runnable() {
        @Override
        public void run() {
            invalidateSelf();
        }
    };

    private void runOnUiThread(Runnable task) {
        scheduleSelf(task, SystemClock.uptimeMillis());
    }

    /**
     * Creates drawable from resource.
     *
     * @param res Resources to read from
     * @param id  resource id
     * @throws NotFoundException    if the given ID does not exist.
     * @throws IOException          when opening failed
     * @throws NullPointerException if res is null
     */
    public GifDrawable(Resources res, int id) throws NotFoundException, IOException {
        this(res.openRawResourceFd(id));
    }

    /**
     * Creates drawable from asset.
     *
     * @param assets    AssetManager to read from
     * @param assetName name of the asset
     * @throws IOException          when opening failed
     * @throws NullPointerException if assets or assetName is null
     */
    public GifDrawable(AssetManager assets, String assetName) throws IOException {
        this(assets.openFd(assetName));
    }

    /**
     * Constructs drawable from given file path.<br>
     * Only metadata is read, no graphic data is decoded here.
     * In practice can be called from main thread. However it will violate
     * {@link StrictMode} policy if disk reads detection is enabled.<br>
     *
     * @param filePath path to the GIF file
     * @throws IOException          when opening failed
     * @throws NullPointerException if filePath is null
     */
    public GifDrawable(String filePath) throws IOException {
        this(openFile(filePath, false), new File(filePath).length());
    }

    /**
     * Equivalent to {@code} GifDrawable(file.getPath())}
     *
     * @param file the GIF file
     * @throws IOException          when opening failed
     * @throws NullPointerException if file is null
     */
    public GifDrawable(File file) throws IOException {
        this(openFile(file.getPath(), false), file.length());
    }

    /**
     * Creates drawable from InputStream.
     * InputStream must support marking, IllegalArgumentException will be thrown otherwise.
     *
     * @param stream stream to read from
     * @throws IOException              when opening failed
     * @throws IllegalArgumentException if stream does not support marking
     * @throws NullPointerException     if stream is null
     */
    public GifDrawable(InputStream stream) throws IOException {
        this(openMarkableInputStream(stream, false), -1L);
    }

    /**
     * Creates drawable from AssetFileDescriptor.
     * Convenience wrapper for {@link GifDrawable#GifDrawable(FileDescriptor)}
     *
     * @param afd source
     * @throws NullPointerException if afd is null
     * @throws IOException          when opening failed
     */
    public GifDrawable(AssetFileDescriptor afd) throws IOException {
        this(openAssetFileDescriptor(afd, false), afd.getLength());
    }

    /**
     * Creates drawable from FileDescriptor
     *
     * @param fd source
     * @throws IOException          when opening failed
     * @throws NullPointerException if fd is null
     */
    public GifDrawable(FileDescriptor fd) throws IOException {
        this(openFd(fd, 0, false), -1L);
    }

    /**
     * Creates drawable from byte array.<br>
     * It can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
     *
     * @param bytes raw GIF bytes
     * @throws IOException          if bytes does not contain valid GIF data
     * @throws NullPointerException if bytes are null
     */
    public GifDrawable(byte[] bytes) throws IOException {
        this(openByteArray(bytes, false), bytes.length);
    }

    /**
     * Creates drawable from {@link ByteBuffer}. Only direct buffers are supported.
     * Buffer can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
     *
     * @param buffer buffer containing GIF data
     * @throws IOException              if buffer does not contain valid GIF data
     * @throws IllegalArgumentException if buffer is indirect
     * @throws NullPointerException     if buffer is null
     */
    public GifDrawable(ByteBuffer buffer) throws IOException {
        this(openDirectByteBuffer(buffer, false), buffer.capacity());
    }

    /**
     * Creates drawable from {@link android.net.Uri} which is resolved using {@code resolver}.
     * {@link android.content.ContentResolver#openAssetFileDescriptor(android.net.Uri, String)}
     * is used to open an Uri.
     *
     * @param uri      GIF Uri, cannot be null.
     * @param resolver resolver, cannot be null.
     * @throws IOException if resolution fails or destination is not a GIF.
     */
    public GifDrawable(ContentResolver resolver, Uri uri) throws IOException {
        this(resolver.openAssetFileDescriptor(uri, "r"));
    }

    private GifDrawable(GifInfoHandle gifInfoHandle, long inputSourceLength) {
        mNativeInfoHandle = gifInfoHandle;
        mInputSourceLength = inputSourceLength;
        mBuffer = Bitmap.createBitmap(mNativeInfoHandle.width, mNativeInfoHandle.height, Bitmap.Config.ARGB_8888);
    }

    /**
     * Frees any memory allocated native way.
     * Operation is irreversible. After this call, nothing will be drawn.
     * This method is idempotent, subsequent calls have no effect.
     * Like {@link android.graphics.Bitmap#recycle()} this is an advanced call and
     * is invoked implicitly by finalizer.
     */
    public void recycle() {
        mIsRunning = false;
        long tmpPtr = mNativeInfoHandle.gifInfoPtr;
        mNativeInfoHandle.gifInfoPtr = 0L;
        mBuffer.recycle();
        free(tmpPtr);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            recycle();
        } finally {
            super.finalize();
        }
    }

    @Override
    public int getIntrinsicHeight() {
        return mNativeInfoHandle.height;
    }

    @Override
    public int getIntrinsicWidth() {
        return mNativeInfoHandle.width;
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
    }

    /**
     * See {@link Drawable#getOpacity()}
     *
     * @return always {@link PixelFormat#TRANSPARENT}
     */
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    /**
     * Starts the animation. Does nothing if GIF is not animated.
     * This method is thread-safe.
     */
    @Override
    public void start() {
        mIsRunning = true;
        runOnUiThread(mStartTask);
    }

    /**
     * Causes the animation to start over.
     * If animation is stopped any effects will occur after restart.<br>
     * If rewinding input source fails then state is not affected.
     * This method is thread-safe.
     */
    public void reset() {
        runOnUiThread(mResetTask);
    }

    /**
     * Stops the animation. Does nothing if GIF is not animated.
     * This method is thread-safe.
     */
    @Override
    public void stop() {
        mIsRunning = false;
        runOnUiThread(mSaveRemainderTask);
    }

    @Override
    public boolean isRunning() {
        return mIsRunning;
    }

    /**
     * Returns GIF comment
     *
     * @return comment or null if there is no one defined in file
     */
    public String getComment() {
        return getComment(mNativeInfoHandle.gifInfoPtr);
    }

    /**
     * Returns loop count previously read from GIF's application extension block.
     * Defaults to 0 (infinite loop) if there is no such extension.
     *
     * @return loop count, 0 means that animation is infinite
     */
    public int getLoopCount() {
        return getLoopCount(mNativeInfoHandle.gifInfoPtr);
    }

    /**
     * @return basic description of the GIF including size and number of frames
     */
    @Override
    public String toString() {
        return String.format(Locale.US, "GIF: size: %dx%d, frames: %d, error: %d", mNativeInfoHandle.width,
                mNativeInfoHandle.height, mNativeInfoHandle.imageCount,
                getNativeErrorCode(mNativeInfoHandle.gifInfoPtr));
    }

    /**
     * @return number of frames in GIF, at least one
     */
    public int getNumberOfFrames() {
        return mNativeInfoHandle.imageCount;
    }

    /**
     * Retrieves last error which is also the indicator of current GIF status.
     *
     * @return current error or {@link GifError#NO_ERROR} if there was no error or drawable is recycled
     */
    public GifError getError() {
        return GifError.fromCode(getNativeErrorCode(mNativeInfoHandle.gifInfoPtr));
    }

    /**
     * An {@link GifDrawable#GifDrawable(Resources, int)} wrapper but returns null
     * instead of throwing exception if creation fails.
     *
     * @param res        resources to read from
     * @param resourceId resource id
     * @return correct drawable or null if creation failed
     */
    public static GifDrawable createFromResource(Resources res, int resourceId) {
        try {
            return new GifDrawable(res, resourceId);
        } catch (IOException ignored) {
            //ignored
        }
        return null;
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
        if (factor <= 0f)
            throw new IllegalArgumentException("Speed factor is not positive");
        setSpeedFactor(mNativeInfoHandle.gifInfoPtr, factor);
    }

    /**
     * Equivalent of {@link #stop()}
     */
    @Override
    public void pause() {
        stop();
    }

    /**
     * Retrieves duration of one loop of the animation.
     * If there is no data (no Graphics Control Extension blocks) 0 is returned.
     * Note that one-frame GIFs can have non-zero duration defined in Graphics Control Extension block,
     * use {@link #getNumberOfFrames()} to determine if there is one or more frames.
     *
     * @return duration of of one loop the animation in milliseconds. Result is always multiple of 10.
     */
    @Override
    public int getDuration() {
        return getDuration(mNativeInfoHandle.gifInfoPtr);
    }

    /**
     * Retrieves elapsed time from the beginning of a current loop of animation.
     * If there is only 1 frame, 0 is returned.
     *
     * @return elapsed time from the beginning of a loop in ms
     */
    @Override
    public int getCurrentPosition() {
        return getCurrentPosition(mNativeInfoHandle.gifInfoPtr);
    }

    /**
     * Seeks animation to given absolute position (within given loop) and refreshes the canvas.<br>
     * <b>NOTE: only seeking forward is supported.</b><br>
     * If position is less than current position or GIF has only one frame then nothing happens.
     * If position is greater than duration of the loop of animation
     * (or whole animation if there is no loop) then animation will be sought to the end.<br>
     * NOTE: all frames from current to desired must be rendered sequentially to perform seeking.
     * It may take a lot of time if number of such frames is large.
     * This method can be called from any thread but actual work will be performed on UI thread.
     *
     * @param position position to seek to in milliseconds
     * @throws IllegalArgumentException if position&lt;0
     */
    @Override
    public void seekTo(final int position) {
        if (position < 0)
            throw new IllegalArgumentException("Position is not positive");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                seekToTime(mNativeInfoHandle.gifInfoPtr, position, mBuffer);
                invalidateSelf();
            }
        });
    }

    /**
     * Like {@link #seekToTime(long, int, android.graphics.Bitmap)} but uses index of the frame instead of time.
     *
     * @param frameIndex index of the frame to seek to (zero based)
     * @throws IllegalArgumentException if frameIndex&lt;0
     */
    public void seekToFrame(final int frameIndex) {
        if (frameIndex < 0)
            throw new IllegalArgumentException("frameIndex is not positive");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                seekToFrame(mNativeInfoHandle.gifInfoPtr, frameIndex, mBuffer);
                invalidateSelf();
            }
        });
    }

    /**
     * Equivalent of {@link #isRunning()}
     *
     * @return true if animation is running
     */
    @Override
    public boolean isPlaying() {
        return mIsRunning;
    }

    /**
     * Used by MediaPlayer for secondary progress bars.
     * There is no buffer in GifDrawable, so buffer is assumed to be always full.
     *
     * @return always 100
     */
    @Override
    public int getBufferPercentage() {
        return 100;
    }

    /**
     * Checks whether pause is supported.
     *
     * @return always true, even if there is only one frame
     */
    @Override
    public boolean canPause() {
        return true;
    }

    /**
     * Checks whether seeking backward can be performed.
     * Due to different frame disposal methods it is not supported now.
     *
     * @return always false
     */
    @Override
    public boolean canSeekBackward() {
        return false;
    }

    /**
     * Checks whether seeking forward can be performed.
     *
     * @return true if GIF has at least 2 frames
     */
    @Override
    public boolean canSeekForward() {
        return getNumberOfFrames() > 1;
    }

    /**
     * Used by MediaPlayer.
     * GIFs contain no sound, so 0 is always returned.
     *
     * @return always 0
     */
    @Override
    public int getAudioSessionId() {
        return 0;
    }

    /**
     * Returns the minimum number of bytes that can be used to store pixels of the single frame.
     * Returned value is the same for all the frames since it is based on the size of GIF screen.
     *
     * @return width * height (of the GIF screen ix pixels) * 4
     */
    public int getFrameByteCount() {
        return mNativeInfoHandle.width * mNativeInfoHandle.height * 4;
    }

    /**
     * Returns size of the allocated memory used to store pixels of this object.
     * It counts length of all frame buffers. Returned value does not change during runtime.
     *
     * @return size of the allocated memory used to store pixels of this object
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public long getAllocationByteCount() {
        long byteCount = getAllocationByteCount(mNativeInfoHandle.gifInfoPtr);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            byteCount += mBuffer.getAllocationByteCount();
        else
            byteCount += mBuffer.getRowBytes() * mBuffer.getWidth();
        return byteCount;
    }

    /**
     * Returns length of the input source obtained at the opening time or -1 if
     * length is unknown. Returned value does not change during runtime.
     * For GifDrawables constructed from {@link InputStream} and {@link FileDescriptor} -1 is always returned.
     * In case of {@link File}, file path, byte array and {@link ByteBuffer} length is always known.
     *
     * @return number of bytes backed by input source or -1 if it is unknown
     */
    public long getInputSourceByteCount() {
        return mInputSourceLength;
    }

    /**
     * Returns in pixels[] a copy of the data in the current frame. Each value is a packed int representing a {@link Color}.
     * @param pixels the array to receive the frame's colors
     * @throws ArrayIndexOutOfBoundsException if the pixels array is too small to receive required number of pixels
     */
    public void getPixels(int[] pixels) {
        mBuffer.getPixels(pixels, 0, mNativeInfoHandle.width, 0, 0, mNativeInfoHandle.width, mNativeInfoHandle.height);
    }

    /**
     * Returns the {@link Color} at the specified location. Throws an exception
     * if x or y are out of bounds (negative or &gt;= to the width or height
     * respectively). The returned color is a non-premultiplied ARGB value.
     *
     * @param x The x coordinate (0...width-1) of the pixel to return
     * @param y The y coordinate (0...height-1) of the pixel to return
     * @return The argb {@link Color} at the specified coordinate
     * @throws IllegalArgumentException if x, y exceed the drawable's bounds
     * @throws IllegalStateException if drawable is recycled
     */
    public int getPixel(int x, int y) {
        return mBuffer.getPixel(x, y);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mApplyTransformation = true;
    }

    /**
     * Reads and renders new frame if needed then draws last rendered frame.
     *
     * @param canvas canvas to draw into
     */
    @Override
    public void draw(Canvas canvas) {
        if (mApplyTransformation) {
            mDstRect.set(getBounds());
            mSx = (float) mDstRect.width() / mNativeInfoHandle.width;
            mSy = (float) mDstRect.height() / mNativeInfoHandle.height;
            mApplyTransformation = false;
        }
        if (mPaint.getShader() == null) {
            final int invalidationDelay;
            if (mIsRunning) {
                long renderResult = renderFrame(mBuffer, mNativeInfoHandle.gifInfoPtr);
                invalidationDelay = (int) (renderResult >> 1);
                if ((int) (renderResult & 1L) == 1)
                    for (AnimationListener listener : mListeners)
                        listener.onAnimationCompleted();
            } else
                invalidationDelay = -1;

            canvas.scale(mSx, mSy);
            canvas.drawBitmap(mBuffer, 0, 0, mPaint);

            if (invalidationDelay >= 0 && mNativeInfoHandle.imageCount > 1)
                scheduleSelf(mInvalidateTask, invalidationDelay);//TODO don't post if message for given frame was already posted
        } else
            canvas.drawRect(mDstRect, mPaint);
    }

    /**
     * @return the paint used to render this drawable
     */
    public final Paint getPaint() {
        return mPaint;
    }

    @Override
    public int getAlpha() {
        return mPaint.getAlpha();
    }

    @Override
    public void setFilterBitmap(boolean filter) {
        mPaint.setFilterBitmap(filter);
        invalidateSelf();
    }

    @Override
    public void setDither(boolean dither) {
        mPaint.setDither(dither);
        invalidateSelf();
    }

    @Override
    public int getMinimumHeight() {
        return mNativeInfoHandle.height;
    }

    @Override
    public int getMinimumWidth() {
        return mNativeInfoHandle.width;
    }

    /**
     * Adds a new animation listener
     *
     * @param listener animation listener to be added, not null
     * @throws java.lang.NullPointerException if listener is null
     */
    public void addAnimationListener(AnimationListener listener) {
        mListeners.add(listener);
    }

    /**
     * Removes an animation listener
     *
     * @param listener animation listener to be removed
     * @return true if listener collection has been modified
     */
    public boolean removeAnimationListener(AnimationListener listener) {
        return mListeners.remove(listener);
    }

    @Override
    public ColorFilter getColorFilter() {
        return mPaint.getColorFilter();
    }

    static GifInfoHandle openMarkableInputStream(InputStream stream, boolean justDecodeMetaData) throws GifIOException {
        if (!stream.markSupported())
            throw new IllegalArgumentException("InputStream does not support marking");
        return openStream(stream, justDecodeMetaData);
    }

    static GifInfoHandle openAssetFileDescriptor(AssetFileDescriptor afd, boolean justDecodeMetaData) throws IOException {
        try {
            return openFd(afd.getFileDescriptor(), afd.getStartOffset(), justDecodeMetaData);
        } catch (IOException ex) {
            afd.close();
            throw ex;
        }
    }
}