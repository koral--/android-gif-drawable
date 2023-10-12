package pl.droidsonroids.gif

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.content.res.Resources.NotFoundException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.widget.MediaController.MediaPlayerControl
import androidx.annotation.DrawableRes
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.annotation.RawRes
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifError.Companion.fromCode
import pl.droidsonroids.gif.GifInfoHandle.Companion.openUri
import pl.droidsonroids.gif.GifViewUtils.getDensityScale
import pl.droidsonroids.gif.transforms.CornerRadiusTransform
import pl.droidsonroids.gif.transforms.Transform
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws
import kotlin.math.max

/**
 * A [Drawable] which can be used to hold GIF images, especially animations.
 * Basic GIF metadata can also be examined.
 *
 * @author koral--
 */
class GifDrawable internal constructor(
    val mNativeInfoHandle: GifInfoHandle,
    oldDrawable: GifDrawable?,
    executor: ScheduledThreadPoolExecutor?,
    val mIsRenderingTriggeredOnDraw: Boolean
) : Drawable(), Animatable, MediaPlayerControl {

    val mExecutor: ScheduledThreadPoolExecutor

    @Volatile
    var mIsRunning = true
    var mNextFrameRenderTime = Long.MIN_VALUE
    private val mDstRect = Rect()

    /**
     * Paint used to draw on a Canvas
     */
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    /**
     * Frame buffer, holds current frame.
     */
    var mBuffer: Bitmap

    val mListeners = ConcurrentLinkedQueue<AnimationListener>()
    private var mTint: ColorStateList? = null
    private var mTintFilter: PorterDuffColorFilter? = null
    private var mTintMode: PorterDuff.Mode? = null
    val mInvalidationHandler: InvalidationHandler
    private val mRenderTask = RenderTask(this)
    private val mSrcRect: Rect
    var mRenderTaskSchedule: ScheduledFuture<*>? = null
    private var mScaledWidth: Int
    private var mScaledHeight: Int
    private var mTransform: Transform? = null

    /**
     * Creates drawable from resource.
     *
     * @param res Resources to read from
     * @param id  resource id (raw or drawable)
     * @throws NotFoundException    if the given ID does not exist.
     * @throws IOException          when opening failed
     * @throws NullPointerException if res is null
     */
    @Throws(NotFoundException::class, IOException::class, NullPointerException::class)
    constructor(res: Resources, @RawRes @DrawableRes id: Int) : this(res.openRawResourceFd(id)) {
        val densityScale = getDensityScale(res, id)
        mScaledHeight = (mNativeInfoHandle.height * densityScale).toInt()
        mScaledWidth = (mNativeInfoHandle.width * densityScale).toInt()
    }

    /**
     * Creates drawable from asset.
     *
     * @param assets    AssetManager to read from
     * @param assetName name of the asset
     * @throws IOException          when opening failed
     * @throws NullPointerException if assets or assetName is null
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(assets: AssetManager, assetName: String) : this(assets.openFd(assetName))

    /**
     * Constructs drawable from given file path.<br></br>
     * Only metadata is read, no graphic data is decoded here.
     * In practice can be called from main thread. However it will violate
     * [StrictMode] policy if disk reads detection is enabled.<br></br>
     *
     * @param filePath path to the GIF file
     * @throws IOException          when opening failed
     * @throws NullPointerException if filePath is null
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(filePath: String) : this(GifInfoHandle(filePath), null, null, true)

    /**
     * Equivalent to `` GifDrawable(file.getPath())}
     *
     * @param file the GIF file
     * @throws IOException          when opening failed
     * @throws NullPointerException if file is null
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(file: File) : this(file.path)

    /**
     * Creates drawable from InputStream.
     * InputStream must support marking, IllegalArgumentException will be thrown otherwise.
     *
     * @param stream stream to read from
     * @throws IOException              when opening failed
     * @throws IllegalArgumentException if stream does not support marking
     * @throws NullPointerException     if stream is null
     */
    @Throws(IOException::class, NullPointerException::class, IllegalArgumentException::class)
    constructor(stream: InputStream) : this(GifInfoHandle(stream), null, null, true)

    /**
     * Creates drawable from AssetFileDescriptor.
     * Convenience wrapper for [GifDrawable.GifDrawable]
     *
     * @param afd source
     * @throws NullPointerException if afd is null
     * @throws IOException          when opening failed
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(afd: AssetFileDescriptor) : this(GifInfoHandle(afd), null, null, true)

    /**
     * Creates drawable from FileDescriptor
     *
     * @param fd source
     * @throws IOException          when opening failed
     * @throws NullPointerException if fd is null
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(fd: FileDescriptor) : this(GifInfoHandle(fd), null, null, true)

    /**
     * Creates drawable from byte array.<br></br>
     * It can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
     *
     * @param bytes raw GIF bytes
     * @throws IOException          if bytes does not contain valid GIF data
     * @throws NullPointerException if bytes are null
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(bytes: ByteArray) : this(GifInfoHandle(bytes), null, null, true)

    /**
     * Creates drawable from [ByteBuffer]. Only direct buffers are supported.
     * Buffer can be larger than size of the GIF data. Bytes beyond GIF terminator are not accessed.
     *
     * @param buffer buffer containing GIF data
     * @throws IOException          if buffer does not contain valid GIF data or is indirect
     * @throws NullPointerException if buffer is null
     */
    @Throws(IOException::class, NullPointerException::class)
    constructor(buffer: ByteBuffer) : this(GifInfoHandle(buffer), null, null, true)

    /**
     * Creates drawable from [android.net.Uri] which is resolved using `resolver`.
     * [android.content.ContentResolver.openAssetFileDescriptor]
     * is used to open an Uri.
     *
     * @param uri      GIF Uri, cannot be null.
     * @param resolver resolver used to query `uri`, can be null for file:// scheme Uris
     * @throws IOException if resolution fails or destination is not a GIF.
     */
    @Throws(IOException::class)
    constructor(resolver: ContentResolver?, uri: Uri) : this(
        openUri(
            resolver, uri
        ), null, null, true
    )

    /**
     * Creates drawable from [InputSource].
     *
     * @param inputSource                The [InputSource] concrete subclass used to construct [GifDrawable].
     * @param oldDrawable                The old drawable that will be reused to save the memory. Can be null.
     * @param executor                   The executor for rendering tasks. Can be null.
     * @param isRenderingTriggeredOnDraw True if rendering of the next frame is scheduled after drawing current one, false otherwise.
     * @param options                    Options controlling various GIF parameters.
     * @throws IOException if input source is invalid.
     */
    @Throws(IOException::class)
    protected constructor(
        inputSource: InputSource,
        oldDrawable: GifDrawable?,
        executor: ScheduledThreadPoolExecutor?,
        isRenderingTriggeredOnDraw: Boolean,
        options: GifOptions
    ) : this(
        inputSource.createHandleWith(options),
        oldDrawable,
        executor,
        isRenderingTriggeredOnDraw
    )

    init {
        mExecutor = executor ?: GifRenderingExecutor
        var oldBitmap: Bitmap? = null
        if (oldDrawable != null) {
            synchronized(oldDrawable.mNativeInfoHandle) {
                if ((!oldDrawable.mNativeInfoHandle.isRecycled
                            && (oldDrawable.mNativeInfoHandle.height >= mNativeInfoHandle.height
                            ) && (oldDrawable.mNativeInfoHandle.width >= mNativeInfoHandle.width))
                ) {
                    oldDrawable.shutdown()
                    oldBitmap = oldDrawable.mBuffer
                    oldBitmap!!.eraseColor(Color.TRANSPARENT)
                }
            }
        }
        mBuffer = oldBitmap
            ?: Bitmap.createBitmap(
                mNativeInfoHandle.width,
                mNativeInfoHandle.height,
                Bitmap.Config.ARGB_8888
            )
        mBuffer.setHasAlpha(!mNativeInfoHandle.isOpaque)
        mSrcRect = Rect(0, 0, mNativeInfoHandle.width, mNativeInfoHandle.height)
        mInvalidationHandler = InvalidationHandler(this)
        mRenderTask.doWork()
        mScaledWidth = mNativeInfoHandle.width
        mScaledHeight = mNativeInfoHandle.height
    }

    /**
     * Frees any memory allocated native way.
     * Operation is irreversible. After this call, nothing will be drawn.
     * This method is idempotent, subsequent calls have no effect.
     * Like [android.graphics.Bitmap.recycle] this is an advanced call and
     * is invoked implicitly by finalizer.
     */
    fun recycle() {
        shutdown()
        mBuffer.recycle()
    }

    private fun shutdown() {
        mIsRunning = false
        mInvalidationHandler.removeMessages(InvalidationHandler.MSG_TYPE_INVALIDATION)
        mNativeInfoHandle.recycle()
    }

    val isRecycled: Boolean
        /**
         * @return true if drawable is recycled
         */
        get() = mNativeInfoHandle.isRecycled

    override fun invalidateSelf() {
        super.invalidateSelf()
        scheduleNextRender()
    }

    override fun getIntrinsicHeight(): Int {
        return mScaledHeight
    }

    override fun getIntrinsicWidth(): Int {
        return mScaledWidth
    }

    override fun setAlpha(@IntRange(from = 0, to = 255) alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        paint.colorFilter = cf
    }

    /**
     * See [Drawable.getOpacity]
     *
     * @return either [PixelFormat.TRANSPARENT] or [PixelFormat.OPAQUE]
     * depending on current [Paint] and [GifOptions#setInIsOpaque] used to construct this Drawable
     */
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return if (!mNativeInfoHandle.isOpaque || paint.alpha < 255) {
            PixelFormat.TRANSPARENT
        } else PixelFormat.OPAQUE
    }

    /**
     * Starts the animation. Does nothing if GIF is not animated.
     * This method is thread-safe.
     */
    override fun start() {
        synchronized(this) {
            if (mIsRunning) {
                return
            }
            mIsRunning = true
        }
        val lastFrameRemainder = mNativeInfoHandle.restoreRemainder()
        startAnimation(lastFrameRemainder)
    }

    fun startAnimation(lastFrameRemainder: Long) {
        if (mIsRenderingTriggeredOnDraw) {
            mNextFrameRenderTime = 0
            mInvalidationHandler.sendEmptyMessageAtTime(
                InvalidationHandler.MSG_TYPE_INVALIDATION,
                0
            )
        } else {
            cancelPendingRenderTask()
            mRenderTaskSchedule = mExecutor.schedule(
                mRenderTask,
                max(lastFrameRemainder, 0),
                TimeUnit.MILLISECONDS
            )
        }
    }

    /**
     * Causes the animation to start over.
     * If rewinding input source fails then state is not affected.
     * This method is thread-safe.
     */
    fun reset() {
        mExecutor.execute(object : SafeRunnable(this) {
            override fun doWork() {
                if (mNativeInfoHandle.reset()) {
                    start()
                }
            }
        })
    }

    /**
     * Stops the animation. Does nothing if GIF is not animated.
     * This method is thread-safe.
     */
    override fun stop() {
        synchronized(this) {
            if (!mIsRunning) {
                return
            }
            mIsRunning = false
        }
        cancelPendingRenderTask()
        mNativeInfoHandle.saveRemainder()
    }

    private fun cancelPendingRenderTask() {
        if (mRenderTaskSchedule != null) {
            mRenderTaskSchedule!!.cancel(false)
        }
        mInvalidationHandler.removeMessages(InvalidationHandler.MSG_TYPE_INVALIDATION)
    }

    override fun isRunning(): Boolean {
        return mIsRunning
    }

    val comment: String
        /**
         * Returns GIF comment
         *
         * @return comment or null if there is no one defined in file
         */
        get() = mNativeInfoHandle.comment
    var loopCount: Int
        /**
         * Returns loop count previously read from GIF's application extension block.
         * Defaults to 1 if there is no such extension.
         *
         * @return loop count, 0 means that animation is infinite
         */
        get() = mNativeInfoHandle.loopCount
        /**
         * Sets loop count of the animation. Loop count must be in range `<0 ,65535>`
         *
         * @param loopCount loop count, 0 means infinity
         */
        set(loopCount) {
            mNativeInfoHandle.loopCount = loopCount
        }

    /**
     * @return basic description of the GIF including size and number of frames
     */
    override fun toString(): String {
        return String.format(
            Locale.ENGLISH,
            "GIF: size: %dx%d, frames: %d, error: %d",
            mNativeInfoHandle.width,
            mNativeInfoHandle.height,
            mNativeInfoHandle.numberOfFrames,
            mNativeInfoHandle.nativeErrorCode
        )
    }

    val numberOfFrames: Int
        /**
         * @return number of frames in GIF, at least one
         */
        get() = mNativeInfoHandle.numberOfFrames
    val error: GifError
        /**
         * Retrieves last error which is also the indicator of current GIF status.
         *
         * @return current error or [GifError.NO_ERROR] if there was no error or drawable is recycled
         */
        get() = fromCode(mNativeInfoHandle.nativeErrorCode)

    /**
     * Sets new animation speed factor.<br></br>
     * Note: If animation is in progress ([.draw]) was already called)
     * then effects will be visible starting from the next frame. Duration of the currently rendered
     * frame is not affected.
     *
     * @param factor new speed factor, eg. 0.5f means half speed, 1.0f - normal, 2.0f - double speed
     * @throws IllegalArgumentException if factor&lt;=0
     */
    @Throws(IllegalArgumentException::class)
    fun setSpeed(@FloatRange(from = 0.0, fromInclusive = false) factor: Float) {
        mNativeInfoHandle.setSpeedFactor(factor)
    }

    /**
     * Equivalent of [.stop]
     */
    override fun pause() {
        stop()
    }

    /**
     * Retrieves duration of one loop of the animation.
     * If there is no data (no Graphics Control Extension blocks) 0 is returned.
     * Note that one-frame GIFs can have non-zero duration defined in Graphics Control Extension block,
     * use [.getNumberOfFrames] to determine if there is one or more frames.
     *
     * @return duration of of one loop the animation in milliseconds. Result is always multiple of 10.
     */
    override fun getDuration(): Int {
        return mNativeInfoHandle.duration
    }

    /**
     * Retrieves elapsed time from the beginning of a current loop of animation.
     * If there is only 1 frame or drawable is recycled 0 is returned.
     *
     * @return elapsed time from the beginning of a loop in ms
     */
    override fun getCurrentPosition(): Int {
        return mNativeInfoHandle.currentPosition
    }

    /**
     * Seeks animation to given absolute position (within given loop) and refreshes the canvas.<br></br>
     * If `position` is greater than duration of the loop of animation (or whole animation if there is no loop)
     * then animation will be sought to the end, no exception will be thrown.<br></br>
     * NOTE: all frames from current (or first one if seeking backward) to desired one must be rendered sequentially to perform seeking.
     * It may take a lot of time if number of such frames is large.
     * Method is thread-safe. Decoding is performed in background thread and drawable is invalidated automatically
     * afterwards.
     *
     * @param position position to seek to in milliseconds
     * @throws IllegalArgumentException if `position`&lt;0
     */
    @Throws(IllegalArgumentException::class)
    override fun seekTo(@IntRange(from = 0, to = Int.MAX_VALUE.toLong()) position: Int) {
        require(position >= 0) { "Position is not positive" }
        mExecutor.execute(object : SafeRunnable(this) {
            override fun doWork() {
                mNativeInfoHandle.seekToTime(position, mBuffer)
                mGifDrawable.mInvalidationHandler.sendEmptyMessageAtTime(
                    InvalidationHandler.MSG_TYPE_INVALIDATION,
                    0
                )
            }
        })
    }

    /**
     * Like [.seekTo] but performs operation synchronously on current thread
     *
     * @param position position to seek to in milliseconds
     * @throws IllegalArgumentException if `position`&lt;0
     */
    @Throws(IllegalArgumentException::class)
    fun seekToBlocking(@IntRange(from = 0, to = Int.MAX_VALUE.toLong()) position: Int) {
        require(position >= 0) { "Position is not positive" }
        synchronized(mNativeInfoHandle) { mNativeInfoHandle.seekToTime(position, mBuffer) }
        mInvalidationHandler.sendEmptyMessageAtTime(InvalidationHandler.MSG_TYPE_INVALIDATION, 0)
    }

    /**
     * Like [.seekTo] but uses index of the frame instead of time.
     * If `frameIndex` exceeds number of frames, seek stops at the end, no exception is thrown.
     *
     * @param frameIndex index of the frame to seek to (zero based)
     * @throws IllegalArgumentException if `frameIndex`&lt;0
     */
    @Throws(IllegalArgumentException::class)
    fun seekToFrame(@IntRange(from = 0, to = Int.MAX_VALUE.toLong()) frameIndex: Int) {
        if (frameIndex < 0) {
            throw IndexOutOfBoundsException("Frame index is not positive")
        }
        mExecutor.execute(object : SafeRunnable(this) {
            override fun doWork() {
                mNativeInfoHandle.seekToFrame(frameIndex, mBuffer)
                mInvalidationHandler.sendEmptyMessageAtTime(
                    InvalidationHandler.MSG_TYPE_INVALIDATION,
                    0
                )
            }
        })
    }

    /**
     * Like [.seekToFrame] but performs operation synchronously and returns that frame.
     *
     * @param frameIndex index of the frame to seek to (zero based)
     * @return frame at desired index
     * @throws IndexOutOfBoundsException if frameIndex&lt;0
     */
    @Throws(IndexOutOfBoundsException::class)
    fun seekToFrameAndGet(
        @IntRange(
            from = 0,
            to = Int.MAX_VALUE.toLong()
        ) frameIndex: Int
    ): Bitmap {
        if (frameIndex < 0) {
            throw IndexOutOfBoundsException("Frame index is not positive")
        }
        val bitmap: Bitmap
        synchronized(mNativeInfoHandle) {
            mNativeInfoHandle.seekToFrame(frameIndex, mBuffer)
            bitmap = currentFrame
        }
        mInvalidationHandler.sendEmptyMessageAtTime(InvalidationHandler.MSG_TYPE_INVALIDATION, 0)
        return bitmap
    }

    /**
     * Like [.seekTo] but performs operation synchronously and returns that frame.
     *
     * @param position position to seek to in milliseconds
     * @return frame at desired position
     * @throws IndexOutOfBoundsException if position&lt;0
     */
    @Throws(IndexOutOfBoundsException::class)
    fun seekToPositionAndGet(
        @IntRange(
            from = 0,
            to = Int.MAX_VALUE.toLong()
        ) position: Int
    ): Bitmap {
        require(position >= 0) { "Position is not positive" }
        val bitmap: Bitmap
        synchronized(mNativeInfoHandle) {
            mNativeInfoHandle.seekToTime(position, mBuffer)
            bitmap = currentFrame
        }
        mInvalidationHandler.sendEmptyMessageAtTime(InvalidationHandler.MSG_TYPE_INVALIDATION, 0)
        return bitmap
    }

    /**
     * Equivalent of [.isRunning]
     *
     * @return true if animation is running
     */
    override fun isPlaying(): Boolean {
        return mIsRunning
    }

    /**
     * Used by MediaPlayer for secondary progress bars.
     * There is no buffer in GifDrawable, so buffer is assumed to be always full.
     *
     * @return always 100
     */
    override fun getBufferPercentage(): Int {
        return 100
    }

    /**
     * Checks whether pause is supported.
     *
     * @return always true, even if there is only one frame
     */
    override fun canPause(): Boolean {
        return true
    }

    /**
     * Checks whether seeking backward can be performed.
     *
     * @return true if GIF has at least 2 frames
     */
    override fun canSeekBackward(): Boolean {
        return numberOfFrames > 1
    }

    /**
     * Checks whether seeking forward can be performed.
     *
     * @return true if GIF has at least 2 frames
     */
    override fun canSeekForward(): Boolean {
        return numberOfFrames > 1
    }

    /**
     * Used by MediaPlayer.
     * GIFs contain no sound, so 0 is always returned.
     *
     * @return always 0
     */
    override fun getAudioSessionId(): Int {
        return 0
    }

    val frameByteCount: Int
        /**
         * Returns the minimum number of bytes that can be used to store pixels of the single frame.
         * Returned value is the same for all the frames since it is based on the size of GIF screen.
         *
         * This method should not be used to calculate the memory usage of the bitmap.
         * Instead see [.getAllocationByteCount].
         *
         * @return the minimum number of bytes that can be used to store pixels of the single frame
         */
        get() = mBuffer.rowBytes * mBuffer.height
    val allocationByteCount: Long
        /**
         * Returns size of the memory needed to store pixels of this object. It counts possible length of all frame buffers.
         * Returned value may be lower than amount of actually allocated memory if GIF uses dispose to previous method but frame requiring it
         * has never been needed yet. Returned value does not change during runtime.
         *
         * @return possible size of the memory needed to store pixels of this object
         */
        get() {
            var byteCount = mNativeInfoHandle.allocationByteCount
            byteCount += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mBuffer.allocationByteCount.toLong()
            } else {
                frameByteCount.toLong()
            }
            return byteCount
        }

    /**
     * Returns in pixels[] a copy of the data in the current frame. Each value is a packed int representing a [Color].
     *
     * @param pixels the array to receive the frame's colors
     * @throws ArrayIndexOutOfBoundsException if the pixels array is too small to receive required number of pixels
     */
    @Throws(ArrayIndexOutOfBoundsException::class)
    fun getPixels(pixels: IntArray) {
        mBuffer.getPixels(
            pixels,
            0,
            mNativeInfoHandle.width,
            0,
            0,
            mNativeInfoHandle.width,
            mNativeInfoHandle.height
        )
    }

    /**
     * Returns the [Color] at the specified location. Throws an exception
     * if x or y are out of bounds (negative or &gt;= to the width or height
     * respectively). The returned color is a non-premultiplied ARGB value.
     *
     * @param x The x coordinate (0...width-1) of the pixel to return
     * @param y The y coordinate (0...height-1) of the pixel to return
     * @return The argb [Color] at the specified coordinate
     * @throws IllegalArgumentException if x, y exceed the drawable's bounds
     * @throws IllegalStateException    if drawable is recycled
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun getPixel(@IntRange(from = 0) x: Int, @IntRange(from = 0) y: Int): Int {
        require(x < mNativeInfoHandle.width) {  //need to check explicitly because reused bitmap may be larger
            "x must be < width"
        }
        if (y >= mNativeInfoHandle.height) {
            throw IllegalArgumentException("y must be < height")
        }
        return mBuffer.getPixel(x, y)
    }

    override fun onBoundsChange(bounds: Rect) {
        mDstRect.set(bounds)
        if (mTransform != null) {
            mTransform!!.onBoundsChange(bounds)
        }
    }

    /**
     * Reads and renders new frame if needed then draws last rendered frame.
     *
     * @param canvas canvas to draw into
     */
    override fun draw(canvas: Canvas) {
        val clearColorFilter: Boolean
        if (mTintFilter != null && paint.colorFilter == null) {
            paint.colorFilter = mTintFilter
            clearColorFilter = true
        } else {
            clearColorFilter = false
        }
        if (mTransform == null) {
            canvas.drawBitmap(mBuffer, mSrcRect, mDstRect, paint)
        } else {
            mTransform!!.onDraw(canvas, paint, mBuffer)
        }
        if (clearColorFilter) {
            paint.colorFilter = null
        }
    }

    private fun scheduleNextRender() {
        if (mIsRenderingTriggeredOnDraw && mIsRunning && mNextFrameRenderTime != Long.MIN_VALUE) {
            val renderDelay = max(0, mNextFrameRenderTime - SystemClock.uptimeMillis())
            mNextFrameRenderTime = Long.MIN_VALUE
            mExecutor.remove(mRenderTask)
            mRenderTaskSchedule =
                mExecutor.schedule(mRenderTask, renderDelay, TimeUnit.MILLISECONDS)
        }
    }

    override fun getAlpha(): Int {
        return paint.alpha
    }

    override fun setFilterBitmap(filter: Boolean) {
        paint.isFilterBitmap = filter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun setDither(dither: Boolean) {
        paint.isDither = dither
        invalidateSelf()
    }

    /**
     * Adds a new animation listener
     *
     * @param listener animation listener to be added, not null
     * @throws NullPointerException if listener is null
     */
    @Throws(NullPointerException::class)
    fun addAnimationListener(listener: AnimationListener) {
        mListeners.add(listener)
    }

    /**
     * Removes an animation listener
     *
     * @param listener animation listener to be removed
     * @return true if listener collection has been modified
     */
    fun removeAnimationListener(listener: AnimationListener): Boolean {
        return mListeners.remove(listener)
    }

    override fun getColorFilter(): ColorFilter? {
        return paint.colorFilter
    }

    private val currentFrame: Bitmap
        /**
         * Retrieves a copy of currently buffered frame.
         *
         * @return current frame
         */
        get() {
            val copy = mBuffer.copy(mBuffer.config, mBuffer.isMutable)
            copy.setHasAlpha(mBuffer.hasAlpha())
            return copy
        }

    private fun updateTintFilter(
        tint: ColorStateList?,
        tintMode: PorterDuff.Mode?
    ): PorterDuffColorFilter? {
        if (tint == null || tintMode == null) {
            return null
        }
        val color = tint.getColorForState(state, Color.TRANSPARENT)
        return PorterDuffColorFilter(color, tintMode)
    }

    override fun setTintList(tint: ColorStateList?) {
        mTint = tint
        mTintFilter = updateTintFilter(tint, mTintMode)
        invalidateSelf()
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        mTintMode = tintMode
        mTintFilter = updateTintFilter(mTint, tintMode)
        invalidateSelf()
    }

    override fun onStateChange(stateSet: IntArray): Boolean {
        if (mTint != null && mTintMode != null) {
            mTintFilter = updateTintFilter(mTint, mTintMode)
            return true
        }
        return false
    }

    override fun isStateful(): Boolean {
        return super.isStateful() || mTint != null && mTint!!.isStateful
    }

    /**
     * Sets whether this drawable is visible. If rendering of next frame is scheduled on draw current one (the default) then this method
     * only calls through to the super class's implementation.<br></br>
     * Otherwise (if [GifDrawableBuilder.setRenderingTriggeredOnDraw] was used with `true`)
     * when the drawable becomes invisible, it will pause its animation. A
     * subsequent change to visible with `restart` set to true will
     * restart the animation from the first frame. If `restart` is
     * false, the animation will resume from the most recent frame.
     *
     * @param visible true if visible, false otherwise
     * @param restart when visible and rendering is triggered on draw, true to force the animation to restart
     * from the first frame
     * @return true if the new visibility is different than its previous state
     */
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (!mIsRenderingTriggeredOnDraw) {
            if (visible) {
                if (restart) {
                    reset()
                }
                if (changed) {
                    start()
                }
            } else if (changed) {
                stop()
            }
        }
        return changed
    }

    val currentFrameIndex: Int
        /**
         * Returns zero-based index of recently rendered frame in given loop or -1 when drawable is recycled.
         *
         * @return index of recently rendered frame or -1 when drawable is recycled
         */
        get() = mNativeInfoHandle.currentFrameIndex
    val currentLoop: Int
        /**
         * Returns zero-based index of currently played animation loop. If animation is infinite or
         * drawable is recycled 0 is returned.
         *
         * @return index of currently played animation loop
         */
        get() {
            val currentLoop = mNativeInfoHandle.currentLoop
            return if (currentLoop == 0 || currentLoop < mNativeInfoHandle.loopCount) {
                currentLoop
            } else {
                currentLoop - 1
            }
        }
    val isAnimationCompleted: Boolean
        /**
         * Returns whether all animation loops has ended. If drawable is recycled false is returned.
         *
         * @return true if all animation loops has ended
         */
        get() = mNativeInfoHandle.isAnimationCompleted

    /**
     * Returns duration of the given frame (in milliseconds). If there is no data (no Graphics
     * Control Extension blocks or drawable is recycled) 0 is returned.
     *
     * @param index index of the frame
     * @return duration of the given frame in milliseconds
     * @throws IndexOutOfBoundsException if index &lt; 0 or index &gt;= number of frames
     */
    @Throws(IndexOutOfBoundsException::class)
    fun getFrameDuration(@IntRange(from = 0) index: Int): Int {
        return mNativeInfoHandle.getFrameDuration(index)
    }

    @get:FloatRange(from = 0.0)
    var cornerRadius: Float
        /**
         * @return The corner radius applied when drawing this drawable. 0 when drawable is not rounded.
         */
        get() {
            return if (mTransform is CornerRadiusTransform) {
                (mTransform as CornerRadiusTransform).cornerRadius
            } else 0.0F
        }
        /**
         * Sets the corner radius to be applied when drawing the bitmap.
         * Note that changing corner radius will cause replacing current [Paint] shader by [BitmapShader].
         * Transform set by [.setTransform] will also be replaced.
         *
         * @param cornerRadius corner radius or 0 to remove rounding
         */
        set(cornerRadius) {
            mTransform = CornerRadiusTransform(cornerRadius)
            (mTransform as CornerRadiusTransform).onBoundsChange(mDstRect)
        }
    var transform: Transform?
        /**
         * @return The current [Transform] implementation that customizes
         * how the GIF's current Bitmap is drawn or null if nothing has been set.
         */
        get() = mTransform
        /**
         * Specify a [Transform] implementation to customize how the GIF's current Bitmap is drawn.
         *
         * @param transform new [Transform] or null to remove current one
         */
        set(transform) {
            mTransform = transform
            if (mTransform != null) {
                mTransform!!.onBoundsChange(mDstRect)
            }
        }

    companion object {
        /**
         * An [GifDrawable.GifDrawable] wrapper but returns null
         * instead of throwing exception if creation fails.
         *
         * @param res        resources to read from
         * @param resourceId resource id
         * @return correct drawable or null if creation failed
         */
        fun createFromResource(res: Resources, @RawRes @DrawableRes resourceId: Int): GifDrawable? {
            return try {
                GifDrawable(res, resourceId)
            } catch (ignored: IOException) {
                null
            }
        }
    }
}