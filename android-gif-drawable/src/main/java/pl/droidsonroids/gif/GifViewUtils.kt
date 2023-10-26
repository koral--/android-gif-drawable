package pl.droidsonroids.gif

import android.content.res.Resources
import android.content.res.Resources.NotFoundException
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import java.io.IOException

internal object GifViewUtils {
    const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    @JvmField
    val SUPPORTED_RESOURCE_TYPE_NAMES: List<String> = mutableListOf("raw", "drawable", "mipmap")
    fun initImageView(
        view: ImageView,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ): GifImageViewAttributes {
        if (attrs != null && !view.isInEditMode) {
            val viewAttributes = GifImageViewAttributes(view, attrs, defStyleAttr, defStyleRes)
            val loopCount = viewAttributes.mLoopCount
            if (loopCount >= 0) {
                applyLoopCount(loopCount, view.drawable)
                applyLoopCount(loopCount, view.background)
            }
            return viewAttributes
        }
        return GifImageViewAttributes()
    }

    fun applyLoopCount(loopCount: Int, drawable: Drawable?) {
        if (drawable is GifDrawable) {
            drawable.loopCount = loopCount
        }
    }

    fun setResource(view: ImageView, isSrc: Boolean, resId: Int): Boolean {
        val res = view.resources
        if (res != null) {
            try {
                val resourceTypeName = res.getResourceTypeName(resId)
                if (!SUPPORTED_RESOURCE_TYPE_NAMES.contains(resourceTypeName)) {
                    return false
                }
                val d = GifDrawable(res, resId)
                if (isSrc) {
                    view.setImageDrawable(d)
                } else {
                    view.background = d
                }
                return true
            } catch (ignored: IOException) {
                //ignored
            } catch (ignored: NotFoundException) {
            }
        }
        return false
    }

    fun setGifImageUri(imageView: ImageView, uri: Uri?): Boolean {
        if (uri != null) {
            try {
                imageView.setImageDrawable(GifDrawable(imageView.context.contentResolver, uri))
                return true
            } catch (ignored: IOException) {
                //ignored
            }
        }
        return false
    }

    fun getDensityScale(res: Resources, @DrawableRes @RawRes id: Int): Float {
        val value = TypedValue()
        res.getValue(id, value, true)
        val resourceDensity = value.density
        val density = if (resourceDensity == TypedValue.DENSITY_DEFAULT) {
            DisplayMetrics.DENSITY_DEFAULT
        } else if (resourceDensity != TypedValue.DENSITY_NONE) {
            resourceDensity
        } else {
            0
        }
        val targetDensity = res.displayMetrics.densityDpi
        return if (density > 0 && targetDensity > 0) {
            targetDensity.toFloat() / density
        } else 1f
    }

    internal open class GifViewAttributes {
        var freezesAnimation: Boolean
        val mLoopCount: Int

        constructor(view: View, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
            val gifViewAttributes = view.context.obtainStyledAttributes(
                attrs,
                R.styleable.GifView,
                defStyleAttr,
                defStyleRes
            )
            freezesAnimation =
                gifViewAttributes.getBoolean(R.styleable.GifView_freezesAnimation, false)
            mLoopCount = gifViewAttributes.getInt(R.styleable.GifView_loopCount, -1)
            gifViewAttributes.recycle()
        }

        constructor() {
            freezesAnimation = false
            mLoopCount = -1
        }
    }

    internal class GifImageViewAttributes : GifViewAttributes {
        val mSourceResId: Int
        val mBackgroundResId: Int

        constructor(
            view: ImageView,
            attrs: AttributeSet,
            defStyleAttr: Int,
            defStyleRes: Int
        ) : super(view, attrs, defStyleAttr, defStyleRes) {
            mSourceResId = getResourceId(view, attrs, true)
            mBackgroundResId = getResourceId(view, attrs, false)
        }

        constructor() : super() {
            mSourceResId = 0
            mBackgroundResId = 0
        }

        companion object {
            private fun getResourceId(view: ImageView, attrs: AttributeSet, isSrc: Boolean): Int {
                val resId = attrs.getAttributeResourceValue(
                    ANDROID_NS,
                    if (isSrc) "src" else "background",
                    0
                )
                if (resId > 0) {
                    val resourceTypeName = view.resources.getResourceTypeName(resId)
                    if (SUPPORTED_RESOURCE_TYPE_NAMES.contains(resourceTypeName) && !setResource(
                            view,
                            isSrc,
                            resId
                        )
                    ) {
                        return resId
                    }
                }
                return 0
            }
        }
    }
}