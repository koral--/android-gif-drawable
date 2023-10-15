package pl.droidsonroids.gif

import android.graphics.Bitmap
import androidx.annotation.IntRange
import kotlin.jvm.Throws

/**
 * GifDecoder allows lightweight access to GIF frames, without wrappers like Drawable or View.
 * [Bitmap] with size equal to or greater than size of the GIF is needed.
 * For access only metadata (size, number of frames etc.) without pixels see [GifAnimationMetaData].
 *
 *
 * @constructor new GifDecoder
 * @param inputSource source
 * @param options     null-ok; options controlling subsampling and opacity
 * @throws IOException when creation fails
 *
 */

class GifDecoder @JvmOverloads constructor(inputSource: InputSource, options: GifOptions? = null) {
    //TODO extract common container
    private val mGifInfoHandle: GifInfoHandle

    init {
        mGifInfoHandle = inputSource.open()
        if (options != null) {
            mGifInfoHandle.setOptions(options.inSampleSize, options.inIsOpaque)
        }
    }

    val comment: String
        /**
         * See [GifDrawable.getComment]
         *
         * @return GIF comment
         */
        get() = mGifInfoHandle.comment
    val loopCount: Int
        /**
         * See [GifDrawable.getLoopCount]
         *
         * @return loop count, 0 means that animation is infinite
         */
        get() = mGifInfoHandle.loopCount
    val sourceLength: Long
        /**
         * See [GifDrawable.getInputSourceByteCount]
         *
         * @return number of bytes backed by input source or -1 if it is unknown
         */
        get() = mGifInfoHandle.inputSourceByteCount

    /**
     * See [GifDrawable.seekTo]
     *
     * @param position position to seek to in milliseconds
     * @param buffer   the frame buffer
     * @throws IllegalArgumentException if `position < 0 `or `buffer` is recycled
     */
    @Throws(IllegalArgumentException::class)
    fun seekToTime(@IntRange(from = 0, to = Int.MAX_VALUE.toLong()) position: Int, buffer: Bitmap) {
        checkBuffer(buffer)
        mGifInfoHandle.seekToTime(position, buffer)
    }

    /**
     * See [GifDrawable.seekToFrame]
     *
     * @param frameIndex position to seek to in milliseconds
     * @param buffer     the frame buffer
     * @throws IllegalArgumentException if `frameIndex < 0` or `buffer` is recycled
     */
    @Throws(IllegalArgumentException::class)
    fun seekToFrame(
        @IntRange(from = 0, to = Int.MAX_VALUE.toLong()) frameIndex: Int,
        buffer: Bitmap
    ) {
        checkBuffer(buffer)
        mGifInfoHandle.seekToFrame(frameIndex, buffer)
    }

    val allocationByteCount: Long
        /**
         * See [GifDrawable.getAllocationByteCount]
         *
         * @return possible size of the memory needed to store pixels of this object
         */
        get() = mGifInfoHandle.allocationByteCount

    /**
     * See [GifDrawable.getFrameDuration]
     *
     * @param index index of the frame
     * @return duration of the given frame in milliseconds
     * @throws IndexOutOfBoundsException if `index < 0 || index >= <number of frames>`
     */
    @Throws(IndexOutOfBoundsException::class)
    fun getFrameDuration(@IntRange(from = 0) index: Int): Int {
        return mGifInfoHandle.getFrameDuration(index)
    }

    val duration: Int
        /**
         * See [GifDrawable.getDuration]
         *
         * @return duration of of one loop the animation in milliseconds. Result is always multiple of 10.
         */
        get() = mGifInfoHandle.duration
    val width: Int
        /**
         * @return width od the GIF canvas in pixels
         */
        get() = mGifInfoHandle.width
    val height: Int
        /**
         * @return height od the GIF canvas in pixels
         */
        get() = mGifInfoHandle.height
    val numberOfFrames: Int
        /**
         * @return number of frames in GIF, at least one
         */
        get() = mGifInfoHandle.numberOfFrames
    val isAnimated: Boolean
        /**
         * @return true if GIF is animated (has at least 2 frames and positive duration), false otherwise
         */
        get() = mGifInfoHandle.numberOfFrames > 1 && duration > 0

    /**
     * See [GifDrawable.recycle]
     */
    fun recycle() {
        mGifInfoHandle.recycle()
    }

    private fun checkBuffer(buffer: Bitmap) {
        require(!buffer.isRecycled) { "Bitmap is recycled" }
        require(!(buffer.width < mGifInfoHandle.width || buffer.height < mGifInfoHandle.height)) { "Bitmap ia too small, size must be greater than or equal to GIF size" }
        require(buffer.config == Bitmap.Config.ARGB_8888) { "Only Config.ARGB_8888 is supported. Current bitmap config: " + buffer.config }
    }
}