package pl.droidsonroids.gif

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView.ScaleType
import androidx.annotation.FloatRange
import androidx.annotation.RequiresApi
import pl.droidsonroids.gif.GifIOException.Companion.fromCode
import pl.droidsonroids.gif.GifViewUtils.GifViewAttributes
import pl.droidsonroids.gif.InputSource.AssetSource
import pl.droidsonroids.gif.InputSource.ResourcesSource
import java.io.IOException
import java.lang.ref.WeakReference
import kotlin.math.min

/**
 *
 * [TextureView] which can display animated GIFs. GifTextureView can only be used in a
 * hardware accelerated window. When rendered in software, GifTextureView will draw nothing.
 *
 * GIF source can be specified in XML or by calling [.setInputSource]
 * <pre> `<pl.droidsonroids.gif.GifTextureView
 * xmlns:app="http://schemas.android.com/apk/res-auto"
 * android:id="@+id/gif_texture_view"
 * android:scaleType="fitEnd"
 * app:gifSource="@drawable/animation"
 * android:layout_width="match_parent"
 * android:layout_height="match_parent"> `
</pre> *
 * Note that **src** attribute comes from app namespace (you can call it whatever you want) not from
 * android one. Drawable, raw, mipmap resources and assets can be specified through XML. If value is a string
 * (referenced from resources or entered directly) it will be treated as an asset.
 *
 * Unlike [TextureView] GifTextureView is transparent by default, but it can be changed by
 * [.setOpaque].
 * You can use scale types the same way as in [android.widget.ImageView].
 */
class GifTextureView : TextureView {
    private var mScaleType = ScaleType.FIT_CENTER
    private val mTransform = Matrix()
    private var mInputSource: InputSource? = null
    private var mRenderThread: RenderThread? = null
    private var mSpeedFactor = 1f
    private var viewAttributes: GifViewAttributes? = null

