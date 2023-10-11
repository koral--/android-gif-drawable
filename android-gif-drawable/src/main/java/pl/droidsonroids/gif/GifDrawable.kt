package pl.droidsonroids.gif;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.StrictMode;
import android.os.SystemClock;
import android.widget.MediaController.MediaPlayerControl;

import androidx.annotation.DrawableRes;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import pl.droidsonroids.gif.transforms.CornerRadiusTransform;
import pl.droidsonroids.gif.transforms.Transform;

import static pl.droidsonroids.gif.InvalidationHandler.MSG_TYPE_INVALIDATION;

/**
 * A {@link Drawable} which can be used to hold GIF images, especially animations.
 * Basic GIF metadata can also be examined.
 *
 * @author koral--
 */
public class GifDrawable extends Drawable implements Animatable, MediaPlayerControl {
	final ScheduledThreadPoolExecutor mExecutor;

	volatile boolean mIsRunning = true;
	long mNextFrameRenderTime = Long.MIN_VALUE;

	private final Rect mDstRect = new Rect();
	/**
	 * Paint used to draw on a Canvas
	 */
	protected final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
	/**
	 * Frame buffer, holds current frame.
	 */
	final Bitmap mBuffer;
	final GifInfoHandle mNativeInfoHandle;
	final ConcurrentLinkedQueue<AnimationListener> mListeners = new ConcurrentLinkedQueue<>();
	private ColorStateList mTint;
	private PorterDuffColorFilter mTintFilter;
	private PorterDuff.Mode mTintMode;
	final boolean mIsRenderingTriggeredOnDraw;
	final InvalidationHandler mInvalidationHandler;

	private final RenderTask mRenderTask = new RenderTask(this);
	private final Rect mSrcRect;
	ScheduledFuture<?> mRenderTaskSchedule;
	private int mScaledWidth;
	private int mScaledHeight;
	private Transform mTransform;

	/**
	 * Creates drawable from resource.
	 *
	 * @param res Resources to read from
	 * @param id  resource id (raw or drawable)
	 * @throws NotFoundException    if the given ID does not exist.
	 * @throws IOException          when opening failed
	 * @throws NullPointerException if res is null
	 */
	public GifDrawable(@NonNull Resources res, @RawRes @DrawableRes int id) throws NotFoundException, IOException {
		this(res.openRawResourceFd(id));
		final float densityScale = GifViewUtils.getDensityScale(res, id);
		mScaledHeight = (int) (mNativeInfoHandle.getHeight() * densityScale);
		mScaledWidth = (int) (mNativeInfoHandle.getWidth() * densityScale);
	}

