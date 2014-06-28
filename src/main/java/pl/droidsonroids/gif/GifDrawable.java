package pl.droidsonroids.gif;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.MediaController.MediaPlayerControl;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link Drawable} which can be used to hold GIF images, especially animations.
 * Basic GIF metadata can be also obtained.
 *
 * @author koral--
 */
public class GifDrawable extends Drawable implements Animatable, MediaPlayerControl
{
    static
    {
        System.loadLibrary("gif");
    }

    private static final RejectedExecutionHandler REJECTED_EXECUTION_HANDLER = new ThreadPoolExecutor.DiscardPolicy();

    //TODO fill constructor javadocs
//TODO correct allocatted byte count descriptions?

    /**
     * Like {@link #GifDrawable(android.content.res.Resources, int)} but additionally offers drawable
     * reusing.
     *
     * @param res        Resources to read from
     * @param id         resource id
     * @param inDrawable drawable to be reused, can be null or recycled. If not null, not recycled
     *                   and its dimensions are not smaller than those from constructed instance then
     *                   its frame buffer will be reused instead of allocation new one.
     *                   If buffer is reused then <code>inDrawable</code> is recycled.
     * @throws android.content.res.Resources.NotFoundException if the given ID does not exist.
     * @throws java.io.IOException                             when opening failed
     * @throws NullPointerException                            if res is null
     */
    public GifDrawable(Resources res, int id, GifDrawable inDrawable) throws Resources.NotFoundException, IOException
    {
        this(res.openRawResourceFd(id), inDrawable);
    }

    public GifDrawable(AssetManager assets, String assetName, GifDrawable inDrawable) throws IOException
    {
        this(assets.openFd(assetName), inDrawable);
    }

    public GifDrawable(String filePath, GifDrawable inDrawable) throws IOException
    {
        if (filePath == null)
            throw new NullPointerException("Source is null");
        mInputSourceLength = new File(filePath).length();
        mGifInfoPtr = openFile(mMetaData, filePath);
        init(inDrawable);
    }

    public GifDrawable(File file, GifDrawable inDrawable) throws IOException
    {
        if (file == null)
            throw new NullPointerException("Source is null");
        mInputSourceLength = file.length();
        mGifInfoPtr = openFile(mMetaData, file.getPath());
        init(inDrawable);
    }

    public GifDrawable(InputStream stream, GifDrawable inDrawable) throws IOException
    {
        if (stream == null)
            throw new NullPointerException("Source is null");
        if (!stream.markSupported())
            throw new IllegalArgumentException("InputStream does not support marking");
        mGifInfoPtr = openStream(mMetaData, stream);
        init(inDrawable);
        mInputSourceLength = -1L;
    }

    public GifDrawable(AssetFileDescriptor afd, GifDrawable inDrawable) throws IOException
    {
        if (afd == null)
            throw new NullPointerException("Source is null");
        FileDescriptor fd = afd.getFileDescriptor();
        try
        {
            mGifInfoPtr = openFd(mMetaData, fd, afd.getStartOffset());
        } catch (IOException ex)
        {
            afd.close();
            throw ex;
        }
        init(inDrawable);
        mInputSourceLength = afd.getLength();
    }

    public GifDrawable(FileDescriptor fd, GifDrawable inDrawable) throws IOException
    {
        if (fd == null)
            throw new NullPointerException("Source is null");
        mGifInfoPtr = openFd(mMetaData, fd, 0);
        init(inDrawable);
        mInputSourceLength = -1L;
    }

    public GifDrawable(byte[] bytes, GifDrawable inDrawable) throws IOException
    {
        if (bytes == null)
            throw new NullPointerException("Source is null");
        mGifInfoPtr = openByteArray(mMetaData, bytes);
        init(inDrawable);
        mInputSourceLength = bytes.length;
    }

    public GifDrawable(ByteBuffer buffer, GifDrawable inDrawable) throws IOException
    {
        if (buffer == null)
            throw new NullPointerException("Source is null");
        if (!buffer.isDirect())
            throw new IllegalArgumentException("ByteBuffer is not direct");
        mGifInfoPtr = openDirectByteBuffer(mMetaData, buffer);
        init(inDrawable);
        mInputSourceLength = buffer.capacity();
    }

