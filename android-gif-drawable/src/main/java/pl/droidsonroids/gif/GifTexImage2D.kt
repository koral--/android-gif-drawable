package pl.droidsonroids.gif

import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import kotlin.jvm.Throws

/**
 * Provides support for animated GIFs in OpenGL.
 * There are 2 possible usages:
 *
 *  1. Automatic animation according to timing defined in GIF file - [.startDecoderThread] and [.stopDecoderThread].
 *  1. Manual frame advancing - [.seekToFrame].
 *
 * Note that call [.seekToFrame] while decoder thread is running will cause frame change
 * but it can be immediately changed again by decoder thread.
 * <br></br>
 * Current frame can be copied to 2D texture when needed. See [.glTexImage2D] and [.glTexSubImage2D].
 */
class GifTexImage2D(inputSource: InputSource, options: GifOptions?) {
    private val mGifInfoHandle: GifInfoHandle

    /**
     * Constructs new GifTexImage2D.
     * Decoder thread is initially stopped, use [.startDecoderThread] to start it.
     *
     * @param inputSource source
     * @param options     null-ok; options controlling parameters like subsampling and opacity
     * @throws IOException when creation fails
     */
    init {
        var gifOptions = options
        if (gifOptions == null) {
            gifOptions = GifOptions()
        }
        mGifInfoHandle = inputSource.open()
        mGifInfoHandle.setOptions(gifOptions.inSampleSize, gifOptions.inIsOpaque)
        mGifInfoHandle.initTexImageDescriptor()
    }

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

    /**
     * Seeks to given frame
     *
     * @param index index of the frame
     * @throws IndexOutOfBoundsException if `index < 0 || index >= <number of frames>`
     */
    @Throws(IndexOutOfBoundsException::class)
    fun seekToFrame(@IntRange(from = 0) index: Int) {
        mGifInfoHandle.seekToFrameGL(index)
    }

    val numberOfFrames: Int
        /**
         * @return number of frames in GIF, at least one
         */
        get() = mGifInfoHandle.numberOfFrames
    val currentFrameIndex: Int
        /**
         * @return index of recently rendered frame or -1 if this object is recycled
         */
        get() = mGifInfoHandle.currentFrameIndex

    /**
     * Sets new animation speed factor. See [GifDrawable.setSpeed].
     *
     * @param factor new speed factor, eg. 0.5f means half speed, 1.0f - normal, 2.0f - double speed
     * @throws IllegalArgumentException if factor&lt;=0
     */
    @Throws(IllegalArgumentException::class)
    fun setSpeed(@FloatRange(from = 0.0, fromInclusive = false) factor: Float) {
        mGifInfoHandle.setSpeedFactor(factor)
    }

    /**
     * Equivalent of [android.opengl.GLES20.glTexImage2D].
     * Where `Buffer` contains pixels of the current frame.
     *
     * @param level  level-of-detail number
     * @param target target texture
     */
    fun glTexImage2D(target: Int, level: Int) {
        mGifInfoHandle.glTexImage2D(target, level)
    }

    /**
     * Equivalent of [android.opengl.GLES20.glTexSubImage2D].
     * Where `Buffer` contains pixels of the current frame.
     *
     * @param level  level-of-detail number
     * @param target target texture
     */
    fun glTexSubImage2D(target: Int, level: Int) {
        mGifInfoHandle.glTexSubImage2D(target, level)
    }

    /**
     * Creates frame buffer and starts decoding thread. Does nothing if already started.
     */
    fun startDecoderThread() {
        mGifInfoHandle.startDecoderThread()
    }

    /**
     * Stops decoder thread and releases frame buffer. Does nothing if already stopped.
     */
    fun stopDecoderThread() {
        mGifInfoHandle.stopDecoderThread()
    }

    /**
     * See [GifDrawable.recycle]. Decoder thread is stopped automatically.
     */
    fun recycle() {
        mGifInfoHandle.recycle()
    }

    val width: Int
        /**
         * @return width of the GIF canvas, 0 if recycled
         */
        get() = mGifInfoHandle.width
    val height: Int
        /**
         * @return height of the GIF canvas, 0 if recycled
         */
        get() = mGifInfoHandle.height
    val duration: Int
        /**
         * See [GifDrawable.getDuration]
         *
         * @return duration of of one loop the animation in milliseconds. Result is always multiple of 10.
         */
        get() = mGifInfoHandle.duration

    protected fun finalize() {
        recycle()
    }
}