	/**
	 * Creates drawable from asset.
	 *
	 * @param assets    AssetManager to read from
	 * @param assetName name of the asset
	 * @throws IOException          when opening failed
	 * @throws NullPointerException if assets or assetName is null
	 */
	public GifDrawable(@NonNull AssetManager assets, @NonNull String assetName) throws IOException {
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
	public GifDrawable(@NonNull String filePath) throws IOException {
		this(new GifInfoHandle(filePath), null, null, true);
	}

	/**
	 * Equivalent to {@code} GifDrawable(file.getPath())}
	 *
	 * @param file the GIF file
	 * @throws IOException          when opening failed
	 * @throws NullPointerException if file is null
	 */
	public GifDrawable(@NonNull File file) throws IOException {
		this(file.getPath());
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
	public GifDrawable(@NonNull InputStream stream) throws IOException {
		this(new GifInfoHandle(stream), null, null, true);
	}

	/**
	 * Creates drawable from AssetFileDescriptor.
	 * Convenience wrapper for {@link GifDrawable#GifDrawable(FileDescriptor)}
	 *
	 * @param afd source
	 * @throws NullPointerException if afd is null
	 * @throws IOException          when opening failed
	 */
	public GifDrawable(@NonNull AssetFileDescriptor afd) throws IOException {
		this(new GifInfoHandle(afd), null, null, true);
	}

	/**
	 * Creates drawable from FileDescriptor
	 *
	 * @param fd source
	 * @throws IOException          when opening failed
	 * @throws NullPointerException if fd is null
	 */
	public GifDrawable(@NonNull FileDescriptor fd) throws IOException {
		this(new GifInfoHandle(fd), null, null, true);
	}

	/**
	 * Creates drawable from byte array.<br>
	 * It can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
	 *
	 * @param bytes raw GIF bytes
	 * @throws IOException          if bytes does not contain valid GIF data
	 * @throws NullPointerException if bytes are null
	 */
	public GifDrawable(@NonNull byte[] bytes) throws IOException {
		this(new GifInfoHandle(bytes), null, null, true);
	}

	/**
	 * Creates drawable from {@link ByteBuffer}. Only direct buffers are supported.
	 * Buffer can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
	 *
	 * @param buffer buffer containing GIF data
	 * @throws IOException          if buffer does not contain valid GIF data or is indirect
	 * @throws NullPointerException if buffer is null
	 */
	public GifDrawable(@NonNull ByteBuffer buffer) throws IOException {
		this(new GifInfoHandle(buffer), null, null, true);
	}

	/**
	 * Creates drawable from {@link android.net.Uri} which is resolved using {@code resolver}.
	 * {@link android.content.ContentResolver#openAssetFileDescriptor(android.net.Uri, String)}
	 * is used to open an Uri.
	 *
	 * @param uri      GIF Uri, cannot be null.
	 * @param resolver resolver used to query {@code uri}, can be null for file:// scheme Uris
	 * @throws IOException if resolution fails or destination is not a GIF.
	 */
	public GifDrawable(@Nullable ContentResolver resolver, @NonNull Uri uri) throws IOException {
		this(GifInfoHandle.openUri(resolver, uri), null, null, true);
	}

	/**
	 * Creates drawable from {@link InputSource}.
	 *
	 * @param inputSource                The {@link InputSource} concrete subclass used to construct {@link GifDrawable}.
	 * @param oldDrawable                The old drawable that will be reused to save the memory. Can be null.
	 * @param executor                   The executor for rendering tasks. Can be null.
	 * @param isRenderingTriggeredOnDraw True if rendering of the next frame is scheduled after drawing current one, false otherwise.
	 * @param options                    Options controlling various GIF parameters.
	 * @throws IOException if input source is invalid.
	 */
	protected GifDrawable(@NonNull InputSource inputSource,
	                      @Nullable GifDrawable oldDrawable,
	                      @Nullable ScheduledThreadPoolExecutor executor,
	                      boolean isRenderingTriggeredOnDraw,
	                      @NonNull GifOptions options) throws IOException {

		this(inputSource.createHandleWith(options), oldDrawable, executor, isRenderingTriggeredOnDraw);
	}

	GifDrawable(GifInfoHandle gifInfoHandle, final GifDrawable oldDrawable, ScheduledThreadPoolExecutor executor, boolean isRenderingTriggeredOnDraw) {
		mIsRenderingTriggeredOnDraw = isRenderingTriggeredOnDraw;
		mExecutor = executor != null ? executor : GifRenderingExecutor.getInstance();
		mNativeInfoHandle = gifInfoHandle;
		Bitmap oldBitmap = null;
		if (oldDrawable != null) {
			synchronized (oldDrawable.mNativeInfoHandle) {
				if (!oldDrawable.mNativeInfoHandle.isRecycled()
						&& oldDrawable.mNativeInfoHandle.getHeight() >= mNativeInfoHandle.getHeight()
						&& oldDrawable.mNativeInfoHandle.getWidth() >= mNativeInfoHandle.getWidth()) {
					oldDrawable.shutdown();
					oldBitmap = oldDrawable.mBuffer;
					oldBitmap.eraseColor(Color.TRANSPARENT);
				}
			}
		}

		if (oldBitmap == null) {
			mBuffer = Bitmap.createBitmap(mNativeInfoHandle.getWidth(), mNativeInfoHandle.getHeight(), Bitmap.Config.ARGB_8888);
		} else {
			mBuffer = oldBitmap;
		}
		mBuffer.setHasAlpha(!gifInfoHandle.isOpaque());
		mSrcRect = new Rect(0, 0, mNativeInfoHandle.getWidth(), mNativeInfoHandle.getHeight());
		mInvalidationHandler = new InvalidationHandler(this);
		mRenderTask.doWork();
		mScaledWidth = mNativeInfoHandle.getWidth();
		mScaledHeight = mNativeInfoHandle.getHeight();
	}

	/**
	 * Frees any memory allocated native way.
	 * Operation is irreversible. After this call, nothing will be drawn.
	 * This method is idempotent, subsequent calls have no effect.
	 * Like {@link android.graphics.Bitmap#recycle()} this is an advanced call and
	 * is invoked implicitly by finalizer.
	 */
	public void recycle() {
		shutdown();
		mBuffer.recycle();
	}

	private void shutdown() {
		mIsRunning = false;
		mInvalidationHandler.removeMessages(MSG_TYPE_INVALIDATION);
		mNativeInfoHandle.recycle();
	}

	/**
	 * @return true if drawable is recycled
	 */
	public boolean isRecycled() {
		return mNativeInfoHandle.isRecycled();
	}

	@Override
	public void invalidateSelf() {
		super.invalidateSelf();
		scheduleNextRender();
	}

	@Override
	public int getIntrinsicHeight() {
		return mScaledHeight;
	}

	@Override
	public int getIntrinsicWidth() {
		return mScaledWidth;
	}

	@Override
	public void setAlpha(@IntRange(from = 0, to = 255) int alpha) {
		mPaint.setAlpha(alpha);
	}

	@Override
	public void setColorFilter(@Nullable ColorFilter cf) {
		mPaint.setColorFilter(cf);
	}

	/**
	 * See {@link Drawable#getOpacity()}
	 *
	 * @return either {@link PixelFormat#TRANSPARENT} or {@link PixelFormat#OPAQUE}
	 * depending on current {@link Paint} and {@link GifOptions#setInIsOpaque(boolean)} used to construct this Drawable
	 */
	@SuppressWarnings("deprecation")
	@Override
	public int getOpacity() {
		if (!mNativeInfoHandle.isOpaque() || mPaint.getAlpha() < 255) {
			return PixelFormat.TRANSPARENT;
		}
		return PixelFormat.OPAQUE;
	}

	/**
	 * Starts the animation. Does nothing if GIF is not animated.
	 * This method is thread-safe.
	 */
	@Override
	public void start() {
		synchronized (this) {
			if (mIsRunning) {
				return;
			}
			mIsRunning = true;
		}
		final long lastFrameRemainder = mNativeInfoHandle.restoreRemainder();
		startAnimation(lastFrameRemainder);
	}

	void startAnimation(long lastFrameRemainder) {
		if (mIsRenderingTriggeredOnDraw) {
			mNextFrameRenderTime = 0;
			mInvalidationHandler.sendEmptyMessageAtTime(MSG_TYPE_INVALIDATION, 0);
		} else {
			cancelPendingRenderTask();
			mRenderTaskSchedule = mExecutor.schedule(mRenderTask, Math.max(lastFrameRemainder, 0), TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Causes the animation to start over.
	 * If rewinding input source fails then state is not affected.
	 * This method is thread-safe.
	 */
	public void reset() {
		mExecutor.execute(new SafeRunnable(this) {
			@Override
			public void doWork() {
				if (mNativeInfoHandle.reset()) {
					start();
				}
			}
		});
	}

	/**
	 * Stops the animation. Does nothing if GIF is not animated.
	 * This method is thread-safe.
	 */
	@Override
	public void stop() {
		synchronized (this) {
			if (!mIsRunning) {
				return;
			}
			mIsRunning = false;
		}

		cancelPendingRenderTask();
		mNativeInfoHandle.saveRemainder();
	}

	private void cancelPendingRenderTask() {
		if (mRenderTaskSchedule != null) {
			mRenderTaskSchedule.cancel(false);
		}
		mInvalidationHandler.removeMessages(MSG_TYPE_INVALIDATION);
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
	@Nullable
	public String getComment() {
		return mNativeInfoHandle.getComment();
	}

	/**
	 * Returns loop count previously read from GIF's application extension block.
	 * Defaults to 1 if there is no such extension.
	 *
	 * @return loop count, 0 means that animation is infinite
	 */
	public int getLoopCount() {
		return mNativeInfoHandle.getLoopCount();
	}

	/**
	 * Sets loop count of the animation. Loop count must be in range {@code <0 ,65535>}
	 *
	 * @param loopCount loop count, 0 means infinity
	 */
	public void setLoopCount(@IntRange(from = 0, to = Character.MAX_VALUE) final int loopCount) {
		mNativeInfoHandle.setLoopCount(loopCount);
	}

	/**
	 * @return basic description of the GIF including size and number of frames
	 */
	@Override
	@NonNull
	public String toString() {
		return String.format(Locale.ENGLISH, "GIF: size: %dx%d, frames: %d, error: %d",
				mNativeInfoHandle.getWidth(), mNativeInfoHandle.getHeight(), mNativeInfoHandle.getNumberOfFrames(), mNativeInfoHandle.getNativeErrorCode());
	}

	/**
	 * @return number of frames in GIF, at least one
	 */
	public int getNumberOfFrames() {
		return mNativeInfoHandle.getNumberOfFrames();
	}

	/**
	 * Retrieves last error which is also the indicator of current GIF status.
	 *
	 * @return current error or {@link GifError#NO_ERROR} if there was no error or drawable is recycled
	 */
	@NonNull
	public GifError getError() {
		return GifError.fromCode(mNativeInfoHandle.getNativeErrorCode());
	}

	/**
	 * An {@link GifDrawable#GifDrawable(Resources, int)} wrapper but returns null
	 * instead of throwing exception if creation fails.
	 *
	 * @param res        resources to read from
	 * @param resourceId resource id
	 * @return correct drawable or null if creation failed
	 */
	@Nullable
	public static GifDrawable createFromResource(@NonNull Resources res, @RawRes @DrawableRes int resourceId) {
		try {
			return new GifDrawable(res, resourceId);
		} catch (IOException ignored) {
			return null;
		}
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
	public void setSpeed(@FloatRange(from = 0, fromInclusive = false) final float factor) {
		mNativeInfoHandle.setSpeedFactor(factor);
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
		return mNativeInfoHandle.getDuration();
	}

	/**
	 * Retrieves elapsed time from the beginning of a current loop of animation.
	 * If there is only 1 frame or drawable is recycled 0 is returned.
	 *
	 * @return elapsed time from the beginning of a loop in ms
	 */
	@Override
	public int getCurrentPosition() {
		return mNativeInfoHandle.getCurrentPosition();
	}

	/**
	 * Seeks animation to given absolute position (within given loop) and refreshes the canvas.<br>
	 * If <code>position</code> is greater than duration of the loop of animation (or whole animation if there is no loop)
	 * then animation will be sought to the end, no exception will be thrown.<br>
	 * NOTE: all frames from current (or first one if seeking backward) to desired one must be rendered sequentially to perform seeking.
	 * It may take a lot of time if number of such frames is large.
	 * Method is thread-safe. Decoding is performed in background thread and drawable is invalidated automatically
	 * afterwards.
	 *
	 * @param position position to seek to in milliseconds
	 * @throws IllegalArgumentException if <code>position</code>&lt;0
	 */
	@Override
	public void seekTo(@IntRange(from = 0, to = Integer.MAX_VALUE) final int position) {
		if (position < 0) {
			throw new IllegalArgumentException("Position is not positive");
		}
		mExecutor.execute(new SafeRunnable(this) {
			@Override
			public void doWork() {
				mNativeInfoHandle.seekToTime(position, mBuffer);
				mGifDrawable.mInvalidationHandler.sendEmptyMessageAtTime(MSG_TYPE_INVALIDATION, 0);
			}
		});
	}

	/**
	 * Like {@link #seekTo(int)} but performs operation synchronously on current thread
	 *
	 * @param position position to seek to in milliseconds
	 * @throws IllegalArgumentException if <code>position</code>&lt;0
	 */
	public void seekToBlocking(@IntRange(from = 0, to = Integer.MAX_VALUE) final int position) {
		if (position < 0) {
			throw new IllegalArgumentException("Position is not positive");
		}

		synchronized (mNativeInfoHandle) {
			mNativeInfoHandle.seekToTime(position, mBuffer);
		}
		mInvalidationHandler.sendEmptyMessageAtTime(MSG_TYPE_INVALIDATION, 0);
	}

	/**
	 * Like {@link #seekTo(int)} but uses index of the frame instead of time.
	 * If <code>frameIndex</code> exceeds number of frames, seek stops at the end, no exception is thrown.
	 *
	 * @param frameIndex index of the frame to seek to (zero based)
	 * @throws IllegalArgumentException if <code>frameIndex</code>&lt;0
	 */
	public void seekToFrame(@IntRange(from = 0, to = Integer.MAX_VALUE) final int frameIndex) {
		if (frameIndex < 0) {
			throw new IndexOutOfBoundsException("Frame index is not positive");
		}
		mExecutor.execute(new SafeRunnable(this) {
			@Override
			public void doWork() {
				mNativeInfoHandle.seekToFrame(frameIndex, mBuffer);
				mInvalidationHandler.sendEmptyMessageAtTime(MSG_TYPE_INVALIDATION, 0);
			}
		});
	}

	/**
	 * Like {@link #seekToFrame(int)} but performs operation synchronously and returns that frame.
	 *
	 * @param frameIndex index of the frame to seek to (zero based)
	 * @return frame at desired index
	 * @throws IndexOutOfBoundsException if frameIndex&lt;0
	 */
	public Bitmap seekToFrameAndGet(@IntRange(from = 0, to = Integer.MAX_VALUE) final int frameIndex) {
		if (frameIndex < 0) {
			throw new IndexOutOfBoundsException("Frame index is not positive");
		}
		final Bitmap bitmap;
		synchronized (mNativeInfoHandle) {
			mNativeInfoHandle.seekToFrame(frameIndex, mBuffer);
			bitmap = getCurrentFrame();
		}
		mInvalidationHandler.sendEmptyMessageAtTime(MSG_TYPE_INVALIDATION, 0);
		return bitmap;
	}

	/**
	 * Like {@link #seekTo(int)} but performs operation synchronously and returns that frame.
	 *
	 * @param position position to seek to in milliseconds
	 * @return frame at desired position
	 * @throws IndexOutOfBoundsException if position&lt;0
	 */
	public Bitmap seekToPositionAndGet(@IntRange(from = 0, to = Integer.MAX_VALUE) final int position) {
		if (position < 0) {
			throw new IllegalArgumentException("Position is not positive");
		}
		final Bitmap bitmap;
		synchronized (mNativeInfoHandle) {
			mNativeInfoHandle.seekToTime(position, mBuffer);
			bitmap = getCurrentFrame();
		}
		mInvalidationHandler.sendEmptyMessageAtTime(MSG_TYPE_INVALIDATION, 0);
		return bitmap;
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
	 *
	 * @return true if GIF has at least 2 frames
	 */
	@Override
	public boolean canSeekBackward() {
		return getNumberOfFrames() > 1;
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
	 * <p>This method should not be used to calculate the memory usage of the bitmap.
	 * Instead see {@link #getAllocationByteCount()}.
	 *
	 * @return the minimum number of bytes that can be used to store pixels of the single frame
	 */
	public int getFrameByteCount() {
		return mBuffer.getRowBytes() * mBuffer.getHeight();
	}

	/**
	 * Returns size of the memory needed to store pixels of this object. It counts possible length of all frame buffers.
	 * Returned value may be lower than amount of actually allocated memory if GIF uses dispose to previous method but frame requiring it
	 * has never been needed yet. Returned value does not change during runtime.
	 *
	 * @return possible size of the memory needed to store pixels of this object
	 */
	public long getAllocationByteCount() {
		long byteCount = mNativeInfoHandle.getAllocationByteCount();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			byteCount += mBuffer.getAllocationByteCount();
		} else {
			byteCount += getFrameByteCount();
		}
		return byteCount;
	}

	/**
	 * Returns the maximum possible size of the allocated memory used to store pixels and metadata of this object.
	 * It counts length of all frame buffers. Returned value does not change over time.
	 *
	 * @return maximum possible size of the allocated memory needed to store metadata of this object
	 */
	public long getMetadataAllocationByteCount() {
		return mNativeInfoHandle.getMetadataByteCount();
	}

	/**
	 * Returns length of the input source obtained at the opening time or -1 if
	 * length cannot be determined. Returned value does not change during runtime.
	 * If GifDrawable is constructed from {@link InputStream} -1 is always returned.
	 * In case of byte array and {@link ByteBuffer} length is always known.
	 * In other cases length -1 can be returned if length cannot be determined.
	 *
	 * @return number of bytes backed by input source or -1 if it is unknown
	 */
	public long getInputSourceByteCount() {
		return mNativeInfoHandle.getSourceLength();
	}

	/**
	 * Returns in pixels[] a copy of the data in the current frame. Each value is a packed int representing a {@link Color}.
	 *
	 * @param pixels the array to receive the frame's colors
	 * @throws ArrayIndexOutOfBoundsException if the pixels array is too small to receive required number of pixels
	 */
	public void getPixels(@NonNull int[] pixels) {
		mBuffer.getPixels(pixels, 0, mNativeInfoHandle.getWidth(), 0, 0, mNativeInfoHandle.getWidth(), mNativeInfoHandle.getHeight());
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
	 * @throws IllegalStateException    if drawable is recycled
	 */
	public int getPixel(@IntRange(from = 0) int x, @IntRange(from = 0) int y) {
		if (x >= mNativeInfoHandle.getWidth()) { //need to check explicitly because reused bitmap may be larger
			throw new IllegalArgumentException("x must be < width");
		}
		if (y >= mNativeInfoHandle.getHeight()) {
			throw new IllegalArgumentException("y must be < height");
		}
		return mBuffer.getPixel(x, y);
	}

	@Override
	protected void onBoundsChange(Rect bounds) {
		mDstRect.set(bounds);
		if (mTransform != null) {
			mTransform.onBoundsChange(bounds);
		}
	}

	/**
	 * Reads and renders new frame if needed then draws last rendered frame.
	 *
	 * @param canvas canvas to draw into
	 */
	@Override
	public void draw(@NonNull Canvas canvas) {
		final boolean clearColorFilter;
		if (mTintFilter != null && mPaint.getColorFilter() == null) {
			mPaint.setColorFilter(mTintFilter);
			clearColorFilter = true;
		} else {
			clearColorFilter = false;
		}
		if (mTransform == null) {
			canvas.drawBitmap(mBuffer, mSrcRect, mDstRect, mPaint);
		} else {
			mTransform.onDraw(canvas, mPaint, mBuffer);
		}
		if (clearColorFilter) {
			mPaint.setColorFilter(null);
		}

	}

	private void scheduleNextRender() {
		if (mIsRenderingTriggeredOnDraw && mIsRunning && mNextFrameRenderTime != Long.MIN_VALUE) {
			final long renderDelay = Math.max(0, mNextFrameRenderTime - SystemClock.uptimeMillis());
			mNextFrameRenderTime = Long.MIN_VALUE;
			mExecutor.remove(mRenderTask);
			mRenderTaskSchedule = mExecutor.schedule(mRenderTask, renderDelay, TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * @return the paint used to render this drawable
	 */
	@NonNull
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

	@SuppressWarnings("deprecation")
	@Override
	public void setDither(boolean dither) {
		mPaint.setDither(dither);
		invalidateSelf();
	}

	/**
	 * Adds a new animation listener
	 *
	 * @param listener animation listener to be added, not null
	 * @throws java.lang.NullPointerException if listener is null
	 */
	public void addAnimationListener(@NonNull AnimationListener listener) {
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

	/**
	 * Retrieves a copy of currently buffered frame.
	 *
	 * @return current frame
	 */
	public Bitmap getCurrentFrame() {
		final Bitmap copy = mBuffer.copy(mBuffer.getConfig(), mBuffer.isMutable());
		copy.setHasAlpha(mBuffer.hasAlpha());
		return copy;
	}

	private PorterDuffColorFilter updateTintFilter(ColorStateList tint, PorterDuff.Mode tintMode) {
		if (tint == null || tintMode == null) {
			return null;
		}

		final int color = tint.getColorForState(getState(), Color.TRANSPARENT);
		return new PorterDuffColorFilter(color, tintMode);
	}

	@Override
	public void setTintList(ColorStateList tint) {
		mTint = tint;
		mTintFilter = updateTintFilter(tint, mTintMode);
		invalidateSelf();
	}

	@Override
	public void setTintMode(@Nullable PorterDuff.Mode tintMode) {
		mTintMode = tintMode;
		mTintFilter = updateTintFilter(mTint, tintMode);
		invalidateSelf();
	}

	@Override
	protected boolean onStateChange(int[] stateSet) {
		if (mTint != null && mTintMode != null) {
			mTintFilter = updateTintFilter(mTint, mTintMode);
			return true;
		}
		return false;
	}

	@Override
	public boolean isStateful() {
		return super.isStateful() || (mTint != null && mTint.isStateful());
	}

	/**
	 * Sets whether this drawable is visible. If rendering of next frame is scheduled on draw current one (the default) then this method
	 * only calls through to the super class's implementation.<br>
	 * Otherwise (if {@link GifDrawableBuilder#setRenderingTriggeredOnDraw(boolean)} was used with <code>true</code>)
	 * when the drawable becomes invisible, it will pause its animation. A
	 * subsequent change to visible with <code>restart</code> set to true will
	 * restart the animation from the first frame. If <code>restart</code> is
	 * false, the animation will resume from the most recent frame.
	 *
	 * @param visible true if visible, false otherwise
	 * @param restart when visible and rendering is triggered on draw, true to force the animation to restart
	 *                from the first frame
	 * @return true if the new visibility is different than its previous state
	 */
	@Override
	public boolean setVisible(boolean visible, boolean restart) {
		final boolean changed = super.setVisible(visible, restart);
		if (!mIsRenderingTriggeredOnDraw) {
			if (visible) {
				if (restart) {
					reset();
				}
				if (changed) {
					start();
				}
			} else if (changed) {
				stop();
			}
		}
		return changed;
	}

	/**
	 * Returns zero-based index of recently rendered frame in given loop or -1 when drawable is recycled.
	 *
	 * @return index of recently rendered frame or -1 when drawable is recycled
	 */
	public int getCurrentFrameIndex() {
		return mNativeInfoHandle.getCurrentFrameIndex();
	}

	/**
	 * Returns zero-based index of currently played animation loop. If animation is infinite or
	 * drawable is recycled 0 is returned.
	 *
	 * @return index of currently played animation loop
	 */
	public int getCurrentLoop() {
		final int currentLoop = mNativeInfoHandle.getCurrentLoop();
		if (currentLoop == 0 || currentLoop < mNativeInfoHandle.getLoopCount()) {
			return currentLoop;
		} else {
			return currentLoop - 1;
		}
	}

	/**
	 * Returns whether all animation loops has ended. If drawable is recycled false is returned.
	 *
	 * @return true if all animation loops has ended
	 */
	public boolean isAnimationCompleted() {
		return mNativeInfoHandle.isAnimationCompleted();
	}

	/**
	 * Returns duration of the given frame (in milliseconds). If there is no data (no Graphics
	 * Control Extension blocks or drawable is recycled) 0 is returned.
	 *
	 * @param index index of the frame
	 * @return duration of the given frame in milliseconds
	 * @throws IndexOutOfBoundsException if index &lt; 0 or index &gt;= number of frames
	 */
	public int getFrameDuration(@IntRange(from = 0) final int index) {
		return mNativeInfoHandle.getFrameDuration(index);
	}

	/**
	 * Sets the corner radius to be applied when drawing the bitmap.
	 * Note that changing corner radius will cause replacing current {@link Paint} shader by {@link BitmapShader}.
	 * Transform set by {@link #setTransform(Transform)} will also be replaced.
	 *
	 * @param cornerRadius corner radius or 0 to remove rounding
	 */
	public void setCornerRadius(@FloatRange(from = 0) final float cornerRadius) {
		mTransform = new CornerRadiusTransform(cornerRadius);
		mTransform.onBoundsChange(mDstRect);
	}

	/**
	 * @return The corner radius applied when drawing this drawable. 0 when drawable is not rounded.
	 */
	@FloatRange(from = 0)
	public float getCornerRadius() {
		if (mTransform instanceof CornerRadiusTransform) {
			return ((CornerRadiusTransform) mTransform).getCornerRadius();
		}
		return 0;
	}

	/**
	 * Specify a {@link Transform} implementation to customize how the GIF's current Bitmap is drawn.
	 *
	 * @param transform new {@link Transform} or null to remove current one
	 */
	public void setTransform(@Nullable Transform transform) {
		mTransform = transform;
		if (mTransform != null) {
			mTransform.onBoundsChange(mDstRect);
		}
	}

	/**
	 * @return The current {@link Transform} implementation that customizes
	 * how the GIF's current Bitmap is drawn or null if nothing has been set.
	 */
	@Nullable
	public Transform getTransform() {
		return mTransform;
	}

}
