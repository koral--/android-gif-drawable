package pl.droidsonroids.gif

import android.content.Context
import android.content.res.Resources.NotFoundException
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.TextView
import androidx.annotation.RequiresApi
import pl.droidsonroids.gif.GifViewUtils.GifViewAttributes
import pl.droidsonroids.gif.GifViewUtils.applyLoopCount
import java.io.IOException

/**
 * A [TextView] which handles GIFs as compound drawables. NOTE:
 * `android:drawableStart` and `android:drawableEnd` from XML are
 * not supported but can be set using
 * [.setCompoundDrawablesRelativeWithIntrinsicBounds]
 *
 * @author koral--
 */
class GifTextView : TextView {
    private var viewAttributes: GifViewAttributes? = null

    /**
     * A corresponding superclass constructor wrapper.
     *
     * @param context
     */
    constructor(context: Context?) : super(context)

    /**
     * Like equivalent from superclass but also try to interpret compound drawables defined in XML
     * attributes as GIFs.
     *
     * @param context
     * @param attrs
     * @see TextView#TextView
     */
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs, 0, 0)
    }

    /**
     * Like equivalent from superclass but also try to interpret compound drawables defined in XML
     * attributes as GIFs.
     *
     * @param context
     * @param attrs
     * @param defStyle
     * @see TextView#TextView
     */
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(attrs, defStyle, 0)
    }

    /**
     * Like equivalent from superclass but also try to interpret compound drawables defined in XML
     * attributes as GIFs.
     *
     * @param context
     * @param attrs
     * @param defStyle
     * @param defStyleRes
     * @see TextView#TextView
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyle,
        defStyleRes
    ) {
        init(attrs, defStyle, defStyleRes)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int, defStyleRes: Int) {
        if (attrs != null) {
            val left = getGifOrDefaultDrawable(
                attrs.getAttributeResourceValue(
                    GifViewUtils.ANDROID_NS,
                    "drawableLeft",
                    0
                )
            )
            val top = getGifOrDefaultDrawable(
                attrs.getAttributeResourceValue(
                    GifViewUtils.ANDROID_NS,
                    "drawableTop",
                    0
                )
            )
            val right = getGifOrDefaultDrawable(
                attrs.getAttributeResourceValue(
                    GifViewUtils.ANDROID_NS,
                    "drawableRight",
                    0
                )
            )
            val bottom = getGifOrDefaultDrawable(
                attrs.getAttributeResourceValue(
                    GifViewUtils.ANDROID_NS,
                    "drawableBottom",
                    0
                )
            )
            var start = getGifOrDefaultDrawable(
                attrs.getAttributeResourceValue(
                    GifViewUtils.ANDROID_NS,
                    "drawableStart",
                    0
                )
            )
            var end = getGifOrDefaultDrawable(
                attrs.getAttributeResourceValue(
                    GifViewUtils.ANDROID_NS,
                    "drawableEnd",
                    0
                )
            )
            if (layoutDirection == LAYOUT_DIRECTION_LTR) {
                if (start == null) {
                    start = left
                }
                if (end == null) {
                    end = right
                }
            } else {
                if (start == null) {
                    start = right
                }
                if (end == null) {
                    end = left
                }
            }
            setCompoundDrawablesRelativeWithIntrinsicBounds(start, top, end, bottom)
            background = getGifOrDefaultDrawable(
                attrs.getAttributeResourceValue(
                    GifViewUtils.ANDROID_NS,
                    "background",
                    0
                )
            )
            viewAttributes = GifViewAttributes(this, attrs, defStyle, defStyleRes)
            applyGifViewAttributes()
        }
        viewAttributes = GifViewAttributes()
    }

    private fun applyGifViewAttributes() {
        if (viewAttributes!!.mLoopCount < 0) {
            return
        }
        for (drawable in compoundDrawables) {
            applyLoopCount(viewAttributes!!.mLoopCount, drawable)
        }
        for (drawable in compoundDrawablesRelative) {
            applyLoopCount(viewAttributes!!.mLoopCount, drawable)
        }
        applyLoopCount(viewAttributes!!.mLoopCount, background)
    }

    @Suppress("deprecation") //Resources#getDrawable(int)
    private fun getGifOrDefaultDrawable(resId: Int): Drawable? {
        if (resId == 0) {
            return null
        }
        val resources = resources
        val resourceTypeName = resources.getResourceTypeName(resId)
        if (!isInEditMode && GifViewUtils.SUPPORTED_RESOURCE_TYPE_NAMES.contains(resourceTypeName)) {
            try {
                return GifDrawable(resources, resId)
            } catch (ignored: IOException) {
                // ignored
            } catch (ignored: NotFoundException) {
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            resources.getDrawable(resId, context.theme)
        } else {
            resources.getDrawable(resId)
        }
    }

    override fun setCompoundDrawablesWithIntrinsicBounds(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        setCompoundDrawablesWithIntrinsicBounds(
            getGifOrDefaultDrawable(left),
            getGifOrDefaultDrawable(top),
            getGifOrDefaultDrawable(right),
            getGifOrDefaultDrawable(bottom)
        )
    }

    override fun setCompoundDrawablesRelativeWithIntrinsicBounds(
        start: Int,
        top: Int,
        end: Int,
        bottom: Int
    ) {
        setCompoundDrawablesRelativeWithIntrinsicBounds(
            getGifOrDefaultDrawable(start),
            getGifOrDefaultDrawable(top),
            getGifOrDefaultDrawable(end),
            getGifOrDefaultDrawable(bottom)
        )
    }

    override fun onSaveInstanceState(): Parcelable {
        val savedDrawables = arrayOfNulls<Drawable>(7)
        if (viewAttributes?.freezesAnimation == true) {
            val compoundDrawables = compoundDrawables
            System.arraycopy(compoundDrawables, 0, savedDrawables, 0, compoundDrawables.size)
            val compoundDrawablesRelative = compoundDrawablesRelative
            savedDrawables[4] = compoundDrawablesRelative[0] //start
            savedDrawables[5] = compoundDrawablesRelative[2] //end
            savedDrawables[6] = background
        }
        return GifViewSavedState(super.onSaveInstanceState(), *savedDrawables)
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is GifViewSavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        val compoundDrawables = compoundDrawables
        state.restoreState(compoundDrawables[0], 0)
        state.restoreState(compoundDrawables[1], 1)
        state.restoreState(compoundDrawables[2], 2)
        state.restoreState(compoundDrawables[3], 3)
        val compoundDrawablesRelative = compoundDrawablesRelative
        state.restoreState(compoundDrawablesRelative[0], 4)
        state.restoreState(compoundDrawablesRelative[2], 5)
        state.restoreState(background, 6)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setCompoundDrawablesVisible(true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        setCompoundDrawablesVisible(false)
    }

    override fun setBackgroundResource(resId: Int) {
        background = getGifOrDefaultDrawable(resId)
    }

    private fun setCompoundDrawablesVisible(visible: Boolean) {
        setDrawablesVisible(compoundDrawables, visible)
        setDrawablesVisible(compoundDrawablesRelative, visible)
    }

    /**
     * Sets whether animation position is saved in [.onSaveInstanceState] and restored
     * in [.onRestoreInstanceState]. This is applicable to all compound drawables.
     *
     * @param freezesAnimation whether animation position is saved
     */
    fun setFreezesAnimation(freezesAnimation: Boolean) {
        viewAttributes!!.freezesAnimation = freezesAnimation
    }

    companion object {
        private fun setDrawablesVisible(drawables: Array<Drawable?>, visible: Boolean) {
            for (drawable in drawables) {
                drawable?.setVisible(visible, false)
            }
        }
    }
}