    constructor(context: Context?) : super(context!!) {
        init(null, 0, 0)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!, attrs
    ) {
        init(attrs, 0, 0)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!, attrs, defStyleAttr
    ) {
        init(attrs, defStyleAttr, 0)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(
        context!!, attrs, defStyleAttr, defStyleRes
    ) {
        init(attrs, defStyleAttr, defStyleRes)
    }

    private fun init(attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        if (attrs != null) {
            val scaleTypeIndex =
                attrs.getAttributeIntValue(GifViewUtils.ANDROID_NS, "scaleType", -1)
            if (scaleTypeIndex >= 0 && scaleTypeIndex < sScaleTypeArray.size) {
                mScaleType = sScaleTypeArray[scaleTypeIndex]
            }
            val textureViewAttributes = context.obtainStyledAttributes(
                attrs,
                R.styleable.GifTextureView,
                defStyleAttr,
                defStyleRes
            )
            mInputSource = findSource(textureViewAttributes)
            super.setOpaque(
                textureViewAttributes.getBoolean(
                    R.styleable.GifTextureView_isOpaque,
                    false
                )
            )
            textureViewAttributes.recycle()
            viewAttributes = GifViewAttributes(this, attrs, defStyleAttr, defStyleRes)
        } else {
            super.setOpaque(false)
            viewAttributes = GifViewAttributes()
        }
        if (!isInEditMode) {
            mRenderThread = RenderThread(this)
            if (mInputSource != null) {
                mRenderThread!!.start()
            }
        }
    }

    /**
     * Always throws [UnsupportedOperationException]. Changing [SurfaceTextureListener]
     * is not supported.
     *
     * @param listener ignored
     */
    override fun setSurfaceTextureListener(listener: SurfaceTextureListener?) {
        throw UnsupportedOperationException("Changing SurfaceTextureListener is not supported")
    }

    /**
     * Always returns null since changing [SurfaceTextureListener] is not supported.
     *
     * @return always null
     */
    override fun getSurfaceTextureListener(): SurfaceTextureListener? {
        return null
    }

    /**
     * Always throws [UnsupportedOperationException]. Changing [SurfaceTexture] is not
     * supported.
     *
     * @param surfaceTexture ignored
     */
    override fun setSurfaceTexture(surfaceTexture: SurfaceTexture) {
        throw UnsupportedOperationException("Changing SurfaceTexture is not supported")
    }

    private class RenderThread internal constructor(gifTextureView: GifTextureView) :
        Thread("GifRenderThread"), SurfaceTextureListener {
        val isSurfaceValid = ConditionVariable()
        var mGifInfoHandle = GifInfoHandle()
        var mIOException: IOException? = null
        var mSavedState: LongArray? = null
        private val mGifTextureViewReference: WeakReference<GifTextureView>

        init {
            mGifTextureViewReference = WeakReference(gifTextureView)
        }

        override fun run() {
            try {
                val gifTextureView = mGifTextureViewReference.get() ?: return
                mGifInfoHandle = gifTextureView.mInputSource?.open()!!
                mGifInfoHandle.setOptions(1.toChar(), gifTextureView.isOpaque)
                if (gifTextureView.viewAttributes != null && gifTextureView.viewAttributes!!.mLoopCount >= 0) {
                    mGifInfoHandle.loopCount = gifTextureView.viewAttributes!!.mLoopCount
                }
            } catch (ex: IOException) {
                mIOException = ex
                return
            }
            val gifTextureView = mGifTextureViewReference.get()
            if (gifTextureView == null) {
                mGifInfoHandle.recycle()
                return
            }
            gifTextureView.setSuperSurfaceTextureListener(this)
            val isSurfaceAvailable = gifTextureView.isAvailable
            isSurfaceValid.set(isSurfaceAvailable)
            if (isSurfaceAvailable) {
                gifTextureView.post(Runnable { gifTextureView.updateTextureViewSize(mGifInfoHandle) })
            }
            mGifInfoHandle.setSpeedFactor(gifTextureView.mSpeedFactor)
            while (!isInterrupted) {
                try {
                    isSurfaceValid.block()
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                    break
                }
                val currentGifTextureView = mGifTextureViewReference.get() ?: break
                val surfaceTexture = currentGifTextureView.surfaceTexture ?: continue
                val surface = Surface(surfaceTexture)
                try {
                    mGifInfoHandle.bindSurface(surface, mSavedState)
                } finally {
                    surface.release()
                }
            }
            mGifInfoHandle.recycle()
            mGifInfoHandle = GifInfoHandle()
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            val gifTextureView = mGifTextureViewReference.get()
            gifTextureView?.updateTextureViewSize(mGifInfoHandle)
            isSurfaceValid.open()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            //no-op
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            isSurfaceValid.close()
            mGifInfoHandle.postUnbindSurface()
            interrupt()
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            //no-op
        }

        fun dispose(gifTextureView: GifTextureView, drawer: PlaceholderDrawListener?) {
            isSurfaceValid.close()
            val listener: SurfaceTextureListener? =
                drawer?.let { PlaceholderDrawingSurfaceTextureListener(it) }
            gifTextureView.setSuperSurfaceTextureListener(listener)
            mGifInfoHandle.postUnbindSurface()
            interrupt()
        }
    }

    private fun setSuperSurfaceTextureListener(listener: SurfaceTextureListener?) {
        super.setSurfaceTextureListener(listener)
    }

    /**
     * Indicates whether the content of this GifTextureView is opaque. The
     * content is assumed to be **non-opaque** by default (unlike [TextureView].
     * View that is known to be opaque can take a faster drawing case than non-opaque one.<br></br>
     * Opacity change will cause animation to restart.
     *
     * @param opaque True if the content of this GifTextureView is opaque,
     * false otherwise
     */
    override fun setOpaque(opaque: Boolean) {
        if (opaque != isOpaque) {
            super.setOpaque(opaque)
            setInputSource(mInputSource)
        }
    }

    override fun onDetachedFromWindow() {
        mRenderThread?.dispose(this, null)
        super.onDetachedFromWindow()
        val surfaceTexture = surfaceTexture
        surfaceTexture?.release()
    }

    /**
     * Sets the source of the animation. Pass `null` to remove current source.
     * Equivalent of `setInputSource(inputSource, null)`.
     *
     * @param inputSource new animation source, may be null
     */
    @Synchronized
    fun setInputSource(inputSource: InputSource?) {
        setInputSource(inputSource, null)
    }

    /**
     * Sets the source of the animation and optionally placeholder drawer. Pass `null inputSource` to remove current source.
     * `placeholderDrawListener` is overwritten on `setInputSource(inputSource)` call.
     *
     * @param inputSource             new animation source, may be null
     * @param placeholderDrawListener placeholder draw listener, may be null
     */
    @Synchronized
    fun setInputSource(
        inputSource: InputSource?,
        placeholderDrawListener: PlaceholderDrawListener?
    ) {
        mRenderThread?.dispose(this, placeholderDrawListener)
        try {
            mRenderThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        mInputSource = inputSource
        mRenderThread = RenderThread(this)
        if (inputSource != null) {
            mRenderThread?.start()
        } else {
            clearSurface()
        }
    }

    private fun clearSurface() {
        val surfaceTexture = surfaceTexture
        if (surfaceTexture != null) {
            val surface = Surface(surfaceTexture)
            try {
                surface.unlockCanvasAndPost(surface.lockCanvas(null))
            } finally {
                surface.release()
            }
        }
    }

    /**
     * Equivalent of [GifDrawable.setSpeed].
     *
     * @param factor new speed factor, eg. 0.5f means half speed, 1.0f - normal, 2.0f - double speed
     * @throws IllegalArgumentException if `factor <= 0`
     * @see GifDrawable.setSpeed
     */
    fun setSpeed(@FloatRange(from = 0.0, fromInclusive = false) factor: Float) {
        mSpeedFactor = factor
        mRenderThread?.mGifInfoHandle?.setSpeedFactor(factor)
    }

    val iOException: IOException?
        /**
         * Returns last [IOException] occurred during loading or playing GIF (in such case only [GifIOException]
         * can be returned. Null is returned when source is not set, surface was not yet created or no error
         * occurred.
         *
         * @return exception occurred during loading or playing GIF or null
         */
        get() = if (mRenderThread?.mIOException != null) {
            mRenderThread?.mIOException
        } else {
            fromCode(mRenderThread?.mGifInfoHandle?.nativeErrorCode)
        }
    var scaleType: ScaleType
        /**
         * @return the current scale type in use by this View.
         * @see ScaleType
         */
        get() = mScaleType
        /**
         * Controls how the image should be resized or moved to match the size
         * of this GifTextureView.
         *
         * @param scaleType The desired scaling mode.
         */
        set(scaleType) {
            mScaleType = scaleType
            updateTextureViewSize(mRenderThread!!.mGifInfoHandle)
        }

    private fun updateTextureViewSize(gifInfoHandle: GifInfoHandle?) {

        if (gifInfoHandle == null) return

        val transform = Matrix()
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val scaleRef: Float
        val scaleX = gifInfoHandle.width / viewWidth
        val scaleY = gifInfoHandle.height / viewHeight
        val src = RectF(0f, 0f, gifInfoHandle.width.toFloat(), gifInfoHandle.height.toFloat())
        val dst = RectF(0f, 0f, viewWidth, viewHeight)
        when (mScaleType) {
            ScaleType.CENTER -> transform.setScale(scaleX, scaleY, viewWidth / 2, viewHeight / 2)
            ScaleType.CENTER_CROP -> {
                scaleRef = 1 / min(scaleX, scaleY)
                transform.setScale(
                    scaleRef * scaleX,
                    scaleRef * scaleY,
                    viewWidth / 2,
                    viewHeight / 2
                )
            }

            ScaleType.CENTER_INSIDE -> {
                scaleRef =
                    if (gifInfoHandle.width <= viewWidth && gifInfoHandle.height <= viewHeight) {
                        1.0f
                    } else {
                        min(1 / scaleX, 1 / scaleY)
                    }
                transform.setScale(
                    scaleRef * scaleX,
                    scaleRef * scaleY,
                    viewWidth / 2,
                    viewHeight / 2
                )
            }

            ScaleType.FIT_CENTER -> {
                transform.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER)
                transform.preScale(scaleX, scaleY)
            }

            ScaleType.FIT_END -> {
                transform.setRectToRect(src, dst, Matrix.ScaleToFit.END)
                transform.preScale(scaleX, scaleY)
            }

            ScaleType.FIT_START -> {
                transform.setRectToRect(src, dst, Matrix.ScaleToFit.START)
                transform.preScale(scaleX, scaleY)
            }

            ScaleType.FIT_XY -> return
            ScaleType.MATRIX -> {
                transform.set(mTransform)
                transform.preScale(scaleX, scaleY)
            }
        }
        super.setTransform(transform)
    }

    /**
     * Wrapper of [.setTransform]. Introduced to preserve the same API as in
     * [GifImageView].
     *
     * @param matrix The transform to apply to the content of this view.
     */
    fun setImageMatrix(matrix: Matrix?) {
        setTransform(matrix)
    }

    /**
     * Works like [TextureView.setTransform] but transform will take effect only if
     * scale type is set to [ScaleType.MATRIX] through XML attribute or via [.setScaleType]
     *
     * @param transform The transform to apply to the content of this view.
     */
    override fun setTransform(transform: Matrix?) {
        mTransform.set(transform)
        updateTextureViewSize(mRenderThread!!.mGifInfoHandle)
    }

    /**
     * Returns the transform associated with this texture view, either set explicitly by [.setTransform]
     * or computed according to the current scale type.
     *
     * @param transform The [Matrix] in which to copy the current transform. Can be null.
     * @return The specified matrix if not null or a new [Matrix] instance otherwise.
     * @see .setTransform
     * @see .setScaleType
     */
    override fun getTransform(transform: Matrix?): Matrix {
        var transform = transform
        if (transform == null) {
            transform = Matrix()
        }
        transform.set(mTransform)
        return transform
    }

    public override fun onSaveInstanceState(): Parcelable {
        mRenderThread?.mSavedState = mRenderThread?.mGifInfoHandle?.savedState
        return GifViewSavedState(
            super.onSaveInstanceState(),
            if (viewAttributes?.freezesAnimation == true) mRenderThread?.mSavedState else null
        )
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is GifViewSavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        mRenderThread?.mSavedState = state.mStates[0]
    }

    /**
     * Sets whether animation position is saved in [.onSaveInstanceState] and restored
     * in [.onRestoreInstanceState]
     *
     * @param freezesAnimation whether animation position is saved
     */
    fun setFreezesAnimation(freezesAnimation: Boolean) {
        viewAttributes?.freezesAnimation = freezesAnimation
    }

    /**
     * This listener can be used to be notified when the [GifTextureView] content placeholder can be drawn.
     * Placeholder is displayed before proper input source is loaded and remains visible when input source loading fails.
     */
    interface PlaceholderDrawListener {
        /**
         * Called when surface is ready and placeholder has to be drawn.
         * It may occur more than once (eg. if `View` visibility is toggled before input source is loaded)
         * or never (eg. when `View` is never visible).<br></br>
         * Note that it is an error to use `canvas` after this method return.
         *
         * @param canvas canvas to draw into
         */
        fun onDrawPlaceholder(canvas: Canvas)
    }

    companion object {
        private val sScaleTypeArray = arrayOf(
            ScaleType.MATRIX,
            ScaleType.FIT_XY,
            ScaleType.FIT_START,
            ScaleType.FIT_CENTER,
            ScaleType.FIT_END,
            ScaleType.CENTER,
            ScaleType.CENTER_CROP,
            ScaleType.CENTER_INSIDE
        )

        private fun findSource(textureViewAttributes: TypedArray): InputSource? {
            val value = TypedValue()
            if (!textureViewAttributes.getValue(R.styleable.GifTextureView_gifSource, value)) {
                return null
            }
            if (value.resourceId != 0) {
                val resourceTypeName =
                    textureViewAttributes.resources.getResourceTypeName(value.resourceId)
                if (GifViewUtils.SUPPORTED_RESOURCE_TYPE_NAMES.contains(resourceTypeName)) {
                    return ResourcesSource(textureViewAttributes.resources, value.resourceId)
                } else require("string" == resourceTypeName) {
                    ("Expected string, drawable, mipmap or raw resource type. '" + resourceTypeName
                            + "' is not supported")
                }
            }
            return AssetSource(textureViewAttributes.resources.assets, value.string.toString())
        }
    }
}