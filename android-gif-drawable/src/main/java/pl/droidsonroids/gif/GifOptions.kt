package pl.droidsonroids.gif

import androidx.annotation.IntRange

/**
 * Options controlling various GIF parameters similar to
 * [android.graphics.BitmapFactory.Options]
 */
class GifOptions {
    var inSampleSize = 0.toChar()

    /**
     * Indicates whether the content is opaque. GIF that is known to be opaque can
     * take a faster drawing case than non-opaque one, since alpha channel processing may be skipped.
     *
     *
     * Common usage is setting this to true when view where GIF is displayed is known to be non-transparent
     * and its background is irrelevant.In such case even if GIF contains transparent areas,
     * they will appear black.
     *
     *
     * See also [GifTextureView.setOpaque].
     * Default value is `false`, which means that content can be transparent.
     *
     * @param inIsOpaque whether the content is opaque
     */
    var inIsOpaque = false

    init {
        reset()
    }

    private fun reset() {
        inSampleSize = 1.toChar()
        inIsOpaque = false
    }

    /**
     * If set to a value `> 1`, requests the decoder to subsample the original
     * frames, returning a smaller frame buffer to save memory. The sample size is
     * the number of pixels in either dimension that correspond to a single
     * pixel in the decoded bitmap. For example, inSampleSize == 4 returns
     * an image that is 1/4 the width/height of the original, and 1/16 the
     * number of pixels. Values outside range `<1, 65635>` are treated as 1.
     * Unlike [android.graphics.BitmapFactory.Options.inSampleSize]
     * values which are not powers of 2 are also supported.
     * Default value is 1.
     *
     * @param inSampleSize the sample size
     */
    fun setInSampleSize(
        @IntRange(
            from = 1,
            to = Character.MAX_VALUE.code.toLong()
        ) inSampleSize: Int
    ) {
        if (inSampleSize < 1 || inSampleSize > Character.MAX_VALUE.code) {
            this.inSampleSize = 1.toChar()
        } else {
            this.inSampleSize = inSampleSize.toChar()
        }
    }

    fun setFrom(source: GifOptions?) {
        if (source == null) {
            reset()
        } else {
            inIsOpaque = source.inIsOpaque
            inSampleSize = source.inSampleSize
        }
    }
}