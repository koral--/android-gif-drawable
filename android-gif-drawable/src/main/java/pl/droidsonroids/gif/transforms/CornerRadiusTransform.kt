package pl.droidsonroids.gif.transforms

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.FloatRange
import kotlin.math.max

/**
 * [Transform] which adds rounded corners.
 *
 * @param cornerRadius corner radius, may be 0.
 */
class CornerRadiusTransform(@FloatRange(from = 0.0) cornerRadius: Float) : Transform {
    private var mCornerRadius = 0f
    private var mShader: Shader? = null

    /**
     * Returns current transform bounds - latest received by [.onBoundsChange].
     *
     * @return current transform bounds
     */
    private val mDstRectF = RectF()

    init {
        setCornerRadiusSafely(cornerRadius)
    }

    private fun setCornerRadiusSafely(@FloatRange(from = 0.0) cornerRadius: Float) {
        val radius = max(0f, cornerRadius)
        if (radius != mCornerRadius) {
            mCornerRadius = radius
            mShader = null
        }
    }

    /**
     * Sets the corner radius to be applied when drawing the bitmap.
     *
     * @param cornerRadius corner radius or 0 to remove rounding
     * @return The corner radius applied when drawing this drawable. 0 when drawable is not rounded.
     */
    @get:FloatRange(from = 0.0)
    var cornerRadius: Float
        get() = mCornerRadius
        set(cornerRadius) {
            setCornerRadiusSafely(cornerRadius)
        }

    override fun onBoundsChange(bounds: Rect) {
        mDstRectF.set(bounds)
        mShader = null
    }

    override fun onDraw(canvas: Canvas, paint: Paint, buffer: Bitmap) {
        if (mCornerRadius == 0f) {
            canvas.drawBitmap(buffer, null, mDstRectF, paint)
            return
        }
        if (mShader == null) {
            buffer.let {
                mShader = BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val shaderMatrix = Matrix()
                shaderMatrix.setTranslate(mDstRectF.left, mDstRectF.top)
                shaderMatrix.preScale(mDstRectF.width() / it.width, mDstRectF.height() / it.height)
                (mShader as BitmapShader).setLocalMatrix(shaderMatrix)
            }
        }
        paint.shader = mShader
        canvas.drawRoundRect(mDstRectF, mCornerRadius, mCornerRadius, paint)
    }
}