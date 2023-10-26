package pl.droidsonroids.gif

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import pl.droidsonroids.gif.GifViewUtils.GifImageViewAttributes

/**
 * An [ImageButton] which tries treating background and src as [GifDrawable]
 *
 * @author koral--
 */
class GifImageButton : ImageButton {
    private var mFreezesAnimation = false

    /**
     * A corresponding superclass constructor wrapper.
     *
     * @param context
     * @see ImageButton.ImageButton
     */
    constructor(context: Context?) : super(context)

    /**
     * Like equivalent from superclass but also try to interpret src and background
     * attributes as [GifDrawable].
     *
     * @param context
     * @param attrs
     * @see ImageButton.ImageButton
     */
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        postInit(GifViewUtils.initImageView(this, attrs, 0, 0))
    }

    /**
     * Like equivalent from superclass but also try to interpret src and background
     * attributes as GIFs.
     *
     * @param context
     * @param attrs
     * @param defStyle
     * @see ImageButton.ImageButton
     */
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        postInit(GifViewUtils.initImageView(this, attrs, defStyle, 0))
    }

    /**
     * Like equivalent from superclass but also try to interpret src and background
     * attributes as GIFs.
     *
     * @param context
     * @param attrs
     * @param defStyle
     * @param defStyleRes
     * @see ImageButton.ImageButton
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyle,
        defStyleRes
    ) {
        postInit(GifViewUtils.initImageView(this, attrs, defStyle, defStyleRes))
    }

    private fun postInit(result: GifImageViewAttributes) {
        mFreezesAnimation = result.freezesAnimation
        if (result.mSourceResId > 0) {
            super.setImageResource(result.mSourceResId)
        }
        if (result.mBackgroundResId > 0) {
            super.setBackgroundResource(result.mBackgroundResId)
        }
    }

    /**
     * Sets the content of this GifImageView to the specified Uri.
     * If uri destination is not a GIF then [android.widget.ImageView.setImageURI]
     * is called as fallback.
     * For supported URI schemes see: [android.content.ContentResolver.openAssetFileDescriptor].
     *
     * @param uri The Uri of an image
     */
    override fun setImageURI(uri: Uri?) {
        if (!GifViewUtils.setGifImageUri(this, uri)) {
            super.setImageURI(uri)
        }
    }

    override fun setImageResource(resId: Int) {
        if (!GifViewUtils.setResource(this, true, resId)) {
            super.setImageResource(resId)
        }
    }

    override fun setBackgroundResource(resId: Int) {
        if (!GifViewUtils.setResource(this, false, resId)) {
            super.setBackgroundResource(resId)
        }
    }

    public override fun onSaveInstanceState(): Parcelable {
        val source = if (mFreezesAnimation) drawable else null
        val background = if (mFreezesAnimation) background else null
        return GifViewSavedState(super.onSaveInstanceState(), source, background)
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is GifViewSavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        state.restoreState(drawable, 0)
        state.restoreState(background, 1)
    }

    /**
     * Sets whether animation position is saved in [.onSaveInstanceState] and restored
     * in [.onRestoreInstanceState]
     *
     * @param freezesAnimation whether animation position is saved
     */
    fun setFreezesAnimation(freezesAnimation: Boolean) {
        mFreezesAnimation = freezesAnimation
    }
}