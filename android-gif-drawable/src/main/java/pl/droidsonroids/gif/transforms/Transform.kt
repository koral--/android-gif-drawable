package pl.droidsonroids.gif.transforms

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * Interface to support clients performing custom transformations before the current GIF Bitmap is drawn.
 */
interface Transform {
    /**
     * Called by [GifDrawable] when its bounds changes by [GifDrawable.onBoundsChange]
     * and when transform is associated using [GifDrawable.setTransform].
     * In this latter case the latest [GifDrawable] bounds (empty [Rect] if they were not set yet).
     * @param bounds new bounds
     */
    fun onBoundsChange(bounds: Rect?)

    /**
     * Called by [GifDrawable] when its [GifDrawable.draw] is called.
     *
     * @param canvas The canvas supplied by the system to draw on.
     * @param paint  The paint to use for custom drawing.
     * @param buffer The current Bitmap for the GIF.
     */
    fun onDraw(canvas: Canvas?, paint: Paint?, buffer: Bitmap?)
}