    public GifDrawable(ContentResolver resolver, Uri uri, GifDrawable inDrawable) throws IOException
    {
        this(resolver.openAssetFileDescriptor(uri, "r"), inDrawable);
    }


    private static native void renderFrame(int[] pixels, int gifFileInPtr, int[] metaData);

    private static native int openFd(int[] metaData, FileDescriptor fd, long offset) throws GifIOException;

    private static native int openByteArray(int[] metaData, byte[] bytes) throws GifIOException;

    private static native int openDirectByteBuffer(int[] metaData, ByteBuffer buffer) throws GifIOException;

    private static native int openStream(int[] metaData, InputStream stream) throws GifIOException;

    private static native int openFile(int[] metaData, String filePath) throws GifIOException;

    private static native void free(int gifFileInPtr);

    private static native void reset(int gifFileInPtr);

    private static native void setSpeedFactor(int gifFileInPtr, float factor);

    private static native String getComment(int gifFileInPtr);

    private static native int getLoopCount(int gifFileInPtr);

    private static native int getDuration(int gifFileInPtr);

    private static native int getCurrentPosition(int gifFileInPtr);

    private static native void seekToTime(int gifFileInPtr, int pos, int[] pixels);

    private static native void seekToFrame(int gifFileInPtr, int frameNr, int[] pixels);

    private static native void saveRemainder(int gifFileInPtr);

    private static native void restoreRemainder(int gifFileInPtr);

    private static native long getAllocationByteCount(int gifFileInPtr);

    private volatile int mGifInfoPtr;
    private final AtomicBoolean mIsRunning = new AtomicBoolean(true);

    private final int[] mMetaData = new int[5];//[w,h,imageCount,errorCode,post invalidation time]
    private final long mInputSourceLength;

    private float mSx = 1f;
    private float mSy = 1f;
    private boolean mApplyTransformation;
    private final Rect mDstRect = new Rect();
    private int[] tempDecoded; //TODO add recycling and reusing
    private final AtomicBoolean mIsRedrawNeeded=new AtomicBoolean();

    /**
     * Paint used to draw on a Canvas
     */
    protected final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    /**
     * Frame buffer, holds current frame.
     * Each element is a packed int representing a {@link Color} at the given pixel.
     */
    private int[] mColors;

    private final ExecutorService mExecutor = new ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(), REJECTED_EXECUTION_HANDLER);

    private final Runnable mResetTask = new Runnable()
    {
        @Override
        public void run()
        {
            reset(mGifInfoPtr);
        }
    };

    private final Runnable mStartTask = new Runnable()
    {
        @Override
        public void run()
        {
            restoreRemainder(mGifInfoPtr);
        }
    };

    private final Runnable mStopTask = new Runnable()
    {
        @Override
        public void run()
        {
            saveRemainder(mGifInfoPtr);
        }
    };

    private final Runnable mInvalidateTask = new Runnable()
    {
        @Override
        public void run()
        {
            invalidateSelf();
        }
    };

    private final Runnable mRedrawFlagSetter = new Runnable()
    {
        @Override
        public void run()
        {
            mIsRedrawNeeded.set(true);
            invalidateSelf();
        }
    };

    private final Runnable mDecoderTask = new Runnable()
    {

        @Override
        public void run()
        {
            if (!mIsRunning.get())
                return;
            final int[] colorsIn = mColors;
            if (colorsIn == null) // In case recycle() was called here
                return;
            renderFrame(tempDecoded, mGifInfoPtr, mMetaData);
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (colorsIn)
            {
                System.arraycopy(tempDecoded, 0, colorsIn, 0, tempDecoded.length);
            }
            postInvalidate();
            if (mMetaData[4] > 0&&mIsRunning.get())
            {
                scheduleSelf(mRedrawFlagSetter, SystemClock.uptimeMillis() + mMetaData[4]);
            }
        }
    };

    private void postTask(Runnable task)
    {
        mExecutor.execute(task);
    }

    private void postInvalidate()
    {
        scheduleSelf(mInvalidateTask, SystemClock.uptimeMillis());
    }

    /**
     * Creates drawable from resource.
     *
     * @param res Resources to read from
     * @param id  resource id
     * @throws android.content.res.Resources.NotFoundException if the given ID does not exist.
     * @throws java.io.IOException                             when opening failed
     * @throws NullPointerException                            if res is null
     */
    public GifDrawable(Resources res, int id) throws Resources.NotFoundException, IOException
    {
        this(res, id, null);
    }

    /**
     * Creates drawable from asset.
     *
     * @param assets    AssetManager to read from
     * @param assetName name of the asset
     * @throws java.io.IOException  when opening failed
     * @throws NullPointerException if assets or assetName is null
     */
    public GifDrawable(AssetManager assets, String assetName) throws IOException
    {
        this(assets, assetName, null);
    }

    /**
     * Constructs drawable from given file path.<br>
     * In practice can be called from main thread. However it will violate
     * {@link android.os.StrictMode} policy if disk reads detection is enabled.<br>
     *
     * @param filePath path to the GIF file
     * @throws java.io.IOException  when opening failed
     * @throws NullPointerException if filePath is null
     */
    public GifDrawable(String filePath) throws IOException
    {
        this(filePath, null);
    }

    /**
     * Equivalent to {@code} GifDrawable(file.getPath())}
     *
     * @param file the GIF file
     * @throws java.io.IOException  when opening failed
     * @throws NullPointerException if file is null
     */
    public GifDrawable(File file) throws IOException
    {
        this(file, null);
    }

    /**
     * Creates drawable from InputStream.
     * InputStream must support marking, IllegalArgumentException will be thrown otherwise.
     *
     * @param stream stream to read from
     * @throws java.io.IOException      when opening failed
     * @throws IllegalArgumentException if stream does not support marking
     * @throws NullPointerException     if stream is null
     */
    public GifDrawable(InputStream stream) throws IOException
    {
        this(stream, null);
    }

    /**
     * Creates drawable from AssetFileDescriptor.
     * Convenience wrapper for {@link pl.droidsonroids.gif.GifDrawable#GifDrawable(FileDescriptor)}
     *
     * @param afd source
     * @throws NullPointerException if afd is null
     * @throws java.io.IOException  when opening failed
     */
    public GifDrawable(AssetFileDescriptor afd) throws IOException
    {
        this(afd, null);
    }

    /**
     * Creates drawable from FileDescriptor
     *
     * @param fd source
     * @throws java.io.IOException  when opening failed
     * @throws NullPointerException if fd is null
     */
    public GifDrawable(FileDescriptor fd) throws IOException
    {
        this(fd, null);
    }

    /**
     * Creates drawable from byte array.<br>
     * It can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
     *
     * @param bytes raw GIF bytes
     * @throws java.io.IOException  if bytes does not contain valid GIF data
     * @throws NullPointerException if bytes are null
     */
    public GifDrawable(byte[] bytes) throws IOException
    {
        this(bytes, null);
    }

    /**
     * Creates drawable from {@link java.nio.ByteBuffer}. Only direct buffers are supported.
     * Buffer can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
     *
     * @param buffer buffer containing GIF data
     * @throws java.io.IOException      if buffer does not contain valid GIF data
     * @throws IllegalArgumentException if buffer is indirect
     * @throws NullPointerException     if buffer is null
     */
    public GifDrawable(ByteBuffer buffer) throws IOException
    {
        this(buffer, null);
    }

    /**
     * Creates drawable from {@link android.net.Uri} which is resolved using {@code resolver}.
     * {@link android.content.ContentResolver#openAssetFileDescriptor(android.net.Uri, String)}
     * is used to open an Uri.
     *
     * @param resolver resolver, cannot be null.
     * @param uri      GIF Uri, cannot be null.
     * @throws java.io.IOException if resolution fails or destination is not a GIF.
     */
    public GifDrawable(ContentResolver resolver, Uri uri) throws IOException
    {
        this(resolver, uri, null);
    }

    private void init(GifDrawable inDrawable)
    {
        if (inDrawable != null)
        {
            final int[] inColors = inDrawable.mColors;
            if (inColors != null && inColors.length >= mMetaData[0] * mMetaData[1])
            {
                inDrawable.recycle();
                mColors = inColors;
            }
        }
        if (mColors == null)
            mColors = new int[mMetaData[0] * mMetaData[1]];
        tempDecoded = new int[mMetaData[0] * mMetaData[1]];
        postTask(mDecoderTask);
    }

    /**
     * Frees any memory allocated native way.
     * Operation is irreversible. After this call, nothing will be drawn.
     * This method is idempotent, subsequent calls have no effect.
     * Like {@link android.graphics.Bitmap#recycle()} this is an advanced call and
     * is invoked implicitly by finalizer.
     */
    public void recycle()
    {
        mIsRedrawNeeded.set(false);
        mIsRunning.set(false);
        unscheduleSelf(mRedrawFlagSetter);
        mExecutor.shutdown();
        int tmpPtr = mGifInfoPtr;
        mGifInfoPtr = 0;
        mColors = null;
        free(tmpPtr);
    }

    @Override
    protected void finalize() throws Throwable
    {
        try
        {
            recycle();
        } finally
        {
            super.finalize();
        }
    }

    @Override
    public int getIntrinsicHeight()
    {
        return mMetaData[1];
    }

    @Override
    public int getIntrinsicWidth()
    {
        return mMetaData[0];
    }

    @Override
    public void setAlpha(int alpha)
    {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf)
    {
        mPaint.setColorFilter(cf);
    }

    /**
     * See {@link Drawable#getOpacity()}
     *
     * @return always {@link PixelFormat#TRANSPARENT}
     */
    @Override
    public int getOpacity()
    {
        return PixelFormat.TRANSPARENT;
    }

    /**
     * Starts the animation. Does nothing if GIF is not animated.
     * This method is thread-safe.
     */
    @Override
    public void start()
    {
        if (!mIsRunning.compareAndSet(false,true))
            return;
        postTask(mStartTask);
        postTask(mDecoderTask);
    }

    /**
     * Causes the animation to start over.
     * If animation is stopped any effects will occur after restart.<br>
     * If rewinding input source fails then state is not affected.
     * This method is thread-safe.
     */
    public void reset()
    {
        postTask(mResetTask);
    }

    /**
     * Stops the animation. Does nothing if GIF is not animated.
     * This method is thread-safe.
     */
    @Override
    public void stop()
    {
        if (!mIsRunning.compareAndSet(true,false))
            return;
        mIsRedrawNeeded.set(false);
        postTask(mStopTask);
    }

    @Override
    public boolean isRunning()
    {
        return mIsRunning.get();
    }

    /**
     * Returns GIF comment
     *
     * @return comment or null if there is no one defined in file
     */
    public String getComment()
    {
        return getComment(mGifInfoPtr);
    }

    /**
     * Returns loop count previously read from GIF's application extension block.
     * Defaults to 0 (infinite loop) if there is no such extension.
     *
     * @return loop count, 0 means infinite loop, 1 means one repetition (animation is played twice) etc.
     */
    public int getLoopCount()
    {
        return getLoopCount(mGifInfoPtr);
    }

    /**
     * @return basic description of the GIF including size and number of frames
     */
    @Override
    public String toString()
    {
        return String.format(Locale.US, "Size: %dx%d, %d frames, error: %d", mMetaData[0], mMetaData[1], mMetaData[2], mMetaData[3]);
    }

    /**
     * @return number of frames in GIF, at least one
     */
    public int getNumberOfFrames()
    {
        return mMetaData[2];
    }

    /**
     * Retrieves last error which is also the indicator of current GIF status.
     *
     * @return current error or {@link GifError#NO_ERROR} if there was no error
     */
    public GifError getError()
    {
        return GifError.fromCode(mMetaData[3]);
    }

    /**
     * An {@link GifDrawable#GifDrawable(Resources, int)} wrapper but returns null
     * instead of throwing exception if creation fails.
     *
     * @param res        resources to read from
     * @param resourceId resource id
     * @return correct drawable or null if creation failed
     */
    public static GifDrawable createFromResource(Resources res, int resourceId)
    {
        try
        {
            return new GifDrawable(res, resourceId);
        } catch (IOException ignored)
        {
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
    public void setSpeed(float factor)
    {
        if (factor <= 0f)
            throw new IllegalArgumentException("Speed factor is not positive");
        setSpeedFactor(mGifInfoPtr, factor);
    }

    /**
     * Equivalent of {@link #stop()}
     */
    @Override
    public void pause()
    {
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
    public int getDuration()
    {
        return getDuration(mGifInfoPtr);
    }

    /**
     * Retrieves elapsed time from the beginning of a current loop of animation.
     * If there is only 1 frame, 0 is returned.
     *
     * @return elapsed time from the beginning of a loop in ms
     */
    @Override
    public int getCurrentPosition()
    {
        return getCurrentPosition(mGifInfoPtr);
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
    public void seekTo(final int position)
    {
        if (position < 0)
            throw new IllegalArgumentException("Position is not positive");
        postTask(new Runnable()
        {
            @Override
            public void run()
            {
                seekToTime(mGifInfoPtr, position, mColors);
                postInvalidate();
            }
        });
    }

    /**
     * Like {@link #seekToTime(int, int, int[])} but uses index of the frame instead of time.
     *
     * @param frameIndex index of the frame to seek to (zero based)
     * @throws IllegalArgumentException if frameIndex&lt;0
     */
    public void seekToFrame(final int frameIndex)
    {
        if (frameIndex < 0)
            throw new IllegalArgumentException("frameIndex is not positive");
        postTask(new Runnable()
        {
            @Override
            public void run()
            {
                seekToFrame(mGifInfoPtr, frameIndex, mColors);
                postInvalidate();
            }
        });
    }

    /**
     * Equivalent of {@link #isRunning()}
     *
     * @return true if animation is running
     */
    @Override
    public boolean isPlaying()
    {
        return mIsRunning.get();
    }

    /**
     * Used by MediaPlayer for secondary progress bars.
     * There is no buffer in GifDrawable, so buffer is assumed to be always full.
     *
     * @return always 100
     */
    @Override
    public int getBufferPercentage()
    {
        return 100;
    }

    /**
     * Checks whether pause is supported.
     *
     * @return always true, even if there is only one frame
     */
    @Override
    public boolean canPause()
    {
        return true;
    }

    /**
     * Checks whether seeking backward can be performed.
     * Due to different frame disposal methods it is not supported now.
     *
     * @return always false
     */
    @Override
    public boolean canSeekBackward()
    {
        return false;
    }

    /**
     * Checks whether seeking forward can be performed.
     *
     * @return true if GIF has at least 2 frames
     */
    @Override
    public boolean canSeekForward()
    {
        return getNumberOfFrames() > 1;
    }

    /**
     * Used by MediaPlayer.
     * GIFs contain no sound, so 0 is always returned.
     *
     * @return always 0
     */
    @Override
    public int getAudioSessionId()
    {
        return 0;
    }

    /**
     * Returns the minimum number of bytes that can be used to store pixels of the single frame.
     * Returned value is the same for all the frames since it is based on the size of GIF screen.
     *
     * @return width * height (of the GIF screen ix pixels) * 4
     */
    public int getFrameByteCount()
    {
        return mMetaData[0] * mMetaData[1] * 4;
    }

    /**
     * Returns size of the allocated memory used to store pixels of this object.
     * It counts length of all frame buffers. Returned value does not change during runtime.
     *
     * @return size of the allocated memory used to store pixels of this object
     */
    public long getAllocationByteCount()
    {
        long nativeSize = getAllocationByteCount(mGifInfoPtr);
        final int[] colors = mColors;
        if (colors == null)
            return nativeSize;
        return nativeSize + mMetaData[0] * mMetaData[1] * 4L;
    }

    /**
     * Returns length of the input source obtained at the opening time or -1 if
     * length is unknown. Returned value does not change during runtime.
     * For GifDrawables constructed from {@link InputStream} and {@link FileDescriptor} -1 is always returned.
     * In case of {@link File}, file path, byte array and {@link ByteBuffer} length is always known.
     *
     * @return number of bytes backed by input source or -1 if it is unknown
     */
    public long getInputSourceByteCount()
    {
        return mInputSourceLength;
    }

    /**
     * Returns in pixels[] a copy of the data in the current frame. Each value is a packed int representing a {@link Color}.
     * If GifDrawable is recycled pixels[] is left unchanged.
     *
     * @param pixels the array to receive the frame's colors
     * @throws ArrayIndexOutOfBoundsException if the pixels array is too small to receive required number of pixels
     */
    public void getPixels(int[] pixels)
    {
        final int[] colors = mColors;
        if (colors == null)
            return;
        if (pixels.length < mMetaData[0] * mMetaData[1])
            throw new ArrayIndexOutOfBoundsException("Pixels array is too small. Required length: " + mMetaData[0] * mMetaData[1]);
        System.arraycopy(colors, 0, pixels, 0, mMetaData[0] * mMetaData[1]);
    }

    /**
     * Returns the {@link Color} at the specified location. Throws an exception
     * if x or y are out of bounds (negative or &gt;= to the width or height
     * respectively). The returned color is a non-premultiplied ARGB value.
     *
     * @param x The x coordinate (0...width-1) of the pixel to return
     * @param y The y coordinate (0...height-1) of the pixel to return
     * @return The argb {@link Color} at the specified coordinate
     * @throws IllegalArgumentException if x, y exceed the drawable's bounds or drawable is recycled
     */
    public int getPixel(int x, int y)
    {
        if (x < 0)
            throw new IllegalArgumentException("x must be >= 0");
        if (y < 0)
            throw new IllegalArgumentException("y must be >= 0");
        if (x >= mMetaData[0])
            throw new IllegalArgumentException("x must be < GIF width");
        if (y >= mMetaData[1])
            throw new IllegalArgumentException("y must be < GIF height");
        final int[] colors = mColors;
        if (colors == null)
            throw new IllegalArgumentException("GifDrawable is recycled");
        return colors[mMetaData[1] * y + x];
    }

    @Override
    protected void onBoundsChange(Rect bounds)
    {
        super.onBoundsChange(bounds);
        mApplyTransformation = true;
    }

    /**
     * Reads and renders new frame if needed then draws last rendered frame.
     *
     * @param canvas canvas to draw into
     */
    @Override
    public void draw(Canvas canvas)
    {
        if (mApplyTransformation)
        {
            mDstRect.set(getBounds());
            mSx = (float) mDstRect.width() / mMetaData[0];
            mSy = (float) mDstRect.height() / mMetaData[1];
            mApplyTransformation = false;
        }
        if (mIsRedrawNeeded.compareAndSet(true,false)&&mIsRunning.get())
        {
            postTask(mDecoderTask);
        }
        if (mPaint.getShader() == null)
        {
            canvas.scale(mSx, mSy);
            final int[] colors = mColors;
            if (colors != null)
            {
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (colors)
                {
                    canvas.drawRect(0f, 0f, mMetaData[0], mMetaData[1], mPaint);
                    canvas.drawBitmap(colors, 0, mMetaData[0], 0f, 0f, mMetaData[0], mMetaData[1], true, mPaint);
                }
            }
        } else
        {
            canvas.drawRect(mDstRect, mPaint);
        }
    }

    /**
     * @return the paint used to render this drawable
     */
    public final Paint getPaint()
    {
        return mPaint;
    }

    @Override
    public int getAlpha()
    {
        return mPaint.getAlpha();
    }

    @Override
    public void setFilterBitmap(boolean filter)
    {
        mPaint.setFilterBitmap(filter);
        invalidateSelf();
    }

    @Override
    public void setDither(boolean dither)
    {
        mPaint.setDither(dither);
        invalidateSelf();
    }

    @Override
    public int getMinimumHeight()
    {
        return mMetaData[1];
    }

    @Override
    public int getMinimumWidth()
    {
        return mMetaData[0];
    }
}